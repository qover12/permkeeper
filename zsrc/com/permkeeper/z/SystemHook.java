package com.permkeeper.z;

import android.app.NotificationChannel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.FileObserver;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Injected into system_server by the Zygisk module. Keeps a per-package baseline
 * (last observed state) and restores it when a "clear data" is confirmed
 * (ACTION_PACKAGE_DATA_CLEARED).
 */
public final class SystemHook {
    private static final String TAG = "PermKeeperZ";
    private static final String BASE_DIR = "/data/system/permkeeper";
    private static final String BASE_FILE = BASE_DIR + "/baseline.json";
    private static final String PERM_DIR = "/data/misc_de/0/apexdata/com.android.permission";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean PENDING = new AtomicBoolean(false);
    // FileObserver holds watchers via WeakReference internally -> keep strong refs.
    private static final List<FileObserver> OBSERVERS = new ArrayList<>();
    private static Rom sRom;
    private static String sMetaSig = "";

    public static void install() {
        log("install() pid=" + android.os.Process.myPid());
        sRom = Rom.detect();
        log("rom=" + sRom.name());
        new Thread(SystemHook::initLoop, "permkeeper-init").start();
    }

    private static void initLoop() {
        for (int i = 0; i < 600; i++) {
            Context ctx = systemContext();
            if (ctx != null && trySetup(ctx)) {
                log("ready (after ~" + (i * 500) + "ms)");
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
        }
        log("gave up waiting for system_server readiness");
    }

    private static boolean trySetup(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        if (pm == null) {
            return false;
        }
        try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        } catch (Throwable t) {
            return false;
        }
        scan(ctx);
        watch(PERM_DIR, null);
        for (String p : sRom.extraWatchPaths()) {
            watch(p, null);
        }
        watch("/data/app", null);                 // new installs
        watch("/data/system", "notification");    // notification prefs/channels
        registerClearReceiver(ctx);               // restore on clear-data only
        log("watching permission/notification stores + /data/app; clear-data receiver armed");
        return true;
    }

    private static void watch(String dir, String nameFilter) {
        try {
            FileObserver o = new FileObserver(dir,
                    FileObserver.MOVED_TO | FileObserver.CREATE | FileObserver.DELETE
                            | FileObserver.CLOSE_WRITE | FileObserver.MODIFY) {
                @Override
                public void onEvent(int event, String path) {
                    if (nameFilter != null) {
                        if (path == null || !path.contains(nameFilter)) {
                            return;
                        }
                        log("watch hit: " + dir + "/" + path + " ev=" + event);
                    }
                    onChange();
                }
            };
            o.startWatching();
            OBSERVERS.add(o);
        } catch (Throwable t) {
            log("watch " + dir + " failed: " + t);
        }
    }

