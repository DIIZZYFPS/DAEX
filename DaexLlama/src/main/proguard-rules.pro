# Keep native method signatures
-keepclassmembers class com.daex.llama.* {
    native <methods>;
}

# Keep JNI bridge class
-keep class com.daex.llama.internal.DaexLlamaEngineImpl {
    <init>(...);
    public *;
}
