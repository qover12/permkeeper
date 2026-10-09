package com.permkeeper.z;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Generates app metadata (label + icon) for the WebUI, since shell can't read labels/icons. */
public final class Meta {
    private static final String FILE = "/data/system/permkeeper/apps.json";
    private static final String LABELS = "/data/system/permkeeper/labels.json";
    private static final String ICON_DIR = "/data/system/permkeeper/icon";

    public static void write(Context ctx) {
        waitBoot();
        try {
            PackageManager pm = ctx.getPackageManager();
            List<PackageInfo> pkgs = pm.getInstalledPackages(0);
            JSONArray arr = new JSONArray();
            JSONObject labels = loadLabels();
            // Prepare per-app icon files (cleared each rebuild); keeps apps.json tiny.
            File iconDir = new File(ICON_DIR);
            try {
                if (iconDir.isDirectory()) {
                    File[] old = iconDir.listFiles();
                    if (old != null) {
                        for (File f : old) {
                            f.delete();
                        }
                    }
                }
                iconDir.mkdirs();
            } catch (Throwable ignore) {
            }
            for (PackageInfo pi : pkgs) {
                ApplicationInfo ai = pi.applicationInfo;
                if (ai == null) {
                    continue;
                }
                String label;
                try {
                    label = String.valueOf(pm.getApplicationLabel(ai));
                } catch (Throwable t) {
                    label = pi.packageName;
                }
                // Cache every installed package's label (keep-only, never pruned) so
                // apps that are later uninstalled can still show a name.
                labels.put(pi.packageName, label);
                boolean sys = (ai.flags & (ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                if (sys && pm.getLaunchIntentForPackage(pi.packageName) == null) {
                    continue; // system apps: only user-facing (have a launcher)
                }
                JSONObject o = new JSONObject();
                o.put("pkg", pi.packageName);
                o.put("sys", sys);
                o.put("installed", true);
                o.put("label", label);
                try {
                    Drawable d = null;
                    try {
                        d = pm.getApplicationIcon(pi.packageName);
                    } catch (Throwable t) {
                    }
                    if (d == null && ai != null) {
                        try {
                            d = ai.loadIcon(pm);
                        } catch (Throwable t) {
                        }
                    }
                    Bitmap bmp = d == null ? null : toBitmap(d);
                    if (bmp != null) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bmp, 96, 96, true);
                        try (FileOutputStream of = new FileOutputStream(
                                new File(iconDir, pi.packageName + ".png"))) {
                            scaled.compress(Bitmap.CompressFormat.PNG, 100, of);
                        }
                    }
                } catch (Throwable ignore) {
                }
                arr.put(o);
            }
            saveLabels(labels);
            new File("/data/system/permkeeper").mkdirs();
            try (FileOutputStream fos = new FileOutputStream(FILE)) {
                OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                w.write(arr.toString());
                w.flush();
                w.close();
            }
            SystemHook.log("apps.json written: " + arr.length() + " apps");
        } catch (Throwable t) {
            SystemHook.log("writeAppsMeta failed: " + t);
        }
    }

    private static JSONObject loadLabels() {
        try {
            File f = new File(LABELS);
            if (f.exists()) {
                return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            }
        } catch (Throwable ignore) {
        }
        return new JSONObject();
    }

    private static void saveLabels(JSONObject labels) {
        try {
            new File("/data/system/permkeeper").mkdirs();
            try (FileOutputStream fos = new FileOutputStream(LABELS)) {
                OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                w.write(labels.toString());
                w.flush();
                w.close();
            }
        } catch (Throwable t) {
            SystemHook.log("saveLabels failed: " + t);
        }
    }

    private static void waitBoot() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class);
            for (int i = 0; i < 300; i++) {
                if ("1".equals(get.invoke(null, "sys.boot_completed"))) {
                    return;
                }
                Thread.sleep(500);
            }
        } catch (Throwable ignore) {
        }
    }

    private static Bitmap toBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null) {
                return b;
            }
        }
        int w = Math.max(1, d.getIntrinsicWidth());
        int h = Math.max(1, d.getIntrinsicHeight());
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return bmp;
    }

    private Meta() {
    }
}
