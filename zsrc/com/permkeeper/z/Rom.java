package com.permkeeper.z;

import android.os.Build;

import java.io.File;
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

    /** 极简 XML 合法性自检（写系统文件前的安全网）。 */
    static boolean isWellFormed(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setValidating(false);
            f.newDocumentBuilder().parse(
                    new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            return true;
        } catch (Throwable t) {
            return false;
        }
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
                String out;
                if (m.find()) {
                    out = xml.substring(0, m.start()) + entry + xml.substring(m.end());
                } else {
                    // P0-6: 追加必须落在根元素闭合标签「之前」。
                    // 原来的 xml + entry 会写到根元素外面，产出两个根元素 → XML 非法。
                    int close = xml.lastIndexOf("</");
                    if (close < 0) {
                        SystemHook.log("writeAutostart: no closing tag in " + FILE
                                + ", refusing to append");
                        return;
                    }
                    out = xml.substring(0, close) + entry + xml.substring(close);
                }
                // P0-6: 写前自检 —— 原文件本来是合法 XML 时，改动后也必须合法，否则放弃写入。
                // 宁可这次不恢复自启动，也不写坏系统文件。
                if (isWellFormed(xml) && !isWellFormed(out)) {
                    SystemHook.log("writeAutostart: result would not be well-formed XML, aborting");
                    return;
                }
                // P0-2: 原子写
                SystemHook.writeAtomic(f, out);
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
