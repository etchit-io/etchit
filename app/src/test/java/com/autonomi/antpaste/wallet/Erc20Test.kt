package com.autonomi.antpaste.wallet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class Erc20Test {

    // ── selectors ────────────────────────────────────────────────

    @Test
    fun encodeBalanceOf_golden() {
        // balanceOf(0x5fbdb2315678afecb367f032d93f642f64180aa3)
        val expected = "70a08231" +
            "0000000000000000000000005fbdb2315678afecb367f032d93f642f64180aa3"
        val calldata = Erc20.encodeBalanceOf("0x5fbdb2315678afecb367f032d93f642f64180aa3")
        assertEquals(expected, Erc20.bytesToHex(calldata))
        assertEquals(36, calldata.size)
    }

    @Test
    fun encodeAllowance_golden() {
        // allowance(0x5fbd..., 0x8464...)
        val expected = "dd62ed3e" +
            "0000000000000000000000005fbdb2315678afecb367f032d93f642f64180aa3" +
            "0000000000000000000000008464135c8f25da09e49bc8782676a84730c318bc"
        val calldata = Erc20.encodeAllowance(
            owner = "0x5fbdb2315678afecb367f032d93f642f64180aa3",
            spender = "0x8464135c8f25da09e49bc8782676a84730c318bc"
        )
        assertEquals(expected, Erc20.bytesToHex(calldata))
        assertEquals(68, calldata.size)
    }

    @Test
    fun encodeApprove_maxUint256() {
        val maxUint256 = BigInteger(1, ByteArray(32) { 0xFF.toByte() })
        val calldata = Erc20.encodeApprove(
            spender = "0x8464135c8f25da09e49bc8782676a84730c318bc",
            amount = maxUint256,
        )
        val expected = "095ea7b3" +
            "0000000000000000000000008464135c8f25da09e49bc8782676a84730c318bc" +
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        assertEquals(expected, Erc20.bytesToHex(calldata))
    }

    @Test
    fun encodeApprove_zero() {
        val calldata = Erc20.encodeApprove(
            spender = "0x8464135c8f25da09e49bc8782676a84730c318bc",
            amount = BigInteger.ZERO,
        )
        val expected = "095ea7b3" +
            "0000000000000000000000008464135c8f25da09e49bc8782676a84730c318bc" +
            "0000000000000000000000000000000000000000000000000000000000000000"
        assertEquals(expected, Erc20.bytesToHex(calldata))
    }

    @Test
    fun encodeApprove_oneEther() {
        // 1e18 = 0x0de0b6b3a7640000
        val oneEther = BigInteger("1000000000000000000")
        val calldata = Erc20.encodeApprove(
            spender = "0x8464135c8f25da09e49bc8782676a84730c318bc",
            amount = oneEther,
        )
        val expected = "095ea7b3" +
            "0000000000000000000000008464135c8f25da09e49bc8782676a84730c318bc" +
            "0000000000000000000000000000000000000000000000000de0b6b3a7640000"
        assertEquals(expected, Erc20.bytesToHex(calldata))
    }

    // ── decoders ─────────────────────────────────────────────────

    @Test
    fun decodeUint256_zero() {
        val bytes = ByteArray(32)
        assertEquals(BigInteger.ZERO, Erc20.decodeUint256(bytes))
    }

    @Test
    fun decodeUint256_one() {
        val bytes = ByteArray(32).apply { this[31] = 1 }
        assertEquals(BigInteger.ONE, Erc20.decodeUint256(bytes))
    }

    @Test
    fun decodeUint256_oneEther() {
        val hex = "0000000000000000000000000000000000000000000000000de0b6b3a7640000"
        val bytes = Erc20.hexToBytes(hex)
        assertEquals(BigInteger("1000000000000000000"), Erc20.decodeUint256(bytes))
    }

    @Test
    fun decodeUint256_max() {
        val bytes = ByteArray(32) { 0xFF.toByte() }
        val expected = BigInteger(1, ByteArray(32) { 0xFF.toByte() })
        assertEquals(expected, Erc20.decodeUint256(bytes))
    }

    // ── hex round-trip ────────────────────────────────────────────

    @Test
    fun hexToBytes_withPrefix() {
        val bytes = Erc20.hexToBytes("0xdeadbeef")
        assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), bytes)
    }

    @Test
    fun hexToBytes_withoutPrefix() {
        val bytes = Erc20.hexToBytes("deadbeef")
        assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), bytes)
    }

    @Test
    fun bytesToHex_roundtrip() {
        val original = "095ea7b30000000000000000000000008464135c8f25da09e49bc8782676a84730c318bc"
        val roundtripped = Erc20.bytesToHex(Erc20.hexToBytes(original))
        assertEquals(original, roundtripped)
    }

    // ── rejection paths ──────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun encodeAddress_rejectsShort() {
        Erc20.encodeAddress("0x1234")
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodeAddress_rejectsLong() {
        Erc20.encodeAddress("0x" + "a".repeat(42))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodeUint256_rejectsNegative() {
        Erc20.encodeUint256(BigInteger("-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexToBytes_rejectsOddLength() {
        Erc20.hexToBytes("abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeUint256_rejectsWrongSize() {
        Erc20.decodeUint256(ByteArray(16))
    }
}
