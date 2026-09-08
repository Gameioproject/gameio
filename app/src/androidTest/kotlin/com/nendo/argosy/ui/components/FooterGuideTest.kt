package com.nendo.argosy.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.GamepadInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FooterGuideTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortcutReleasesSpaceAndRestoresGuideWithoutCornerArrow() {
        val controller = FooterHostController()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFooterHost provides controller) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).testTag("content-end"))
                            FooterSpacer()
                        }
                        FooterHints(listOf(InputButton.X to "Details"))
                        FooterHost(controller, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("content-end").fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithContentDescription("Hide button guide (Select + Down)").assertDoesNotExist()
        compose.runOnIdle { controller.handleInput(GamepadInput(GamepadEvent.ToggleGuide)) }
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.waitForIdle()
        val after = compose.onNodeWithTag("content-end").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Hidden guide must return space to content", after > before)
        compose.onNodeWithContentDescription("Show button guide (Select + Down)").assertDoesNotExist()
        compose.runOnIdle { controller.handleInput(GamepadInput(GamepadEvent.ToggleGuide)) }
        compose.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test fun shortcutTogglesOncePerPressAndSurvivesRestoration() {
        lateinit var controller: FooterHostController
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            controller = rememberSaveable(saver = FooterHostController.Saver) { FooterHostController() }
        }
        compose.runOnIdle {
            assertFalse(controller.handleInput(GamepadInput(GamepadEvent.Down)))
            assertTrue(controller.handleInput(GamepadInput(GamepadEvent.ToggleGuide)))
            assertTrue(controller.isHidden)
            controller.handleInput(GamepadInput(GamepadEvent.ToggleGuide, isRepeat = true))
            assertTrue(controller.isHidden)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertTrue(controller.isHidden)
            controller.handleInput(GamepadInput(GamepadEvent.ToggleGuide))
            assertFalse(controller.isHidden)
        }
    }
}
