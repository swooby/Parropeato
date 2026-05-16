package com.swooby.parropeato

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.White.copy(alpha = 0.8f),
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val viewportH = size.height
        val barH = (viewportH * viewportH / (viewportH + state.maxValue))
            .coerceAtLeast(20.dp.toPx())
        val barTop = (state.value.toFloat() / state.maxValue * (viewportH - barH))
            .coerceIn(0f, viewportH - barH)
        drawRect(
            color = color,
            topLeft = Offset(size.width - width.toPx(), barTop),
            size = Size(width.toPx(), barH),
        )
    }
}

fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.White.copy(alpha = 0.8f),
): Modifier = drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val totalCount = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalCount > visibleItems.size && visibleItems.isNotEmpty()) {
        val firstIndex = visibleItems.first().index
        val viewportH = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        val avgItemH = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
        val totalH = totalCount * avgItemH
        val barH = (viewportH * viewportH / totalH)
            .coerceAtLeast(20.dp.toPx())
            .coerceAtMost(viewportH)
        val barTop = (firstIndex * avgItemH / totalH * viewportH)
            .coerceIn(0f, viewportH - barH)
        drawRect(
            color = color,
            topLeft = Offset(size.width - width.toPx(), barTop),
            size = Size(width.toPx(), barH),
        )
    }
}
