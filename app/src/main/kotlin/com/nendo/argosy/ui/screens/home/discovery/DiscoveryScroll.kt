package com.nendo.argosy.ui.screens.home.discovery

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import com.nendo.argosy.ui.theme.generated.MotionTokens

suspend fun LazyListState.scrollDiscoverySelection(index: Int) {
    if (index == firstVisibleItemIndex && firstVisibleItemScrollOffset == 0) return
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible != null) {
        animateScrollBy(visible.offset.toFloat(), tween(MotionTokens.Tween.slideMs))
    } else {
        scrollToItem(index)
    }
}
