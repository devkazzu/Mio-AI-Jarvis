package com.mio.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mio.ai.ui.components.GlassSurface
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.MioTopBar
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

private data class License(val name: String, val license: String, val url: String)

private val LICENSES = listOf(
    License("Kotlin", "Apache 2.0", "github.com/JetBrains/kotlin"),
    License("Jetpack Compose + Material 3", "Apache 2.0", "developer.android.com/jetpack/compose"),
    License("AndroidX Navigation", "Apache 2.0", "developer.android.com/jetpack/androidx"),
    License("AndroidX Lifecycle", "Apache 2.0", "developer.android.com/jetpack/androidx"),
    License("AndroidX DataStore", "Apache 2.0", "developer.android.com/jetpack/androidx"),
    License("Kotlin Coroutines", "Apache 2.0", "github.com/Kotlin/kotlinx.coroutines"),
    License("OkHttp", "Apache 2.0", "square.github.io/okhttp"),
    License("AndroidX Security Crypto", "Apache 2.0", "developer.android.com/jetpack/androidx"),
    License("UI/UX Pro Max skill", "MIT", "github.com/nextlevelbuilder/ui-ux-pro-max-skill"),
)

/** Open-source notices. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val mio = mioColors
    val dim = mioDimens
    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = dim.gutter),
        ) {
            MioTopBar(title = "Licenses", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dim.md),
            ) {
                items(LICENSES) { l ->
                    GlassSurface {
                        Column(Modifier.fillMaxWidth()) {
                            Text(l.name, style = MioTypography.bodyLarge, color = mio.textPrimary)
                            Spacer(Modifier.height(dim.xs))
                            Text(l.license, style = CaptionMono, color = mio.textSecondary)
                            Text(l.url, style = CaptionMono, color = mio.textMuted)
                        }
                    }
                }
                item { Spacer(Modifier.height(dim.xl)) }
            }
        }
    }
}
