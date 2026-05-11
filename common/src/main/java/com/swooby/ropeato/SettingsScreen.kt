package com.swooby.ropeato

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swooby.ropeato.common.R

@Composable
fun SettingsGearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    Icon(
        imageVector = Icons.Filled.Settings,
        contentDescription = stringResource(R.string.cd_open_settings),
        tint = Color.White.copy(alpha = 0.55f),
        modifier = modifier
            .size(iconSize)
            .clickable(onClick = onClick),
    )
}
