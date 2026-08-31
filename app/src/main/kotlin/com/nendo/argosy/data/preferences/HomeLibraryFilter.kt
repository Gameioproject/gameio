package com.nendo.argosy.data.preferences

/**
 * What a Home row is allowed to show. On a catalog-only server most games are metadata the device
 * neither owns nor can fetch, so the middle state exists to hide the ones no host offers.
 */
enum class HomeLibraryFilter {
    /** Everything the catalog knows about. */
    ALL,

    /** Only games a host offers a file for, whether or not this device has it. */
    DOWNLOADABLE,

    /** Only games whose content is on this device. */
    LIBRARY;

    fun next(): HomeLibraryFilter = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromOrdinal(value: Int): HomeLibraryFilter =
            entries.getOrElse(value) { ALL }
    }
}
