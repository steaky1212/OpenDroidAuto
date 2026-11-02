package it.smg.libs.common;

/**
 * Test-only stand-in for the real Log, which loads native libraries in a static
 * initializer and so cannot be loaded in a plain JVM unit test. Shadows the real
 * class because test classes come first on the unit-test classpath.
 */
public class Log {
    public static boolean isVerbose() { return false; }
    public static boolean isDebug() { return false; }
    public static boolean isInfo() { return false; }
    public static boolean isWarn() { return false; }
    public static boolean isError() { return false; }

    public static void v(String tag, String message) {}
    public static void d(String tag, String message) {}
    public static void i(String tag, String message) {}
    public static void w(String tag, String message) {}
    public static void e(String tag, String message) {}
    public static void e(String tag, String message, Throwable t) {}
}
