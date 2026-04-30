package com.dark.tool_neuron.repo.research

import android.content.Context
import com.dark.networking.WebNative
import com.dark.networking.WebSearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DdgSearch @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun ensureReady() {
        WebNative.ensureReady(context)
    }

    suspend fun search(query: String, maxResults: Int): Result<List<WebSearchResult>> {
        ensureReady()
        return WebNative.search(query = query, maxResults = maxResults)
    }

    suspend fun fetch(url: String, timeoutMs: Int = 15000): Result<String> {
        ensureReady()
        return WebNative.fetch(url = url, timeoutMs = timeoutMs).mapCatching { resp ->
            if (!resp.isSuccess) error("HTTP ${resp.status}${resp.error?.let { " ($it)" } ?: ""}")
            resp.body
        }
    }
}
