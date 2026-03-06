-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.sknote.app.data.model.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# SM.MS Uploader
-keep class com.sknote.app.data.api.SmmsUploader** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }
