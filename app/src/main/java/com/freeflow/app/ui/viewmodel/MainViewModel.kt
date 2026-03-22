package com.freeflow.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freeflow.app.FreeFlowApp
import com.freeflow.app.client.FreeFlowConnection
import com.freeflow.app.client.FreeFlowException
import com.freeflow.app.data.*
import com.freeflow.app.identity.Contact
import com.freeflow.app.identity.Identity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: AppRepository = (application as FreeFlowApp).repository

    private var connection: FreeFlowConnection? = null

    // Expose repository flows
    val identity = repository.identity
    val contacts = repository.contacts
    val messages = repository.messages
    val bulletins = repository.bulletins
    val connectionStatus = repository.connectionStatus
    val logEntries = repository.logEntries
    val settings = repository.settings

    // Selected chat contact
    private val _selectedContact = MutableStateFlow<Contact?>(null)
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()

    // Status messages for UI feedback
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Ensure identity exists
        repository.getOrCreateIdentity("Android User")
    }

    fun selectContact(contact: Contact?) {
        _selectedContact.value = contact
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // --- Connection Operations ---

    private fun getOrCreateConnection(): FreeFlowConnection {
        val id = repository.identity.value ?: repository.getOrCreateIdentity()
        val s = repository.settings.value

        val oraclePubKey = if (s.oraclePublicKeyHex.length == 64) {
            hexToBytes(s.oraclePublicKeyHex) ?: ByteArray(32)
        } else {
            ByteArray(32)
        }

        val conn = connection ?: FreeFlowConnection(
            identity = id,
            oraclePublicKey = oraclePubKey,
            domain = s.oracleDomain,
            resolver = s.resolver,
            transport = s.transport,
            encoding = s.encoding,
            relayUrl = s.relayUrl,
            relayApiKey = s.relayApiKey,
            queryDelay = s.queryDelay
        )

        conn.domain = s.oracleDomain
        conn.resolver = s.resolver
        conn.transport = s.transport
        conn.encoding = s.encoding
        conn.relayUrl = s.relayUrl
        conn.relayApiKey = s.relayApiKey
        conn.queryDelay = s.queryDelay

        if (s.oraclePublicKeyHex.length == 64) {
            hexToBytes(s.oraclePublicKeyHex)?.let { conn.oraclePublicKey = it }
        }

        conn.onLog = { query, response, transport ->
            repository.addLog(LogEntry(
                timestamp = System.currentTimeMillis(),
                direction = if (query.contains("sending")) ">>>" else "<<<",
                message = "$query | $response",
                transport = transport
            ))
        }

        connection = conn
        return conn
    }

    fun ping() {
        viewModelScope.launch {
            try {
                repository.addLog(LogEntry.query("PING", "DNS"))
                val conn = getOrCreateConnection()
                val serverTime = conn.ping()
                val msg = "PONG: server time = $serverTime"
                _statusMessage.value = msg
                repository.addLog(LogEntry.response(msg, "DNS"))
            } catch (e: Exception) {
                val msg = "PING failed: ${e.message}"
                _statusMessage.value = msg
                repository.addLog(LogEntry.response(msg, "DNS"))
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            try {
                repository.setConnectionStatus(ConnectionStatus.CONNECTING)
                _statusMessage.value = "Connecting..."
                val conn = getOrCreateConnection()
                conn.connect()
                repository.setConnectionStatus(ConnectionStatus.CONNECTED)
                _statusMessage.value = "Connected and registered!"
            } catch (e: Exception) {
                repository.setConnectionStatus(ConnectionStatus.DISCONNECTED)
                _statusMessage.value = "Connection failed: ${e.message}"
            }
        }
    }

    fun disconnect() {
        connection?.disconnect()
        connection = null
        repository.setConnectionStatus(ConnectionStatus.DISCONNECTED)
        _statusMessage.value = "Disconnected"
    }

    fun sendMessage(contactFp: String, text: String) {
        viewModelScope.launch {
            try {
                val contact = repository.findContactByFingerprint(contactFp)
                    ?: throw FreeFlowException("Contact not found")
                val conn = getOrCreateConnection()

                val msg = repository.createOutgoingMessage(contactFp, text)
                val fragments = conn.sendMessage(text, contact)
                repository.updateMessageStatus(msg.id, MessageStatus.SENT)
                _statusMessage.value = "Sent ($fragments fragments)"
            } catch (e: Exception) {
                _statusMessage.value = "Send failed: ${e.message}"
            }
        }
    }

    fun pollMessages() {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Checking inbox..."
                val conn = getOrCreateConnection()
                val result = conn.pollMessages()

                if (result == null) {
                    _statusMessage.value = "No new messages"
                    return@launch
                }

                val (ciphertext, senderFP) = result
                val senderFpHex = senderFP.joinToString("") { String.format("%02x", it) }
                val contact = repository.findContactByFingerprint(senderFpHex)

                if (contact == null) {
                    _statusMessage.value = "Message from unknown sender: $senderFpHex"
                    return@launch
                }

                val plaintext = conn.decryptMessage(ciphertext, contact.publicKey)
                repository.addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        contactFingerprint = senderFpHex,
                        text = plaintext,
                        isOutgoing = false,
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.DELIVERED
                    )
                )
                _statusMessage.value = "New message from ${contact.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Poll failed: ${e.message}"
            }
        }
    }

    fun fetchBulletin() {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Fetching bulletin..."
                val conn = getOrCreateConnection()
                val data = conn.getBulletin()

                if (data.size >= 2) {
                    val bulletinId = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val content = if (data.size > 2) {
                        String(data, 2, data.size - 2, Charsets.UTF_8)
                    } else {
                        "(no content)"
                    }
                    repository.addBulletin(
                        Bulletin(
                            id = bulletinId,
                            content = content,
                            timestamp = System.currentTimeMillis(),
                            verified = true
                        )
                    )
                    _statusMessage.value = "Bulletin #$bulletinId received"
                } else {
                    _statusMessage.value = "No bulletin available"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Bulletin failed: ${e.message}"
            }
        }
    }

    fun addContact(name: String, pubkeyHex: String) {
        val contact = Contact.fromHex(name, pubkeyHex)
        if (contact != null) {
            repository.addContact(contact)
            _statusMessage.value = "Contact added: ${contact.fingerprintHex}"
        } else {
            _statusMessage.value = "Invalid public key"
        }
    }

    fun removeContact(fingerprint: String) {
        repository.removeContact(fingerprint)
        _statusMessage.value = "Contact removed"
    }

    fun updateSettings(newSettings: AppSettings) {
        repository.updateSettings(newSettings)
        // Force recreation of connection with new settings
        connection = null
        _statusMessage.value = "Settings saved"
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun regenerateIdentity() {
        repository.resetIdentity()
        repository.getOrCreateIdentity("Android User")
        connection = null
        _statusMessage.value = "Identity regenerated"
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
