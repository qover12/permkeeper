package com.permkeeper.z;

import android.app.NotificationChannel;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/** Best-effort access to per-app notification channels via INotificationManager. */
public final class Notif {

    public static Object service() {
        try {
            Class<?> nm = Class.forName("android.app.NotificationManager");
            return nm.getMethod("getService").invoke(null);
        } catch (Throwable t) {
            SystemHook.log("notif service failed: " + t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<NotificationChannel> channels(Object svc, String pkg, int uid) {
        if (svc == null) {
            return Collections.emptyList();
        }
        try {
            for (Method m : svc.getClass().getMethods()) {
                if (!m.getName().equals("getNotificationChannelsForPackage")) {
                    continue;
                }
                if (m.getParameterCount() != 3) {
                    continue;
                }
                Object r = m.invoke(svc, pkg, uid, true);
                if (r instanceof List) {
                    return (List<NotificationChannel>) r;
                }
                if (r != null) {
                    try {
                        Method gl = null;
                        for (Class<?> k = r.getClass(); k != null && gl == null; k = k.getSuperclass()) {
                            try { gl = k.getDeclaredMethod("getList"); }
                            catch (NoSuchMethodException ignored) { }
                        }
                        if (gl != null) {
                            gl.setAccessible(true);
                            Object l = gl.invoke(r);
                            if (l instanceof List) {
                                return (List<NotificationChannel>) l;
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
        } catch (Throwable t) {
            SystemHook.log("getChannels failed: " + t);
        }
        return Collections.emptyList();
    }

    public static void update(Object svc, String pkg, int uid, NotificationChannel ch) {
        if (svc == null) {
            return;
        }
        try {
            for (Method m : svc.getClass().getMethods()) {
                if (!m.getName().equals("updateNotificationChannelForPackage")) {
                    continue;
                }
                if (m.getParameterCount() == 3) {
                    m.invoke(svc, pkg, uid, ch);
                    return;
                }
                if (m.getParameterCount() == 4) {
                    m.invoke(svc, pkg, uid, ch, true);
                    return;
                }
            }
            SystemHook.log("updateNotificationChannelForPackage not found");
        } catch (Throwable t) {
            SystemHook.log("updateChannel failed: " + t);
        }
    }

    /** Recreate a channel that was removed (e.g. by clear-data). */
    public static boolean create(Object svc, String pkg, int uid, NotificationChannel ch) {
        if (svc == null) {
            return false;
        }
        Object slice;
        Class<?> pls;
        try {
            pls = Class.forName("android.content.pm.ParceledListSlice");
            slice = pls.getConstructor(List.class).newInstance(java.util.Collections.singletonList(ch));
        } catch (Throwable t) {
            SystemHook.log("ParceledListSlice failed: " + t);
            return false;
        }
        try {
            // 必须优先用「带 uid」的重载。
            // 两参数版 createNotificationChannels(pkg, slice) 用的是调用方 uid
            // （system_server = 1000），渠道会挂到 uid 1000 名下；应用自己按自己的
            // uid 查不到这些渠道，恢复等于没做，而且后续 scan 会把「应用名下 0 个
            // 渠道」记成新基线，原有渠道从基线里永久丢失。
            // 实测（ColorOS 16 / Android 16）：清数据后 4 个渠道全落到 uid 1000。
            Method withUid = null;
            Method withoutUid = null;
            for (Method m : svc.getClass().getMethods()) {
                if (!m.getName().startsWith("createNotificationChannels")) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] == pls) {
                    withUid = m;
                } else if (p.length == 2 && p[0] == String.class && p[1] == pls) {
                    withoutUid = m;
                }
            }
            if (withUid != null) {
                try {
                    withUid.invoke(svc, pkg, uid, slice);
                    return true;
                } catch (Throwable t) {
                    SystemHook.log("create ex " + withUid.getName() + ": " + t);
                }
            }
            if (withoutUid != null) {
                // 兜底：没有带 uid 的版本时才用，并记一笔 —— 会挂到调用方 uid 下。
                SystemHook.log("create: no uid overload, falling back to "
                        + withoutUid.getName() + " (channels may land under caller uid)");
                try {
                    withoutUid.invoke(svc, pkg, slice);
                    return true;
                } catch (Throwable t) {
                    SystemHook.log("create ex " + withoutUid.getName() + ": " + t);
                }
            }
        } catch (Throwable t) {
            SystemHook.log("createChannels failed: " + t);
        }
        return false;
    }

    private Notif() {
    }
}
