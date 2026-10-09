package com.permkeeper.z;

import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-OEM abstraction so nothing is hard-coded to OnePlus.
 * New ROMs only need to implement this interface.
 */
public interface Rom {

    String name();

    /** Extra filesystem paths worth watching for this ROM's state changes. */
    default String[] extraWatchPaths() {
        return new String[0];
    }

    /** OEM autostart entry (raw marker) for a package, or null if unsupported. */
    default String readAutostart(String pkg) {
        return null;
    }

    default void writeAutostart(String pkg, String entry) {
    }

    // ------------------------------------------------------------------ impls

    final class OnePlus implements Rom {
        private static final String FILE =
                "/data/oplus/os/startup/startup_dynamic_list.xml";

        @Override
        public String name() {
            return "oneplus";
        }

        @Override
        public String[] extraWatchPaths() {
            return new String[]{"/data/oplus/os/startup"};
        }

        @Override
        public String readAutostart(String pkg) {
            try {
                File f = new File(FILE);
                if (!f.exists()) {
                    return null;
                }
                String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile(
                        "<dynamic\\s+[^>]*pkgName=\"" + Pattern.quote(pkg) + "\"[^>]*/>").matcher(xml);
                return m.find() ? m.group() : null;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public void writeAutostart(String pkg, String entry) {
            if (entry == null) {
                return;
            }
            try {
                File f = new File(FILE);
                if (!f.exists()) {
                    return;
                }
                String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile(
                        "<dynamic\\s+[^>]*pkgName=\"" + Pattern.quote(pkg) + "\"[^>]*/>").matcher(xml);
                String out = m.find()
                        ? xml.substring(0, m.start()) + entry + xml.substring(m.end())
                        : xml + entry;
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(out.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable t) {
                SystemHook.log("writeAutostart failed: " + t);
            }
        }
    }

    final class Aosp implements Rom {
        @Override
        public String name() {
            return "aosp";
        }
    }

    // Xiaomi/HyperOS autostart is exposed through custom AppOps (ids >= 10001),
    // handled generically by the AppOps category; OEM file hooks TBD on device.
    final class Xiaomi implements Rom {
        @Override
        public String name() {
            return "xiaomi";
        }
    }

    static Rom detect() {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
        if (m.contains("oneplus") || m.contains("oppo") || m.contains("realme")) {
            return new OnePlus();
        }
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            return new Xiaomi();
        }
        return new Aosp();
    }
}
