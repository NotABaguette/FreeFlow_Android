package com.freeflow.app.data

/**
 * Data models for the FreeFlow Android client.
 */

data class ChatMessage(
    val id: String,
    val contactFingerprint: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

data class Bulletin(
    val id: Int,
    val content: String,
    val timestamp: Long,
    val verified: Boolean = false
)

data class LogEntry(
    val timestamp: Long,
    val direction: String,
    val message: String,
    val transport: String
) {
    companion object {
        fun query(message: String, transport: String) = LogEntry(
            timestamp = System.currentTimeMillis(),
            direction = ">>>",
            message = message,
            transport = transport
        )

        fun response(message: String, transport: String) = LogEntry(
            timestamp = System.currentTimeMillis(),
            direction = "<<<",
            message = message,
            transport = transport
        )
    }
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class TransportMode {
    DNS,
    HTTP
}

enum class EncodingMode {
    PROQUINT,
    HEX
}

data class AppSettings(
    val oracleDomain: String = "cdn-static-eu.net",
    val resolver: String = "8.8.8.8",
    val oraclePublicKeyHex: String = "",
    val transport: TransportMode = TransportMode.DNS,
    val encoding: EncodingMode = EncodingMode.PROQUINT,
    val relayUrl: String = "",
    val relayApiKey: String = "",
    val queryDelay: Long = 3000L,
    val devMode: Boolean = false
)
