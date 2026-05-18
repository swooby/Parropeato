package com.swooby.parropeato

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartfoo.android.core.logging.FooLog
import com.swooby.parropeato.common.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Arc geometry constants
// ─────────────────────────────────────────────────────────────────────────────

// Layout: (360° - 60° top gap - 2×40° junction gaps) / 3 arcs = 73.33° per arc.
// Top gap (-120° to -60°) for the time display. Junction gaps give each pair of
// endpoint icons their own half of the gap so they don't overlap one another or the arc.
// Order: Volume (right) → Speed (bottom) → Pitch (left). All at the outer arc radius.
//
//   -120° ── top gap (60°) ── -60°
//          Volume: -60° → 13.33°
//   13.33° ── junction gap (40°) ── 53.33°   [vol-min @ 26°, speed-max @ 40°]
//          Speed: 53.33° → 126.67°
//  126.67° ── junction gap (40°) ── 166.67°  [speed-min @ 140°, pitch-min @ 154°]
//          Pitch: 166.67° → 240°
//   240° ── top gap continues ── 300° (-60°)  [pitch-max @ -112°, vol-max @ -68°]
// Top max icons sit 8° from their arc endpoints, moving them farther out from the
// time/settings target at 12 o'clock. Junction icons sit 13° from their endpoints,
// so each 40° junction gap leaves 14° between the paired icons.

private const val VOLUME_ARC_START_ANGLE_DEGREES = -60f
private const val VOLUME_ARC_SWEEP_DEGREES = 73.33f
private const val VOLUME_ICON_MAX_ANGLE_DEGREES = -68f  // in top gap, 8° before arc start
private const val VOLUME_ICON_MIN_ANGLE_DEGREES = 26f   // first half of junction gap 1

private const val VOICE_SPEED_ARC_START_ANGLE_DEGREES = 53.33f
private const val VOICE_SPEED_ARC_SWEEP_DEGREES = 73.33f
private const val VOICE_SPEED_ARC_EXTRA_INSET_DP = 0f   // same outer radius as volume/pitch
private const val VOICE_SPEED_ICON_MAX_ANGLE_DEGREES = 40f   // second half of junction gap 1
private const val VOICE_SPEED_ICON_MIN_ANGLE_DEGREES = 140f  // first half of junction gap 2

private const val VOICE_PITCH_ARC_START_ANGLE_DEGREES = 166.67f
private const val VOICE_PITCH_ARC_SWEEP_DEGREES = 73.33f
private const val VOICE_PITCH_ICON_MIN_ANGLE_DEGREES = 154f   // second half of junction gap 2
private const val VOICE_PITCH_ICON_MAX_ANGLE_DEGREES = -112f  // in top gap, 8° after arc end (248° = -112°)

// Reference scene diameter used to derive the control scale factor on larger screens.
private val WearReferenceSceneSize = 213.dp

// ─────────────────────────────────────────────────────────────────────────────
// Root composable (called from BaseMainActivity.setupUI)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level watch-face content composable. Hosts the arc controls, push-to-talk button,
 * greeting text, border ring, and the platform/settings overlays.
 *
 * Extracted from [BaseMainActivity] so the UI layer lives in its own file.
 */
