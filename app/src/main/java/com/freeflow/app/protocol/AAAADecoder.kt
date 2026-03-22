package com.freeflow.app.protocol

/**
 * AAAA record decoder (PROTOCOL.md Section 1.4)
 *
 * Each AAAA record is a 16-byte IPv6 address:
 *   [prefix:4][flags:1][seq:1][recIdx:1][recTotal:1][payload:8]
 *
 * Flags: bit 7 = isLast, bit 6 = continuation, bit 5 = compressed
 * Max 8 records per response, 8 bytes payload per record = 64 bytes max.
 */
object AAAADecoder {

    const val RECORD_SIZE = 16
    const val PAYLOAD_OFFSET = 8
    const val PAYLOAD_SIZE = 8
    const val MAX_RECORDS = 8
    const val FLAGS_OFFSET = 4
    const val IS_LAST_FLAG = 0x80

    /**
     * Decode AAAA records into concatenated payload bytes.
     * Records are sorted by their record index before concatenation.
     *
     * @param records List of 16-byte AAAA records
     * @return Concatenated payload bytes
     */
    fun decode(records: List<ByteArray>): ByteArray {
        if (records.isEmpty()) return ByteArray(0)

        // Sort by record index (byte 6)
        val sorted = records.sortedBy { it[6].toInt() and 0xFF }

        val result = mutableListOf<Byte>()
        for (record in sorted) {
            if (record.size < RECORD_SIZE) continue
            // Extract 8-byte payload from offset 8
            for (i in PAYLOAD_OFFSET until RECORD_SIZE) {
                result.add(record[i])
            }
        }
        return result.toByteArray()
    }

    /**
     * Check if a record has the isLast flag set.
     */
    fun isLast(record: ByteArray): Boolean {
        if (record.size <= FLAGS_OFFSET) return false
        return (record[FLAGS_OFFSET].toInt() and IS_LAST_FLAG) != 0
    }

    /**
     * Extract the record index from a record.
     */
    fun recordIndex(record: ByteArray): Int {
        if (record.size < 7) return 0
        return record[6].toInt() and 0xFF
    }

    /**
     * Extract the total record count from a record.
     */
    fun recordTotal(record: ByteArray): Int {
        if (record.size < 8) return 0
        return record[7].toInt() and 0xFF
    }
}
