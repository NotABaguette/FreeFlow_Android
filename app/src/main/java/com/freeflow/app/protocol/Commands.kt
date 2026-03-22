package com.freeflow.app.protocol

/**
 * FreeFlow protocol command codes (PROTOCOL.md Section 3.1)
 */
object Commands {
    const val HELLO: Byte = 0x01
    const val GET_BULLETIN: Byte = 0x02
    const val SEND_MSG: Byte = 0x03
    const val GET_MSG: Byte = 0x04
    const val ACK: Byte = 0x05
    const val DISCOVER: Byte = 0x06
    const val PING: Byte = 0x07
    const val REGISTER: Byte = 0x08
    const val ERR: Byte = 0xFF.toByte()
}

/**
 * GET_MSG sub-commands (PROTOCOL.md Section 6.4)
 */
object GetMsgSubCommand {
    const val CHECK: Byte = 0x00
    const val FETCH: Byte = 0x01
    const val ACK: Byte = 0x02
}

/**
 * Oracle error codes (PROTOCOL.md Section 3.2)
 */
object ErrorCodes {
    const val UNKNOWN: Byte = 0x00
    const val NO_SESSION: Byte = 0x01
    const val INVALID_TOKEN: Byte = 0x02
    const val RATE_LIMIT: Byte = 0x03
    const val MALFORMED: Byte = 0x04
    const val NO_BULLETIN: Byte = 0x05
    const val NO_MESSAGE: Byte = 0x06
    const val HELLO_TIMEOUT: Byte = 0x07
    const val HELLO_CONFLICT: Byte = 0x08

    fun name(code: Byte): String = when (code) {
        UNKNOWN -> "Unknown"
        NO_SESSION -> "NoSession"
        INVALID_TOKEN -> "InvalidToken"
        RATE_LIMIT -> "RateLimit"
        MALFORMED -> "Malformed"
        NO_BULLETIN -> "NoBulletin"
        NO_MESSAGE -> "NoMessage"
        HELLO_TIMEOUT -> "HelloTimeout"
        HELLO_CONFLICT -> "HelloConflict"
        else -> "0x${String.format("%02x", code)}"
    }
}
