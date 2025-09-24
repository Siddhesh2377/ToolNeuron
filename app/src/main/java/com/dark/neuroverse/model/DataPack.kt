package com.dark.neuroverse.model

import androidx.annotation.Keep

@Keep
data class DataPack(
    val name: String = "",
    val description: String = "",
    val size: String = "",
    val author: String = "",
    val issued: String = "",
    val license_text: String = "",
    val license_type: String = "",
    val link: String = ""
)
