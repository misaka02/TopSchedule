# Keep Custom Views & View Constructors
-keep class com.topware.timetable.ui.view.** { *; }
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep DataBinding Classes
-keep class com.topware.timetable.databinding.** { *; }

# Keep Data Models
-keep class com.topware.timetable.data.model.** { *; }
-keepclassmembers class com.topware.timetable.data.model.** { *; }

# Keep Android Components
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# Keep JS Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Gson Serialization
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**
