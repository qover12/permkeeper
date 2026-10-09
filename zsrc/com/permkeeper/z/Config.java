package com.permkeeper.z;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Module configuration, shared with the WebUI via /data/system/permkeeper/config.json. */
public final class Config {
    public static final String DIR = "/data/system/permkeeper";
    public static final String FILE = DIR + "/config.json";

    public boolean all = false;
    public boolean includeSystem = false;
    public final Set<String> apps = new HashSet<>();     // explicit opt-in list (legacy)
    public final Set<String> exclude = new HashSet<>();  // exceptions to "all"

    public boolean permissions = true;
    public boolean notifications = true;
    public boolean appops = true;
    public boolean autostart = true;

    /** per-pkg overrides: pkg -> { cats: {perm:bool,...} } */
    public final Map<String, JSONObject> overrides = new HashMap<>();

    public static Config load() {
        Config c = new Config();
        try {
            File f = new File(FILE);
            if (!f.exists()) {
                return c;
            }
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(s);
            c.all = o.optBoolean("all", false);
            c.includeSystem = o.optBoolean("includeSystem", false);
            addAll(c.apps, o.optJSONArray("apps"));
            addAll(c.exclude, o.optJSONArray("exclude"));
            JSONObject cat = o.optJSONObject("categories");
            if (cat != null) {
                c.permissions = cat.optBoolean("permissions", true);
                c.notifications = cat.optBoolean("notifications", true);
                c.appops = cat.optBoolean("appops", true);
                c.autostart = cat.optBoolean("autostart", true);
            }
            JSONObject ov = o.optJSONObject("overrides");
            if (ov != null) {
                for (Iterator<String> it = ov.keys(); it.hasNext(); ) {
                    String pkg = it.next();
                    JSONObject v = ov.optJSONObject(pkg);
                    if (v != null) {
                        c.overrides.put(pkg, v);
                    }
                }
            }
        } catch (Throwable t) {
            SystemHook.log("config load failed: " + t);
        }
        return c;
    }

    private static void addAll(Set<String> set, JSONArray a) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            String p = a.optString(i);
            if (p != null && !p.isEmpty()) {
                set.add(p);
            }
        }
    }

    public void save() {
        try {
            new File(DIR).mkdirs();
            JSONObject o = new JSONObject();
            o.put("all", all);
            o.put("includeSystem", includeSystem);
            o.put("apps", arr(apps));
            o.put("exclude", arr(exclude));
            JSONObject cat = new JSONObject();
            cat.put("permissions", permissions);
            cat.put("notifications", notifications);
            cat.put("appops", appops);
            cat.put("autostart", autostart);
            o.put("categories", cat);
            JSONObject ov = new JSONObject();
            for (Map.Entry<String, JSONObject> e : overrides.entrySet()) {
                ov.put(e.getKey(), e.getValue());
            }
            o.put("overrides", ov);
            try (FileOutputStream fos = new FileOutputStream(FILE)) {
                OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                w.write(o.toString(2));
                w.flush();
                w.close();
            }
        } catch (Throwable t) {
            SystemHook.log("config save failed: " + t);
        }
    }

    private static JSONArray arr(Set<String> set) {
        JSONArray a = new JSONArray();
        for (String s : set) {
            a.put(s);
        }
        return a;
    }

    public boolean covers(String pkg) {
        return all ? !exclude.contains(pkg) : apps.contains(pkg);
    }

    public boolean catFor(String pkg, String name) {
        JSONObject ov = overrides.get(pkg);
        if (ov != null) {
            JSONObject cats = ov.optJSONObject("cats");
            if (cats != null && cats.has(name)) {
                return cats.optBoolean(name, true);
            }
        }
        switch (name) {
            case "permissions":
                return permissions;
            case "notifications":
                return notifications;
            case "appops":
                return appops;
            default:
                return autostart;
        }
    }
}
