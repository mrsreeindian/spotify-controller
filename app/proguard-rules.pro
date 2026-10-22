# ProGuard / R8 rules for Spotify Controller
-keep class com.spotify.controller.data.model.** { *; }
-keep class com.spotify.controller.widget.** { *; }

# Keep AppWidgetProviders referenced in AndroidManifest
-keep public class * extends android.appwidget.AppWidgetProvider {
    public <init>();
    public void onUpdate(android.content.Context, android.appwidget.AppWidgetManager, int[]);
    public void onReceive(android.content.Context, android.content.Intent);
}

# OkHttp & Okio rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
