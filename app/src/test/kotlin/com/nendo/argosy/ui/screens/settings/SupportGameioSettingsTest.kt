package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsItem
import com.nendo.argosy.ui.screens.settings.sections.mainSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.mainSettingsMaxFocusIndex
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportGameioSettingsTest {
    private val supportUrl = "https://support.example/gameio"

    @Test fun `no support row occupies a controller index without configured link`() {
        val last = mainSettingsMaxFocusIndex()
        assertEquals(MainSettingsItem.About, mainSettingsItemAtFocusIndex(last))
        assertNull(mainSettingsItemAtFocusIndex(last + 1))
        assertEquals(last + 1, mainSettingsMaxFocusIndex(supportUrl))
        assertEquals(MainSettingsItem.Support, mainSettingsItemAtFocusIndex(last + 1, supportUrl))
    }

    @Test fun `controller confirm executes same support command as touch`() {
        val state = SettingsUiState(
            focusedIndex = mainSettingsMaxFocusIndex(supportUrl),
            server = ServerState(supportUrl = supportUrl)
        )
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm._uiState } returns MutableStateFlow(state)
        assertEquals(InputResult.HANDLED, routeConfirm(vm))
        verify(exactly = 1) { vm.supportGameio() }
        verify(exactly = 0) { vm.navigateToSection(any()) }
    }

    @Test fun `hidden support index cannot trigger a donation action`() {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm._uiState } returns MutableStateFlow(SettingsUiState(focusedIndex = mainSettingsMaxFocusIndex() + 1))
        routeConfirm(vm)
        verify(exactly = 0) { vm.supportGameio() }
    }
}
