package com.freeflow.app.identity

import com.freeflow.app.crypto.KeyPair
import org.bouncycastle.crypto.digests.SHA256Digest

/**
 * FreeFlow identity (PROTOCOL.md Section 4)
 *
 * - X25519 key pair for identity
 * - Fingerprint = SHA-256(raw_public_key_bytes)[0:8] displayed as 16 hex chars
 */
data class Identity(
    val name: String,
    val privateKey: ByteArray,
    val publicKey: ByteArray
) {
    /**
     * Fingerprint bytes: SHA-256(publicKey)[0:8]
     */
    val fingerprintBytes: ByteArray
        get() = computeFingerprint(publicKey)

    /**
     * Fingerprint as 16 lowercase hex characters.
     */
    val fingerprintHex: String
        get() = fingerprintBytes.joinToString("") { String.format("%02x", it) }

    companion object {
        /**
         * Generate a new identity with CSPRNG-generated X25519 keys.
         */
        fun generate(name: String): Identity {
            val kp = KeyPair.generate()
            return Identity(
                name = name,
                privateKey = kp.privateKeyBytes,
                publicKey = kp.publicKeyBytes
            )
        }

        /**
         * Compute fingerprint: SHA-256(raw_32_byte_pubkey)[0:8]
         */
        fun computeFingerprint(publicKey: ByteArray): ByteArray {
            val digest = SHA256Digest()
            digest.update(publicKey, 0, publicKey.size)
            val hash = ByteArray(32)
            digest.doFinal(hash, 0)
            return hash.copyOfRange(0, 8)
        }

        /**
         * Compute fingerprint as hex string.
         */
        fun fingerprintHex(publicKey: ByteArray): String {
            return computeFingerprint(publicKey).joinToString("") { String.format("%02x", it) }
        }

        /**
         * Validate a freeflow:// contact URI.
         * Verifies fp == SHA-256(decode_hex(pubkey))[0:8]
         */
        fun validateContactUri(pubkeyHex: String, fpHex: String): Boolean {
            val pubBytes = hexToBytes(pubkeyHex) ?: return false
            if (pubBytes.size != 32) return false
            val computedFp = fingerprintHex(pubBytes)
            return computedFp == fpHex.lowercase()
        }

        fun hexToBytes(hex: String): ByteArray? {
            if (hex.length % 2 != 0) return null
            return try {
                ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Identity) return false
        return name == other.name && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        return 31 * name.hashCode() + publicKey.contentHashCode()
    }
}

/**
 * Contact: another user's public identity.
 */
data class Contact(
    val name: String,
    val publicKey: ByteArray,
    val fingerprintHex: String
) {
    companion object {
        fun fromPublicKey(name: String, publicKey: ByteArray): Contact {
            return Contact(
                name = name,
                publicKey = publicKey,
                fingerprintHex = Identity.fingerprintHex(publicKey)
            )
        }

        fun fromHex(name: String, pubkeyHex: String): Contact? {
            val bytes = Identity.hexToBytes(pubkeyHex) ?: return null
            if (bytes.size != 32) return null
            return fromPublicKey(name, bytes)
        }
    }

    val publicKeyHex: String
        get() = publicKey.joinToString("") { String.format("%02x", it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return name == other.name && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        return 31 * name.hashCode() + publicKey.contentHashCode()
    }
}
