package com.nendo.argosy.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes catalog identity writes with folder discovery so both keep the same game row.
 */
@Singleton
class CatalogIdentityLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