    private static void registerClearReceiver(Context ctx) {
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_DATA_CLEARED);
            f.addDataScheme("package");
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    try {
                        String d = i.getDataString();
                        if (d == null) {
                            return;
                        }
                        String pkg = d.startsWith("package:") ? d.substring(8) : d;
                        JSONObject snap = null;
                        try {
                            snap = loadBaseline().optJSONObject(pkg);
                        } catch (Throwable t) {
                            log("clear-data capture failed: " + t);
                        }
                        onPackageCleared(pkg, snap);
                    } catch (Throwable t) {
                        log("dataCleared onReceive failed: " + t);
                    }
                }
            }, f);
        } catch (Throwable t) {
            log("registerClearReceiver failed: " + t);
        }
    }

    private static void onPackageCleared(String pkg, JSONObject snapJson) {
        EXEC.submit(() -> {
            try {
                Context ctx = systemContext();
                if (ctx == null) {
                    return;
                }
                Config cfg = Config.load();
                if (!cfg.covers(pkg)) {
                    return;
                }
                PackageInfo pi;
                try {
                    pi = ctx.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
                } catch (Throwable t) {
                    return;
                }
                if (pi.applicationInfo == null) {
                    return;
                }
                State snap = State.fromJson(snapJson);
                // HyperOS: PACKAGE_DATA_CLEARED can arrive BEFORE the runtime-permission reset.
                // Wait for the permission store to settle, then restore; verify + retry as a safety net.
                int rounds = 0;
                boolean ok = false;
                for (int r = 0; r < 3; r++) {
                    if (r == 0) {
                        waitPermStoreSettled();
                    } else {
                        try {
                            Thread.sleep(900);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    snap.restoreAll(ctx, pi, cfg, sRom);
                    rounds++;
                    State after = State.capture(ctx, pi, sRom);
                    if (after.perms.containsAll(snap.perms)) {
                        ok = true;
                        break;
                    }
                }
                // Keep baseline at the known-good snapshot (a scan may have run in between).
                try {
                    JSONObject base = loadBaseline();
                    base.put(pkg, snap.toJson());
                    saveBaseline(base);
                } catch (Throwable ignore) {
                }
                log("clear-data " + pkg + " restored rounds=" + rounds + (ok ? " ok" : " incomplete"));
            } catch (Throwable t) {
                log("onPackageCleared failed: " + t);
            }
        });
    }

    /** Newest mtime among the permission-store files (detects when a reset has settled). */
    private static long newestPermMtime() {
        long max = 0;
        try {
            File d = new File(PERM_DIR);
            File[] fs = d.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    long m = f.lastModified();
                    if (m > max) {
                        max = m;
                    }
                }
            }
            long m = d.lastModified();
            if (m > max) {
                max = m;
            }
        } catch (Throwable ignore) {
        }
        return max;
    }

    /** Block until the permission store has been quiet (>=1s) after at least ~1.8s, or timeout. */
    private static void waitPermStoreSettled() {
        long t0 = System.currentTimeMillis();
        long deadline = t0 + 9000;
        long last = newestPermMtime();
        int quiet = 0;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                return;
            }
            long t = newestPermMtime();
            if (t != last) {
                last = t;
                quiet = 0;
            } else {
                quiet++;
            }
            if (quiet >= 4 && System.currentTimeMillis() - t0 >= 1800) {
                return;
            }
        }
    }

    private static void onChange() {
        if (PENDING.getAndSet(true)) {
            return;
        }
        EXEC.submit(() -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
            }
            PENDING.set(false);
            try {
                scan(systemContext());
            } catch (Throwable t) {
                log("scan failed: " + t);
            }
        });
    }

    private static synchronized void scan(Context ctx) {
        if (ctx == null) {
            return;
        }
        PackageManager pm = ctx.getPackageManager();
        if (pm == null) {
            return;
        }
        Config cfg = Config.load();
        JSONObject base = loadBaseline();
        List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        // Regenerate app label/icon metadata when the third-party set changes.
        StringBuilder sigB = new StringBuilder();
        for (PackageInfo pi : pkgs) {
            if (pi.applicationInfo != null) {
                sigB.append(pi.packageName).append(',');
            }
        }
        String sig = sigB.toString();
        if (!sig.equals(sMetaSig)) {
            sMetaSig = sig;
            final Context c2 = ctx;
            EXEC.submit(() -> Meta.write(c2));
        }

        // Build proxy-channel index (OEM notification aggregators, e.g. ColorOS "mundo@<pkg>@<id>").
        Set<String> covered = new HashSet<>();
        for (PackageInfo pi : pkgs) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) {
                continue;
            }
            boolean sys = (ai.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            boolean eligible = !sys
                    || (cfg.includeSystem && pm.getLaunchIntentForPackage(pi.packageName) != null);
            if (eligible && cfg.covers(pi.packageName)) {
                covered.add(pi.packageName);
            }
        }
        Map<String, List<Object[]>> proxy = new HashMap<>();
        Object nsvc = Notif.service();
        boolean notifReady = false;
        for (PackageInfo pi : pkgs) {
            if (pi.applicationInfo == null || !isThirdParty(pi.applicationInfo)) {
                continue;
            }
            int ouid = pi.applicationInfo.uid;
            String opkg = pi.packageName;
            java.util.List<NotificationChannel> chs = Notif.channels(nsvc, opkg, ouid);
            if (!chs.isEmpty()) {
                notifReady = true;
            }
            for (NotificationChannel ch : chs) {
                String id = ch.getId();
                if (id == null || id.indexOf('@') < 0) {
                    continue;
                }
                for (String seg : id.split("@")) {
                    if (seg.indexOf('.') > 0 && covered.contains(seg)) {
                        List<Object[]> l = proxy.get(seg);
                        if (l == null) {
                            l = new ArrayList<>();
                            proxy.put(seg, l);
                        }
                        l.add(new Object[]{opkg, ouid, id, ch.getImportance()});
                        break;
                    }
                }
            }
        }
        State.sProxyMap = proxy;
        State.sNotifReady = notifReady;

        // Baseline = last observed state. No restore here (restore only on clear-data).
        for (PackageInfo pi : pkgs) {
            if (pi.applicationInfo == null) {
                continue;
            }
            String pkg = pi.packageName;
            if (!covered.contains(pkg)) {
                continue;
            }
            State cur = State.capture(ctx, pi, sRom);
            try {
                base.put(pkg, cur.toJson());
            } catch (Throwable ignore) {
            }
        }
        saveBaseline(base);
    }

    private static boolean isThirdParty(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && ai.uid >= 10000;
    }

    private static JSONObject loadBaseline() {
        try {
            File f = new File(BASE_FILE);
            if (!f.exists()) {
                return new JSONObject();
            }
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    private static void saveBaseline(JSONObject o) {
        try {
            new File(BASE_DIR).mkdirs();
            try (FileOutputStream fos = new FileOutputStream(BASE_FILE)) {
                OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                w.write(o.toString());
                w.flush();
                w.close();
            }
        } catch (Throwable t) {
            log("save baseline failed: " + t);
        }
    }

    public static Context systemContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            return (Context) at.getMethod("getSystemContext").invoke(thread);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void log(String m) {
        Log.i(TAG, m);
    }

    private SystemHook() {
    }
}
