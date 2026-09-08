package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.components.FooterHostController
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.GamepadInput
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class HomeGuideShortcutTest {
    private val actions = mockk<HomeInputActions>(relaxed = true)
    private val footer = FooterHostController()
    private val dispatcher = InputDispatcher()

    private fun subscribeHome() {
        every { actions.uiState } returns MutableStateFlow(HomeUiState())
        dispatcher.subscribeView(HomeInputHandler(
            actions = actions, isDefaultView = true, onGameSelect = {},
            onNavigateToDefault = {}, onDrawerToggle = {}, onToggleGuide = footer::toggle
        ))
    }

    @Test fun `right stick hides and restores guide without launching surprise me`() {
        subscribeHome()
        dispatcher.dispatch(GamepadInput(GamepadEvent.RightStickClick))
        assertTrue(footer.isHidden)
        dispatcher.dispatch(GamepadInput(GamepadEvent.RightStickClick, isRepeat = true))
        assertTrue(footer.isHidden)
        dispatcher.dispatch(GamepadInput(GamepadEvent.RightStickClick))
        assertFalse(footer.isHidden)
        verify(exactly = 0) { actions.surpriseMe() }
    }

    @Test fun `modal owns right stick until it is dismissed`() {
        subscribeHome()
        dispatcher.pushModal(object : InputHandler {
            override fun onRightStickClick() = InputResult.HANDLED
        })
        dispatcher.dispatch(GamepadInput(GamepadEvent.RightStickClick))
        assertFalse(footer.isHidden)
        dispatcher.popModal()
        dispatcher.dispatch(GamepadInput(GamepadEvent.RightStickClick))
        assertTrue(footer.isHidden)
        verify(exactly = 0) { actions.surpriseMe() }
    }
}
