package com.freeflow.app.client

import com.freeflow.app.crypto.E2ECrypto
import com.freeflow.app.crypto.KeyPair
import com.freeflow.app.crypto.SessionCrypto
import com.freeflow.app.data.EncodingMode
import com.freeflow.app.data.TransportMode
import com.freeflow.app.identity.Contact
import com.freeflow.app.identity.Identity
import com.freeflow.app.protocol.AAAADecoder
import com.freeflow.app.protocol.Commands
import com.freeflow.app.protocol.ErrorCodes
import com.freeflow.app.protocol.Frame
import com.freeflow.app.protocol.Proquint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom

/**
 * Full FreeFlow connection matching PROTOCOL.md exactly.
 *
 * Implements: ping, connect (4-query HELLO), register (single 40B frame, 3 retries, verify fp),
 * sendMessage (FP per fragment, 4B ct for proquint), pollMessages (CHECK/FETCH/ACK),
 * queryViaDNS (proquint/hex encoding, q-nonce subdomain), queryViaHTTP (relay),
 * DNS packet build/parse, checkErrorResponse (0xFF), even byte frames, registered guard.
 */
class FreeFlowConnection(
    val identity: Identity,
    var oraclePublicKey: ByteArray,
    var domain: String = "cdn-static-eu.net",
    var resolver: String = "8.8.8.8",
    var transport: TransportMode = TransportMode.DNS,
    var encoding: EncodingMode = EncodingMode.PROQUINT,
    var relayUrl: String = "",
    var relayApiKey: String = "",
    var queryDelay: Long = 3000L,
    resolvers: List<String> = emptyList(),
    loadBalanceStrength: Int = 5
) {
    val resolverPool: ResolverPool = ResolverPool(resolvers, loadBalanceStrength)
    // Session state
    private var sessionKey: ByteArray? = null
    private var sessionId: ByteArray? = null
    private var seqNo: Int = 0
    var registered: Boolean = false
        private set

    // Callback for logging
    var onLog: ((query: String, response: String, transport: String) -> Unit)? = null

    private val secureRandom = SecureRandom()

    private val transportLabel: String
        get() = if (transport == TransportMode.HTTP) "HTTP" else "DNS(${encoding.name.lowercase()})"

    /**
     * Get next sequence number (wraps at 256 for the byte field,
     * but the token HMAC uses the full int for forward window matching).
     */
    private fun nextSeqNo(): Int {
        seqNo++
        return seqNo
    }

    /**
     * Compute session token for a given sequence number.
     */
    private fun tokenFor(seq: Int): ByteArray {
        val key = sessionKey ?: throw FreeFlowException("No session key")
        return SessionCrypto.computeToken(key, seq)
    }

    // ========================================================================
    // PING (Section 7.1)
    // ========================================================================

    /**
     * Send PING command.
     * Response: 4 bytes big-endian uint32(unix_timestamp_utc), zero-padded to 8 bytes.
     *
     * @return Server time as Unix timestamp
     */
    suspend fun ping(): Long = withContext(Dispatchers.IO) {
        val frame = Frame.build(command = Commands.PING)
        onLog?.invoke("PING cmd=0x07", "sending...", transportLabel)

        val response = checkErrorResponse(queryOracle(frame))
        if (response.size < 4) throw FreeFlowException("PING response too short: ${response.size}")

        val serverTime = (response[0].toLong() and 0xFF shl 24) or
                (response[1].toLong() and 0xFF shl 16) or
                (response[2].toLong() and 0xFF shl 8) or
                (response[3].toLong() and 0xFF)

        onLog?.invoke("PING cmd=0x07", "PONG server_time=$serverTime", transportLabel)
        serverTime
    }

    // ========================================================================
    // HELLO — 4-query handshake (Section 5)
    // ========================================================================

    /**
     * Establish encrypted session via 4-query HELLO handshake.
     * Generates ephemeral X25519 keypair, sends pubkey in 4 chunks,
     * derives session key via ECDH + HKDF, recovers session ID.
     * Auto-REGISTERs identity after HELLO.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val ephemeral = KeyPair.generate()
        val pubBytes = ephemeral.publicKeyBytes
        val helloNonce = secureRandom.nextInt(0xFFFF + 1)

        for (i in 0 until 4) {
            val chunk = pubBytes.copyOfRange(i * 8, (i + 1) * 8)

            // Build HELLO chunk frame (exactly 20 bytes)
            val frame = Frame.buildHelloChunk(
                chunkIndex = i,
                helloNonce = helloNonce,
                pubkeyChunk = chunk
            )

            val chunkHex = chunk.joinToString("") { String.format("%02x", it) }
            onLog?.invoke(
                "HELLO chunk=$i/4 nonce=$helloNonce data=$chunkHex",
                "sending ${frame.size}B frame...", transportLabel
            )

            val response = checkErrorResponse(queryOracle(frame))

            if (i == 3) {
                // 4th chunk: derive session key and decode response
                val sharedSecret = KeyPair.sharedSecret(ephemeral.privateKeyBytes, oraclePublicKey)
                val sessKey = SessionCrypto.deriveSessionKey(sharedSecret)
                val sessId = SessionCrypto.decodeHelloComplete(response, sessKey)

                sessionKey = sessKey
                sessionId = sessId
                seqNo = 0 // Reset sequence counter

                val sidHex = sessId.joinToString("") { String.format("%02x", it) }
                onLog?.invoke("HELLO_COMPLETE", "session_id=$sidHex key_derived=32B", transportLabel)

                // Auto-REGISTER after HELLO (Section 5.5)
                delay(queryDelay)
                register()
            } else {
                onLog?.invoke("HELLO chunk=$i/4", "ACK chunk_idx=$i", transportLabel)
                delay(queryDelay)
            }
        }
    }

    /**
     * Disconnect and destroy session state.
     */
    fun disconnect() {
        sessionKey = null
        sessionId = null
        seqNo = 0
        registered = false
        resolverPool.stopHealthCheck()
        onLog?.invoke("DISCONNECT", "session destroyed", transportLabel)
    }

    // ========================================================================
    // REGISTER (Section 4.4)
    // ========================================================================

    /**
     * Register persistent identity with the session.
     * Sends single 40-byte frame (8 header + 32 pubkey), fragTotal=1.
     * The 40-byte frame uses 2 proquint labels (label-level splitting, not protocol fragmentation).
     * Retries up to 3 times. Verifies Oracle-returned fingerprint.
     * First session token uses sequence_number = 1.
     */
    suspend fun register() = withContext(Dispatchers.IO) {
        requireSession()

        val pubkey = identity.publicKey // 32 bytes
        var lastError: Exception? = null

        for (attempt in 1..3) {
            val seq = nextSeqNo()
            val token = tokenFor(seq)

            // Single frame, fragTotal=1 — matches reference client exactly
            // Proquint encoder handles multi-label splitting (40 bytes = 2 labels)
            val frame = Frame.build(
                command = Commands.REGISTER,
                seqNo = seq,
                fragIndex = 0,
                fragTotal = 1,
                token = token,
                data = pubkey
            )

            onLog?.invoke("REGISTER attempt=$attempt frame=${frame.size}B", "sending...", transportLabel)

            try {
                val response = checkErrorResponse(queryOracle(frame))
                verifyRegisterResponse(response)
                registered = true
                return@withContext
            } catch (e: Exception) {
                lastError = e
                onLog?.invoke("REGISTER attempt=$attempt", "failed: ${e.message}", transportLabel)
                if (attempt < 3) {
                    delay(queryDelay)
                }
            }
        }
        throw lastError ?: FreeFlowException("REGISTER failed after 3 attempts")
    }

    /**
     * Verify Oracle-returned fingerprint matches local computation.
     */
    private fun verifyRegisterResponse(response: ByteArray) {
        if (response.size >= 8) {
            val oracleFP = response.copyOfRange(0, 8).joinToString("") { String.format("%02x", it) }
            val localFP = identity.fingerprintHex
            if (oracleFP == localFP) {
                onLog?.invoke("REGISTER", "OK - fingerprint verified: $oracleFP", transportLabel)
            } else {
                onLog?.invoke(
                    "REGISTER",
                    "WARNING - fingerprint mismatch! Oracle=$oracleFP Local=$localFP",
                    transportLabel
                )
            }
        }
    }

    // ========================================================================
    // SEND MESSAGE (Section 6.3)
    // ========================================================================

    /**
     * Send E2E encrypted message to a contact.
     *
     * 1. Derive E2E key via ECDH + HKDF("freeflow-e2e-v1")
     * 2. Encrypt with ChaCha20-Poly1305: output = nonce(12) || ciphertext || tag(16)
     * 3. Fragment ciphertext: 4 bytes per fragment for proquint transport
     * 4. Each fragment: [recipient_fingerprint(8)][ciphertext_chunk] — FP in EVERY fragment
     * 5. Ensure even byte length for proquint
     *
     * @return Number of fragments sent
     */
    suspend fun sendMessage(text: String, to: Contact): Int = withContext(Dispatchers.IO) {
        requireSession()
        requireRegistered()

        val e2eKey = E2ECrypto.deriveKey(identity.privateKey, to.publicKey)
        val plaintext = text.toByteArray(Charsets.UTF_8)
        val ciphertext = E2ECrypto.encrypt(e2eKey, plaintext)

        val fpBytes = Identity.computeFingerprint(to.publicKey)

        // Fragment ciphertext: 4 bytes per fragment for proquint
        // (max frame=20B, header=8B, fp=8B -> 4B ciphertext)
        val maxCtPerFragment = if (encoding == EncodingMode.PROQUINT) 4 else 50
        val ctFragments = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < ciphertext.size) {
            val end = minOf(offset + maxCtPerFragment, ciphertext.size)
            ctFragments.add(ciphertext.copyOfRange(offset, end))
            offset = end
        }
        if (ctFragments.isEmpty()) ctFragments.add(ByteArray(0))

        for ((i, ctChunk) in ctFragments.withIndex()) {
            delay(queryDelay)
            val seq = nextSeqNo()
            val token = tokenFor(seq)

            // Data = [recipientFP(8)] + [ciphertext_chunk] for EVERY fragment
            var data = fpBytes + ctChunk

            // Ensure total frame (8 header + data) is even for proquint
            if ((Frame.HEADER_SIZE + data.size) % 2 != 0) {
                data = data + byteArrayOf(0)
            }

            val frame = Frame.build(
                command = Commands.SEND_MSG,
                seqNo = seq and 0xFF,
                fragIndex = i,
                fragTotal = ctFragments.size,
                token = token,
                data = data
            )

            val fpHex = fpBytes.joinToString("") { String.format("%02x", it) }
            onLog?.invoke(
                "SEND_MSG frag=${i + 1}/${ctFragments.size} to=${fpHex.take(8)} ct=${ctChunk.size}B",
                "sending ${frame.size}B frame...", transportLabel
            )
            val response = checkErrorResponse(queryOracle(frame))
            onLog?.invoke("SEND_MSG frag=${i + 1}/${ctFragments.size}", "ACK ${response.size}B", transportLabel)
        }
        ctFragments.size
    }

    // ========================================================================
    // GET MESSAGES — CHECK/FETCH/ACK (Section 6.4)
    // ========================================================================

    /**
     * Poll for incoming messages using the Oracle's sub-protocol:
     * Step 1: CHECK (0x00) - is there a pending message?
     * Step 2: FETCH (0x01) x N - download 8 bytes at a time
     * Step 3: ACK (0x02) - mark delivered
     *
     * @return Pair of (ciphertext, senderFingerprintBytes) or null if no messages
     */
    suspend fun pollMessages(): Pair<ByteArray, ByteArray>? = withContext(Dispatchers.IO) {
        requireSession()
        requireRegistered()

        // Step 1: CHECK
        delay(queryDelay)
        val seq1 = nextSeqNo()
        val token1 = tokenFor(seq1)

        // GET_MSG CHECK: data = [0x00]
        // Frame is 9 bytes (odd), pad to 10 for proquint
        var checkData = byteArrayOf(0x00)
        if ((Frame.HEADER_SIZE + checkData.size) % 2 != 0) {
            checkData = checkData + byteArrayOf(0)
        }

        val checkFrame = Frame.build(
            command = Commands.GET_MSG,
            seqNo = seq1 and 0xFF,
            token = token1,
            data = checkData
        )

        onLog?.invoke("GET_MSG CHECK", "sending...", transportLabel)
        val checkResp = checkErrorResponse(queryOracle(checkFrame))

        // Response: [0x00,...] = no messages, [0x01, senderFP(4), lenHi, lenLo, 0] = has message
        if (checkResp.isEmpty() || checkResp[0] != 0x01.toByte()) {
            onLog?.invoke("GET_MSG CHECK", "no pending messages", transportLabel)
            return@withContext null
        }

        val totalLen = if (checkResp.size >= 7) {
            ((checkResp[5].toInt() and 0xFF) shl 8) or (checkResp[6].toInt() and 0xFF)
        } else 0

        onLog?.invoke("GET_MSG CHECK", "message found, totalLen=${totalLen}B", transportLabel)

        // Step 2: FETCH chunks (8 bytes per response)
        val blob = mutableListOf<Byte>()
        val chunksNeeded = maxOf(1, (totalLen + 7) / 8)

        for (chunkIdx in 0 until chunksNeeded) {
            delay(queryDelay)
            val seqN = nextSeqNo()
            val tokenN = tokenFor(seqN)

            // FETCH: data = [0x01, chunkIdx]
            val fetchData = byteArrayOf(0x01, chunkIdx.toByte())

            val fetchFrame = Frame.build(
                command = Commands.GET_MSG,
                seqNo = seqN and 0xFF,
                token = tokenN,
                data = fetchData
            )

            onLog?.invoke("GET_MSG FETCH chunk=$chunkIdx", "sending...", transportLabel)
            val fetchResp = checkErrorResponse(queryOracle(fetchFrame))
            blob.addAll(fetchResp.toList())
            onLog?.invoke("GET_MSG FETCH chunk=$chunkIdx", "got ${fetchResp.size}B", transportLabel)
        }

        // Step 3: ACK
        delay(queryDelay)
        val seqAck = nextSeqNo()
        val tokenAck = tokenFor(seqAck)

        // ACK: data = [0x02]
        var ackData = byteArrayOf(0x02)
        if ((Frame.HEADER_SIZE + ackData.size) % 2 != 0) {
            ackData = ackData + byteArrayOf(0)
        }

        val ackFrame = Frame.build(
            command = Commands.GET_MSG,
            seqNo = seqAck and 0xFF,
            token = tokenAck,
            data = ackData
        )

        onLog?.invoke("GET_MSG ACK", "sending...", transportLabel)
        checkErrorResponse(queryOracle(ackFrame))
        onLog?.invoke("GET_MSG ACK", "delivered", transportLabel)

        // Trim blob to actual totalLen
        val blobArray = blob.toByteArray()
        val trimmed = if (totalLen > 0 && blobArray.size > totalLen) {
            blobArray.copyOfRange(0, totalLen)
        } else {
            blobArray
        }

        // Parse blob: [senderFP(8)][ciphertext...]
        if (trimmed.size <= 8) return@withContext null
        val senderFP = trimmed.copyOfRange(0, 8)
        val ciphertext = trimmed.copyOfRange(8, trimmed.size)
        Pair(ciphertext, senderFP)
    }

    /**
     * Decrypt received message data using sender's public key.
     */
    fun decryptMessage(ciphertext: ByteArray, senderPublicKey: ByteArray): String {
        val e2eKey = E2ECrypto.deriveKey(identity.privateKey, senderPublicKey)
        val plaintext = E2ECrypto.decrypt(e2eKey, ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    // ========================================================================
    // GET BULLETIN (Section 7.3)
    // ========================================================================

    /**
     * Fetch bulletin from Oracle.
     */
    suspend fun getBulletin(lastSeenId: Int = 0): ByteArray = withContext(Dispatchers.IO) {
        val data = byteArrayOf(
            ((lastSeenId shr 8) and 0xFF).toByte(),
            (lastSeenId and 0xFF).toByte()
        )
        val frame = Frame.build(command = Commands.GET_BULLETIN, data = data)

        onLog?.invoke("GET_BULLETIN lastID=$lastSeenId", "sending...", transportLabel)
        val response = checkErrorResponse(queryOracle(frame))
        onLog?.invoke("GET_BULLETIN", "response=${response.size}B", transportLabel)
        response
    }

    // ========================================================================
    // Transport Layer
    // ========================================================================

    /**
     * Route query through configured transport (DNS or HTTP).
     */
    private suspend fun queryOracle(payload: ByteArray): ByteArray {
        return if (transport == TransportMode.HTTP) {
            queryViaHTTP(payload)
        } else {
            queryViaDNS(payload)
        }
    }

    /**
     * DNS AAAA transport - encodes frame as DNS query per PROTOCOL.md.
     *
     * Proquint: CVCVC words, 20 bytes/label, evades entropy detectors.
     * Hex: hex-encoded labels, 31 bytes/label, for uncensored networks.
     * Every query includes a unique q-<random> subdomain for cache isolation (Section 1.3).
     */
    private suspend fun queryViaDNS(payload: ByteArray): ByteArray {
        // Generate per-query cache-busting nonce
        val nonceBytes = ByteArray(4)
        secureRandom.nextBytes(nonceBytes)
        val nonceHex = nonceBytes.joinToString("") { String.format("%02x", it) }
        val nonceLabel = "q-$nonceHex"

        val frameLabels: String = when (encoding) {
            EncodingMode.PROQUINT -> {
                // Ensure even byte length for proquint
                var frame = payload
                if (frame.size % 2 != 0) {
                    frame = frame + byteArrayOf(0)
                }
                // Single label if <= 20 bytes, multi-label if larger
                if (frame.size <= Proquint.MAX_BYTES_PER_LABEL) {
                    Proquint.encode(frame)
                } else {
                    // Split across multiple proquint labels
                    val labels = mutableListOf<String>()
                    var i = 0
                    while (i < frame.size) {
                        val end = minOf(i + Proquint.MAX_BYTES_PER_LABEL, frame.size)
                        var chunk = frame.copyOfRange(i, end)
                        if (chunk.size % 2 != 0) {
                            chunk = chunk + byteArrayOf(0)
                        }
                        labels.add(Proquint.encode(chunk))
                        i = end
                    }
                    labels.joinToString(".")
                }
            }

            EncodingMode.HEX -> {
                // Hex encoding: frame bytes as hex labels, max 62 hex chars (31 bytes) per label
                val hexStr = payload.joinToString("") { String.format("%02x", it) }
                val labels = mutableListOf<String>()
                var i = 0
                while (i < hexStr.length) {
                    val end = minOf(i + 62, hexStr.length)
                    labels.add(hexStr.substring(i, end))
                    i = end
                }
                labels.joinToString(".")
            }
        }

        // Assemble: <frame_labels>.q-<nonce>.<domain>
        val queryName = "$frameLabels.$nonceLabel.$domain"

        onLog?.invoke("DNS_QUERY [${encoding.name.lowercase()}]", queryName, transportLabel)
        val records = dnsQueryAAAA(queryName)
        val responsePayload = AAAADecoder.decode(records)
        return responsePayload
    }

    /**
     * HTTP Relay transport.
     */
    private suspend fun queryViaHTTP(payload: ByteArray): ByteArray {
        if (relayUrl.isEmpty()) throw FreeFlowException("Relay URL not configured")

        val url = URL("$relayUrl/api/query")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            if (relayApiKey.isNotEmpty()) {
                setRequestProperty("X-API-Key", relayApiKey)
            }
            connectTimeout = 15000
            readTimeout = 15000
        }

        try {
            conn.outputStream.use { os: OutputStream ->
                os.write(payload)
                os.flush()
            }

            if (conn.responseCode != 200) {
                throw FreeFlowException("HTTP relay returned status ${conn.responseCode}")
            }

            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    // ========================================================================
    // DNS Packet Build/Parse
    // ========================================================================

    /**
     * Build a DNS AAAA query packet.
     */
    private fun buildDnsQuery(name: String): ByteArray {
        val packet = mutableListOf<Byte>()
        val txid = secureRandom.nextInt(0xFFFF + 1)
        packet.add(((txid shr 8) and 0xFF).toByte())
        packet.add((txid and 0xFF).toByte())
        packet.add(0x01) // Flags: recursion desired
        packet.add(0x00)
        packet.add(0x00) // Questions: 1
        packet.add(0x01)
        // Answer, Authority, Additional: 0
        repeat(6) { packet.add(0x00) }

        // Encode QNAME
        for (label in name.split(".")) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            packet.add(bytes.size.toByte())
            packet.addAll(bytes.toList())
        }
        packet.add(0x00) // Root label

        // QTYPE = AAAA (28)
        packet.add(0x00)
        packet.add(0x1C)
        // QCLASS = IN (1)
        packet.add(0x00)
        packet.add(0x01)

        return packet.toByteArray()
    }

    /**
     * Parse AAAA records from a DNS response packet.
     */
    private fun parseDnsResponse(data: ByteArray): List<ByteArray> {
        if (data.size < 12) throw FreeFlowException("DNS response too short")

        val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (anCount == 0) throw FreeFlowException("No AAAA records in response")

        var pos = 12

        // Skip QNAME
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if (len and 0xC0 == 0xC0) { pos += 2; break }
            pos += 1 + len
        }
        pos += 4 // QTYPE + QCLASS

        val records = mutableListOf<ByteArray>()
        for (i in 0 until anCount) {
            if (pos + 12 > data.size) break

            // Skip NAME (may be compressed pointer)
            if (data[pos].toInt() and 0xC0 == 0xC0) {
                pos += 2
            } else {
                while (pos < data.size && data[pos].toInt() != 0) {
                    pos += (data[pos].toInt() and 0xFF) + 1
                }
                pos++
            }

            if (pos + 10 > data.size) break

            val rtype = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2 + 2 + 4 // TYPE + CLASS + TTL

            val rdLength = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2

            if (rtype == 28 && rdLength == 16 && pos + 16 <= data.size) { // AAAA
                records.add(data.copyOfRange(pos, pos + 16))
            }
            pos += rdLength
        }
        return records
    }

    /**
     * Send DNS AAAA query via UDP with resolver pool load balancing.
     * On failure, the resolver is marked unhealthy and the next one is tried.
     */
    private fun dnsQueryAAAA(name: String): List<ByteArray> {
        val query = buildDnsQuery(name)
        val maxAttempts = maxOf(3, resolverPool.healthyCount())

        var lastError: Exception = FreeFlowException("No resolvers available")
        for (attempt in 0 until maxAttempts) {
            val currentResolver = resolverPool.next()
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 10000

                try {
                    val address = InetAddress.getByName(currentResolver)
                    val sendPacket = DatagramPacket(query, query.size, address, 53)
                    socket.send(sendPacket)

                    val buffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(recvPacket)

                    val responseData = buffer.copyOfRange(0, recvPacket.length)
                    return parseDnsResponse(responseData)
                } finally {
                    socket.close()
                }
            } catch (e: Exception) {
                lastError = e
                resolverPool.markUnhealthy(currentResolver)
            }
        }
        throw lastError
    }

    // ========================================================================
    // Error Handling
    // ========================================================================

    /**
     * Check for 0xFF error responses from Oracle (Section 3.2).
     * If first byte is 0xFF, second byte is error code.
     */
    private fun checkErrorResponse(response: ByteArray): ByteArray {
        if (response.size >= 2 && response[0] == Commands.ERR) {
            val code = response[1]
            val name = ErrorCodes.name(code)
            throw FreeFlowException("Oracle error: $name")
        }
        return response
    }

    private fun requireSession() {
        if (sessionKey == null) throw FreeFlowException("No active session. Call connect() first.")
    }

    private fun requireRegistered() {
        if (!registered) throw FreeFlowException("Not registered. Identity not bound to session.")
    }
}

class FreeFlowException(message: String) : Exception(message)
