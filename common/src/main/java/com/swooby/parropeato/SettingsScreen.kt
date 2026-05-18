package com.swooby.parropeato

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swooby.parropeato.common.R

/** Navigation route constants shared between mobile and wear settings screens. */
object SettingsNavRoutes {
    const val SETTINGS         = "settings"
    const val TTS_LANGUAGES    = "tts_languages"
    const val TTS_VARIANTS     = "tts_variants/{code}"
    const val SPEECH_LANGUAGES = "speech_languages"
    const val SPEECH_VARIANTS  = "speech_variants/{code}"

    fun ttsVariants(code: String)    = "tts_variants/$code"
    fun speechVariants(code: String) = "speech_variants/$code"
}

@Composable
fun SettingsGearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.cd_open_settings),
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize),
        )
    }
}
