package com.autonomi.antpaste.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autonomi.antpaste.wallet.WalletSession
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import com.reown.appkit.ui.appKitGraph

/**
 * Minimal Compose host for Reown AppKit's wallet-picker modal.
 *
 * The rest of etchit is View-based (XML + findViewById). This composable
 * is hosted inside a single `ComposeView` in `activity_main.xml` and
 * does nothing visible under normal use — its `"home"` route is an empty
 * `Box`. When [WalletSession.connect] fires `navController.openAppKit`,
 * AppKit's bottom-sheet graph takes over the screen via
 * `ModalBottomSheetLayout`; when the user dismisses it, we're back to
 * the empty home route and XML views handle everything.
 *
 * The `DisposableEffect` attaches the NavController to [session] while
 * this composable is in the tree and detaches it on disposal — so if the
 * Activity is recreated (rotation, theme change) the reference stays
 * correct instead of pointing at a stale controller.
 */
@OptIn(ExperimentalMaterialNavigationApi::class)
@Composable
fun WalletModalHost(session: WalletSession) {
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)

    DisposableEffect(navController) {
        session.attachNav(navController)
        onDispose { session.detachNav() }
    }

    MaterialTheme {
        ModalBottomSheetLayout(bottomSheetNavigator) {
            NavHost(
                navController = navController,
                startDestination = "home",
            ) {
                composable("home") {
                    // Empty home route — no visible Compose UI of our own.
                    // ModalBottomSheetLayout is transparent and pass-through
                    // for touches when no sheet is showing.
                    Box(modifier = Modifier.fillMaxSize())
                }
                appKitGraph(navController)
            }
        }
    }
}