@Composable
internal fun WatchFaceScreen(
    viewModel: ParropeatoViewModel,
    logTag: String,
    sceneScale: Float,
    controlsScale: Float,
    borderOutset: Boolean,
    platformOverlay: @Composable (onSettingsClick: () -> Unit) -> Unit,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeInteraction: (ArcSliderInteraction) -> Unit,
    onVoiceSpeedChange: (Float) -> Unit,
    onVoiceSpeedInteraction: (ArcSliderInteraction) -> Unit,
    onVoicePitchChange: (Float) -> Unit,
    onVoicePitchInteraction: (ArcSliderInteraction) -> Unit,
    greetingBottomInsetDp: Float,
    settingsOverlay: @Composable () -> Unit,
) {
    val greetingScrollState = rememberScrollState()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(viewModel.accentColor),
            background = Color.Black,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (viewModel.state == ParropeatoViewModel.State.Initializing) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
            }

            val sceneSize = (if (maxWidth < maxHeight) maxWidth else maxHeight) * sceneScale
            val controlsSize = sceneSize * controlsScale
            val controlScale = (controlsSize / WearReferenceSceneSize).coerceIn(1f, 1.4f)
            val borderScale = (sceneSize / WearReferenceSceneSize).coerceIn(1f, 1.4f)

            Box(
                modifier = Modifier.size(sceneSize),
                contentAlignment = Alignment.Center,
            ) {
                WatchFaceBorder(
                    modifier = Modifier.fillMaxSize(),
                    scale = borderScale,
                    outset = borderOutset,
                )
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    // Speed arc at bottom-center; no shift needed — arc angles do the positioning.
                    // PTT pushed down so its outer ring sits 8dp inside the speed arc radius.
                    val arcOuterR = controlsSize / 2 - (8.dp + 5.dp) * controlScale
                    val pttRadius = 36.5.dp * controlScale
                    val pttDownShift = arcOuterR - 16.dp - pttRadius
                    VoiceSpeedArcControl(
                        modifier = Modifier.size(controlsSize),
                        voiceSpeed = viewModel.voiceSpeed,
                        scale = controlScale,
                        cuteIcons = viewModel.cuteIcons,
                        onVoiceSpeedChange = onVoiceSpeedChange,
                        onVoiceSpeedInteraction = onVoiceSpeedInteraction,
                    )
                    PushToTalkButton(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = pttDownShift),
                        isListening = viewModel.state == ParropeatoViewModel.State.Listening,
                        scale = controlScale,
                        cuteIcons = viewModel.cuteIcons,
                        logTag = logTag,
                        onPushToTalkPressed = onPushToTalkPressed,
                        onPushToTalkReleased = onPushToTalkReleased,
                    )
                    val sceneRadius = sceneSize / 2
                    // Greeting spans from near the circle top down to just above the PTT top.
                    val greetingBottomY = pttDownShift - pttRadius - 2.dp
                    val greetingTopY = -(sceneRadius - 18.dp - greetingBottomInsetDp.dp)
                    val greetingHeight = (greetingBottomY - greetingTopY).coerceAtLeast(20.dp)
                    val greetingCenterY = (greetingTopY + greetingBottomY) / 2
                    // Width constrained by the chord at greetingTopY (narrowest point of the
                    // upper arc), so text never overflows the circle at any scroll position.
                    val greetingWidthFraction = with(LocalDensity.current) {
                        val rPx = sceneRadius.toPx()
                        val yPx = greetingTopY.toPx()
                        (sqrt(maxOf(0f, rPx * rPx - yPx * yPx)) / rPx * 0.92f).coerceIn(0.3f, 1f)
                    }
                    Greeting(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(greetingWidthFraction)
                            .height(greetingHeight)
                            .offset(y = greetingCenterY),
                        text = viewModel.text,
                        scrollState = greetingScrollState,
                    )
                    // Volume and pitch arcs drawn last so they render above the greeting text.
                    // Their pointerInteropFilter returns false for non-arc touches, so scroll
                    // gestures in the greeting region pass through correctly.
                    VolumeArcControl(
                        modifier = Modifier.size(controlsSize),
                        volumePercent = viewModel.volumePercent,
                        scale = controlScale,
                        cuteIcons = viewModel.cuteIcons,
                        onVolumeChange = onVolumeChange,
                        onVolumeInteraction = onVolumeInteraction,
                    )
                    VoicePitchArcControl(
                        modifier = Modifier.size(controlsSize),
                        voicePitch = viewModel.voicePitch,
                        scale = controlScale,
                        cuteIcons = viewModel.cuteIcons,
                        onVoicePitchChange = onVoicePitchChange,
                        onVoicePitchInteraction = onVoicePitchInteraction,
                    )
                }
                platformOverlay { viewModel.showSettings = true }
            }
        }

        settingsOverlay()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Watch face border ring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WatchFaceBorder(
    scale: Float,
    outset: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = (12.dp * scale).toPx()
        val radius = if (outset) {
            size.minDimension / 2f + strokeWidth / 2f
        } else {
            size.minDimension / 2f - strokeWidth / 2f
        }
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF1E1E1E),
                    Color(0xFF5F5F5F),
                    Color(0xFF2B2B2B),
                    Color(0xFF0F0F0F),
                    Color(0xFF6A6A6A),
                    Color(0xFF1E1E1E),
                ),
                center = center,
            ),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = radius - strokeWidth / 2.7f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = radius + strokeWidth / 2.8f,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Arc hit-testing helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns how far [normAngle] is clockwise from [startAngleDegrees], in [0°, 360°).
 */
