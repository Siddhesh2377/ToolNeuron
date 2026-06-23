package com.dark.tool_neuron.repo.web_search

import android.content.Context
import com.dark.networking.WebNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

data class FetchedPage(
    val url: String,
    val text: String,
)

@Singleton
class PageFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun fetch(urls: List<String>, maxCharsPerPage: Int = PAGE_CHAR_CAP): List<FetchedPage> {
        if (urls.isEmpty()) return emptyList()
        WebNative.ensureReady(context)
        return coroutineScope {
            urls.distinct().map { url ->
                async(Dispatchers.IO) {
                    val resp = WebNative.fetch(url = url, timeoutMs = PAGE_TIMEOUT_MS).getOrNull()
                    if (resp == null || resp.status !in 200..399 || resp.body.isBlank()) return@async null
                    val text = HtmlText.extract(resp.body, maxCharsPerPage)
                    if (text.length < MIN_USEFUL_CHARS) null else FetchedPage(url, text)
                }
            }.awaitAll().filterNotNull()
        }
    }

    private companion object {
        const val PAGE_TIMEOUT_MS = 12_000
        const val PAGE_CHAR_CAP = 2500
        const val MIN_USEFUL_CHARS = 120
    }
}
