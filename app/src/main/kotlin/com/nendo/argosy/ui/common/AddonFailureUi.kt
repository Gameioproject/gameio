package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.data.addon.AddonFailure

val AddonFailure.messageRes: Int
    @StringRes get() = when (this) {
        AddonFailure.NO_ADDONS -> R.string.addons_error_no_addons
        AddonFailure.INTEGRITY -> R.string.addons_error_integrity
        AddonFailure.INVALID_MANIFEST -> R.string.addons_error_manifest
        AddonFailure.UNSUPPORTED_VERSION -> R.string.addons_error_version
        AddonFailure.INVALID_SOURCE -> R.string.addons_error_source
        AddonFailure.FILE_UNREADABLE -> R.string.addons_error_file
        AddonFailure.TOO_LARGE -> R.string.addons_error_large
        AddonFailure.UNTRUSTED_HOST -> R.string.addons_error_host
        AddonFailure.NETWORK -> R.string.addons_error_network
        AddonFailure.NOT_FOUND -> R.string.addons_error_missing
        AddonFailure.STORAGE -> R.string.addons_error_storage
        AddonFailure.REMOVED -> R.string.addons_error_removed
        AddonFailure.ACCOUNT_REQUIRED -> R.string.addons_error_account_required
        AddonFailure.ACCOUNT_REJECTED -> R.string.addons_error_account_rejected
        AddonFailure.SOURCE_NOT_READY -> R.string.addons_error_not_ready
    }

fun AddonFailure.toNotificationText(): NotificationText = NotificationText.Res(messageRes)
