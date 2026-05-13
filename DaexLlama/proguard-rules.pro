# Keep JNI native methods
-keep class com.daex.llama.internal.** { *; }
-dontwarn com.daex.llama.internal.**

# Keep llama.cpp symbols
-keep class org.** { *; }
-dontwarn org.**

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.** { *; }
-keep interface kotlinx.coroutines.** { *; }
