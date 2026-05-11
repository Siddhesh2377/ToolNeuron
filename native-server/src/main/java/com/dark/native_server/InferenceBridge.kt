package com.dark.native_server

abstract class InferenceBridge {

    abstract fun startGeneration(
        genId: Long,
        modelId: String,
        messagesJson: String,
        paramsJson: String,
        imagePaths: Array<String>,
    ): Boolean

    abstract fun cancelGeneration(genId: Long)

    open fun startEmbedding(replyId: Long, modelId: String, inputsJson: String): Boolean = false

    open fun startTts(
        replyId: Long,
        modelId: String,
        text: String,
        speakerId: Int,
        speed: Float,
        outPath: String,
    ): Boolean = false

    open fun startStt(replyId: Long, modelId: String, wavPath: String): Boolean = false

    open fun startImageGen(
        replyId: Long,
        modelId: String,
        paramsJson: String,
        inputImagePath: String,
        maskPath: String,
        outPath: String,
    ): Boolean = false

    open fun startImageUpscale(
        replyId: Long,
        modelId: String,
        imagePath: String,
        outPath: String,
    ): Boolean = false

    open fun onRequestEvent(eventJson: String) {}
}
