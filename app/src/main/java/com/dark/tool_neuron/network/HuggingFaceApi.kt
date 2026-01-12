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
}

data class HuggingFaceRepoResponse(
    val modelId: String,
    val siblings: List<HuggingFaceFileResponse>?
)

data class HuggingFaceFileResponse(
    val path: String,
    val size: Long?
)
