package io.shubham0204.smollm.workers

import android.content.Context
import android.os.Build
import io.shubham0204.smollm.repo.Architecture
import io.shubham0204.smollm.repo.JNILIB
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

    suspend fun downloadLib(context: Context, onDownloadComplete: () -> Unit) {
        val nativeJniPath = File(context.filesDir, "jniLibs").apply { mkdirs() }

        val libName = getCompatibleJniLibName()
        val matchedLib = jniLibs.find {
            it.name == "lib$libName" // match full filename (without .so)
        } ?: error("Compatible JNI lib not found for $libName")

        jniLibsDownloader(
            fileUrl = matchedLib.link,
            outputFile = File(nativeJniPath, "${matchedLib.name}.so"),
            onProgress = {},
            onComplete = { onDownloadComplete() },
            onError = {}
        )
    }

    // Helper: full CPU feature info
    private fun getCpuInfo(): Triple<String, Boolean, String> {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val isEmulated = Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")
        val features = try {
            File("/proc/cpuinfo").readText()
                .substringAfter("Features").substringAfter(":")
                .substringBefore("\n").trim()
        } catch (_: Exception) {
            ""
        }
        return Triple(abi, isEmulated, features)
    }

    // Helper: CPU capabilities
    private fun decodeFeatures(cpuFeatures: String): FeatureSet {
        return FeatureSet(
            hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp"),
            hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp"),
            hasSve = cpuFeatures.contains("sve"),
            hasI8mm = cpuFeatures.contains("i8mm"),
            isArmV82 = cpuFeatures.contains("asimd") &&
                    cpuFeatures.contains("crc32") &&
                    cpuFeatures.contains("aes"),
            isArmV84 = cpuFeatures.contains("dcpop") &&
                    cpuFeatures.contains("uscat")
        )
    }

    data class FeatureSet(
        val hasFp16: Boolean,
        val hasDotProd: Boolean,
        val hasSve: Boolean,
        val hasI8mm: Boolean,
        val isArmV82: Boolean,
        val isArmV84: Boolean,
    )
}
