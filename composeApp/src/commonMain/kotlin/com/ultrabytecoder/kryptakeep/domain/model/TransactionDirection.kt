package com.ultrabytecoder.kryptakeep.domain.model

enum class TransactionDirection(val code: String) {
    INCOMING("INCOMING"),
    OUTGOING("OUTGOING"),
    SELF("SELF");

    companion object {
        fun fromCode(code: String): TransactionDirection =
            entries.find { it.code == code }
                ?: throw IllegalArgumentException("Unknown transaction direction: $code")
    }
}
