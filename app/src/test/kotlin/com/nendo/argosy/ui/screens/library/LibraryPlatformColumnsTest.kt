package com.nendo.argosy.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPlatformColumnsTest {
    @Test fun portraitUsesReadableSystemColumns() {
        assertEquals(3, libraryPlatformColumns(6, 480))
        assertEquals(2, libraryPlatformColumns(8, 360))
    }

    @Test fun handheldLandscapeRetainsPreferredDensity() {
        assertEquals(6, libraryPlatformColumns(6, 853))
        assertEquals(5, libraryPlatformColumns(5, 853))
    }

    @Test fun unmeasuredAndTinyWidthsStayValid() {
        assertEquals(6, libraryPlatformColumns(6, 0))
        assertEquals(1, libraryPlatformColumns(6, 100))
    }
}
