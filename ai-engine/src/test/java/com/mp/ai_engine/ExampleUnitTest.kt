package com.mp.ai_engine

import com.mp.ai_engine.workers.installer.internal_workers.DiffusionModelInstaller
import com.mp.ai_engine.workers.installer.internal_workers.SherpaSTTModelInstaller
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() = runBlocking {
        DiffusionModelInstaller().unzipFile(File("/home/home/Downloads/HyperSpireV5_qnn2.28_min.zip"), File("/home/home/Downloads/"))
    }
}