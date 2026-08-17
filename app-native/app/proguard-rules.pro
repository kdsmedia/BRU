# Project-specific ProGuard rules.
# Keep model classes used by kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Supabase / Ktor
-dontwarn io.ktor.**
-dontwarn io.github.jan_supabase.**

# ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# Google Mobile Ads
-keep public class com.google.android.gms.ads.** { public *; }
