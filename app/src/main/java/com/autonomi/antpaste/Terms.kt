package com.autonomi.antpaste

object Terms {
    const val ACCEPTED_KEY = "terms_accepted"

    val TEXT = """
        By using etchit you agree:

        1. You own what you etch. Don't upload material that infringes copyright, violates the law, or that you don't have the right to share.

        2. No illegal content. No CSAM, no malware, no content that harms others.

        3. Etches are permanent. Once written to the Autonomi network, content cannot be deleted — by you, by us, or by anyone.

        4. Your data map is the only key to a private etch. Treat it like a password — keep it secure, don't expose it. Loss or compromise means loss of privacy, and we cannot revoke access.

        5. Your wallet, your keys, your costs. etchit never holds your private keys. You sign every transaction yourself, and you pay the gas and ANT cost.

        6. No warranty. etchit is provided as-is. The Autonomi network and Arbitrum RPC are operated by third parties; we don't guarantee uptime, data availability, or recoverability.

        7. No data recovery. If you lose a private data map, the content is gone. We cannot recover it.

        8. You are responsible for what you post. etchit is a client app, not a host. We do not monitor, scan, or moderate content. Misuse is your liability.

        9. No refunds for failed uploads. Network errors, app crashes, transaction failures, or any other technical issue during an etch may result in spent ANT or gas with no content stored. Blockchain transactions cannot be reversed and we cannot refund.
    """.trimIndent()
}
