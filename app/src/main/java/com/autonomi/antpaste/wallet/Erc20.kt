package com.autonomi.antpaste.wallet

import java.math.BigInteger

object Erc20 {

    private val SELECTOR_BALANCE_OF: ByteArray = hexToBytes("70a08231")
    private val SELECTOR_ALLOWANCE: ByteArray = hexToBytes("dd62ed3e")
    private val SELECTOR_APPROVE: ByteArray = hexToBytes("095ea7b3")

    fun encodeBalanceOf(owner: String): ByteArray =
        SELECTOR_BALANCE_OF + encodeAddress(owner)

    fun encodeAllowance(owner: String, spender: String): ByteArray =
        SELECTOR_ALLOWANCE + encodeAddress(owner) + encodeAddress(spender)

    fun encodeApprove(spender: String, amount: BigInteger): ByteArray =
        SELECTOR_APPROVE + encodeAddress(spender) + encodeUint256(amount)

    fun decodeUint256(bytes: ByteArray): BigInteger {
        require(bytes.size == 32) { "uint256 must be 32 bytes, got ${bytes.size}" }
        return BigInteger(1, bytes)
    }

    internal fun encodeAddress(address: String): ByteArray {
        val clean = address.removePrefix("0x").removePrefix("0X")
        require(clean.length == 40) {
            "address must be 40 hex chars, got ${clean.length}: $address"
        }
        return ByteArray(12) + hexToBytes(clean)
    }

    internal fun encodeUint256(value: BigInteger): ByteArray {
        require(value.signum() >= 0) { "uint256 cannot be negative: $value" }
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size < 32 -> ByteArray(32 - raw.size) + raw
            // BigInteger prepends a 0x00 sign byte for positive values whose MSB is set.
            raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
            else -> throw IllegalArgumentException("uint256 overflow: ${raw.size} bytes")
        }
    }

    internal fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        require(clean.length % 2 == 0) { "hex string must be even length" }
        return ByteArray(clean.length / 2) { i ->
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex char at offset ${i * 2}" }
            ((hi shl 4) + lo).toByte()
        }
    }

    internal fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
