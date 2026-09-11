package com.mio.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mio.ai.ui.screens.ActionsScreen
import com.mio.ai.ui.screens.ConversationScreen
import com.mio.ai.ui.screens.HomeScreen
import com.mio.ai.ui.screens.LicensesScreen
import com.mio.ai.ui.screens.OverlayPermissionScreen
import com.mio.ai.ui.screens.PermissionsScreen
import com.mio.ai.ui.screens.SettingsScreen
import com.mio.ai.ui.vm.AssistantViewModel

/**
 * Deliberately no bottom navigation: Home owns the voice experience;
 * Conversation / Activity are one tap away in the top bar, Settings via
 * the gear. Linear, shallow, assistant-first.
 *
 * [destSignal] carries a one-shot navigation request (route string) from
 * notification / overlay intents, e.g. "permissions?highlight=mic".
 */
@Composable
fun MioNav(vm: AssistantViewModel, wakeSignal: Int, destSignal: String?) {
    val nav = rememberNavController()
    LaunchedEffect(destSignal) {
        if (destSignal != null) {
            runCatching { nav.navigate(destSignal) }
        }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onOpenSettings = { nav.navigate("settings") },
                onOpenPermissions = { highlight ->
                    nav.navigate(
                        if (highlight != null) "permissions?highlight=$highlight" else "permissions",
                    )
                },
                onOpenConversation = { nav.navigate("conversation") },
                onOpenActions = { nav.navigate("actions") },
                wakeSignal = wakeSignal,
            )
        }
        composable("conversation") {
            ConversationScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onFix = { destination -> nav.navigate("permissions?highlight=$destination") },
            )
        }
        composable("actions") {
            ActionsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenPermissions = { highlight ->
                    nav.navigate(
                        if (highlight != null) "permissions?highlight=$highlight" else "permissions",
                    )
                },
                onOpenOverlay = { nav.navigate("overlay_setup") },
                onOpenLicenses = { nav.navigate("licenses") },
                onHelp = {
                    nav.popBackStack()
                    vm.submitText("what can you do")
                },
            )
        }
        composable(
            route = "permissions?highlight={highlight}",
            arguments = listOf(
                navArgument("highlight") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            PermissionsScreen(
                highlight = backStack.arguments?.getString("highlight"),
                onBack = { nav.popBackStack() },
            )
        }
        composable("overlay_setup") {
            OverlayPermissionScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
        composable("licenses") {
            LicensesScreen(onBack = { nav.popBackStack() })
        }
    }
}
