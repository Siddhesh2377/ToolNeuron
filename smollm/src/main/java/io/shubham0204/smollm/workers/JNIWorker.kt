package io.shubham0204.smollm.workers

import android.content.Context
import android.os.Build
import io.shubham0204.smollm.repo.Architecture
import io.shubham0204.smollm.repo.JNILIB
import io.shubham0204.smollm.repo.jniLibs
import java.io.File
import java.io.FileNotFoundException

object JNIWorker {

    suspend fun downloadLib(context: Context, onDownloadComplete: () -> Unit) {
        var libToDownload = JNILIB("", "", Architecture.ARM64)
        val nativeJniPath = File(context.filesDir, "jniLibs")

        val cpuFeatures = getCPUFeatures()
        val hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp")
        val hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")
        val hasSve = cpuFeatures.contains("sve")
        val hasI8mm = cpuFeatures.contains("i8mm")
        val isAtLeastArmV82 =
            cpuFeatures.contains("asimd") && cpuFeatures.contains("crc32") && cpuFeatures.contains(
                "aes",
            )
        val isAtLeastArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")

        val isEmulated = (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu"))

        if (!isEmulated) {
            if (supportsArm64V8a()) {
                libToDownload = if (isAtLeastArmV84 && hasSve && hasI8mm && hasFp16 && hasDotProd) {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8_4_fp16_dotprod_i8mm_sve" }!!
                } else if (isAtLeastArmV84 && hasSve && hasFp16 && hasDotProd) {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8_4_fp16_dotprod_sve" }!!
                } else if (isAtLeastArmV84 && hasI8mm && hasFp16 && hasDotProd) {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8_4_fp16_dotprod_i8mm" }!!
                } else if (isAtLeastArmV84 && hasFp16 && hasDotProd) {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8_4_fp16_dotprod" }!!
                } else if (isAtLeastArmV82 && hasFp16 && hasDotProd) {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8_2_fp16_dotprod" }!!
                } else if (isAtLeastArmV82 && hasFp16) {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8_2_fp16" }!!
                } else {
                    jniLibs.find { it.architecture == Architecture.ARM64 && it.name == "libsmollm_v8" }!!
                }
            } else if (Build.SUPPORTED_32_BIT_ABIS[0]?.equals("armeabi-v7a") == true) {
                System.loadLibrary("smollm_v7a")
                libToDownload =
                    jniLibs.find { it.architecture == Architecture.ARM && it.name == "smollm_v7a" }!!
            }
        } else {
            libToDownload =
                jniLibs.find { it.architecture == Architecture.X86_64 && it.name == "smollm" }!!
        }


        jniLibsDownloader(
            fileUrl = libToDownload.link,
            outputFile = File(nativeJniPath, "${libToDownload.name}.so"),
            onProgress = {

            },
            onComplete = {
                onDownloadComplete()
            },
            onError = {

            })
    }

    private fun getCPUFeatures(): String {
        val cpuInfo = try {
            File("/proc/cpuinfo").readText()
        } catch (e: FileNotFoundException) {
            ""
        }
        val cpuFeatures =
            cpuInfo.substringAfter("Features").substringAfter(":").substringBefore("\n").trim()
        return cpuFeatures
    }

    private fun supportsArm64V8a(): Boolean = Build.SUPPORTED_ABIS[0].equals("arm64-v8a")
}

