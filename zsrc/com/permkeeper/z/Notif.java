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
                if (!m.getName().equals("getNotificationChannelsForPackage")
                        || m.getParameterCount() != 3) {
                    continue;
                }
                Object r = m.invoke(svc, pkg, uid, true);
                if (r instanceof List) {
                    return (List<NotificationChannel>) r;
                }
                if (r != null) {
                    try {
                        Object l = r.getClass().getMethod("getList").invoke(r);
                        if (l instanceof List) {
                            return (List<NotificationChannel>) l;
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
            for (Method m : svc.getClass().getMethods()) {
                if (!m.getName().startsWith("createNotificationChannels")) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                try {
                    if (p.length == 2 && p[0] == String.class && p[1] == pls) {
                        m.invoke(svc, pkg, slice);
                        return true;
                    }
                    if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] == pls) {
                        m.invoke(svc, pkg, uid, slice);
                        return true;
                    }
                } catch (Throwable t) {
                    SystemHook.log("create ex " + m.getName() + ": " + t);
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
