package com.autonomi.antpaste.vault

import com.autonomi.antpaste.wallet.Erc20
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class PaymentVaultTest {

    // ── selector ─────────────────────────────────────────────────

    @Test
    fun encodeBatchPayment_singlePayment_golden() {
        // payForQuotes([{rewards=0x5fbd..., amount=1, quoteHash=0x11..11}])
        val calldata = PaymentVault.encodeBatchPayment(
            listOf(
                PaymentVault.Payment(
                    rewardsAddress = "0x5fbdb2315678afecb367f032d93f642f64180aa3",
                    amount = BigInteger.ONE,
                    quoteHash = "1111111111111111111111111111111111111111111111111111111111111111",
                )
            )
        )

        val expected = buildString {
            append("b6c2141b")
            append("0000000000000000000000000000000000000000000000000000000000000020")
            append("0000000000000000000000000000000000000000000000000000000000000001")
            append("0000000000000000000000005fbdb2315678afecb367f032d93f642f64180aa3")
            append("0000000000000000000000000000000000000000000000000000000000000001")
            append("1111111111111111111111111111111111111111111111111111111111111111")
        }
        assertEquals(expected, Erc20.bytesToHex(calldata))
        assertEquals(4 + 32 + 32 + 96, calldata.size)
    }

    @Test
    fun encodeBatchPayment_twoPayments_packsInOrder() {
        // Two payments with distinct rewards + quote hashes; asserts the
        // array elements are concatenated contiguously (no per-element
        // offset table, since tuples of static types are static).
        val calldata = PaymentVault.encodeBatchPayment(
            listOf(
                PaymentVault.Payment(
                    rewardsAddress = "0x5fbdb2315678afecb367f032d93f642f64180aa3",
                    amount = BigInteger("1000000000000000000"), // 1 ether
                    quoteHash = "1111111111111111111111111111111111111111111111111111111111111111",
                ),
                PaymentVault.Payment(
                    rewardsAddress = "0x8464135c8f25da09e49bc8782676a84730c318bc",
                    amount = BigInteger("2000000000000000000"), // 2 ether
                    quoteHash = "2222222222222222222222222222222222222222222222222222222222222222",
                ),
            )
        )

        val expected = buildString {
            append("b6c2141b")
            append("0000000000000000000000000000000000000000000000000000000000000020")
            append("0000000000000000000000000000000000000000000000000000000000000002")
            append("0000000000000000000000005fbdb2315678afecb367f032d93f642f64180aa3")
            append("0000000000000000000000000000000000000000000000000de0b6b3a7640000")
            append("1111111111111111111111111111111111111111111111111111111111111111")
            append("0000000000000000000000008464135c8f25da09e49bc8782676a84730c318bc")
            append("0000000000000000000000000000000000000000000000001bc16d674ec80000")
            append("2222222222222222222222222222222222222222222222222222222222222222")
        }
        assertEquals(expected, Erc20.bytesToHex(calldata))
        assertEquals(4 + 32 + 32 + 2 * 96, calldata.size)
    }

    @Test
    fun encodeBatchPayment_empty_onlyHeadAndZeroLength() {
        val calldata = PaymentVault.encodeBatchPayment(emptyList())

        val expected = buildString {
            append("b6c2141b")
            append("0000000000000000000000000000000000000000000000000000000000000020")
            append("0000000000000000000000000000000000000000000000000000000000000000")
        }
        assertEquals(expected, Erc20.bytesToHex(calldata))
        assertEquals(4 + 32 + 32, calldata.size)
    }

    @Test
    fun encodeBatchPayment_stripsHexPrefix() {
        // Same golden encoding whether hashes have the 0x prefix or not.
        val withPrefix = PaymentVault.encodeBatchPayment(
            listOf(
                PaymentVault.Payment(
                    rewardsAddress = "0x5fbdb2315678afecb367f032d93f642f64180aa3",
                    amount = BigInteger.ONE,
                    quoteHash = "0x1111111111111111111111111111111111111111111111111111111111111111",
                )
            )
        )
        val withoutPrefix = PaymentVault.encodeBatchPayment(
            listOf(
                PaymentVault.Payment(
                    rewardsAddress = "5fbdb2315678afecb367f032d93f642f64180aa3",
                    amount = BigInteger.ONE,
                    quoteHash = "1111111111111111111111111111111111111111111111111111111111111111",
                )
            )
        )
        assertEquals(Erc20.bytesToHex(withPrefix), Erc20.bytesToHex(withoutPrefix))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodeBatchPayment_rejectsShortQuoteHash() {
        PaymentVault.encodeBatchPayment(
            listOf(
                PaymentVault.Payment(
                    rewardsAddress = "0x5fbdb2315678afecb367f032d93f642f64180aa3",
                    amount = BigInteger.ONE,
                    quoteHash = "1111", // too short
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodeBatchPayment_rejectsBadAddress() {
        PaymentVault.encodeBatchPayment(
            listOf(
                PaymentVault.Payment(
                    rewardsAddress = "0x5fbd", // too short
                    amount = BigInteger.ONE,
                    quoteHash = "1111111111111111111111111111111111111111111111111111111111111111",
                )
            )
        )
    }
}