private fun arcAngularOffset(normAngle: Float, startAngleDegrees: Float): Float {
    val normStart = ((startAngleDegrees % 360f) + 360f) % 360f
    return ((normAngle - normStart + 360f) % 360f)
}

/** Which arc the touch point lands on. */
private enum class ActiveArc { NONE, VOLUME, SPEED, PITCH }

internal enum class ArcSliderInteraction { ARC_TAP, DRAG, ENDPOINT_MAX, ENDPOINT_MIN }

/**
 * Identifies which arc curve [x],[y] belongs to.
 *
 * Requires BOTH: radial proximity to the arc's drawn radius AND angular position within the
 * sweep. Volume and Pitch sit at outerR; Speed sits at innerR (inset by [speedExtraInsetF]).
 * The overlap zones near arc endpoints are resolved by whichever arc the touch is radially
 * closer to.
 */
private fun identifyTouchedArc(
    x: Float, y: Float, size: IntSize,
    strokeWidthF: Float, radiusInsetF: Float, speedExtraInsetF: Float,
): ActiveArc {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = x - cx
    val dy = y - cy
    val r = sqrt(dx * dx + dy * dy)
    val rawAngle = atan2(dy, dx) * 180f / PI.toFloat()
    val normAngle = ((rawAngle % 360f) + 360f) % 360f
    val outerR = size.width / 2f - strokeWidthF - radiusInsetF
    val innerR = outerR - speedExtraInsetF
    val hitBand = strokeWidthF * 2f
    val nearOuter = abs(r - outerR) <= hitBand
    val nearInner = abs(r - innerR) <= hitBand
    val inVolumeAngle =
        arcAngularOffset(normAngle, VOLUME_ARC_START_ANGLE_DEGREES) <= VOLUME_ARC_SWEEP_DEGREES
    val inSpeedAngle =
        arcAngularOffset(normAngle, VOICE_SPEED_ARC_START_ANGLE_DEGREES) <= VOICE_SPEED_ARC_SWEEP_DEGREES
    val inPitchAngle =
        arcAngularOffset(normAngle, VOICE_PITCH_ARC_START_ANGLE_DEGREES) <= VOICE_PITCH_ARC_SWEEP_DEGREES
    val hitVolume = nearOuter && inVolumeAngle
    val hitSpeed = nearInner && inSpeedAngle
    val hitPitch = nearOuter && inPitchAngle
    return when {
        !hitVolume && !hitSpeed && !hitPitch -> ActiveArc.NONE
        hitVolume && hitSpeed ->
            if (abs(r - outerR) <= abs(r - innerR)) ActiveArc.VOLUME else ActiveArc.SPEED
        hitSpeed && hitPitch ->
            if (abs(r - innerR) <= abs(r - outerR)) ActiveArc.SPEED else ActiveArc.PITCH
        hitVolume -> ActiveArc.VOLUME
        hitSpeed -> ActiveArc.SPEED
        else -> ActiveArc.PITCH
    }
}

