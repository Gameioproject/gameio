package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.components.QuickSettingsItem
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionsContext
import com.nendo.argosy.ui.screens.gamedetail.buildMoreOptions
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsItem
import com.nendo.argosy.ui.screens.settings.sections.RomMItem
import com.nendo.argosy.ui.screens.settings.sections.buildRomMItems
import com.nendo.argosy.ui.screens.settings.sections.rommFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.rommItemAtFocusIndex
import org.junit.Assert.*
import org.junit.Test

class GameioHostedSettingsTest {
    @Test fun `hidden services have no settings or quick settings entry`() {
        assertFalse(MainSettingsItem.ALL.contains(MainSettingsItem.Jellyfin))
        assertFalse(MainSettingsItem.ALL.contains(MainSettingsItem.Social))
        assertFalse(QuickSettingsItem.ALL.contains(QuickSettingsItem.QuayPass))
    }

    @Test fun `signed in account offers sync and sign out without server reconfiguration`() {
        val items = buildRomMItems(isConnected = true, isSignedIntoRomM = true)
        assertFalse(items.contains(RomMItem.RomManager))
        listOf(RomMItem.Accounts, RomMItem.RomMSignOut, RomMItem.SyncSettings, RomMItem.SyncLibrary).forEach {
            assertEquals(it, rommItemAtFocusIndex(rommFocusIndexOf(it, items), items))
        }
        val signedOut = buildRomMItems(isConnected = false, isSignedIntoRomM = false)
        assertEquals(listOf(RomMItem.RomManager), signedOut.filter { it.isFocusable })
    }

    @Test fun `home settings keep previews and artwork without old layouts or media rails`() {
        assertFalse(HomeScreenItem.ALL.contains(HomeScreenItem.LayoutSelector))
        assertFalse(HomeScreenItem.ALL.contains(HomeScreenItem.LayoutPreview))
        assertFalse(HomeScreenItem.ALL.any { it is HomeScreenItem.LayoutField })
        assertTrue(HomeScreenItem.ALL.containsAll(listOf(HomeScreenItem.GameArtwork,
            HomeScreenItem.VideoWallpaper, HomeScreenItem.VideoDelay, HomeScreenItem.VideoMuted)))
        assertTrue(HomeScreenItem.VideoWallpaper.visibleWhen(DisplayState()))
        assertFalse(HomeScreenItem.VideoDelay.visibleWhen(DisplayState(videoWallpaperEnabled = false)))
        assertTrue(HomeScreenItem.VideoDelay.visibleWhen(DisplayState(videoWallpaperEnabled = true)))
    }

    @Test fun `game options preserve save and refresh actions when collections are hidden`() {
        val items = buildMoreOptions(MoreOptionsContext(isDownloaded = true, isRommGame = true,
            canManageSaves = true, canManageStates = true))
        assertFalse(items.contains(MoreOptionAction.AddToCollection))
        assertTrue(items.containsAll(listOf(MoreOptionAction.ManageSaves,
            MoreOptionAction.RefreshData, MoreOptionAction.Delete)))
        assertEquals(items.size, items.distinct().size)
    }
}
