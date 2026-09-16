package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.addon.AddonSource

val AddonSource.kindLabelRes: Int
    @StringRes get() = when (kind) {
        "internet_archive" -> R.string.game_sources_kind_archive
        "torrent" -> R.string.game_sources_kind_torrent
        else -> R.string.game_sources_kind_direct
    }
