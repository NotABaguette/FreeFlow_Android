package com.freeflow.app.protocol

/**
 * FreeFlow frame builder/parser (PROTOCOL.md Section 2)
 *
 * Full frame format (8-byte header):
 *   [cmd:1][seqNo:1][fragIdx:1][fragTotal:1][token:4][data:N]
 *
 * Minimum valid frame: 8 bytes (data may be empty).
 */
object Frame {
    const val HEADER_SIZE = 8

    /**
     * Build a full frame with 8-byte header.
     */
    fun build(
        command: Byte,
        seqNo: Int = 0,
        fragIndex: Int = 0,
        fragTotal: Int = 1,
        token: ByteArray = ByteArray(4),
        data: ByteArray = ByteArray(0)
    ): ByteArray {
        val frame = ByteArray(HEADER_SIZE + data.size)
        frame[0] = command
        frame[1] = (seqNo and 0xFF).toByte()
        frame[2] = (fragIndex and 0xFF).toByte()
        frame[3] = (fragTotal and 0xFF).toByte()
        token.copyInto(frame, 4, 0, minOf(token.size, 4))
        data.copyInto(frame, HEADER_SIZE)
        return frame
    }

    /**
     * Parse a full frame from raw bytes.
     * Returns null if data is too short.
     */
    fun parse(raw: ByteArray): ParsedFrame? {
        if (raw.size < HEADER_SIZE) return null
        return ParsedFrame(
            command = raw[0],
            seqNo = raw[1].toInt() and 0xFF,
            fragIndex = raw[2].toInt() and 0xFF,
            fragTotal = raw[3].toInt() and 0xFF,
            token = raw.copyOfRange(4, 8),
            data = if (raw.size > HEADER_SIZE) raw.copyOfRange(HEADER_SIZE, raw.size) else ByteArray(0)
        )
    }

    /**
     * Build a HELLO chunk frame (exactly 20 bytes).
     * Data: [chunkIdx:1][totalChunks:1][nonce_hi:1][nonce_lo:1][pubkey_chunk:8]
     */
    fun buildHelloChunk(
        chunkIndex: Int,
        helloNonce: Int,
        pubkeyChunk: ByteArray
    ): ByteArray {
        val data = ByteArray(12)
        data[0] = chunkIndex.toByte()
        data[1] = 4 // total chunks always 4
        data[2] = ((helloNonce shr 8) and 0xFF).toByte()
        data[3] = (helloNonce and 0xFF).toByte()
        pubkeyChunk.copyInto(data, 4, 0, minOf(pubkeyChunk.size, 8))

        return build(
            command = Commands.HELLO,
            seqNo = chunkIndex,
            fragIndex = chunkIndex,
            fragTotal = 4,
            token = ByteArray(4), // no session token for HELLO
            data = data
        )
    }

    /**
     * Ensure a frame has even byte length for proquint encoding.
     * Pads with 0x00 if odd.
     */
    fun ensureEvenLength(frame: ByteArray): ByteArray {
        return if (frame.size % 2 != 0) {
            frame + byteArrayOf(0)
        } else {
            frame
        }
    }
}

data class ParsedFrame(
    val command: Byte,
    val seqNo: Int,
    val fragIndex: Int,
    val fragTotal: Int,
    val token: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedFrame) return false
        return command == other.command && seqNo == other.seqNo &&
                fragIndex == other.fragIndex && fragTotal == other.fragTotal &&
                token.contentEquals(other.token) && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = command.hashCode()
        result = 31 * result + seqNo
        result = 31 * result + fragIndex
        result = 31 * result + fragTotal
        result = 31 * result + token.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
