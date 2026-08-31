package com.nendo.argosy.data.catalog

/**
 * How wide a search reaches. The catalog holds far more than the device has paged in, so a query
 * that only reads the local store answers a different question than one that asks the server.
 */
enum class SearchScope {
    /** Only what this device already holds. The default, and the only offline-safe option. */
    LOCAL,

    /** The server, limited to the platform being browsed. */
    PLATFORM,

    /** The server's whole catalog. */
    SERVER;

    fun next(): SearchScope = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromOrdinal(value: Int): SearchScope = entries.getOrElse(value) { LOCAL }
    }
}
