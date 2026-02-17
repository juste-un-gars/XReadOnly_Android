# XReadOnly ProGuard/R8 Rules

# ---- Log stripping (safety net) ----
# All Log calls are already gated behind BuildConfig.DEBUG checks,
# so R8 eliminates them as dead code. These rules are an extra guarantee
# that no log strings leak into the release APK.
# Log.e is intentionally kept for crash diagnostics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# ---- WebView ----
# Keep @JavascriptInterface methods if added in future
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- Crash reports ----
# Preserve source file names and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
