package com.memoryvault.core

enum class BlockType(val code: Byte) {
    MESSAGE(1),
    FILE(2),
    CUSTOM_DATA(3),
    EMBEDDING(4);

    companion object {
        private val map = values().associateBy { it.code }
        fun fromCode(code: Byte): BlockType = map[code]
            ?: throw IllegalArgumentException("Unknown block type: $code")
    }
}
