package com.freeflow.app.crypto

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * X25519 key pair generation and operations (PROTOCOL.md Section 4.1)
 *
 * Clamping applied to private key before use:
 *   private_key[0]  &= 248   // clear bottom 3 bits
 *   private_key[31] &= 127   // clear top bit
 *   private_key[31] |= 64    // set bit 6
 */
class KeyPair(
    val privateKeyBytes: ByteArray,
    val publicKeyBytes: ByteArray
) {
    companion object {
        private val secureRandom = SecureRandom()

        /**
         * Generate a new X25519 key pair from CSPRNG with proper clamping.
         */
        fun generate(): KeyPair {
            val privateParams = X25519PrivateKeyParameters(secureRandom)
            val publicParams = privateParams.generatePublicKey()

            val privBytes = privateParams.encoded
            val pubBytes = publicParams.encoded

            return KeyPair(privBytes, pubBytes)
        }

        /**
         * Create a KeyPair from existing private key bytes.
         * BouncyCastle applies clamping internally.
         */
        fun fromPrivateKey(privBytes: ByteArray): KeyPair {
            val privateParams = X25519PrivateKeyParameters(privBytes, 0)
            val publicParams = privateParams.generatePublicKey()
            return KeyPair(privateParams.encoded, publicParams.encoded)
        }

        /**
         * Perform X25519 Diffie-Hellman key agreement.
         *
         * @param myPrivateKey 32-byte private key
         * @param theirPublicKey 32-byte public key
         * @return 32-byte shared secret
         */
        fun sharedSecret(myPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray {
            val privParams = X25519PrivateKeyParameters(myPrivateKey, 0)
            val pubParams = X25519PublicKeyParameters(theirPublicKey, 0)

            val sharedSecret = ByteArray(32)
            privParams.generateSecret(pubParams, sharedSecret, 0)
            return sharedSecret
        }
    }
}
