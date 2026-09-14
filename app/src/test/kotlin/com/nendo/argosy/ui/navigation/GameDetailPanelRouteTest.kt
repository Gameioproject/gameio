package com.nendo.argosy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDetailPanelRouteTest {
    @Test
    fun `existing game route stays byte compatible`() {
        assertEquals("game/42", Screen.GameDetail.createRoute(42))
    }

    @Test
    fun `only supported panels may be handed off`() {
        assertEquals("game/42?panel=comments", Screen.GameDetail.createRoute(42, "comments"))
        assertEquals("game/42?panel=sources", Screen.GameDetail.createRoute(42, "sources"))
        assertEquals("game/42", Screen.GameDetail.createRoute(42, "sources&gameId=99"))
    }
}
