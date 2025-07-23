package io.shubham0204.smollm.workers

import android.content.Context
import android.os.Build
import android.util.Log
import io.shubham0204.smollm.repo.jniLibs
import java.io.File

object JNIWorker {

    fun getCompatibleJniLibName(): String {
        val (abi, isEmulated, features) = getCpuInfo()
        val (hasFp16, hasDotProd, hasSve, hasI8mm, isArmV82, isArmV84) = decodeFeatures(features)

        return when {
            isEmulated -> "smollm"
            !abi.contains("arm64-v8a") -> "smollm_v7a"
            isArmV84 && hasSve && hasI8mm && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod_i8mm_sve"
            isArmV84 && hasSve && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod_sve"
            isArmV84 && hasI8mm && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod_i8mm"
            isArmV84 && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod"
            isArmV82 && hasFp16 && hasDotProd -> "smollm_v8_2_fp16_dotprod"
            isArmV82 && hasFp16 -> "smollm_v8_2_fp16"
            else -> "smollm_v8"
        }
    }

    suspend fun downloadLib(context: Context, libName: String, onDownloadComplete: () -> Unit) {
        val outputFile =
            File(context.filesDir, "jniLibs/lib$libName.so").apply { parentFile?.mkdirs() }
        val matchedLib =
            jniLibs.find { it.name == "lib$libName" } ?: error("No matching JNI lib for $libName")

        jniLibsDownloader(
            fileUrl = matchedLib.link,
            outputFile = outputFile,
            onProgress = {},
            onComplete = onDownloadComplete,
            onError = { Log.e("JNIWorker", "Failed to download $libName") })
    }

    suspend fun downloadLib(context: Context, onDownloadComplete: () -> Unit) {
        downloadLib(context, getCompatibleJniLibName(), onDownloadComplete)
    }

    private fun getCpuInfo(): Triple<String, Boolean, String> {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val isEmulated = Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")
        val features = try {
            File("/proc/cpuinfo").readText().substringAfter("Features:").substringBefore("\n")
                .trim()
        } catch (_: Exception) {
            ""
        }
        return Triple(abi, isEmulated, features)
    }

    private fun decodeFeatures(cpuFeatures: String) = FeatureSet(
        hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp"),
        hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp"),
        hasSve = cpuFeatures.contains("sve"),
        hasI8mm = cpuFeatures.contains("i8mm"),
        isArmV82 = cpuFeatures.contains("asimd") && cpuFeatures.contains("crc32") && cpuFeatures.contains(
            "aes"
        ),
        isArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")
    )

    data class FeatureSet(
        val hasFp16: Boolean,
        val hasDotProd: Boolean,
        val hasSve: Boolean,
        val hasI8mm: Boolean,
        val isArmV82: Boolean,
        val isArmV84: Boolean
    )
}

