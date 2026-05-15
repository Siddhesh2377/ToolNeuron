package com.dark.tool_neuron.repo.web_search

import android.content.Context
import com.dark.networking.WebNative
import com.dark.tool_neuron.model.WebSearchHit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun search(query: String, maxResults: Int, queryIndex: Int): Result<List<WebSearchHit>> {
        WebNative.ensureReady(context)
        return WebNative.search(query = query, maxResults = maxResults, locale = "").map { raw ->
            raw.map {
                WebSearchHit(
                    title = it.title,
                    url = it.url,
                    snippet = it.snippet,
                    sourceQueryIndex = queryIndex,
                )
            }
        }
    }
}
