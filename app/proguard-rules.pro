# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Keep model classes (Serializable)
-keep class com.batteryok.evdoctor.model.** { *; }

# Keep activity classes
-keep class com.batteryok.evdoctor.ui.** { *; }

# Keep BuildConfig
-keep class com.batteryok.evdoctor.BuildConfig { *; }
