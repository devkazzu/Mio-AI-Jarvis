package com.mio.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) wakeSignal++
        setContent {
            val vm: AssistantViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val motion = rememberEffectiveMotion(settings.animation)
            MioTheme(
                mode = themeModeOf(settings.theme),
                accentIntensity = settings.accentIntensity,
                motion = motion,
            ) {
                MioNav(vm = vm, wakeSignal = wakeSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) wakeSignal++
    }

    companion object {
        const val EXTRA_WAKE = "com.mio.ai.extra.WAKE"
    }
}
