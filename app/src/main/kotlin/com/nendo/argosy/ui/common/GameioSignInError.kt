package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.SignInFailureReason
import com.nendo.argosy.data.remote.romm.SignInResult

@get:StringRes
val SignInResult.Failed.messageRes: Int
    get() = when (reason) {
        SignInFailureReason.INVALID_CREDENTIALS -> R.string.gameio_login_invalid_credentials
        SignInFailureReason.ACCOUNT_UNAVAILABLE -> R.string.gameio_login_account_unavailable
        SignInFailureReason.TOO_MANY_DEVICES -> R.string.gameio_login_too_many_devices
        SignInFailureReason.UNAVAILABLE -> R.string.gameio_login_unavailable
    }
