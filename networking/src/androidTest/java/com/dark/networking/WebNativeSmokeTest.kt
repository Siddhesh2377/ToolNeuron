package com.dark.networking

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebNativeSmokeTest {

    private val tag = "WebNativeSmoke"

    @Test
    fun backendIsCurlImpersonate() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        WebNative.ensureReady(ctx)
        Log.i(tag, "isReady=${WebNative.isReady} backend=${WebNative.backend}")
        assertTrue("native backend not ready", WebNative.isReady)
        assertTrue(
            "expected curl-impersonate backend but got ${WebNative.backend}",
            WebNative.backend.contains("curl-impersonate")
        )
    }

    @Test
    fun ddgLiveSearchReturnsResults() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        WebNative.ensureReady(ctx)

        val outcome = WebNative.search("kotlin coroutines", maxResults = 5)
        val err = outcome.exceptionOrNull()
        Log.i(tag, "search success=${outcome.isSuccess} err=${err?.message}")

        assertTrue("search failed: ${err?.message}", outcome.isSuccess)

        val results = outcome.getOrThrow()
        results.forEachIndexed { i, r ->
            Log.i(tag, "[$i] ${r.title} — ${r.url}")
        }
        assertTrue("expected >=1 result, got ${results.size}", results.isNotEmpty())
        assertTrue("first url empty", results.first().url.isNotBlank())
    }
}
