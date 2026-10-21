# ProGuard / R8 rules for Spotify Controller
-keep class com.spotify.controller.data.model.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
