package com.autonomi.antpaste.wallet

import android.util.Log
import androidx.navigation.NavController
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.ui.openAppKit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WalletSession(
    private val signer: WalletSigner,
) : AppKit.ModalDelegate {

    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _relayAvailable = MutableStateFlow(false)
    val relayAvailable: StateFlow<Boolean> = _relayAvailable.asStateFlow()

    @Volatile
    private var nav: NavController? = null

    init {
        // Optimistic restore — relay may not be up yet; onConnectionStateChange retries.
        tryRestoreSession("init")
    }

    val isConnected: Boolean
        get() = _state.value is SessionState.Connected

    val shortAddress: String?
        get() = (_state.value as? SessionState.Connected)?.let { short(it.address) }

    fun attachNav(navController: NavController) {
        nav = navController
        Log.i("ant-paste", "WalletSession: nav attached")
    }

    fun detachNav() {
        nav = null
        Log.i("ant-paste", "WalletSession: nav detached")
    }

    fun connect() {
        val n = nav
        if (n == null) {
            Log.w("ant-paste", "WalletSession.connect: no nav — is WalletModalHost mounted?")
            return
        }
        Log.i("ant-paste", "WalletSession.connect: opening AppKit modal")
        _state.value = SessionState.Connecting
        n.openAppKit(
            shouldOpenChooseNetwork = false,
            onError = { err ->
                Log.e("ant-paste", "WalletSession.connect: openAppKit error", err)
                _state.value = SessionState.Error(err.message ?: "unknown error")
            },
        )
    }

    fun disconnect() {
        Log.i("ant-paste", "WalletSession.disconnect")
        AppKit.disconnect(
            onSuccess = {
                _state.value = SessionState.Disconnected
                Log.i("ant-paste", "WalletSession: disconnected cleanly")
            },
            onError = { t ->
                Log.e("ant-paste", "WalletSession.disconnect failed", t)
                _state.value = SessionState.Error(t.message ?: "disconnect failed")
            },
        )
    }

    fun onDeepLink(uri: String) {
        Log.i("ant-paste", "WalletSession.onDeepLink: $uri")
    }

    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
        val account = AppKit.getAccount()
        if (account != null) {
            _state.value = SessionState.Connected(
                address = account.address,
                chainId = account.chain.id,
            )
            Log.i(
                "ant-paste",
                "WalletSession: connected ${short(account.address)} on ${account.chain.id}"
            )
        } else {
            Log.w("ant-paste", "onSessionApproved fired but AppKit.getAccount() is null")
            _state.value = SessionState.Error("session approved but account missing")
        }
    }

    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
        Log.w("ant-paste", "WalletSession: rejected (${rejectedSession.reason})")
        _state.value = SessionState.Error("User rejected: ${rejectedSession.reason}")
    }

    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
        Log.i("ant-paste", "WalletSession: session updated topic=${updatedSession.topic}")
        AppKit.getAccount()?.let { account ->
            _state.value = SessionState.Connected(
                address = account.address,
                chainId = account.chain.id,
            )
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {
        Log.i("ant-paste", "WalletSession: session event $sessionEvent")
    }

    override fun onSessionExtend(session: Modal.Model.Session) {
        Log.i("ant-paste", "WalletSession: session extended")
    }

    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
        Log.i("ant-paste", "WalletSession: session deleted by wallet")
        _state.value = SessionState.Disconnected
    }

    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
        Log.i(
            "ant-paste",
            "WalletSession: request response topic=${response.topic} method=${response.method}"
        )
        signer.handleResponse(response)
    }

    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
        Log.w("ant-paste", "WalletSession: proposal expired (current=${_state.value})")
        if (_state.value is SessionState.Connecting) {
            _state.value = SessionState.Error("Proposal expired — try again")
        }
    }

    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
        Log.w("ant-paste", "WalletSession: request expired id=${request.id}")
        signer.cancelPending(
            request.id,
            "Wallet didn't see the request — try again",
        )
    }

    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
        Log.i("ant-paste", "WalletSession: relay available=${state.isAvailable}")
        _relayAvailable.value = state.isAvailable
        if (state.isAvailable && _state.value is SessionState.Disconnected) {
            tryRestoreSession("relay-connect")
        }
    }

    override fun onError(error: Modal.Model.Error) {
        Log.e("ant-paste", "WalletSession: AppKit error", error.throwable)
        val raw = error.throwable.message ?: "unknown error"
        val firstLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
            .ifEmpty { "unknown error" }
        // Transient relay hiccups — SDK retries internally. Don't paint a scary banner.
        if (firstLine.contains("Cannot send respondWithError", ignoreCase = true) ||
            firstLine.contains("Publish error", ignoreCase = true) ||
            firstLine.contains("Timed out", ignoreCase = true)
        ) {
            Log.w("ant-paste", "WalletSession: ignoring transient relay error: $firstLine")
            return
        }
        _state.value = SessionState.Error(firstLine)
    }

    private fun tryRestoreSession(source: String) {
        val account = try { AppKit.getAccount() } catch (_: IllegalStateException) { null }
        if (account != null) {
            _state.value = SessionState.Connected(
                address = account.address,
                chainId = account.chain.id,
            )
            Log.i(
                "ant-paste",
                "WalletSession: restored session ${short(account.address)} on ${account.chain.id} (source=$source)"
            )
        }
    }

    private fun short(address: String): String {
        if (address.length < 10) return address
        return "${address.take(6)}…${address.takeLast(4)}"
    }

}

sealed interface SessionState {
    data object Disconnected : SessionState
    data object Connecting : SessionState
    data class Connected(val address: String, val chainId: String) : SessionState
    data class Error(val reason: String) : SessionState
}
