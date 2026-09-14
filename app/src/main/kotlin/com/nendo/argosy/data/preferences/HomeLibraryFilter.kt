package com.nendo.argosy.data.preferences

/**
 * Catalog browsing is independent of on-demand add-on availability.
 */
enum class HomeLibraryFilter {
    /** Everything the catalog knows about. */
    ALL,

    /**
     * Retained only to read preferences saved before add-ons.
     */
    DOWNLOADABLE,

    /** Only games whose content is on this device. */
    LIBRARY;

    fun next(): HomeLibraryFilter = if (this == LIBRARY) ALL else LIBRARY

    fun normalized(): HomeLibraryFilter = if (this == DOWNLOADABLE) ALL else this

    companion object {
        fun fromOrdinal(value: Int): HomeLibraryFilter =
            entries.getOrElse(value) { ALL }.normalized()
    }
}
