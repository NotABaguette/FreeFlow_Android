package com.freeflow.app.client

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Manages a pool of DNS resolvers with round-robin rotation and periodic
 * health checking. Distributing queries across many resolvers reduces the
 * fingerprint on any single resolver (anti-detection).
 *
 * Rotation schedule is controlled by [strength] (1-10):
 *   - strength 10 → rotate every query
 *   - strength  5 → rotate every 6 queries
 *   - strength  1 → rotate every 10 queries
 */
class ResolverPool(
    resolvers: List<String> = emptyList(),
    strength: Int = 5
) {
    companion object {
        /** Well-known public DNS resolvers (default pool). */
        val DEFAULT_RESOLVERS = listOf(
            "8.8.8.8",          // Google
            "8.8.4.4",          // Google
            "1.1.1.1",          // Cloudflare
            "1.0.0.1",          // Cloudflare
            "9.9.9.9",          // Quad9
            "149.112.112.112",  // Quad9
            "208.67.222.222",   // OpenDNS
            "208.67.220.220",   // OpenDNS
            "76.76.2.0",        // ControlD
            "76.76.10.0",       // ControlD
            "94.140.14.14",     // AdGuard
            "94.140.15.15",     // AdGuard
        )
    }

    private val resolverList: List<String> = resolvers.ifEmpty { DEFAULT_RESOLVERS }
    private val healthy: MutableMap<String, Boolean> = resolverList.associateWith { true }.toMutableMap()
    private var index: Int = 0
    private var queryCount: Int = 0
    private var _strength: Int = strength.coerceIn(1, 10)
    private var healthJob: Job? = null

    /**
     * Returns the next healthy resolver based on the round-robin rotation schedule.
     * If no healthy resolvers remain, all are reset to healthy (best-effort).
     */
    @Synchronized
    fun next(): String {
        val rotateEvery = 11 - _strength
        queryCount++

        if (queryCount >= rotateEvery) {
            queryCount = 0
            index = (index + 1) % resolverList.size
        }

        // Find a healthy resolver starting from current index
        for (i in resolverList.indices) {
            val idx = (index + i) % resolverList.size
            val r = resolverList[idx]
            if (healthy[r] == true) {
                index = idx
                return r
            }
        }

        // No healthy resolvers — reset all
        resolverList.forEach { healthy[it] = true }
        return resolverList[index]
    }

    /**
     * Mark a resolver as unhealthy. It will be skipped by [next] until
     * the health checker restores it.
     */
    @Synchronized
    fun markUnhealthy(resolver: String) {
        healthy[resolver] = false
    }

    /**
     * Returns the number of currently healthy resolvers.
     */
    @Synchronized
    fun healthyCount(): Int = healthy.values.count { it }

    /**
     * Update rotation strength (clamped to 1-10).
     */
    @Synchronized
    fun setStrength(s: Int) {
        _strength = s.coerceIn(1, 10)
    }

    /**
     * Start periodic health checking using a coroutine. Every 60 seconds,
     * each resolver is tested with a DNS AAAA query for "google.com"
     * (5-second timeout per resolver).
     */
    fun startHealthCheck(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        healthJob?.cancel()
        healthJob = scope.launch {
            while (isActive) {
                delay(60_000L)
                checkAll()
            }
        }
    }

    /**
     * Stop the periodic health check.
     */
    fun stopHealthCheck() {
        healthJob?.cancel()
        healthJob = null
    }

    private fun checkAll() {
        val resolversCopy: List<String>
        synchronized(this) {
            resolversCopy = resolverList.toList()
        }

        for (r in resolversCopy) {
            val isHealthy = probe(r)
            synchronized(this) {
                healthy[r] = isHealthy
            }
        }
    }

    private val secureRandom = SecureRandom()

    /**
     * Tests resolver reachability by sending a minimal DNS query over UDP.
     * Conforms to DNS protocol — no ICMP, no spawned processes.
     */
    private fun probe(resolver: String): Boolean {
        return try {
            val query = buildDNSProbe()
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            try {
                val address = InetAddress.getByName(resolver)
                val sendPacket = DatagramPacket(query, query.size, address, 53)
                socket.send(sendPacket)
                val buffer = ByteArray(512)
                val recvPacket = DatagramPacket(buffer, buffer.size)
                socket.receive(recvPacket)
                recvPacket.length >= 12
            } finally {
                socket.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildDNSProbe(): ByteArray {
        val pkt = mutableListOf<Byte>()
        val txid = secureRandom.nextInt(0xFFFF + 1)
        pkt.add(((txid shr 8) and 0xFF).toByte())
        pkt.add((txid and 0xFF).toByte())
        pkt.add(0x01); pkt.add(0x00) // flags
        pkt.add(0x00); pkt.add(0x01) // questions
        repeat(6) { pkt.add(0x00) }
        pkt.add(3); pkt.addAll("dns".toByteArray().toList())
        pkt.add(6); pkt.addAll("google".toByteArray().toList())
        pkt.add(0x00)
        pkt.add(0x00); pkt.add(0x01) // A
        pkt.add(0x00); pkt.add(0x01) // IN
        return pkt.toByteArray()
    }
}
