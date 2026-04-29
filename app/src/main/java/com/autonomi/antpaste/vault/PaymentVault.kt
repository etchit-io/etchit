package com.autonomi.antpaste.vault

import com.autonomi.antpaste.wallet.Erc20
import java.math.BigInteger

/** ABI encoder for PaymentVault.payForQuotes((address,uint256,bytes32)[]). */
object PaymentVault {

    private val SELECTOR_PAY_FOR_QUOTES: ByteArray = Erc20.hexToBytes("b6c2141b")

    fun encodeBatchPayment(payments: List<Payment>): ByteArray {
        val headOffset = Erc20.encodeUint256(BigInteger.valueOf(0x20))
        val length = Erc20.encodeUint256(BigInteger.valueOf(payments.size.toLong()))
        val totalSize =
            SELECTOR_PAY_FOR_QUOTES.size + headOffset.size + length.size + payments.size * 96
        val out = ByteArray(totalSize)

        var pos = 0
        System.arraycopy(SELECTOR_PAY_FOR_QUOTES, 0, out, pos, SELECTOR_PAY_FOR_QUOTES.size)
        pos += SELECTOR_PAY_FOR_QUOTES.size

        System.arraycopy(headOffset, 0, out, pos, headOffset.size)
        pos += headOffset.size

        System.arraycopy(length, 0, out, pos, length.size)
        pos += length.size

        for (p in payments) {
            val rewards = Erc20.encodeAddress(p.rewardsAddress)
            val amount = Erc20.encodeUint256(p.amount)
            val quote = encodeBytes32(p.quoteHash)

            System.arraycopy(rewards, 0, out, pos, 32); pos += 32
            System.arraycopy(amount, 0, out, pos, 32); pos += 32
            System.arraycopy(quote, 0, out, pos, 32); pos += 32
        }

        return out
    }

    private fun encodeBytes32(hex: String): ByteArray {
        val bytes = Erc20.hexToBytes(hex)
        require(bytes.size == 32) {
            "bytes32 must be 32 bytes, got ${bytes.size} (hex=$hex)"
        }
        return bytes
    }

    data class Payment(
        val rewardsAddress: String,
        val amount: BigInteger,
        val quoteHash: String,
    )
}
