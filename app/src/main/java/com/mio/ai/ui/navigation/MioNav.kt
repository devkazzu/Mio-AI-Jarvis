package com.mio.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mio.ai.ui.screens.HomeScreen
import com.mio.ai.ui.screens.PermissionsScreen
import com.mio.ai.ui.screens.SettingsScreen
import com.mio.ai.ui.vm.AssistantViewModel

/** Three destinations: HUD, Settings, Permissions (with optional highlight). */
@Composable
fun MioNav(vm: AssistantViewModel, wakeSignal: Int) {
    val nav = rememberNavController()
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
                wakeSignal = wakeSignal,
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenPermissions = { nav.navigate("permissions") },
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
    }
}
