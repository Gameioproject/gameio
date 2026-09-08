package com.nendo.argosy.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.screens.home.discovery.DiscoveryCoverTransition
import com.nendo.argosy.ui.screens.home.discovery.DiscoveryDimensions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DiscoveryTrailerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun coverExpandsOnlyWhenPlayingAndRestoresAfterFailure() {
        val playing = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.testTag("frame")) {
                DiscoveryCoverTransition(
                    playing = playing.value, focused = true, cardHeight = 150.dp,
                    maxWidth = 320.dp, dimensions = DiscoveryDimensions(1f),
                    onClick = { clicks++ }, onLongClick = {}, modifier = Modifier.testTag("tile"),
                    cover = { Box(it.background(Color.Blue).testTag("cover")) },
                    video = { Box(it.background(Color.Black).testTag("video")) }
                )
                }
            }
        }
        val portrait = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("cover", useUnmergedTree = true).assertExists()
        compose.runOnIdle { playing.value = true }
        compose.waitForIdle()
        val landscape = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
        assertTrue(landscape.width > portrait.width * 2)
        assertEquals(portrait.height, landscape.height, 1f)
        compose.onNodeWithTag("tile").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, clicks); playing.value = false }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
        assertEquals(portrait.width, restored.width, 1f)
    }

    @Test fun trailerFitsNarrowHandheldRail() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.testTag("frame")) {
                DiscoveryCoverTransition(
                    playing = true, focused = true, cardHeight = 158.dp,
                    maxWidth = 180.dp, dimensions = DiscoveryDimensions(1f),
                    onClick = {}, onLongClick = {}, modifier = Modifier.testTag("tile"),
                    cover = { Box(it) }, video = { Box(it.testTag("video")) }
                )
                }
            }
        }
        compose.onNodeWithTag("frame").assertWidthIsEqualTo(180.dp)
        compose.onNodeWithTag("video", useUnmergedTree = true).assertIsDisplayed()
        val tile = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag("video", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(tile.contains(video.topLeft) && tile.contains(video.bottomRight))
    }
}
