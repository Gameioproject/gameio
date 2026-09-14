package com.nendo.argosy.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLibraryFilterTest {
    @Test fun `home cycles only catalog and on device`() {
        assertEquals(HomeLibraryFilter.LIBRARY, HomeLibraryFilter.ALL.next())
        assertEquals(HomeLibraryFilter.ALL, HomeLibraryFilter.LIBRARY.next())
    }

    @Test fun `legacy available preference opens all catalog metadata`() {
        assertEquals(HomeLibraryFilter.ALL, HomeLibraryFilter.fromOrdinal(1))
        assertEquals(HomeLibraryFilter.LIBRARY, HomeLibraryFilter.fromOrdinal(2))
        assertEquals(HomeLibraryFilter.ALL, HomeLibraryFilter.fromOrdinal(-1))
    }
}
