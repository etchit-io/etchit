package com.autonomi.antpaste.wallet

import android.util.Log
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request
import com.reown.appkit.client.models.request.SentRequestResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Coroutine wrapper around Reown AppKit's callback-based request API.
// AppKit fires onSuccess immediately with a requestId, then onSessionRequestResponse
// later via WalletSession.handleResponse — keyed by the same id in `pending`.
class WalletSigner {

    private val pending = ConcurrentHashMap<Long, CancellableContinuation<Any?>>()

    suspend fun sendTransaction(
        chainId: String,
        from: String,
        to: String,
        data: ByteArray,
        value: BigInteger = BigInteger.ZERO,
    ): String {
        val paramsJson = buildSendTxParams(from, to, data, value)
        val result = dispatch(
            method = "eth_sendTransaction",
            params = paramsJson,
            chainId = chainId,
        )
        return result as? String
            ?: throw RuntimeException("eth_sendTransaction returned non-string result: $result")
    }

    suspend fun switchChain(sessionChainId: String, targetChainId: Long) {
        val hex = "0x" + targetChainId.toString(16)
        val paramsJson = """[{"chainId":"$hex"}]"""
        dispatch(
            method = "wallet_switchEthereumChain",
            params = paramsJson,
            chainId = sessionChainId,
        )
    }

    fun cancelPending(requestId: Long, reason: String) {
        val cont = pending.remove(requestId) ?: return
        Log.w("ant-paste", "WalletSigner: cancelling pending id=$requestId — $reason")
        cont.resumeWithException(RuntimeException(reason))
    }

    fun handleResponse(response: Modal.Model.SessionRequestResponse) {
        when (val result = response.result) {
            is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                val cont = pending.remove(result.id)
                if (cont == null) {
                    Log.w("ant-paste", "WalletSigner: orphan result id=${result.id}")
                    return
                }
                Log.i(
                    "ant-paste",
                    "WalletSigner: response id=${result.id} method=${response.method} → ${result.result}"
                )
                cont.resume(result.result)
            }
            is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                val cont = pending.remove(result.id)
                if (cont == null) {
                    Log.w("ant-paste", "WalletSigner: orphan error id=${result.id}")
                    return
                }
                val userFacing = mapErrorToUserFacing(result.code, result.message)
                Log.w(
                    "ant-paste",
                    "WalletSigner: error id=${result.id} method=${response.method} code=${result.code}: ${result.message}"
                )
                cont.resumeWithException(RuntimeException(userFacing))
            }
        }
    }

    private suspend fun dispatch(
        method: String,
        params: String,
        chainId: String,
    ): Any? = suspendCancellableCoroutine { cont ->
        val request = Request(method = method, params = params, chainId = chainId)
        Log.i("ant-paste", "WalletSigner: dispatch $method chain=$chainId")

        AppKit.request(
            request = request,
            onSuccess = { sent ->
                when (sent) {
                    is SentRequestResult.WalletConnect -> {
                        @Suppress("UNCHECKED_CAST")
                        pending[sent.requestId] = cont as CancellableContinuation<Any?>
                        cont.invokeOnCancellation { pending.remove(sent.requestId) }
                    }
                    is SentRequestResult.Coinbase -> {
                        cont.resumeWithException(
                            RuntimeException("Coinbase Smart Wallet is not supported")
                        )
                    }
                }
            },
            onError = { err ->
                Log.e("ant-paste", "WalletSigner: AppKit.request failed", err)
                cont.resumeWithException(err)
            },
        )
    }

    private fun buildSendTxParams(
        from: String,
        to: String,
        data: ByteArray,
        value: BigInteger,
    ): String {
        val dataHex = "0x" + Erc20.bytesToHex(data)
        val valueHex = "0x" + value.toString(16)
        // gas/gasPrice/nonce omitted on purpose — wallet fills them and shows
        // the user current market gas at signing time.
        return """[{"from":"$from","to":"$to","data":"$dataHex","value":"$valueHex"}]"""
    }

    private fun mapErrorToUserFacing(code: Int, message: String): String = when (code) {
        // MetaMask Mobile returns code=1 "Invalid Id" when its sessions map
        // forgot the topic — peer dropped us, not a protocol error.
        1 -> "Wallet lost the session — reconnect and try again"
        4001 -> "User rejected the request in the wallet"
        4100 -> "Wallet refused the method (not approved for this session)"
        4200 -> "Wallet does not support this method"
        4900 -> "Wallet is disconnected"
        4901 -> "Wallet is not connected to the requested chain"
        4902 -> "Chain not added to wallet — please add it manually"
        else -> "Wallet error $code: $message"
    }
}
