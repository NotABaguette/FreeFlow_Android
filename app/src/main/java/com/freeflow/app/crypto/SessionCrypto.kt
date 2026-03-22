package com.freeflow.app.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import java.security.SecureRandom

/**
 * Session cryptography (PROTOCOL.md Sections 2.3, 5.4, 10)
 *
 * - Session key: HKDF-SHA256(shared_secret, salt=nil, info="freeflow-v2-session", len=32)
 * - Token: HMAC-SHA256(session_key, uint32be(seqNo))[0:4]
 * - Hello mask: HMAC-SHA256(session_key, "freeflow-hello-complete")[0:8]
 * - Encryption: ChaCha20-Poly1305
 */
object SessionCrypto {

    private const val SESSION_INFO = "freeflow-v2-session"
    private const val HELLO_COMPLETE_MSG = "freeflow-hello-complete"

    /**
     * Derive session key from X25519 shared secret using HKDF-SHA256.
     *
     * @param sharedSecret 32-byte X25519 shared secret
     * @return 32-byte session key
     */
    fun deriveSessionKey(sharedSecret: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, ByteArray(0), SESSION_INFO.toByteArray(Charsets.US_ASCII)))
        val sessionKey = ByteArray(32)
        hkdf.generateBytes(sessionKey, 0, 32)
        return sessionKey
    }

    /**
     * Compute session token for a given sequence number.
     * token = HMAC-SHA256(session_key, uint32be(seqNo))[0:4]
     *
     * @param sessionKey 32-byte session key
     * @param seqNo Sequence number
     * @return 4-byte token
     */
    fun computeToken(sessionKey: ByteArray, seqNo: Int): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(sessionKey))

        val msg = ByteArray(4)
        msg[0] = ((seqNo shr 24) and 0xFF).toByte()
        msg[1] = ((seqNo shr 16) and 0xFF).toByte()
        msg[2] = ((seqNo shr 8) and 0xFF).toByte()
        msg[3] = (seqNo and 0xFF).toByte()

        hmac.update(msg, 0, msg.size)
        val output = ByteArray(32)
        hmac.doFinal(output, 0)

        return output.copyOfRange(0, 4)
    }

    /**
     * Compute HELLO completion mask.
     * mask = HMAC-SHA256(session_key, "freeflow-hello-complete")[0:8]
     *
     * @param sessionKey 32-byte session key
     * @return 8-byte mask
     */
    fun computeHelloMask(sessionKey: ByteArray): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(sessionKey))

        val msg = HELLO_COMPLETE_MSG.toByteArray(Charsets.US_ASCII)
        hmac.update(msg, 0, msg.size)
        val output = ByteArray(32)
        hmac.doFinal(output, 0)

        return output.copyOfRange(0, 8)
    }

    /**
     * Decode the HELLO_COMPLETE response from Oracle.
     * Response = session_id XOR mask.
     *
     * @param response 8-byte masked response from Oracle
     * @param sessionKey 32-byte session key
     * @return 8-byte session ID
     */
    fun decodeHelloComplete(response: ByteArray, sessionKey: ByteArray): ByteArray {
        val mask = computeHelloMask(sessionKey)
        val sessionId = ByteArray(8)
        for (i in 0 until minOf(8, response.size)) {
            sessionId[i] = (response[i].toInt() xor mask[i].toInt()).toByte()
        }
        return sessionId
    }

    /**
     * Encrypt data with ChaCha20-Poly1305.
     * Output: nonce(12) || ciphertext || tag(16)
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)

        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, null))

        val ciphertextWithTag = ByteArray(cipher.getOutputSize(plaintext.size))
        var off = cipher.processBytes(plaintext, 0, plaintext.size, ciphertextWithTag, 0)
        off += cipher.doFinal(ciphertextWithTag, off)

        return nonce + ciphertextWithTag.copyOfRange(0, off)
    }

    /**
     * Decrypt data with ChaCha20-Poly1305.
     * Input: nonce(12) || ciphertext || tag(16)
     */
    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size >= 28) { "Encrypted blob too short: ${blob.size} bytes (min 28)" }

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
