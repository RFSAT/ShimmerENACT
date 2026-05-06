# ShimmerENACT ProGuard rules

# Keep Shimmer protocol classes
-keep class com.rfsat.shimmerenact.data.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# OpenCSV
-keep class com.opencsv.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }
