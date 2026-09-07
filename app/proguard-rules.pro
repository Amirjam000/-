# =================================================================
# قوانین Proguard / R8 برای بیلد Release بهینه‌شده و پایدار
# =================================================================

# ۱. حفظ کلاس‌ها و توابع بومی (JNI) کتابخانه WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# ۲. حفظ کلاس‌های Google Play Services Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# ۳. حفظ مدل‌های داده‌ای سریالایز شونده با Gson
-keepclassmembers class com.meshconnect.app.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <fields>;
    <init>(...);
}
-keep class com.meshconnect.app.model.** { *; }

# ۴. حفظ کلاس‌های انیمیشن و کامپوننت‌های رندر Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ۵. پشتیبانی از فایربیس
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
