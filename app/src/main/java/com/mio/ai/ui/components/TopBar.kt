package com.mio.ai.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

/**
 * Secondary-screen top bar: back + 24sp title + optional actions.
 */
@Composable
fun MioTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val mio = mioColors
    val dim = mioDimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dim.xs, vertical = dim.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = mio.textPrimary)
        }
        Text(
            title,
            style = MioTypography.displaySmall,
            color = mio.textPrimary,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
