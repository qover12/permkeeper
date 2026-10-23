package com.permkeeper.z;

import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Per-package snapshot across categories. */
public final class State {
    public final Set<String> perms = new LinkedHashSet<>();   // granted dangerous perms
    public final Set<String> req = new LinkedHashSet<>();     // requested dangerous perms (manifest)
    public final Map<String, Integer> appops = new LinkedHashMap<>();
    public final Map<String, Integer> channels = new LinkedHashMap<>();
    /**
     * P0-1: 渠道显示名，与 {@link #channels} 同键。
     * 渠道被清数据删掉后需要靠它重建；只存 importance 会把名字丢成渠道 id。
     * 老快照没有这个字段，读取侧按缺失处理。
     */
    public final Map<String, String> chanName = new LinkedHashMap<>();
    public boolean doze;
    public String startup;
    private static boolean sDozeDisabled = false;

    public static volatile Map<String, java.util.List<Object[]>> sProxyMap =
            java.util.Collections.emptyMap();
    public static volatile boolean sNotifReady = false;

    private static final String[] OP_STRINGS = {
            "SYSTEM_ALERT_WINDOW", "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND",
            "MANAGE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES",
            "SCHEDULE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT",
    };

    public static State capture(Context ctx, PackageInfo pi, Rom rom) {
        State s = new State();
        PackageManager pm = ctx.getPackageManager();
        String pkg = pi.packageName;
        if (pi.requestedPermissions != null) {
            for (String p : pi.requestedPermissions) {
                try {
                    PermissionInfo info = pm.getPermissionInfo(p, 0);
                    if ((info.protectionLevel & 0xf) != PermissionInfo.PROTECTION_DANGEROUS) {
                        continue;
                    }
                } catch (Throwable t) {
                    continue;
                }
                s.req.add(p);
                if (pm.checkPermission(p, pkg) == PackageManager.PERMISSION_GRANTED) {
                    s.perms.add(p);
                }
            }
        }
        int uid = pi.applicationInfo != null ? pi.applicationInfo.uid : -1;
        AppOpsManager aop = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        // AOSP "special" app ops (SYSTEM_ALERT_WINDOW=24, WRITE_SETTINGS, install, exact-alarm...),
        boolean canFilter = true;
        try {
            invokeStatic(AppOpsManager.class, "opToPermission", 0);
        } catch (Throwable t) {
            canFilter = false;
        }
        for (int op = 0; op <= 200; op++) {
            try {
                if (canFilter) {
                    Object backing = invokeStatic(AppOpsManager.class, "opToPermission", op);
                    if (backing != null) {
                        boolean danger = false;
                        try {
                            android.content.pm.PermissionInfo pi2 =
                                    pm.getPermissionInfo((String) backing, 0);
                            danger = pi2 != null && (pi2.protectionLevel & 0xf)
                                    == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS;
                        } catch (Throwable ig) {
                        }
                        if (danger) {
                            continue; // runtime-permission-backed; handled by the permission restore
                        }
                    }
                }
                Object r = invoke(aop, "checkOpNoThrow", op, uid, pkg);
                if (r instanceof Integer && (Integer) r != AppOpsManager.MODE_DEFAULT) {
                    s.appops.put(String.valueOf(op), (Integer) r);
                }
            } catch (Throwable ignore) {
            }
        }
        // OEM ops by id (MIUI 10001+, e.g. autostart/background/network switches).
        for (int op = 10000; op <= 10200; op++) {
            try {
                Object r = invoke(aop, "checkOpNoThrow", op, uid, pkg);
                if (r instanceof Integer && (Integer) r != AppOpsManager.MODE_DEFAULT) {
                    s.appops.put(String.valueOf(op), (Integer) r);
                }
            } catch (Throwable ignore) {
            }
        }
        try {
            PowerManager pw = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            s.doze = pw.isIgnoringBatteryOptimizations(pkg);
        } catch (Throwable ignore) {
        }
        try {
            Object svc = Notif.service();
            for (NotificationChannel ch : Notif.channels(svc, pkg, uid)) {
                String key = pkg + "|" + uid + "|" + ch.getId();
                s.channels.put(key, ch.getImportance());
                if (ch.getName() != null) {
                    s.chanName.put(key, ch.getName().toString());
                }
            }
            java.util.List<Object[]> px = sProxyMap.get(pkg);
            if (px != null) {
                for (Object[] o : px) {
                    String key = o[0] + "|" + o[1] + "|" + o[2];
                    s.channels.put(key, (Integer) o[3]);
                    if (o.length > 4 && o[4] != null) {
                        s.chanName.put(key, String.valueOf(o[4]));
                    }
                }
            }
        } catch (Throwable t) {
            SystemHook.log("captureChannels " + pkg + ": " + t);
        }
        s.startup = rom.readAutostart(pkg);
        return s;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            JSONArray a = new JSONArray();
            for (String p : perms) {
                a.put(p);
            }
            o.put("perms", a);
            JSONArray rq = new JSONArray();
            for (String p : req) {
                rq.put(p);
            }
            o.put("req", rq);
            JSONObject ops = new JSONObject();
            for (Map.Entry<String, Integer> e : appops.entrySet()) {
                ops.put(e.getKey(), e.getValue());
            }
            o.put("appops", ops);
            JSONObject chs = new JSONObject();
            for (Map.Entry<String, Integer> e : channels.entrySet()) {
                chs.put(e.getKey(), e.getValue());
            }
            o.put("channels", chs);
            // P0-1: 渠道显示名（老快照没有这个字段，读取侧按缺失处理）
            JSONObject names = new JSONObject();
            for (Map.Entry<String, String> e : chanName.entrySet()) {
                if (e.getValue() != null) {
                    names.put(e.getKey(), e.getValue());
                }
            }
            o.put("chanName", names);
            o.put("doze", doze);
            if (startup != null) {
                o.put("startup", startup);
            }
        } catch (Throwable ignore) {
        }
        return o;
    }

    public static State fromJson(JSONObject o) {
        State s = new State();
        if (o == null) {
            return s;
        }
        JSONArray a = o.optJSONArray("perms");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                s.perms.add(a.optString(i));
            }
        }
        JSONArray rq = o.optJSONArray("req");
        if (rq != null) {
            for (int i = 0; i < rq.length(); i++) {
                s.req.add(rq.optString(i));
            }
        }
        JSONObject ops = o.optJSONObject("appops");
        if (ops != null) {
            for (java.util.Iterator<String> it = ops.keys(); it.hasNext(); ) {
                String k = it.next();
                s.appops.put(k, ops.optInt(k));
            }
        }
        s.doze = o.optBoolean("doze", false);
        s.startup = o.optString("startup", null);
        JSONObject chs = o.optJSONObject("channels");
        if (chs != null) {
            for (java.util.Iterator<String> it = chs.keys(); it.hasNext(); ) {
                String k = it.next();
                s.channels.put(k, chs.optInt(k));
            }
        }
        // P0-1: 渠道显示名（可选，老快照没有）
        JSONObject names = o.optJSONObject("chanName");
        if (names != null) {
            for (java.util.Iterator<String> it = names.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = names.optString(k, null);
                if (v != null && !v.isEmpty()) {
                    s.chanName.put(k, v);
                }
            }
        }
        return s;
    }

    /**
     * Restore everything this baseline holds that {@code cur} lacks.
     * Called only when a "clear data" was confirmed. Respects category toggles.
     */
    public boolean restoreMissing(Context ctx, PackageInfo pi, State cur, Config cfg, Rom rom) {
        String pkg = pi.packageName;
        boolean dPerms = cfg.catFor(pkg, "permissions");
        boolean dNotif = cfg.catFor(pkg, "notifications");
        boolean dOps = cfg.catFor(pkg, "appops");
        boolean dAuto = cfg.catFor(pkg, "autostart");

        java.util.List<String> missPerms = new java.util.ArrayList<>();
        if (dPerms) {
            for (String p : perms) {
                if (!cur.perms.contains(p)) {
                    missPerms.add(p);
                }
            }
        }
        java.util.List<Map.Entry<String, Integer>> missOps = new java.util.ArrayList<>();
        if (dOps) {
            for (Map.Entry<String, Integer> e : appops.entrySet()) {
                Integer c = cur.appops.get(e.getKey());
                if (c == null || !c.equals(e.getValue())) {
                    missOps.add(e);
                }
            }
        }
        java.util.List<Object[]> missChans = new java.util.ArrayList<>();
        if (dNotif && !channels.isEmpty() && sNotifReady) {
            Object svc = Notif.service();
            Map<String, Map<String, NotificationChannel>> ownerCache = new HashMap<>();
            for (Map.Entry<String, Integer> e : channels.entrySet()) {
                String[] k = e.getKey().split("\\|", 3);
                if (k.length != 3) {
                    continue;
                }
                String ownerPkg = k[0];
                int ownerUid;
                try {
                    ownerUid = Integer.parseInt(k[1]);
                } catch (Throwable t) {
                    continue;
                }
                String cid = k[2];
                String oc = ownerPkg + "|" + ownerUid;
                Map<String, NotificationChannel> ownerCh = ownerCache.get(oc);
                if (ownerCh == null) {
                    ownerCh = new HashMap<>();
                    for (NotificationChannel c : Notif.channels(svc, ownerPkg, ownerUid)) {
                        ownerCh.put(c.getId(), c);
                    }
                    ownerCache.put(oc, ownerCh);
                }
                NotificationChannel c = ownerCh.get(cid);
                if (c == null || c.getImportance() != e.getValue()) {
                    // P0-1: 同 restoreAll，传活对象（此方法目前无调用者，保持与 applyChannels 契约一致）
                    missChans.add(new Object[]{ownerPkg, ownerUid, cid, e.getValue(), c});
                }
            }
        }
        boolean missDoze = doze && !cur.doze;
        boolean missStartup = dAuto && startup != null && cur.startup == null;

        boolean changed = false;
        for (String p : missPerms) {
            if (grant(ctx, pkg, p)) {
                changed = true;
            }
        }
        changed |= applyOps(ctx, pi, missOps);
        changed |= applyChannels(missChans);
        if (missDoze && addDozeWhitelist(pkg)) {
            changed = true;
        }
        if (missStartup) {
            rom.writeAutostart(pkg, startup);
            changed = true;
        }
        return changed;
    }

    /**
     * Unconditional restore of everything in this snapshot (used on confirmed clear-data).
     * Respects category toggles. Grants all snapshot perms (idempotent).
     */
    public boolean restoreAll(Context ctx, PackageInfo pi, Config cfg, Rom rom) {
        String pkg = pi.packageName;
        boolean changed = false;
        if (cfg.catFor(pkg, "permissions")) {
            for (String p : perms) {
                if (grant(ctx, pkg, p)) {
                    changed = true;
                }
            }
        }
        if (cfg.catFor(pkg, "appops")) {
            changed |= applyOps(ctx, pi, new java.util.ArrayList<>(appops.entrySet()));
        }
        if (cfg.catFor(pkg, "notifications") && !channels.isEmpty() && sNotifReady) {
            java.util.List<Object[]> chans = new java.util.ArrayList<>();
            Object svc = Notif.service();
            Map<String, Map<String, NotificationChannel>> ownerCache = new HashMap<>();
            for (Map.Entry<String, Integer> e : channels.entrySet()) {
                String[] k = e.getKey().split("\\|", 3);
                if (k.length != 3) {
                    continue;
                }
                String ownerPkg = k[0];
                int ownerUid;
                try {
                    ownerUid = Integer.parseInt(k[1]);
                } catch (Throwable t) {
                    continue;
                }
                String cid = k[2];
                String oc = ownerPkg + "|" + ownerUid;
                Map<String, NotificationChannel> ownerCh = ownerCache.get(oc);
                if (ownerCh == null) {
                    ownerCh = new HashMap<>();
                    for (NotificationChannel c : Notif.channels(svc, ownerPkg, ownerUid)) {
                        ownerCh.put(c.getId(), c);
                    }
                    ownerCache.put(oc, ownerCh);
                }
                NotificationChannel c = ownerCh.get(cid);
                // P0-1: 第 5 个元素传「活渠道对象」（可能为 null），而不是布尔。
                // 渠道仍在时只改 importance，绝不重建对象（重建会把名字/声音等打回默认）。
                chans.add(new Object[]{ownerPkg, ownerUid, cid, e.getValue(), c});
            }
            changed |= applyChannels(chans);
        }
        if (doze && addDozeWhitelist(pkg)) {
            changed = true;
        }
        if (cfg.catFor(pkg, "autostart") && startup != null) {
            rom.writeAutostart(pkg, startup);
            changed = true;
        }
        return changed;
    }

    /**
     * Import-and-apply: make the package state match this snapshot exactly.
     * Grants snapshot perms, revokes current dangerous perms not in snapshot.
     */
    public boolean enforceExact(Context ctx, PackageInfo pi, State cur, Config cfg, Rom rom) {
        String pkg = pi.packageName;
        boolean dPerms = cfg.catFor(pkg, "permissions");
        boolean dNotif = cfg.catFor(pkg, "notifications");
        boolean dOps = cfg.catFor(pkg, "appops");
        boolean dAuto = cfg.catFor(pkg, "autostart");
        boolean changed = false;

        if (dPerms) {
            for (String p : perms) {
                if (!cur.perms.contains(p)) {
                    if (grant(ctx, pkg, p)) {
                        changed = true;
                    }
                }
            }
            for (String p : cur.perms) {
                if (!perms.contains(p)) {
                    if (revoke(ctx, pkg, p)) {
                        changed = true;
                    }
                }
            }
        }
        // appops: set each snapshot value
        java.util.List<Map.Entry<String, Integer>> ops = new java.util.ArrayList<>(appops.entrySet());
        changed |= applyOps(ctx, pi, ops);
        // channels
        if (dNotif && !channels.isEmpty() && sNotifReady) {
            Object svc = Notif.service();
            Map<String, Map<String, NotificationChannel>> ownerCache = new HashMap<>();
            java.util.List<Object[]> chans = new java.util.ArrayList<>();
            for (Map.Entry<String, Integer> e : channels.entrySet()) {
                String[] k = e.getKey().split("\\|", 3);
                if (k.length != 3) {
                    continue;
                }
                String ownerPkg = k[0];
                int ownerUid;
                try {
                    ownerUid = Integer.parseInt(k[1]);
                } catch (Throwable t) {
                    continue;
                }
                String cid = k[2];
                String oc = ownerPkg + "|" + ownerUid;
                Map<String, NotificationChannel> ownerCh = ownerCache.get(oc);
                if (ownerCh == null) {
                    ownerCh = new HashMap<>();
                    for (NotificationChannel c : Notif.channels(svc, ownerPkg, ownerUid)) {
                        ownerCh.put(c.getId(), c);
                    }
                    ownerCache.put(oc, ownerCh);
                }
                NotificationChannel c = ownerCh.get(cid);
                if (c == null || c.getImportance() != e.getValue()) {
                    // P0-1: 第 5 个元素传「活渠道对象」（可能为 null），而不是布尔。
                // 渠道仍在时只改 importance，绝不重建对象（重建会把名字/声音等打回默认）。
                chans.add(new Object[]{ownerPkg, ownerUid, cid, e.getValue(), c});
                }
            }
            changed |= applyChannels(chans);
        }
        if (doze && !cur.doze && addDozeWhitelist(pkg)) {
            changed = true;
        }
        if (dAuto && startup != null && cur.startup == null) {
            rom.writeAutostart(pkg, startup);
            changed = true;
        }
        return changed;
    }

    private boolean applyOps(Context ctx, PackageInfo pi,
                             java.util.List<Map.Entry<String, Integer>> ops) {
        if (ops.isEmpty()) {
            return false;
        }
        boolean changed = false;
        AppOpsManager aop = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        int uid = pi.applicationInfo != null ? pi.applicationInfo.uid : -1;
        for (Map.Entry<String, Integer> e : ops) {
            try {
                int op;
                try {
                    op = Integer.parseInt(e.getKey());
                } catch (NumberFormatException nfe) {
                    op = (Integer) invokeStatic(AppOpsManager.class, "strOpToOp", e.getKey());
                }
                invoke(aop, "setMode", op, uid, pi.packageName, e.getValue());
                changed = true;
            } catch (Throwable t) {
                SystemHook.log("setMode " + e.getKey() + " failed: " + t);
            }
        }
        return changed;
    }

    private boolean applyChannels(java.util.List<Object[]> chans) {
        if (chans.isEmpty()) {
            return false;
        }
        Object svc = Notif.service();
        for (Object[] o : chans) {
            String ownerPkg = (String) o[0];
            int ownerUid = (Integer) o[1];
            String cid = (String) o[2];
            int imp = (Integer) o[3];
            Object live = o[4];
            if (live instanceof NotificationChannel) {
                // P0-1: 渠道还在 —— 只改 importance 再回写。
                // 系统侧是 r.channels.put(id, updatedChannel) 整对象替换，所以必须回传「原对象」；
                // 之前传 new NotificationChannel(cid, cid, imp) 会把名字/描述/声音/震动/灯/角标
                // 全部打回默认值（渠道名会变成渠道 id）。
                NotificationChannel ch = (NotificationChannel) live;
                ch.setImportance(imp);
                Notif.update(svc, ownerPkg, ownerUid, ch);
            } else {
                // 渠道已被清数据删除 —— 只能用快照里存的显示名重建
                String name = chanName.get(ownerPkg + "|" + ownerUid + "|" + cid);
                if (name == null || name.isEmpty()) {
                    name = cid;
                    SystemHook.log("recreate channel without stored name, falling back to id: "
                            + ownerPkg + " " + cid);
                }
                boolean ok = Notif.create(svc, ownerPkg, ownerUid,
                        new NotificationChannel(cid, name, imp));
                if (!ok) {
                    SystemHook.log("createChannel failed " + ownerPkg + " " + cid);
                }
            }
        }
        return true;
    }

    /**
     * P0-3: 校验 {@code after} 是否已把本快照的各类别恢复到位。
     *
     * <p>返回 {@code null} = 全部达标；否则返回未达标项的可读描述。
     * 类别开关关闭、或本次无法判定（通知服务未就绪 / doze 不可用）时跳过，不计入失败 ——
     * 否则会永远报 incomplete。
     *
     * <p>以前只校验 {@code after.perms ⊇ snap.perms}，AppOps / 通知 / Doze / 自启动
     * 失败了也照样报 ok，故障被完全掩盖。
     */
    public String verify(State after, Config cfg, String pkg) {
        StringBuilder miss = new StringBuilder();
        if (cfg.catFor(pkg, "permissions")) {
            for (String p : perms) {
                if (!after.perms.contains(p)) {
                    miss.append("perm:").append(p).append(' ');
                }
            }
        }
        if (cfg.catFor(pkg, "appops")) {
            for (Map.Entry<String, Integer> e : appops.entrySet()) {
                Integer a = after.appops.get(e.getKey());
                if (a == null || !a.equals(e.getValue())) {
                    miss.append("op:").append(e.getKey()).append(' ');
                }
            }
        }
        if (cfg.catFor(pkg, "notifications") && sNotifReady) {
            for (Map.Entry<String, Integer> e : channels.entrySet()) {
                Integer a = after.channels.get(e.getKey());
                if (a == null || !a.equals(e.getValue())) {
                    miss.append("chan:").append(e.getKey()).append(' ');
                }
            }
        }
        if (doze && !sDozeDisabled && !after.doze) {
            miss.append("doze ");
        }
        if (cfg.catFor(pkg, "autostart") && startup != null && after.startup == null) {
            miss.append("autostart ");
        }
        return miss.length() == 0 ? null : miss.toString().trim();
    }

    private static boolean grant(Context ctx, String pkg, String perm) {
        return callPerm("grantRuntimePermission", pkg, perm);
    }

    private static boolean revoke(Context ctx, String pkg, String perm) {
        return callPerm("revokeRuntimePermission", pkg, perm);
    }

    private static boolean callPerm(String name, String pkg, String perm) {
        try {
            Object ipm = Class.forName("android.app.AppGlobals")
                    .getMethod("getPackageManager").invoke(null);
            Object svc = ipm;
            Method m = findNamed(allMethods(ipm.getClass()), name);
            if (m == null) {
                try {
                    Object ps = Class.forName("android.os.ServiceManager")
                            .getMethod("getService", String.class).invoke(null, "permission");
                    if (ps != null) {
                        Method m2 = findNamed(allMethods(ps.getClass()), name);
                        if (m2 != null) {
                            m = m2;
                            svc = ps;
                        }
                    }
                } catch (Throwable ignore) {
                }
            }
            if (m == null) {
                SystemHook.log(name + ": method not found; " + dumpPermMethods(ipm));
                return false;
            }
            m.setAccessible(true);
            if (m.getParameterCount() == 3) {
                m.invoke(svc, pkg, perm, 0);
            } else {
                m.invoke(svc, pkg, perm, 0, "android");
            }
            return true;
        } catch (Throwable t) {
            SystemHook.log(name + " " + perm + " failed: " + t);
            return false;
        }
    }

    private static String dumpPermMethods(Object ipm) {
        StringBuilder sb = new StringBuilder("ipm[");
        for (Method x : allMethods(ipm.getClass())) {
            Class<?>[] ps = x.getParameterTypes();
            if (ps.length >= 3 && ps[0] == String.class && ps[1] == String.class) {
                sb.append(x.getName()).append('/').append(ps.length).append(' ');
            }
        }
        sb.append("] perm[");
        try {
            Object ps = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "permission");
            if (ps != null) {
                for (Method x : allMethods(ps.getClass())) {
                    Class<?>[] pt = x.getParameterTypes();
                    if (pt.length >= 3 && pt[0] == String.class && pt[1] == String.class) {
                        sb.append(x.getName()).append('/').append(pt.length).append(' ');
                    }
                }
            } else {
                sb.append("null");
            }
        } catch (Throwable t) {
            sb.append("err:").append(t);
        }
        sb.append("]");
        return sb.toString();
    }

    private static java.util.List<Method> allMethods(Class<?> c) {
        java.util.List<Method> out = new java.util.ArrayList<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                out.add(m);
            }
        }
        for (Method m : c.getMethods()) {
            boolean dup = false;
            for (Method o : out) {
                if (o.getName().equals(m.getName())
                        && java.util.Arrays.equals(o.getParameterTypes(), m.getParameterTypes())) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(m);
            }
        }
        return out;
    }

    private static Method findNamed(java.util.List<Method> methods, String name) {
        for (Method x : methods) {
            if (x.getName().equals(name)
                    && x.getParameterCount() >= 3
                    && x.getParameterTypes()[0] == String.class
                    && x.getParameterTypes()[1] == String.class) {
                return x;
            }
        }
        return null;
    }

    private static boolean addDozeWhitelist(String pkg) {
        if (sDozeDisabled) {
            return false;
        }
        // Reach DeviceIdleController through its binder ("deviceidle"); com.android.server.*
        // classes are not visible to the injected classloader on this build.
        try {
            Object svc = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "deviceidle");
            if (svc != null) {
                for (Method m : svc.getClass().getMethods()) {
                    if (m.getName().equals("addPowerSaveWhitelistApp")
                            && m.getParameterCount() >= 1
                            && m.getParameterTypes()[0] == String.class) {
                        if (m.getParameterCount() == 1) {
                            m.invoke(svc, pkg);
                        } else {
                            m.invoke(svc, pkg, 0);
                        }
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            SystemHook.log("doze add via binder failed: " + t);
        }
        sDozeDisabled = true;
        SystemHook.log("doze add: IDeviceIdleController unavailable");
        return false;
    }

    // ---------------------------------------------------------------- reflect

    static Object invoke(Object target, String name, Object... args) throws Exception {
        Method m = findMethod(target.getClass(), name, args);
        if (m == null) {
            throw new NoSuchMethodException(name);
        }
        return m.invoke(target, args);
    }

    static Object invokeStatic(Class<?> cls, String name, Object... args) throws Exception {
        Method m = findMethod(cls, name, args);
        if (m == null) {
            throw new NoSuchMethodException(name);
        }
        return m.invoke(null, args);
    }

    static Method findMethod(Class<?> cls, String name, Object... args) {
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length != args.length) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < ps.length; i++) {
                if (args[i] == null) {
                    continue;
                }
                Class<?> p = wrap(ps[i]);
                if (!p.isAssignableFrom(args[i].getClass())) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m;
            }
        }
        return null;
    }

    private static Class<?> wrap(Class<?> c) {
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        return c;
    }
}
