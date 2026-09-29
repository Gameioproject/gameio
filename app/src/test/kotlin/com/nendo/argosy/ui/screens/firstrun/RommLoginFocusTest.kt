package com.nendo.argosy.ui.screens.firstrun

import org.junit.Assert.assertEquals
import org.junit.Test

class RommLoginFocusTest {

    @Test
    fun `sign-in without Google keeps the original order`() {
        val focus = RommLoginFocus(signUpMode = false, googleAvailable = false)
        assertEquals(2, focus.connect)
        assertEquals(RommLoginFocus.NONE, focus.google)
        assertEquals(3, focus.toggle)
        assertEquals(4, focus.reset)
        assertEquals(4, focus.max)
    }

    @Test
    fun `sign-up without Google keeps the original order`() {
        val focus = RommLoginFocus(signUpMode = true, googleAvailable = false)
        assertEquals(3, focus.connect)
        assertEquals(4, focus.toggle)
        assertEquals(RommLoginFocus.NONE, focus.reset)
        assertEquals(4, focus.max)
    }

    @Test
    fun `Google sits between the main button and the mode switch`() {
        val signIn = RommLoginFocus(signUpMode = false, googleAvailable = true)
        assertEquals(listOf(2, 3, 4, 5), listOf(signIn.connect, signIn.google, signIn.toggle, signIn.reset))
        assertEquals(5, signIn.max)

        val signUp = RommLoginFocus(signUpMode = true, googleAvailable = true)
        assertEquals(listOf(3, 4, 5), listOf(signUp.connect, signUp.google, signUp.toggle))
        assertEquals(5, signUp.max)
    }
}
