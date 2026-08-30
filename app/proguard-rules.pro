# Keep data models for Gson serialization
-keepclassmembers class com.topware.timetable.data.model.** { *; }
-keep class com.topware.timetable.data.model.** { *; }

# Keep JS Bridge interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Jsoup rules
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
