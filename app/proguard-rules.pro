# Keep original class and package names without obfuscation
-dontobfuscate

# Keep all project classes, methods, and constructors
-keep class com.topware.timetable.** { *; }
-keepclassmembers class com.topware.timetable.** { *; }

# Keep all custom view constructors used in XML layouts
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep JS Bridge interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Gson & Jsoup
-keep class com.google.gson.** { *; }
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**
-dontwarn sun.misc.**

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
