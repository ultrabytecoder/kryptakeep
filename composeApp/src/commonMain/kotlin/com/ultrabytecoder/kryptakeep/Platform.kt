package com.ultrabytecoder.kryptakeep

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform