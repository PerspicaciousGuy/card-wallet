# Strip all android.util.Log calls from release builds (plan §3 rule 6):
# even accidental future logging cannot leak card data in production.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
