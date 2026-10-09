package com.permkeeper.z;

import java.lang.reflect.Method;

public final class Utils {

    public static Object localService(String className) {
        try {
            // Use the bootstrap classloader: our injected classloader cannot see com.android.server.*
            Class<?> iface = Class.forName(className, false, null);
            Class<?> ls = Class.forName("com.android.server.LocalServices", false, null);
            Method m = ls.getMethod("getService", Class.class);
            return m.invoke(null, iface);
        } catch (Throwable t) {
            SystemHook.log("localService " + className + ": " + t);
            return null;
        }
    }

    private Utils() {
    }
}
