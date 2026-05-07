# ── ShimmerENACT ProGuard / R8 rules ─────────────────────────────────────────

# Keep data model classes used in DataStore / serialisation
-keep class com.rfsat.shimmerenact.data.models.** { *; }
-keep class com.rfsat.shimmerenact.data.repository.** { *; }

# Keep BT protocol constants (accessed via reflection in some cases)
-keep class com.rfsat.shimmerenact.data.bluetooth.ShimmerProtocol { *; }
-keep class com.rfsat.shimmerenact.data.bluetooth.CalibrationParams { *; }

# Jetpack Compose — R8 handles most of this automatically with Kotlin metadata,
# but keep the @Composable annotation processor output intact.
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# MPAndroidChart — uses reflection for animations
-keep class com.github.mikephil.charting.** { *; }

# OkHttp (pulled in transitively) — suppress irrelevant warnings
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines / serialisation metadata
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes SourceFile, LineNumberTable   # preserves crash stack traces

# Remove verbose logging in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# DataStore Preferences
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
