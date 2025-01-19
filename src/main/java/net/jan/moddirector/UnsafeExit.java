package net.jan.moddirector;

import net.minecraftforge.fml.exit.QualifiedExit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Attempts to bypass forge security manager
 */
public class UnsafeExit {
    private static MethodHandle exit;

    static {
        // Go nuclear trying to bypass Forge's security manager without removing it
        try {
            var lookup = MethodHandles.lookup();
            // Java 9+ won't let us have fun with arbitrary reflection, go Unsafe
            try {
                var unsafeClass = Class.forName("sun.misc.Unsafe");
                Object unsafe = null;
                for (Field field : unsafeClass.getDeclaredFields()) {
                    if (field.getType().equals(unsafeClass)) {
                        field.setAccessible(true);
                        unsafe = field.get(null);
                    }
                }
                if (unsafe != null) {
                    var lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                    long lookupFieldOffset = (long) unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class)
                        .invoke(unsafe, lookupField);
                    lookup = (MethodHandles.Lookup) unsafeClass.getDeclaredMethod("getObject", Object.class, long.class)
                        .invoke(unsafe, MethodHandles.Lookup.class, lookupFieldOffset);
                }
            } catch (Throwable ignored) {
                // Hope we have access to normal reflection, eh?
            }

            var shutdown = Class.forName("java.lang.Shutdown");
            var exitMethod = shutdown.getDeclaredMethod("exit", int.class);
            try {
                exitMethod.setAccessible(true);
            } catch (Throwable ignored) {
                // Too many catches, setAccessible may fail, but we may still be able to unreflect with IMPL_LOOKUP
            }
            exit = lookup.unreflect(exitMethod); // :)
        } catch (Throwable ignored) {
            // We are out of luck here :(
        }
    }

    public static void exit(int code) {
        if (exit != null) {
            try {
                exit.invoke(code);
            } catch (Throwable ignored) {
                // Attempt normal exit instead
            }
        }
        QualifiedExit.exit(code);
    }
}
