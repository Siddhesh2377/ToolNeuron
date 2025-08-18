// export.gradle.kts

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.util.Locale
import java.util.Properties

// ---------- SDK + tools ----------
fun sdkRootDir(rootProject: org.gradle.api.Project): File {
    val lp = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    lp.getProperty("sdk.dir")?.let { return File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
    System.getenv("ANDROID_HOME")?.let { return File(it) }
    error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_SDK_ROOT.")
}

fun latestBuildTools(sdk: File): File {
    val dir = File(sdk, "build-tools")
    val all = dir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
    require(all.isNotEmpty()) { "No build-tools in $dir" }
    return all.first()
}

fun findCompileSdkIfAndroid(project: org.gradle.api.Project): Int? {
    val ext = project.extensions.findByName("android") ?: return null
    val m = ext.javaClass.methods.firstOrNull { it.name == "getCompileSdk" } ?: return null
    return (m.invoke(ext) as? Number)?.toInt()
}

fun findAndroidJar(sdk: File, compileSdk: Int?): File {
    if (compileSdk != null) {
        val f = File(sdk, "platforms/android-$compileSdk/android.jar")
        if (f.exists()) return f
    }
    val platformsDir = File(sdk, "platforms")
    val candidates = platformsDir.listFiles()
        ?.filter { File(it, "android.jar").exists() }
        ?.sortedByDescending { plat ->
            plat.name.removePrefix("android-").toIntOrNull() ?: -1
        }
        ?: emptyList()
    require(candidates.isNotEmpty()) { "No android.jar found in $platformsDir" }
    return File(candidates.first(), "android.jar")
}

val sdkDir = sdkRootDir(rootProject)
val buildToolsDir = latestBuildTools(sdkDir)
val d8Exe = File(
    buildToolsDir,
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) "d8.bat" else "d8"
)

val compileSdkOpt = findCompileSdkIfAndroid(project)
val androidJarFile = findAndroidJar(sdkDir, compileSdkOpt)

val dexWorkDir = layout.buildDirectory.dir("outputs/pluginDex")
val tmpDir = layout.buildDirectory.dir("tmp/pluginDex")

val pluginVariant = providers.gradleProperty("pluginVariant").orElse("release")
fun cap(s: String) = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

// ---------- Locator (run at execution time only) ----------
sealed interface PluginInput {
    data class Jar(val file: File) : PluginInput
    data class Dir(val dir: File) : PluginInput
    data class Aar(val file: File) : PluginInput
}

fun locateBuildArtifact(project: org.gradle.api.Project, variant: String): PluginInput? {
    val b = project.layout.buildDirectory.get().asFile
    fun newest(files: List<File>): File? =
        files.filter { it.exists() }.maxByOrNull { it.lastModified() }

    // 1) AAR (Android library)
    newest(
        listOf(
            File(b, "outputs/aar/${project.name}-${variant}.aar"),
            File(b, "outputs/aar/${project.name}-${cap(variant)}.aar")
        ) + (File(b, "outputs/aar").listFiles()?.toList() ?: emptyList())
            .filter { it.extension == "aar" && it.name.contains(variant, ignoreCase = true) }
    )?.let { return PluginInput.Aar(it) }

    // 2) classes.jar produced by AGP (various places)
    val classJars = mutableListOf<File>()
    classJars += File(b, "intermediates/aar_main_jar/$variant/classes.jar")
    classJars += File(b, "intermediates/compile_library_classes_jar/$variant/classes.jar")
    classJars += File(b, "intermediates/compile_app_classes_jar/$variant/classes.jar")
    classJars += File(b, "intermediates/runtime_library_classes_jar/$variant/classes.jar")
    classJars += File(b, "intermediates/compiled_local_resources/$variant/classes.jar")
    // any stray classes.jar
    classJars += project.fileTree(b) { include("**/$variant/**/classes.jar") }.files
    newest(classJars)?.let { return PluginInput.Jar(it) }

    // 3) JVM library jar in build/libs
    newest((File(b, "libs").listFiles() ?: emptyArray()).filter { it.extension == "jar" }.toList())
        ?.let { return PluginInput.Jar(it) }

    // 4) Class directories (javac/kotlin)
    val classDirs = listOf(
        File(b, "intermediates/javac/$variant/classes"),
        File(b, "tmp/kotlin-classes/$variant"),
        File(b, "classes/kotlin/main"),
        File(b, "classes/java/main")
    ).filter { it.exists() }
    newest(classDirs)?.let { return PluginInput.Dir(it) }

    return null
}

// ---------- Tasks ----------
val makeDex = tasks.register("makeDex", Exec::class) {
    group = "build"
    description = "Runs D8 to produce classes.dex from your plugin outputs."

    // Depend on assemble<Variant> if present, else plain assemble
    val assembleTaskName = "assemble" + cap(pluginVariant.get())
    val assembleCandidate = tasks.findByName(assembleTaskName) ?: tasks.findByName("assemble")
    if (assembleCandidate != null) dependsOn(assembleCandidate)

    doFirst {
        require(d8Exe.exists()) { "d8 not found: $d8Exe" }
        require(androidJarFile.exists()) { "android.jar not found: $androidJarFile" }

        val variant = pluginVariant.get()
        val input = locateBuildArtifact(project, variant)
            ?: error("Could not find classes.jar, AAR, JAR, or class dirs in ${project.layout.buildDirectory.get().asFile} for variant '$variant'. Try assembling that variant first.")

        dexWorkDir.get().asFile.mkdirs()
        tmpDir.get().asFile.mkdirs()

        val inputForD8: String = when (input) {
            is PluginInput.Jar -> input.file.absolutePath
            is PluginInput.Dir -> input.dir.absolutePath
            is PluginInput.Aar -> {
                // Extract classes.jar from AAR
                val dest = tmpDir.get().asFile
                project.copy {
                    from(project.zipTree(input.file))
                    into(dest)
                    include("classes.jar")
                }
                val jar = File(dest, "classes.jar")
                require(jar.exists()) { "classes.jar not found inside AAR: ${input.file}" }
                jar.absolutePath
            }
        }

        println(">> D8 input: $inputForD8")
        println(">> Using android.jar: $androidJarFile")
        commandLine(
            d8Exe.absolutePath,
            "--release",
            "--min-api", "26",
            "--lib", androidJarFile.absolutePath,
            "--output", dexWorkDir.get().asFile.absolutePath,
            inputForD8
        )
    }

    doLast {
        val dex = dexWorkDir.get().asFile.resolve("classes.dex")
        if (!dex.exists()) error("d8 finished but no classes.dex was produced at $dex")
        println(">> d8 wrote: $dex")
    }
}

val packDexJar = tasks.register("packDexJar", Zip::class) {
    group = "build"
    description = "Packs classes.dex into plugin.dex.jar for dynamic loading."
    dependsOn(makeDex)

    archiveFileName.set("plugin.dex.jar")
    destinationDirectory.set(dexWorkDir)
    from(dexWorkDir) { include("classes.dex") }

    doLast {
        println(">> Created: ${destinationDirectory.get().asFile.resolve(archiveFileName.get())}")
    }
}

tasks.register("buildPluginDexJar") {
    group = "build"
    description = "Builds plugin.dex.jar (jar containing classes.dex) for dynamic loading."
    dependsOn(packDexJar)
}
