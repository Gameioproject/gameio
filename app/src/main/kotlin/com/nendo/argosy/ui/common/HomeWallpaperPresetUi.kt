package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.HomeWallpaperPreset

val HomeWallpaperPreset.labelRes: Int
    @StringRes get() = when (this) {
        HomeWallpaperPreset.NONE -> R.string.settings_home_screen_wallpaper_none
        HomeWallpaperPreset.PLAYSTATION -> R.string.settings_home_screen_wallpaper_playstation
        HomeWallpaperPreset.SWITCH -> R.string.settings_home_screen_wallpaper_switch
        HomeWallpaperPreset.XBOX -> R.string.settings_home_screen_wallpaper_xbox
    }
