package com.dark.neuroverse.model

data class OnlinePlugin(
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val downloadLink: String = "",
    val size: String = "",
    val sha: String = "",
    val apiVersion: String? = null,
    val category: String? = null,
    val fileExtension: String? = "zip",
    val mainClass: String? = null,
    val tools: List<String>? = null,
    val thumbnailUrl: String? = null,
    val lastUpdated: String? = null,
    val minAppVersion: String? = null
)