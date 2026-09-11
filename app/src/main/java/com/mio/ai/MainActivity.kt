package com.mio.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mio.ai.ui.navigation.MioNav
import com.mio.ai.ui.theme.MioTheme
import com.mio.ai.ui.theme.rememberEffectiveMotion
import com.mio.ai.ui.theme.themeModeOf
import com.mio.ai.ui.vm.AssistantViewModel

/**
 * Single-activity host. Theme, accent intensity and motion come from user
 * settings (motion also honors the OS animator-scale accessibility switch).
 * The wake-word service reuses this activity via [EXTRA_WAKE].
 */
class MainActivity : ComponentActivity() {

    private var wakeSignal by mutableIntStateOf(0)
    private var destSignal by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) wakeSignal++
        destSignal = destRouteOf(intent)
        setContent {
            val vm: AssistantViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val motion = rememberEffectiveMotion(settings.animation)
            MioTheme(
                mode = themeModeOf(settings.theme),
                accentIntensity = settings.accentIntensity,
                motion = motion,
            ) {
                MioNav(vm = vm, wakeSignal = wakeSignal, destSignal = destSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) wakeSignal++
        destRouteOf(intent)?.let { destSignal = it }
    }

    companion object {
        const val EXTRA_WAKE = "com.mio.ai.extra.WAKE"
        const val EXTRA_DEST = "com.mio.ai.extra.DEST"
        const val EXTRA_HIGHLIGHT = "com.mio.ai.extra.HIGHLIGHT"
        const val DEST_PERMISSIONS = "permissions"
        const val DEST_OVERLAY = "overlay_setup"

        /** Whitelisted deep routes from notification / overlay intents. */
        private fun destRouteOf(intent: Intent?): String? = when (intent?.getStringExtra(EXTRA_DEST)) {
            DEST_OVERLAY -> DEST_OVERLAY
            DEST_PERMISSIONS -> {
                val h = intent.getStringExtra(EXTRA_HIGHLIGHT)
                if (h != null) "$DEST_PERMISSIONS?highlight=$h" else DEST_PERMISSIONS
            }
            else -> null
        }
    }
}
