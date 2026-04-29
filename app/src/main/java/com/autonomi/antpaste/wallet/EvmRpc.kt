package com.autonomi.antpaste.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/** Read-only JSON-RPC 2.0 client for EVM queries. Writes go through WalletSigner. */
class EvmRpc(private val rpcUrl: String) {

    private val requestIdGenerator = AtomicLong(1)

    suspend fun getBalance(address: String): BigInteger = withContext(Dispatchers.IO) {
        val hex = call("eth_getBalance", """["$address", "latest"]""")
        BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
    }

    suspend fun ethCall(to: String, data: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val dataHex = "0x" + Erc20.bytesToHex(data)
        val params = """[{"to":"$to","data":"$dataHex"}, "latest"]"""
        val hex = call("eth_call", params).removePrefix("0x")
        if (hex.isEmpty()) ByteArray(0) else Erc20.hexToBytes(hex)
    }

    suspend fun chainId(): Long = withContext(Dispatchers.IO) {
        val hex = call("eth_chainId", "[]").removePrefix("0x")
        hex.toLong(16)
    }

    suspend fun getTransactionReceipt(txHash: String): Receipt? = withContext(Dispatchers.IO) {
        val raw = callRaw("eth_getTransactionReceipt", """["$txHash"]""")
        if (raw.contains("\"result\":null")) return@withContext null

        val status = Regex("\"status\"\\s*:\\s*\"(0x[0-9a-fA-F]+)\"")
            .find(raw)?.groupValues?.get(1)
            ?: return@withContext null
        val blockHex = Regex("\"blockNumber\"\\s*:\\s*\"(0x[0-9a-fA-F]+)\"")
            .find(raw)?.groupValues?.get(1)

        Receipt(
            txHash = txHash,
            status = if (status == "0x1") ReceiptStatus.SUCCESS else ReceiptStatus.REVERTED,
            blockNumber = blockHex?.removePrefix("0x")?.toLongOrNull(16) ?: 0L,
        )
    }

    suspend fun blockNumber(): Long = withContext(Dispatchers.IO) {
        call("eth_blockNumber", "[]").removePrefix("0x").toLong(16)
    }

    suspend fun getTransactionTo(txHash: String): String? = withContext(Dispatchers.IO) {
        val raw = callRaw("eth_getTransactionByHash", """["$txHash"]""")
        if (raw.contains("\"result\":null")) return@withContext null
        Regex("\"to\"\\s*:\\s*\"(0x[0-9a-fA-F]+)\"")
            .find(raw)?.groupValues?.get(1)
    }

    // Used by EtchSigner's ghost-payment guard to recover a payForQuotes hash
    // when MetaMask returned "Invalid Id" but the tx still landed on-chain.
    suspend fun findRecentTxFromUserToContract(
        userAddress: String,
        contractAddress: String,
        tokenAddress: String,
        lookbackBlocks: Long = 1200,
    ): String? = withContext(Dispatchers.IO) {
        val latest = blockNumber()
        val fromBlock = (latest - lookbackBlocks).coerceAtLeast(0L)
        val transferTopic = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
        val paddedUser = "0x" + "0".repeat(24) + userAddress.removePrefix("0x").lowercase()
        val params = """[{
            "fromBlock":"0x${fromBlock.toString(16)}",
            "toBlock":"0x${latest.toString(16)}",
            "address":"$tokenAddress",
            "topics":["$transferTopic","$paddedUser"]
        }]""".replace("\n", "").replace("  ", "")

        val raw = callRaw("eth_getLogs", params)
        val txHashes = Regex("\"transactionHash\"\\s*:\\s*\"(0x[0-9a-fA-F]+)\"")
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
            .distinct()
            .reversed()

        for (txHash in txHashes) {
            val to = getTransactionTo(txHash) ?: continue
            if (to.equals(contractAddress, ignoreCase = true)) return@withContext txHash
        }
        null
    }

    // Backoff tuned for Arbitrum (~250ms blocks): immediate → 500ms → +500 → cap 2s.
    suspend fun waitForReceipt(txHash: String, timeoutMs: Long = 120_000): Receipt {
        val start = System.currentTimeMillis()
        var backoff = 0L
        while (true) {
            if (backoff > 0) delay(backoff)
            val r = getTransactionReceipt(txHash)
            if (r != null) return r
            if (System.currentTimeMillis() - start >= timeoutMs) {
                throw RuntimeException("tx $txHash not confirmed within ${timeoutMs}ms")
            }
            backoff = if (backoff == 0L) 500L else (backoff + 500L).coerceAtMost(2_000L)
        }
    }

    private fun call(method: String, paramsJson: String): String {
        val raw = callRaw(method, paramsJson)
        Regex("\"result\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)?.let { return it }
        val error = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)
        throw RuntimeException("JSON-RPC error ($method): ${error ?: raw}")
    }

    private fun callRaw(method: String, paramsJson: String): String {
        val id = requestIdGenerator.getAndIncrement()
        val body = """{"jsonrpc":"2.0","method":"$method","params":$paramsJson,"id":$id}"""
        val conn = URL(rpcUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.readText()
                ?: throw RuntimeException("HTTP $code (empty body)")
        } finally {
            conn.disconnect()
        }
    }

    data class Receipt(
        val txHash: String,
        val status: ReceiptStatus,
        val blockNumber: Long,
    )

    enum class ReceiptStatus { SUCCESS, REVERTED }
}
