package com.dark.tool_neuron

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.RAGEngine
import com.dark.gguf_lib.models.GenerationEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Models are not bundled. Push them into <cacheDir>/tn-smoke/ first:
//   adb shell "cat /data/local/tmp/tn/<file> | run-as com.dark.tool_neuron sh -c 'cat > cache/tn-smoke/<file>'"
// A test whose model is missing is skipped, not failed.
@RunWith(AndroidJUnit4::class)
class GgufBackendDeviceTest {

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "tn-smoke")

    private fun model(name: String): File {
        val file = File(dir, name)
        assumeTrue("missing ${file.path}", file.exists())
        return file
    }

    private fun chat(engine: GGMLEngine, question: String, maxTokens: Int = 64): String = runBlocking {
        val messages = """[{"role":"user","content":${JSONObject.quote(question)}}]"""
        val events = withTimeout(180_000) { engine.generateMultiTurnFlow(messages, maxTokens).toList() }
        val errors = events.filterIsInstance<GenerationEvent.Error>()
        assertTrue("generation error: $errors", errors.isEmpty())
        assertTrue(events.any { it is GenerationEvent.Done })
        events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
    }

    private fun assertAnswersParis(engine: GGMLEngine) {
        val answer = chat(engine, "What is the capital of France? Answer with one word.")
        assertTrue("unexpected answer: $answer", answer.contains("Paris", ignoreCase = true))
    }

    @Test
    fun chatModelLoadsFromPathAndAnswers() = runBlocking {
        val engine = GGMLEngine()
        assertTrue(engine.load(model("lfm2-350m-q4.gguf").path, contextSize = 2048))
        assertNotNull(engine.getModelInfoJson())
        assertAnswersParis(engine)
        engine.unload()
        assertFalse(engine.isLoaded)
    }

    @Test
    fun chatModelLoadsFromFileDescriptorAndAnswers() = runBlocking {
        val engine = GGMLEngine()
        ParcelFileDescriptor.open(model("lfm2-350m-q4.gguf"), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            assertTrue(engine.loadFromFd(pfd.fd, contextSize = 2048))
        }
        assertAnswersParis(engine)
        engine.unload()
    }

    @Test
    fun gemma4LoadsAndAnswers() = runBlocking {
        val engine = GGMLEngine()
        assertTrue(engine.load(model("gemma-4-e2b-q4.gguf").path, contextSize = 2048))
        engine.setThinkingEnabled(false)
        assertAnswersParis(engine)
        engine.unload()
    }

    @Test
    fun loadFailureIsReportedNotFatal() = runBlocking {
        val engine = GGMLEngine()
        assertFalse(engine.load(File(dir, "does-not-exist.gguf").path))
        assertFalse(engine.isLoaded)
    }

    @Test
    fun visionModelDescribesAnImage() = runBlocking {
        val engine = GGMLEngine()
        assertTrue(engine.load(model("smolvlm-256m-q8.gguf").path, contextSize = 4096))
        assertTrue(engine.loadVlmProjector(model("smolvlm-256m-mmproj-q8.gguf").path))
        val prompt = engine.getVlmDefaultMarker() + "\nDescribe this image in one sentence."
        val messages = """[{"role":"user","content":${JSONObject.quote(prompt)}}]"""
        val events = withTimeout(300_000) {
            engine.generateVlmFlow(messages, listOf(model("test-1.jpeg").readBytes()), maxTokens = 24).toList()
        }
        assertTrue(events.filterIsInstance<GenerationEvent.Error>().isEmpty())
        assertTrue(events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }.isNotBlank())
        engine.releaseVlmProjector()
        engine.unload()
    }

    private fun assertRetrieves(modelFile: String) = runBlocking {
        RAGEngine().use { rag ->
            assertTrue(rag.create())
            assertTrue(rag.loadModel(model(modelFile).path))
            assertTrue(rag.addDocument("Paris is the capital of France. The Eiffel Tower stands beside the Seine.", "geo:paris") > 0)
            assertTrue(rag.addDocument("Rust is a systems language. Its borrow checker rejects data races at compile time.", "code:rust") > 0)
            assertTrue(rag.addDocument("Sourdough bread needs a fed starter, a long ferment and a hot dutch oven.", "food:bread") > 0)
            assertEquals(3, rag.documentCount)
            assertEquals("geo:paris", rag.query("Which city is the capital of France?").first().docId)
            assertEquals("code:rust", rag.query("How does the borrow checker stop data races?").first().docId)
            assertEquals("food:bread", rag.query("How do I bake a sourdough loaf?").first().docId)
            val vector = rag.encode("hello world")
            assertNotNull(vector)
            assertTrue(vector!!.isNotEmpty())
        }
    }

    @Test
    fun encoderEmbeddingModelRetrieves() = assertRetrieves("nomic-embed-q4.gguf")

    @Test
    fun decoderEmbeddingModelRetrieves() = assertRetrieves("embed-qwen3-0.6b-q8.gguf")
}
