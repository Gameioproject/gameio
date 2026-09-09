package com.nendo.argosy.ui.screens.firstrun

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.components.ConsoleKeyboardOverlay
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.GamepadInput
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.theme.*
import com.nendo.argosy.ui.theme.generated.TypographyTokens
import com.nendo.argosy.util.PlatformFilterLogic
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UniformUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = InputDispatcher()
    private val light = InstrumentationRegistry.getArguments().getString("theme") == "light"

    @Before fun fullscreen() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = if (InstrumentationRegistry.getArguments().getString("aspect") == "portrait") {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        val portrait = InstrumentationRegistry.getArguments().getString("aspect") == "portrait"
        compose.waitUntil(5000) {
            (compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) == portrait
        }
    }

    @Composable private fun Surface(content: @Composable () -> Unit) {
        val launcher = LocalLauncherTheme.current.copy(isDarkTheme = !light)
        CompositionLocalProvider(
            LocalInputDispatcher provides dispatcher,
            LocalLauncherTheme provides launcher,
            LocalArgosyTheme provides argosyThemeTokens(isDark = !light)
        ) {
            MaterialTheme(
                colorScheme = if (light) createLightColorScheme() else createDarkColorScheme(),
                typography = TypographyTokens.Material3
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = compose.activity.resources.configuration
        val file = File(context.getExternalFilesDir(null), "uniform-ui/$name-${if(light) "light" else "dark"}-${config.screenWidthDp}x${config.screenHeightDp}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun loginFieldsAndActionsFit() {
        var connected = false
        compose.setContent {
            Surface {
                RommLoginStep(
                    username = "Player", password = "sample-password", isConnecting = false,
                    error = null, focusedIndex = 2, rommFocusField = null,
                    onUsernameChange = {}, onPasswordChange = {},
                    onConnect = { connected = true },
                    onClearFocusField = {}, keyboardField = null,
                    keyboardText = "", onKeyboardTextChange = {}, onKeyboardDismiss = {}
                )
            }
        }
        compose.onAllNodesWithText("Sign in").onLast().assertIsDisplayed().performClick()
        assertTrue(connected)
        compose.onNodeWithText("Change server").assertDoesNotExist()
        compose.onNodeWithText("playgameio.com", substring = true).assertDoesNotExist()
        capture("login")
    }

    @Test fun loginKeyboardKeepsFormVisible() {
        val text = mutableStateOf("secret")
        compose.setContent {
            Surface {
                RommLoginStep(
                    username = "Player", password = text.value, isConnecting = false,
                    error = null, focusedIndex = 2, rommFocusField = null,
                    onUsernameChange = {}, onPasswordChange = { text.value = it },
                    onConnect = {},
                    onClearFocusField = {}, keyboardField = 1,
                    keyboardText = text.value, onKeyboardTextChange = { text.value = it }, onKeyboardDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onAllNodesWithText("Sign in").onLast().assertIsDisplayed()
        compose.runOnIdle { dispatcher.dispatch(GamepadInput(GamepadEvent.Confirm)) }
        compose.runOnIdle { assertEquals("secret1", text.value) }
        capture("login-keyboard")
    }

    @Test fun keyboardMasksPasswordAndCapturesController() {
        val text = mutableStateOf("secret")
        val visible = mutableStateOf(true)
        compose.setContent {
            Surface {
                if (visible.value) ConsoleKeyboardOverlay(
                    query = text.value, onQueryChange = { text.value = it },
                    onDismiss = { visible.value = false }, isPassword = true
                )
            }
        }
        compose.onNodeWithText("secret").assertDoesNotExist()
        compose.onNodeWithText("••••••").assertIsDisplayed()
        compose.runOnIdle { dispatcher.dispatch(GamepadInput(GamepadEvent.Confirm)) }
        compose.runOnIdle { assertEquals("secret1", text.value) }
        compose.runOnIdle { dispatcher.dispatch(GamepadInput(GamepadEvent.ContextMenu)) }
        compose.runOnIdle { assertEquals("secret", text.value) }
        compose.onNodeWithText("q", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("secretq", text.value) }
        capture("keyboard")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertFalse(dispatcher.hasActiveModal()) }
    }

    @Test fun systemSelectionTogglesAndScrolls() {
        val platforms = mutableStateOf(listOf(
            platform(1, "snes", "Super Nintendo", 1420),
            platform(2, "n64", "Nintendo 64", 388),
            platform(3, "ngc", "GameCube", 653),
            platform(4, "gb", "Game Boy", 1031),
            platform(5, "gba", "Game Boy Advance", 1589),
            platform(6, "psx", "PlayStation", 2380),
            platform(7, "dc", "Dreamcast", 412),
            platform(8, "psp", "PlayStation Portable", 827)
        ))
        var continued = false
        compose.setContent {
            Surface {
                PlatformSelectStep(
                    platforms = platforms.value,
                    filterMode = PlatformFilterLogic.FilterMode.ALL,
                    searchQuery = "", focusedIndex = 0, buttonFocusIndex = 1,
                    headerFocused = false, headerIndex = 0, searchActive = false,
                    sortMenuOpen = false, sortMenuIndex = 0,
                    onToggle = { id -> platforms.value = platforms.value.map { if(it.id == id) it.copy(syncEnabled = !it.syncEnabled) else it } },
                    onToggleAll = {}, onSortModeChange = {}, onFilterModeChange = {},
                    onSearchQueryChange = {}, onOpenSearch = {}, onCloseSearch = {},
                    onOpenSortMenu = {}, onCloseSortMenu = {}, onContinue = { continued = true }
                )
            }
        }
        compose.onNodeWithText("Super Nintendo").performClick()
        compose.runOnIdle { assertFalse(platforms.value.first().syncEnabled) }
        capture("systems")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(platforms.value.lastIndex)
        compose.onNodeWithText("PlayStation Portable").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assertTrue(continued)
    }

    private fun platform(id: Long, slug: String, name: String, count: Int) = PlatformEntity(
        id = id, slug = slug, name = name, shortName = name, romExtensions = "zip", gameCount = count
    )
}
