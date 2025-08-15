## Consumer ProGuard rules for SmolLM

# Keep SmolLM core and inner classes
-keep class io.shubham0204.smollm.SmolLM { *; }
-keep class io.shubham0204.smollm.SmolLM$* { *; }

# Keep JNI worker used by SmolLM
-keep class io.shubham0204.smollm.workers.JNIWorker { *; }

# Prevent warnings for SmolLM and desugared invoke
-dontwarn io.shubham0204.smollm.**
-keep class java.lang.invoke.** { *; }
-dontwarn java.lang.invoke.**