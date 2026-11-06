package me.elhizazi.Castix.display;

import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Method;

/**
 * Isolated display-power bridge adapted from Moderpach/Extinguish (GPL-3.0).
 *
 * Run exclusively in a privileged app_process child launched through su.
 * DO NOT call from Castix's normal app process or system_server.
 *
 * OFF:  SurfaceControl.POWER_MODE_OFF (0)
 * ON:   SurfaceControl.POWER_MODE_NORMAL (2)
 *
 * DisplayControl.getPhysicalDisplayToken() became necessary on Android 14+.
 * Vendor ROMs may reject hidden API access; failures exit with non-zero status.
 */
public final class RootDisplayCli {
    private static final int POWER_MODE_OFF = 0;
    private static final int POWER_MODE_NORMAL = 2;

    private RootDisplayCli() {}

    public static void main(String[] args) {
        if (args.length != 1 || (!"off".equals(args[0]) && !"on".equals(args[0]))) {
            System.err.println("Usage: RootDisplayCli off|on");
            System.exit(2);
            return;
        }
        try {
            IBinder token = getPrimaryPhysicalDisplayToken();
            Class<?> surfaceControl = Class.forName("android.view.SurfaceControl");
            Method setPowerMode = surfaceControl.getMethod(
                "setDisplayPowerMode", IBinder.class, int.class);
            int mode = "off".equals(args[0]) ? POWER_MODE_OFF : POWER_MODE_NORMAL;
            setPowerMode.invoke(null, token, mode);
            System.out.println("CASTIX_DISPLAY_" + args[0].toUpperCase() + "_OK");
        } catch (Throwable t) {
            System.err.println("CASTIX_DISPLAY_FAILED: " + t);
            t.printStackTrace(System.err);
            System.exit(3);
        }
    }

    private static IBinder getPrimaryPhysicalDisplayToken() throws Exception {
        Class<?> surfaceControl = Class.forName("android.view.SurfaceControl");
        if (Build.VERSION.SDK_INT < 29) {
            return (IBinder) surfaceControl.getMethod(
                "getBuiltInDisplay", int.class).invoke(null, 0);
        }
        if (Build.VERSION.SDK_INT < 34) {
            return (IBinder) surfaceControl.getMethod("getInternalDisplayToken")
                .invoke(null);
        }

        // Extinguish's Android 14+ compatibility path. The services jar
        // is not on the public app classpath.
        Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
        Method createLoader = factory.getDeclaredMethod(
            "createClassLoader",
            String.class, String.class, String.class, ClassLoader.class,
            int.class, boolean.class, String.class);
        ClassLoader loader = (ClassLoader) createLoader.invoke(
            null,
            "/system/framework/services.jar",
            null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
        Class<?> displayControl = loader.loadClass(
            "com.android.server.display.DisplayControl");

        // Match Extinguish's runtime JNI initialization for DisplayControl.
        try {
            Method loadLibrary0 = Runtime.class.getDeclaredMethod(
                "loadLibrary0", Class.class, String.class);
            loadLibrary0.setAccessible(true);
            loadLibrary0.invoke(Runtime.getRuntime(), displayControl, "android_servers");
        } catch (Throwable ex) {
            // On some builds the library was already loaded by app_process;
            // continue and let the token methods report a real error if not.
            System.err.println("DisplayControl JNI init warning: " + ex);
        }

        long[] ids = (long[]) displayControl.getMethod("getPhysicalDisplayIds")
            .invoke(null);
        if (ids == null || ids.length == 0) {
            throw new IllegalStateException("No physical displays returned");
        }
        return (IBinder) displayControl.getMethod(
            "getPhysicalDisplayToken", long.class).invoke(null, ids[0]);
    }
}
