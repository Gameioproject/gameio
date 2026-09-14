package com.nendo.argosy.data.addon

import org.junit.Assert.*
import org.junit.Test

class AddonDownloadServiceTest {
    @Test fun `archive names preserve plus spaces and encoded percent`() {
        val url = AddonDownloadService.archiveUrl(AddonLocator(item = "test-item", path = "SNES/Game + 100%.zip"))
        assertEquals("https://archive.org/download/test-item/SNES/Game%20+%20100%25.zip", url.toString())
    }

    @Test fun `resume accepts only content starting at requested offset`() {
        assertTrue(AddonDownloadService.validContentRange("bytes=123-", "bytes 123-999/1000"))
        assertFalse(AddonDownloadService.validContentRange("bytes=123-", "bytes 0-999/1000"))
        assertFalse(AddonDownloadService.validContentRange("bytes=123-", "bytes 123-1000/1000"))
        assertFalse(AddonDownloadService.validContentRange(null, "bytes 0-999/1000"))
        assertFalse(AddonDownloadService.validContentRange("bytes=123-", null))
    }
}
