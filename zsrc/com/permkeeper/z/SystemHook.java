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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    // P0-5: Meta.write() 里的 waitBoot() 最长会阻塞 150s，必须与恢复任务隔离，
    // 否则清数据恢复会被堵在它后面。
    private static final ExecutorService META_EXEC = Executors.newSingleThreadExecutor();
    // P0-5: scan 的防抖不再用 Thread.sleep 占住 EXEC。
    private static final ScheduledExecutorService DEBOUNCE =
            Executors.newSingleThreadScheduledExecutor();
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
        watchExportRequest();                     // on-demand full snapshot for export
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
                        // P0-4: 广播带 EXTRA_UID（AOSP Intent.java 明确记录：
                        // "<li> {@link #EXTRA_UID} containing the integer uid assigned to the package."）。
                        // 分身 / XSpace 暂不支持 —— 直接跳过，绝不拿主空间 uid 去操作同名包。
                        int extraUid = i.getIntExtra(Intent.EXTRA_UID, -1);
                        // 注意：UserHandle.getUserId(int) 是 @hide，公开 android.jar 里没有 —— 反射调用，
                        // 失败则按 uid = userId * PER_USER_RANGE + appId 反推（兜底必须确定性，
                        // 否则反射一失败就会把分身当成主用户，正是要防的误伤）。
                        int userId;
                        if (extraUid < 0) {
                            userId = 0;   // 没带 EXTRA_UID：维持原行为，按主用户处理
                        } else {
                            int u;
                            try {
                                u = (Integer) android.os.UserHandle.class
                                        .getMethod("getUserId", int.class).invoke(null, extraUid);
                            } catch (Throwable t) {
                                u = extraUid / 100000;
                            }
                            userId = u;
                        }
                        if (userId != 0) {
                            log("clear-data for " + pkg + " user=" + userId
                                    + " ignored (multi-user unsupported)");
                            return;
                        }
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
                    // 注：PackageManager.getPackageInfoAsUser 是 @hide，公开 API 拿不到。
                    // 系统上下文本身就是 user 0，且非 0 用户已在 onReceive 里跳过，所以这里等价。
                    pi = ctx.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
                } catch (Throwable t) {
                    log("clear-data " + pkg + " getPackageInfo failed: " + t);
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
                String miss = "";
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
                    // P0-3: 逐类别校验。以前只看 permissions，AppOps/通知/Doze/自启动
                    // 失败也会被报成 ok，故障被完全掩盖。
                    String m = snap.verify(after, cfg, pkg);
                    if (m == null) {
                        ok = true;
                        break;
                    }
                    miss = m;
                }
                // Keep baseline at the known-good snapshot (a scan may have run in between).
                try {
                    JSONObject base = loadBaseline();
                    base.put(pkg, snap.toJson());
                    saveBaseline(base);
                } catch (Throwable ignore) {
                }
                String brief = miss.length() > 180 ? miss.substring(0, 180) + "..." : miss;
                log("clear-data " + pkg + " restored rounds=" + rounds
                        + (ok ? " ok" : " incomplete: " + brief));
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

    /** Watch for /data/system/permkeeper/export.req written by the WebUI to request a full snapshot. */
    private static void watchExportRequest() {
        try {
            FileObserver o = new FileObserver(BASE_DIR,
                    FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null || !path.equals("export.req")) {
                        return;
                    }
                    log("export request received");
                    EXEC.submit(() -> exportAll());
                }
            };
            o.startWatching();
            OBSERVERS.add(o);
        } catch (Throwable t) {
            log("watchExportRequest failed: " + t);
        }
    }

    /** Capture every installed app (not just protected ones) into export.json, then signal done. */
    private static void exportAll() {
        long startedAt = System.currentTimeMillis();
        try {
            Context ctx = systemContext();
            if (ctx == null) {
                return;
            }
            PackageManager pm = ctx.getPackageManager();
            List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            // reuse the proxy index so OEM notification channels are attributed correctly
            Map<String, List<Object[]>> proxy = buildProxyIndex(ctx, pm, pkgs);
            State.sProxyMap = proxy;
            State.sNotifReady = Notif.service() != null;
            long captureStart = System.currentTimeMillis();
            final JSONObject out = new JSONObject();
            final int total = pkgs.size();
            final java.util.concurrent.atomic.AtomicInteger done =
                    new java.util.concurrent.atomic.AtomicInteger(0);
            final long[] lastReport = {0};

            // Parallel capture: the work is binder-bound, so a small pool helps a lot.
            int threads = 8;
            try {
                java.io.File cf = new java.io.File(BASE_DIR, "export.threads");
                if (cf.exists()) {
                    threads = Integer.parseInt(new String(Files.readAllBytes(cf.toPath()),
                            StandardCharsets.UTF_8).trim());
                }
            } catch (Throwable ignore) {
            }
            if (threads <= 1) {
                // serial path (baseline for comparison)
                for (final PackageInfo pi : pkgs) {
                    if (pi.applicationInfo == null) {
                        continue;
                    }
                    try {
                        JSONObject js = State.capture(ctx, pi, sRom).toJson();
                        synchronized (out) {
                            out.put(pi.packageName, js);
                        }
                    } catch (Throwable ignore) {
                    }
                    int d = done.incrementAndGet();
                    long now = System.currentTimeMillis();
                    synchronized (lastReport) {
                        if (now - lastReport[0] > 200 || d >= total) {
                            lastReport[0] = now;
                            writeProgress(d, total);
                        }
                    }
                }
            } else {
                java.util.concurrent.ExecutorService pool =
                        java.util.concurrent.Executors.newFixedThreadPool(threads);
                java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (final PackageInfo pi : pkgs) {
                    if (pi.applicationInfo == null) {
                        continue;
                    }
                    futures.add(pool.submit(() -> {
                        try {
                            JSONObject js = State.capture(ctx, pi, sRom).toJson();
                            synchronized (out) {
                                out.put(pi.packageName, js);
                            }
                        } catch (Throwable ignore) {
                        }
                        int d = done.incrementAndGet();
                        long now = System.currentTimeMillis();
                        synchronized (lastReport) {
                            if (now - lastReport[0] > 200 || d >= total) {
                                lastReport[0] = now;
                                writeProgress(d, total);
                            }
                        }
                    }));
                }
                pool.shutdown();
                for (java.util.concurrent.Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (Throwable ignore) {
                    }
                }
            }
            File f = new File(BASE_DIR, "export.json");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                w.write(out.toString());
                w.flush();
                w.close();
            }
            // signal completion (with internal timing so we can compare implementations)
            long elapsed = System.currentTimeMillis() - startedAt;
            try (FileOutputStream fos = new FileOutputStream(new File(BASE_DIR, "export.done"))) {
                fos.write((done.get() + "|" + total + "|" + elapsed).getBytes(StandardCharsets.UTF_8));
            }
            new File(BASE_DIR, "export.req").delete();
            new File(BASE_DIR, "export.progress").delete();
            log("export done: " + out.length() + " apps");
        } catch (Throwable t) {
            log("exportAll failed: " + t);
        }
    }

    /** Write "done/total" so the WebUI can show real progress while exporting. */
    private static void writeProgress(int done, int total) {
        try (FileOutputStream fos = new FileOutputStream(new File(BASE_DIR, "export.progress"))) {
            fos.write((done + "/" + total).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignore) {
        }
    }

    private static void onChange() {
        if (PENDING.getAndSet(true)) {
            return;
        }
        // P0-5: 防抖改由调度器完成，不再用 Thread.sleep 占住 EXEC（否则清数据恢复要等它 2.5s）
        DEBOUNCE.schedule(() -> {
            PENDING.set(false);
            EXEC.submit(() -> {
                try {
                    scan(systemContext());
                } catch (Throwable t) {
                    log("scan failed: " + t);
                }
            });
        }, 2500, TimeUnit.MILLISECONDS);
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
            // P0-5: 用独立线程池，别把恢复任务堵在 Meta.write 的 waitBoot 后面
            META_EXEC.submit(() -> Meta.write(c2));
        }

        // Which apps are protected (used for baseline capture + clear-data restore).
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

        // Build proxy-channel index (OEM notification aggregators, e.g. ColorOS "mundo@<pkg>@<id>").
        Map<String, List<Object[]>> proxy = buildProxyIndex(ctx, pm, pkgs);
        State.sProxyMap = proxy;
        State.sNotifReady = Notif.service() != null;

        // Baseline = last observed state of PROTECTED apps (keeps periodic scans cheap).
        // A full snapshot of all apps is produced on demand for export (see exportAll()).
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

    /** Index OEM proxy channels ("mundo@<target>@<id>") so they can be attributed to the target app. */
    private static Map<String, List<Object[]>> buildProxyIndex(Context ctx, PackageManager pm,
                                                               List<PackageInfo> pkgs) {
        Config cfg = Config.load();
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
        for (PackageInfo pi : pkgs) {
            if (pi.applicationInfo == null || !isThirdParty(pi.applicationInfo)) {
                continue;
            }
            int ouid = pi.applicationInfo.uid;
            String opkg = pi.packageName;
            for (NotificationChannel ch : Notif.channels(nsvc, opkg, ouid)) {
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
                        l.add(new Object[]{opkg, ouid, id, ch.getImportance(), ch.getName()});
                        break;
                    }
                }
            }
        }
        return proxy;
    }

    private static boolean isThirdParty(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && ai.uid >= 10000;
    }

    private static JSONObject loadBaseline() {
        File f = new File(BASE_FILE);
        if (!f.exists()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            // P0-2: 以前这里静默返回空对象 —— 基线坏了，用户和开发者都看不到。
            log("baseline load failed: " + t);
            quarantine(f, "baseline");
            return new JSONObject();
        }
    }

    private static void saveBaseline(JSONObject o) {
        writeAtomic(new File(BASE_FILE), o.toString());
    }

    /**
     * P0-2: 原子写 —— 先写 .tmp、fsync、再 rename 覆盖。
     * 直接 FileOutputStream 覆盖原文件时，一旦中途掉电 / system_server 被杀，
     * 就会留下一个截断的文件（下次读取直接失败）。
     */
    public static void writeAtomic(File target, String content) {
        File dir = target.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        File tmp = new File(dir, target.getName() + ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                w.write(content);
                w.flush();
                fos.getFD().sync();
            }
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable moveErr) {
                // 个别实现不支持 ATOMIC_MOVE；退化为普通覆盖（tmp + fsync 的好处仍在）
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            log("writeAtomic " + target + " failed: " + t);
            try {
                tmp.delete();
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * P0-2: 把损坏的文件改名留档（<name>.corrupt-<ts>）。
     * 既保留证据，也让下次启动走「文件不存在」这条可预期的路径，
     * 而不是每次启动都重复踩同一个坑。
     */
    public static void quarantine(File f, String tag) {
        try {
            File dst = new File(f.getParentFile(),
                    f.getName() + ".corrupt-" + System.currentTimeMillis());
            if (f.renameTo(dst)) {
                log(tag + " quarantined -> " + dst.getName());
            } else {
                log(tag + " quarantine failed (rename returned false)");
            }
        } catch (Throwable t) {
            log(tag + " quarantine failed: " + t);
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
