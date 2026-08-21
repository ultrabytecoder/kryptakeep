package com.ultrabytecoder.kryptakeep.domain.repository

enum class SecurityMethod {
    PIN,
    PASSWORD;
}

/**
 * Platforms with no OS-backed hardware keystore must restrict the user
 * to high-entropy passwords.
 */
expect val supportedSecurityMethods: List<SecurityMethod>
