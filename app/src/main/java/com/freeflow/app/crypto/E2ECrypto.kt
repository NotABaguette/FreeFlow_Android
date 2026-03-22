package com.freeflow.app.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import java.security.SecureRandom

/**
 * End-to-end encryption (PROTOCOL.md Section 6)
 *
 * Key derivation:
 *   shared_secret = X25519(my_priv, their_pub)
 *   e2e_key = HKDF-SHA256(shared_secret, salt=nil, info="freeflow-e2e-v1", len=32)
 *
 * Encryption:
 *   ChaCha20-Poly1305, nonce=12B random, output = nonce(12) || ciphertext || tag(16)
 *
 * Overhead: 12 (nonce) + 16 (tag) = 28 bytes per message
 */
object E2ECrypto {

    private const val E2E_INFO = "freeflow-e2e-v1"

    /**
     * Derive E2E encryption key from identity keys.
     *
     * @param myPrivateKey My 32-byte X25519 private key
     * @param theirPublicKey Their 32-byte X25519 public key
     * @return 32-byte E2E key
     */
    fun deriveKey(myPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray {
        val sharedSecret = KeyPair.sharedSecret(myPrivateKey, theirPublicKey)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, ByteArray(0), E2E_INFO.toByteArray(Charsets.US_ASCII)))
        val e2eKey = ByteArray(32)
        hkdf.generateBytes(e2eKey, 0, 32)
        return e2eKey
    }

    /**
     * Encrypt plaintext with ChaCha20-Poly1305.
     * Output: nonce(12) || ciphertext || tag(16)
     *
     * @param key 32-byte E2E key
     * @param plaintext Message bytes
     * @return Encrypted blob
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)

        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, null))

        val ciphertextWithTag = ByteArray(cipher.getOutputSize(plaintext.size))
        var off = cipher.processBytes(plaintext, 0, plaintext.size, ciphertextWithTag, 0)
        off += cipher.doFinal(ciphertextWithTag, off)

        // nonce(12) || ciphertext || tag(16)
        return nonce + ciphertextWithTag.copyOfRange(0, off)
    }

    /**
     * Decrypt E2E encrypted blob.
     * Input: nonce(12) || ciphertext || tag(16)
     *
     * @param key 32-byte E2E key
     * @param blob Encrypted blob (min 28 bytes)
     * @return Decrypted plaintext
     * @throws Exception if decryption fails (wrong key, corrupted data, unknown sender)
     */
    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size >= 28) { "E2E blob too short: ${blob.size} bytes (min 28)" }

        val nonce = blob.copyOfRange(0, 12)
        val ctWithTag = blob.copyOfRange(12, blob.size)

        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, null))

        val plaintext = ByteArray(cipher.getOutputSize(ctWithTag.size))
        var off = cipher.processBytes(ctWithTag, 0, ctWithTag.size, plaintext, 0)
        off += cipher.doFinal(plaintext, off)

        return plaintext.copyOfRange(0, off)
    }
}
