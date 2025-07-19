# Keep everything in SmolLM, including its inner classes
-keep class io.shubham0204.smollm.SmolLM { *; }
-keep class io.shubham0204.smollm.SmolLM$* { *; }

# Keep JNIWorker
-keep class io.shubham0204.smollm.workers.JNIWorker { *; }

# Optional: prevent warnings
-dontwarn io.shubham0204.smollm.**

# Keep desugar core library classes
-keep class java.lang.invoke.** { *; }
-dontwarn java.lang.invoke.**
