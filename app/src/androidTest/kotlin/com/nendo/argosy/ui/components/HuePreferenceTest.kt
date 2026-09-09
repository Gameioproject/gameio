package com.nendo.argosy.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HuePreferenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val hue = mutableStateOf<Float?>(null)

    private fun showPreference() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.testTag("colorPreference")) {
                        HueSliderPreference("Accent", hue.value, false, { hue.value = it })
                    }
                    Spacer(Modifier.height(2000.dp))
                }
            }
        }
    }

    @Test fun verticalScrollingAcrossColorSliderKeepsColorUnchanged() {
        showPreference()
        compose.onNodeWithTag("colorPreference").performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.7f), Offset(width * 0.5f, -height.toFloat()), 300)
        }
        compose.runOnIdle { assertNull(hue.value) }
    }

    @Test fun horizontalDraggingChangesColorAndDefaultResetsItByTouch() {
        showPreference()
        compose.onNodeWithTag("colorPreference").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.7f), Offset(width * 0.8f, height * 0.7f), 300)
        }
        compose.runOnIdle { assertNotNull(hue.value); assertTrue(hue.value!! > 180f) }
        compose.onNodeWithText("Default").performClick()
        compose.runOnIdle { assertNull(hue.value) }
    }
}
