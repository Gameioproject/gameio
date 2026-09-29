package com.nendo.argosy.ui.screens.firstrun

/**
 * Where each control of the sign-in form sits in the index space the d-pad and the console
 * keyboard share, top to bottom. Fields come first; a control the form is not showing gets
 * [NONE].
 */
internal data class RommLoginFocus(val signUpMode: Boolean, val googleAvailable: Boolean) {
    val lastField: Int get() = if (signUpMode) 2 else 1
    val connect: Int get() = lastField + 1
    val google: Int get() = if (googleAvailable) connect + 1 else NONE
    val toggle: Int get() = maxOf(connect, google) + 1
    val reset: Int get() = if (signUpMode) NONE else toggle + 1
    val max: Int get() = maxOf(toggle, reset)

    companion object {
        const val NONE = -1
    }
}
