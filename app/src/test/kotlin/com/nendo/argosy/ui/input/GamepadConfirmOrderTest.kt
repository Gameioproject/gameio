package com.nendo.argosy.ui.input

import android.view.KeyEvent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GamepadConfirmOrderTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun installDispatcher() = Dispatchers.setMain(dispatcher)

    @After fun restoreDispatcher() = Dispatchers.resetMain()

    private fun handler(): GamepadInputHandler {
        val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
            every { userPreferences } returns MutableStateFlow(mockk(relaxed = true))
        }
        return GamepadInputHandler(preferences)
    }

    private fun key(code: Int, action: Int, repeat: Int = 0) = mockk<KeyEvent>(relaxed = true) {
        every { keyCode } returns code
        every { this@mockk.action } returns action
        every { repeatCount } returns repeat
        every { device } returns null
        every { eventTime } returns 0L
        every { deviceId } returns 1
    }

    @Test fun `a direction pressed before the button is released still confirms first`() = runTest(dispatcher) {
        val handler = handler()
        val seen = mutableListOf<GamepadInput>()
        val collector = launch { handler.eventFlow().toList(seen) }
        runCurrent()

        handler.handleKeyEvent(key(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN))
        runCurrent()
        assertEquals(emptyList<GamepadInput>(), seen)

        handler.handleKeyEvent(key(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN))
        handler.handleKeyEvent(key(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_UP))
        runCurrent()

        assertEquals(listOf(GamepadEvent.Confirm, GamepadEvent.Left), seen.map { it.event })
        collector.cancel()
    }

    @Test fun `holding the button on its own still reports a long press`() = runTest(dispatcher) {
        val handler = handler()
        val seen = mutableListOf<GamepadInput>()
        val collector = launch { handler.eventFlow().toList(seen) }
        runCurrent()

        handler.handleKeyEvent(key(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN))
        advanceTimeBy(600)
        runCurrent()

        assertEquals(listOf(GamepadEvent.LongConfirm), seen.map { it.event })
        collector.cancel()
    }
}
