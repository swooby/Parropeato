package com.swooby.ropeato.presentation

import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.smartfoo.android.core.logging.FooLog
import com.swooby.ropeato.BaseMainActivity
import com.swooby.ropeato.R
import com.swooby.ropeato.RopeatoViewModel
import com.swooby.ropeato.presentation.theme.AlfredTheme

class MainActivity : BaseMainActivity() {
    companion object {
        val TAG = FooLog.TAG(MainActivity::class)
    }

    override val textToSpeechVoiceSpeed: Float = 1.3f

    override fun setupUI() {
        setContent {
            WearApp(
                viewModel = viewModel,
                onPushToTalkPressed = ::onPushToTalkPressed,
                onPushToTalkReleased = ::onPushToTalkReleased,
                onVolumeDown = ::volumeDown,
                onVolumeUp = ::volumeUp,
            )
        }
    }
}

@Composable
fun WearApp(
    viewModel: RopeatoViewModel = RopeatoViewModel(),
    onPushToTalkPressed: () -> Unit = {},
    onPushToTalkReleased: () -> Unit = {},
    onVolumeDown: () -> Unit = {},
    onVolumeUp: () -> Unit = {},
) {
    AlfredTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            if (viewModel.state == RopeatoViewModel.State.Initializing) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                VolumeControls(
                    onVolumeDown = onVolumeDown,
                    onVolumeUp = onVolumeUp,
                )
                PushToTalkButton(
                    isListening = viewModel.state == RopeatoViewModel.State.Listening,
                    onPushToTalkPressed = onPushToTalkPressed,
                    onPushToTalkReleased = onPushToTalkReleased,
                )
                Greeting(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    text = viewModel.text,
                )
            }
        }
    }
}

@Composable
fun VolumeControls(
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.size(44.dp),
            onClick = onVolumeDown,
        ) {
            Icon(
                modifier = Modifier.size(34.dp),
                painter = painterResource(id = R.drawable.volume_down),
                contentDescription = "Volume down",
            )
        }
        Button(
            modifier = Modifier.size(44.dp),
            onClick = onVolumeUp,
        ) {
            Icon(
                modifier = Modifier.size(34.dp),
                painter = painterResource(id = R.drawable.volume_up),
                contentDescription = "Volume up",
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun PushToTalkButton(
    isListening: Boolean,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .border(
                width = 3.dp,
                color = MaterialTheme.colors.primary,
                shape = CircleShape,
            )
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        FooLog.i(MainActivity.TAG, "PushToTalkButton ACTION_DOWN")
                        onPushToTalkPressed()
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        FooLog.i(MainActivity.TAG, "PushToTalkButton ${MotionEvent.actionToString(event.actionMasked)}")
                        onPushToTalkReleased()
                        true
                    }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(42.dp),
            painter = painterResource(id = if (isListening) R.drawable.mic_fill else R.drawable.mic_line),
            contentDescription = if (isListening) "Listening" else "Hold to talk",
        )
    }
}

@Composable
fun Greeting(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = text,
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}