/** Returns true if [x],[y] is within [iconSizeF] pixels of the icon at ([angleDeg], [radius]). */
private fun hitsIcon(
    x: Float, y: Float, size: IntSize,
    angleDeg: Float, radius: Float, iconSizeF: Float,
): Boolean {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rad = Math.toRadians(angleDeg.toDouble())
    val icx = cx + cos(rad).toFloat() * radius
    val icy = cy + sin(rad).toFloat() * radius
    val dx = x - icx
    val dy = y - icy
    return sqrt(dx * dx + dy * dy) <= iconSizeF
}

// ─────────────────────────────────────────────────────────────────────────────
// Arc controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VolumeArcControl(
    volumePercent: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVolumeInteraction: (ArcSliderInteraction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val isTracking = remember { mutableStateOf(false) }
    val activeInteraction = remember { mutableStateOf<ArcSliderInteraction?>(null) }

    val boundedVolume = volumePercent.coerceIn(0f, 1f)
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val speedExtraInset = VOICE_SPEED_ARC_EXTRA_INSET_DP.dp * scale

    val iconSizeF = with(density) { iconSize.toPx() }
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthF = with(density) { strokeWidth.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetF = with(density) { radiusInset.toPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }
    val speedExtraInsetF = with(density) { speedExtraInset.toPx() }

    val maxIconRes = if (cuteIcons) R.drawable.volume_max_elephant_24px else R.drawable.volume_up
    val minIconRes = if (cuteIcons) R.drawable.volume_min_ladybug_24px else R.drawable.volume_down

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val size = layoutSize.value
                        if (size.width <= 0 || size.height <= 0) return@pointerInteropFilter false
                        val outerR = size.width / 2f - strokeWidthF - radiusInsetF
                        if (hitsIcon(event.x, event.y, size, VOLUME_ICON_MAX_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            activeInteraction.value = null
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVolumeChange(1f)
                            onVolumeInteraction(ArcSliderInteraction.ENDPOINT_MAX)
                            return@pointerInteropFilter true
                        }
                        if (hitsIcon(event.x, event.y, size, VOLUME_ICON_MIN_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            activeInteraction.value = null
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVolumeChange(0f)
                            onVolumeInteraction(ArcSliderInteraction.ENDPOINT_MIN)
                            return@pointerInteropFilter true
                        }
                        if (identifyTouchedArc(
                                event.x, event.y, size, strokeWidthF, radiusInsetF, speedExtraInsetF
                            ) != ActiveArc.VOLUME
                        ) return@pointerInteropFilter false
                        isTracking.value = true
                        activeInteraction.value = ArcSliderInteraction.ARC_TAP
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onVolumeChange(
                            arcPercentFromPosition(
                                event.x, event.y, size,
                                VOLUME_ARC_START_ANGLE_DEGREES, VOLUME_ARC_SWEEP_DEGREES,
                                reverse = true,
                            )
                        )
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTracking.value) return@pointerInteropFilter false
                        activeInteraction.value = ArcSliderInteraction.DRAG
                        onVolumeChange(
                            arcPercentFromPosition(
                                event.x, event.y, layoutSize.value,
                                VOLUME_ARC_START_ANGLE_DEGREES, VOLUME_ARC_SWEEP_DEGREES,
                                reverse = true,
                            )
                        )
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTracking = isTracking.value
                        if (wasTracking) {
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                activeInteraction.value?.let(onVolumeInteraction)
                            }
                            activeInteraction.value = null
                            isTracking.value = false
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        wasTracking
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val swPx = strokeWidth.toPx()
            val outerR = size.width / 2f - swPx - radiusInset.toPx()
            drawArcThumb(
                track, primary, outerR, swPx,
                VOLUME_ARC_START_ANGLE_DEGREES, VOLUME_ARC_SWEEP_DEGREES,
                1f - boundedVolume, scale,
            )
        }
        EdgeControlIcon(
            modifier = Modifier.offset {
                arcIconOffset(layoutSize.value, VOLUME_ICON_MAX_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx)
            },
            icon = ImageVector.vectorResource(maxIconRes),
            contentDescription = stringResource(R.string.cd_volume_max),
            size = iconSize,
        )
        EdgeControlIcon(
            modifier = Modifier.offset {
                arcIconOffset(layoutSize.value, VOLUME_ICON_MIN_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx)
            },
            icon = ImageVector.vectorResource(minIconRes),
            contentDescription = stringResource(R.string.cd_volume_min),
            size = iconSize,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VoiceSpeedArcControl(
    voiceSpeed: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVoiceSpeedChange: (Float) -> Unit,
    onVoiceSpeedInteraction: (ArcSliderInteraction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val isTracking = remember { mutableStateOf(false) }
    val activeInteraction = remember { mutableStateOf<ArcSliderInteraction?>(null) }

    val speedPercent =
        (VOICE_SPEED_MAX - voiceSpeed.coerceIn(VOICE_SPEED_MIN, VOICE_SPEED_MAX)) /
            (VOICE_SPEED_MAX - VOICE_SPEED_MIN)
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val speedExtraInset = VOICE_SPEED_ARC_EXTRA_INSET_DP.dp * scale

    val iconSizeF = with(density) { iconSize.toPx() }
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthF = with(density) { strokeWidth.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetF = with(density) { radiusInset.toPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }
    val speedExtraInsetF = with(density) { speedExtraInset.toPx() }
    val speedExtraInsetPx = with(density) { speedExtraInset.roundToPx() }

    val maxIconRes = if (cuteIcons) R.drawable.speed_max_rabbit_24px else R.drawable.speed_24px
    val minIconRes = if (cuteIcons) R.drawable.speed_min_turtle_24px else R.drawable.speed_2_24px

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val size = layoutSize.value
                        if (size.width <= 0 || size.height <= 0) return@pointerInteropFilter false
                        val innerR = size.width / 2f - strokeWidthF - radiusInsetF - speedExtraInsetF
                        if (hitsIcon(event.x, event.y, size, VOICE_SPEED_ICON_MAX_ANGLE_DEGREES, innerR, iconSizeF)) {
                            isTracking.value = true
                            activeInteraction.value = null
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoiceSpeedChange(VOICE_SPEED_MAX)
                            onVoiceSpeedInteraction(ArcSliderInteraction.ENDPOINT_MAX)
                            return@pointerInteropFilter true
                        }
                        if (hitsIcon(event.x, event.y, size, VOICE_SPEED_ICON_MIN_ANGLE_DEGREES, innerR, iconSizeF)) {
                            isTracking.value = true
                            activeInteraction.value = null
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoiceSpeedChange(VOICE_SPEED_MIN)
                            onVoiceSpeedInteraction(ArcSliderInteraction.ENDPOINT_MIN)
                            return@pointerInteropFilter true
                        }
                        if (identifyTouchedArc(
                                event.x, event.y, size, strokeWidthF, radiusInsetF, speedExtraInsetF
                            ) != ActiveArc.SPEED
                        ) return@pointerInteropFilter false
                        isTracking.value = true
                        activeInteraction.value = ArcSliderInteraction.ARC_TAP
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val pct = arcPercentFromPosition(
                            event.x, event.y, size,
                            VOICE_SPEED_ARC_START_ANGLE_DEGREES, VOICE_SPEED_ARC_SWEEP_DEGREES,
                        )
                        onVoiceSpeedChange(VOICE_SPEED_MAX - (VOICE_SPEED_MAX - VOICE_SPEED_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTracking.value) return@pointerInteropFilter false
                        activeInteraction.value = ArcSliderInteraction.DRAG
                        val pct = arcPercentFromPosition(
                            event.x, event.y, layoutSize.value,
                            VOICE_SPEED_ARC_START_ANGLE_DEGREES, VOICE_SPEED_ARC_SWEEP_DEGREES,
                        )
                        onVoiceSpeedChange(VOICE_SPEED_MAX - (VOICE_SPEED_MAX - VOICE_SPEED_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTracking = isTracking.value
                        if (wasTracking) {
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                activeInteraction.value?.let(onVoiceSpeedInteraction)
                            }
                            activeInteraction.value = null
                            isTracking.value = false
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        wasTracking
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val swPx = strokeWidth.toPx()
            val outerR = size.width / 2f - swPx - radiusInset.toPx()
            val innerR = outerR - speedExtraInset.toPx()
            drawArcThumb(
                track, primary, innerR, swPx,
                VOICE_SPEED_ARC_START_ANGLE_DEGREES, VOICE_SPEED_ARC_SWEEP_DEGREES,
                speedPercent, scale,
            )
        }
        EdgeControlIcon(
            modifier = Modifier.offset {
                arcIconOffset(layoutSize.value, VOICE_SPEED_ICON_MAX_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx + speedExtraInsetPx)
            },
            icon = ImageVector.vectorResource(maxIconRes),
            contentDescription = stringResource(R.string.cd_voice_speed_max),
            size = iconSize,
        )
        EdgeControlIcon(
            modifier = Modifier.offset {
                arcIconOffset(layoutSize.value, VOICE_SPEED_ICON_MIN_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx + speedExtraInsetPx)
            },
            icon = ImageVector.vectorResource(minIconRes),
            contentDescription = stringResource(R.string.cd_voice_speed_min),
            size = iconSize,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VoicePitchArcControl(
    voicePitch: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVoicePitchChange: (Float) -> Unit,
    onVoicePitchInteraction: (ArcSliderInteraction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val isTracking = remember { mutableStateOf(false) }
    val activeInteraction = remember { mutableStateOf<ArcSliderInteraction?>(null) }

    val pitchPercent =
        (voicePitch.coerceIn(VOICE_PITCH_MIN, VOICE_PITCH_MAX) - VOICE_PITCH_MIN) /
            (VOICE_PITCH_MAX - VOICE_PITCH_MIN)
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val speedExtraInset = VOICE_SPEED_ARC_EXTRA_INSET_DP.dp * scale

    val iconSizeF = with(density) { iconSize.toPx() }
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthF = with(density) { strokeWidth.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetF = with(density) { radiusInset.toPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }
    val speedExtraInsetF = with(density) { speedExtraInset.toPx() }

    val maxIconRes = if (cuteIcons) R.drawable.pitch_max_mouse_24px else R.drawable.music_clef_treble
    val minIconRes = if (cuteIcons) R.drawable.pitch_min_whale_24px else R.drawable.music_clef_bass

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val size = layoutSize.value
                        if (size.width <= 0 || size.height <= 0) return@pointerInteropFilter false
                        val outerR = size.width / 2f - strokeWidthF - radiusInsetF
                        if (hitsIcon(event.x, event.y, size, VOICE_PITCH_ICON_MAX_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            activeInteraction.value = null
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoicePitchChange(VOICE_PITCH_MAX)
                            onVoicePitchInteraction(ArcSliderInteraction.ENDPOINT_MAX)
                            return@pointerInteropFilter true
                        }
                        if (hitsIcon(event.x, event.y, size, VOICE_PITCH_ICON_MIN_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            activeInteraction.value = null
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoicePitchChange(VOICE_PITCH_MIN)
                            onVoicePitchInteraction(ArcSliderInteraction.ENDPOINT_MIN)
                            return@pointerInteropFilter true
                        }
                        if (identifyTouchedArc(
                                event.x, event.y, size, strokeWidthF, radiusInsetF, speedExtraInsetF
                            ) != ActiveArc.PITCH
                        ) return@pointerInteropFilter false
                        isTracking.value = true
                        activeInteraction.value = ArcSliderInteraction.ARC_TAP
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val pct = arcPercentFromPosition(
                            event.x, event.y, size,
                            VOICE_PITCH_ARC_START_ANGLE_DEGREES, VOICE_PITCH_ARC_SWEEP_DEGREES,
                        )
                        onVoicePitchChange(VOICE_PITCH_MIN + (VOICE_PITCH_MAX - VOICE_PITCH_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTracking.value) return@pointerInteropFilter false
                        activeInteraction.value = ArcSliderInteraction.DRAG
                        val pct = arcPercentFromPosition(
                            event.x, event.y, layoutSize.value,
                            VOICE_PITCH_ARC_START_ANGLE_DEGREES, VOICE_PITCH_ARC_SWEEP_DEGREES,
                        )
                        onVoicePitchChange(VOICE_PITCH_MIN + (VOICE_PITCH_MAX - VOICE_PITCH_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTracking = isTracking.value
                        if (wasTracking) {
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                activeInteraction.value?.let(onVoicePitchInteraction)
                            }
                            activeInteraction.value = null
                            isTracking.value = false
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        wasTracking
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val swPx = strokeWidth.toPx()
            val outerR = size.width / 2f - swPx - radiusInset.toPx()
            drawArcThumb(
                track, primary, outerR, swPx,
                VOICE_PITCH_ARC_START_ANGLE_DEGREES, VOICE_PITCH_ARC_SWEEP_DEGREES,
                pitchPercent, scale,
            )
        }
        EdgeControlIcon(
            modifier = Modifier.offset {
                arcIconOffset(layoutSize.value, VOICE_PITCH_ICON_MAX_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx)
            },
            icon = ImageVector.vectorResource(maxIconRes),
            contentDescription = stringResource(R.string.cd_voice_pitch_max),
            size = iconSize,
        )
        EdgeControlIcon(
            modifier = Modifier.offset {
                arcIconOffset(layoutSize.value, VOICE_PITCH_ICON_MIN_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx)
            },
            icon = ImageVector.vectorResource(minIconRes),
            contentDescription = stringResource(R.string.cd_voice_pitch_min),
            size = iconSize,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Push-to-talk button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PushToTalkButton(
    isListening: Boolean,
    scale: Float,
    cuteIcons: Boolean,
    logTag: String,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(73.dp * scale)
            .border(
                width = 3.dp * scale,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        FooLog.i(logTag, "PushToTalkButton ACTION_DOWN")
                        onPushToTalkPressed()
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        FooLog.i(logTag, "PushToTalkButton ${MotionEvent.actionToString(event.actionMasked)}")
                        onPushToTalkReleased()
                        true
                    }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(42.dp * scale),
            imageVector = if (cuteIcons) {
                ImageVector.vectorResource(R.drawable.mic_parrot_24px)
            } else {
                Icons.Filled.Mic
            },
            contentDescription = if (isListening) {
                stringResource(R.string.cd_listening)
            } else {
                stringResource(R.string.cd_hold_to_talk)
            },
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Greeting text area
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Greeting(
    text: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge
    val minFontSize = style.fontSize  // current default; text scrolls if it still overflows
    val maxFontSize = 24.sp
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier.verticalScrollbar(scrollState),
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Binary-search the largest font size where the full text fits within containerHeight
        // AND no individual word exceeds containerWidth (which would cause mid-word breaks).
        // Falls back to minFontSize (enabling scrolling) when the text is too long.
        val fontSize = remember(text, containerWidth, containerHeight, density) {
            with(density) {
                val widthPx = containerWidth.roundToPx()
                val heightPx = containerHeight.roundToPx()
                val longestWord = text.split(' ', '\n').maxByOrNull { it.length } ?: text
                var lo = minFontSize.value
                var hi = maxFontSize.value
                var best = lo
                repeat(10) {
                    val mid = (lo + hi) / 2f
                    val midSp = mid.sp
                    val heightResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = style.copy(fontSize = midSp),
                        constraints = Constraints(maxWidth = widthPx),
                    )
                    val wordResult = textMeasurer.measure(
                        text = AnnotatedString(longestWord),
                        style = style.copy(fontSize = midSp),
                        constraints = Constraints(),
                    )
                    if (heightResult.size.height <= heightPx && wordResult.size.width <= widthPx) {
                        best = mid
                        lo = mid
                    } else {
                        hi = mid
                    }
                }
                best.sp
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = containerHeight)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = primary,
                text = text,
                style = style.copy(fontSize = fontSize),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared arc-drawing helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a single arc track with a circular thumb at [percent] along the arc.
 * Shared by [VolumeArcControl], [VoiceSpeedArcControl], and [VoicePitchArcControl].
 */
private fun DrawScope.drawArcThumb(
    track: Color,
    primary: Color,
    arcRadius: Float,
    strokeWidthPx: Float,
    startAngle: Float,
    sweepAngle: Float,
    percent: Float,
    scale: Float,
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val ovalTl = Offset(c.x - arcRadius, c.y - arcRadius)
    val ovalSz = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2)
    drawArc(
        color = track,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = ovalTl,
        size = ovalSz,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
    )
    val thumbDeg = startAngle + sweepAngle * percent
    val thumbRad = Math.toRadians(thumbDeg.toDouble())
    val thumb = Offset(
        c.x + cos(thumbRad).toFloat() * arcRadius,
        c.y + sin(thumbRad).toFloat() * arcRadius,
    )
    drawCircle(color = primary.copy(alpha = 0.18f), radius = (17.dp * scale).toPx(), center = thumb)
    drawCircle(color = primary, radius = (9.dp * scale).toPx(), center = thumb)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared icon composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EdgeControlIcon(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        modifier = modifier.size(size),
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Arc math utilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the arc-fraction (0..1) for touch position [x],[y] on an arc defined by
 * [startAngleDegrees] + [sweepDegrees]. Optionally reversed (e.g. volume, which maps
 * top → max).
 */
private fun arcPercentFromPosition(
    x: Float,
    y: Float,
    size: IntSize,
    startAngleDegrees: Float,
    sweepDegrees: Float,
    reverse: Boolean = false,
): Float {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rawAngle = atan2(y - cy, x - cx) * 180f / PI.toFloat()
    // Normalize both to [0°, 360°) so ±180° wrap never causes sign-flip jumps.
    val normAngle = ((rawAngle % 360f) + 360f) % 360f
    val normStart = ((startAngleDegrees % 360f) + 360f) % 360f
    // Offset from arc start, wrapped into [0°, 360°).
    val offset = (((normAngle - normStart) % 360f) + 360f) % 360f
    // Within arc: use directly. Past arc end (<180° overshoot): snap to end.
    // Before arc start (≥180° wrap): snap to start.
    val clampedOffset = when {
        offset <= sweepDegrees -> offset
        offset < 180f -> sweepDegrees
        else -> 0f
    }
    val percent = clampedOffset / sweepDegrees
    return if (reverse) 1f - percent else percent
}

/** Returns the [IntOffset] for an arc edge icon at [angleDegrees]. */
private fun arcIconOffset(
    size: IntSize,
    angleDegrees: Float,
    iconSizePx: Int,
    strokeWidthPx: Int,
    radiusInsetPx: Int,
): IntOffset {
    val radius = size.width / 2f - strokeWidthPx - radiusInsetPx
    val angle = Math.toRadians(angleDegrees.toDouble())
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    return IntOffset(
        x = (centerX + cos(angle).toFloat() * radius - iconSizePx / 2f).roundToInt(),
        y = (centerY + sin(angle).toFloat() * radius - iconSizePx / 2f).roundToInt(),
    )
}
