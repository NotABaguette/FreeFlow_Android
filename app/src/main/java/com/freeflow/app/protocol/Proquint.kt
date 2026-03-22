package com.freeflow.app.protocol

/**
 * Proquint encoding/decoding (PROTOCOL.md Section 1.2.1)
 *
 * Converts 16-bit values to 5-character CVCVC pronounceable words.
 * Words joined with hyphens. Max 10 words per DNS label (20 bytes).
 */
object Proquint {

    /** Max bytes per single DNS label (10 words x 2 bytes) */
    const val MAX_BYTES_PER_LABEL = 20

    private val CONSONANTS = charArrayOf(
        'b', 'd', 'f', 'g', 'h', 'j', 'k', 'l',
        'm', 'n', 'p', 'r', 's', 't', 'v', 'z'
    )

    private val VOWELS = charArrayOf('a', 'i', 'o', 'u')

    // Reverse lookup tables
    private val CONSONANT_MAP: Map<Char, Int> = CONSONANTS.withIndex().associate { (i, c) -> c to i }
    private val VOWEL_MAP: Map<Char, Int> = VOWELS.withIndex().associate { (i, c) -> c to i }

    /**
     * Encode bytes as a proquint string (hyphen-separated words).
     * Input MUST have even length. If odd, caller must pad with 0x00.
     */
    fun encode(data: ByteArray): String {
        require(data.size % 2 == 0) { "Proquint encode requires even-length input, got ${data.size}" }

        val words = mutableListOf<String>()
        for (i in data.indices step 2) {
            val value = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            words.add(encodeWord(value))
        }
        return words.joinToString("-")
    }

    /**
     * Decode a proquint string back to bytes.
     */
    fun decode(proquint: String): ByteArray {
        val words = proquint.split("-").filter { it.isNotEmpty() }
        val result = ByteArray(words.size * 2)
        for ((idx, word) in words.withIndex()) {
            val value = decodeWord(word)
            result[idx * 2] = ((value shr 8) and 0xFF).toByte()
            result[idx * 2 + 1] = (value and 0xFF).toByte()
        }
        return result
    }

    /**
     * Encode a 16-bit value to a 5-character CVCVC word.
     */
    private fun encodeWord(value: Int): String {
        val c0 = CONSONANTS[(value shr 12) and 0x0F]
        val v0 = VOWELS[(value shr 10) and 0x03]
        val c1 = CONSONANTS[(value shr 6) and 0x0F]
        val v1 = VOWELS[(value shr 4) and 0x03]
        val c2 = CONSONANTS[value and 0x0F]
        return "$c0$v0$c1$v1$c2"
    }

    /**
     * Decode a 5-character CVCVC word to a 16-bit value.
     */
    private fun decodeWord(word: String): Int {
        require(word.length == 5) { "Proquint word must be 5 characters, got '${word}'" }
        val c0 = CONSONANT_MAP[word[0]] ?: error("Invalid consonant: ${word[0]}")
        val v0 = VOWEL_MAP[word[1]] ?: error("Invalid vowel: ${word[1]}")
        val c1 = CONSONANT_MAP[word[2]] ?: error("Invalid consonant: ${word[2]}")
        val v1 = VOWEL_MAP[word[3]] ?: error("Invalid vowel: ${word[3]}")
        val c2 = CONSONANT_MAP[word[4]] ?: error("Invalid consonant: ${word[4]}")

        return (c0 shl 12) or (v0 shl 10) or (c1 shl 6) or (v1 shl 4) or c2
    }
}
