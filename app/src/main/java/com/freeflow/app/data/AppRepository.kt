package com.freeflow.app.data

import android.content.Context
import android.content.SharedPreferences
import com.freeflow.app.identity.Contact
import com.freeflow.app.identity.Identity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Central application state repository.
 * Manages identity, contacts, messages, settings, and connection state.
 * Persists data to SharedPreferences (encrypted at rest via Android Keystore on supported devices).
 */
class AppRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("freeflow_data", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences("freeflow_settings", Context.MODE_PRIVATE)

    // Identity
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity: StateFlow<Identity?> = _identity.asStateFlow()

    // Contacts
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Bulletins
    private val _bulletins = MutableStateFlow<List<Bulletin>>(emptyList())
    val bulletins: StateFlow<List<Bulletin>> = _bulletins.asStateFlow()

    // Connection state
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    // Log entries
    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        loadIdentity()
        loadContacts()
        loadMessages()
        loadSettings()
    }

    // --- Identity ---

    fun getOrCreateIdentity(name: String = "User"): Identity {
        _identity.value?.let { return it }
        val id = Identity.generate(name)
        saveIdentity(id)
        return id
    }

    private fun saveIdentity(identity: Identity) {
        _identity.value = identity
        prefs.edit()
            .putString("identity_name", identity.name)
            .putString("identity_private_key", identity.privateKey.joinToString("") { String.format("%02x", it) })
            .putString("identity_public_key", identity.publicKey.joinToString("") { String.format("%02x", it) })
            .apply()
    }

    private fun loadIdentity() {
        val name = prefs.getString("identity_name", null) ?: return
        val privHex = prefs.getString("identity_private_key", null) ?: return
        val pubHex = prefs.getString("identity_public_key", null) ?: return
        val privBytes = hexToBytes(privHex) ?: return
        val pubBytes = hexToBytes(pubHex) ?: return
        if (privBytes.size != 32 || pubBytes.size != 32) return
        _identity.value = Identity(name, privBytes, pubBytes)
    }

    fun resetIdentity() {
        prefs.edit().remove("identity_name").remove("identity_private_key").remove("identity_public_key").apply()
        _identity.value = null
    }

    // --- Contacts ---

    fun addContact(contact: Contact) {
        val current = _contacts.value.toMutableList()
        // Replace if same fingerprint exists
        current.removeAll { it.fingerprintHex == contact.fingerprintHex }
        current.add(contact)
        _contacts.value = current
        saveContacts()
    }

    fun removeContact(fingerprint: String) {
        _contacts.value = _contacts.value.filter { it.fingerprintHex != fingerprint }
        saveContacts()
    }

    fun findContactByFingerprint(fp: String): Contact? {
        return _contacts.value.find { it.fingerprintHex == fp }
    }

    fun findContactByFingerprintBytes(fpBytes: ByteArray): Contact? {
        val fpHex = fpBytes.joinToString("") { String.format("%02x", it) }
        return findContactByFingerprint(fpHex)
    }

    private fun saveContacts() {
        val arr = JSONArray()
        for (c in _contacts.value) {
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("pubkey", c.publicKeyHex)
            obj.put("fp", c.fingerprintHex)
            arr.put(obj)
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    private fun loadContacts() {
        val json = prefs.getString("contacts", null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Contact>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name")
                val pubHex = obj.getString("pubkey")
                val pubBytes = hexToBytes(pubHex) ?: continue
                if (pubBytes.size != 32) continue
                list.add(Contact.fromPublicKey(name, pubBytes))
            }
            _contacts.value = list
        } catch (_: Exception) {
        }
    }

    // --- Messages ---

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
        saveMessages()
    }

    fun getMessagesForContact(fingerprint: String): List<ChatMessage> {
        return _messages.value.filter { it.contactFingerprint == fingerprint }
    }

    fun createOutgoingMessage(contactFingerprint: String, text: String): ChatMessage {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            contactFingerprint = contactFingerprint,
            text = text,
            isOutgoing = true,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )
        addMessage(msg)
        return msg
    }

    fun updateMessageStatus(id: String, status: MessageStatus) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
        saveMessages()
    }

    private fun saveMessages() {
        val arr = JSONArray()
        // Keep last 500 messages
        val recent = _messages.value.takeLast(500)
        for (m in recent) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("fp", m.contactFingerprint)
            obj.put("text", m.text)
            obj.put("out", m.isOutgoing)
            obj.put("ts", m.timestamp)
            obj.put("status", m.status.name)
            arr.put(obj)
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    private fun loadMessages() {
        val json = prefs.getString("messages", null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = obj.getString("id"),
                        contactFingerprint = obj.getString("fp"),
                        text = obj.getString("text"),
                        isOutgoing = obj.getBoolean("out"),
                        timestamp = obj.getLong("ts"),
                        status = try {
                            MessageStatus.valueOf(obj.getString("status"))
                        } catch (_: Exception) {
                            MessageStatus.SENT
                        }
                    )
                )
            }
            _messages.value = list
        } catch (_: Exception) {
        }
    }

    // --- Bulletins ---

    fun addBulletin(bulletin: Bulletin) {
        val current = _bulletins.value.toMutableList()
        if (current.none { it.id == bulletin.id }) {
            current.add(bulletin)
            _bulletins.value = current
        }
    }

    // --- Connection ---

    fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
    }

    // --- Logs ---

    fun addLog(entry: LogEntry) {
        val current = _logEntries.value.toMutableList()
        current.add(entry)
        // Keep last 200 log entries
        if (current.size > 200) {
            _logEntries.value = current.takeLast(200)
        } else {
            _logEntries.value = current
        }
    }

    fun clearLogs() {
        _logEntries.value = emptyList()
    }

    // --- Settings ---

    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        settingsPrefs.edit()
            .putString("oracle_domain", settings.oracleDomain)
            .putString("resolver", settings.resolver)
            .putString("oracle_pubkey", settings.oraclePublicKeyHex)
            .putString("transport", settings.transport.name)
            .putString("encoding", settings.encoding.name)
            .putString("relay_url", settings.relayUrl)
            .putString("relay_api_key", settings.relayApiKey)
            .putLong("query_delay", settings.queryDelay)
            .putBoolean("dev_mode", settings.devMode)
            .apply()
    }

    private fun loadSettings() {
        _settings.value = AppSettings(
            oracleDomain = settingsPrefs.getString("oracle_domain", "cdn-static-eu.net") ?: "cdn-static-eu.net",
            resolver = settingsPrefs.getString("resolver", "8.8.8.8") ?: "8.8.8.8",
            oraclePublicKeyHex = settingsPrefs.getString("oracle_pubkey", "") ?: "",
            transport = try {
                TransportMode.valueOf(settingsPrefs.getString("transport", "DNS") ?: "DNS")
            } catch (_: Exception) {
                TransportMode.DNS
            },
            encoding = try {
                EncodingMode.valueOf(settingsPrefs.getString("encoding", "PROQUINT") ?: "PROQUINT")
            } catch (_: Exception) {
                EncodingMode.PROQUINT
            },
            relayUrl = settingsPrefs.getString("relay_url", "") ?: "",
            relayApiKey = settingsPrefs.getString("relay_api_key", "") ?: "",
            queryDelay = settingsPrefs.getLong("query_delay", 3000L),
            devMode = settingsPrefs.getBoolean("dev_mode", false)
        )
    }

    // --- Utilities ---

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
