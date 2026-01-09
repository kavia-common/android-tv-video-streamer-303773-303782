# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
