package com.dark.tool_neuron.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HuggingFaceApi {
    
    @GET("api/models/{repo}")
    suspend fun getRepoInfo(@Path("repo", encoded = true) repo: String): Response<HuggingFaceRepoResponse>
    
    @GET("api/models/{repo}/tree/main")
    suspend fun getRepoFiles(
        @Path("repo", encoded = true) repo: String,
        @Query("recursive") recursive: Boolean = true
    ): Response<List<HuggingFaceFileResponse>>

    @GET("api/models")
    suspend fun searchModels(
        @Query("filter") filter: String = "gguf",
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
        @Query("limit") limit: Int = 20
    ): Response<List<HuggingFaceSearchRepoResponse>>
}

data class HuggingFaceRepoResponse(
    val modelId: String,
    val siblings: List<HuggingFaceFileResponse>?
)

data class HuggingFaceFileResponse(
    val path: String,
    val size: Long?
)

data class HuggingFaceSearchRepoResponse(
    val id: String,
    val author: String?,
    val downloads: Long?,
    val likes: Long?,
    val tags: List<String>?,
    val gated: Boolean?
)
