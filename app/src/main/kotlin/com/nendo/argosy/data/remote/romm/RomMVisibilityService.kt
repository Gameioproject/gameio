package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMVisibility"
private const val VISIBILITY_CACHE_MS = 60_000L

/**
 * What the server withholds from the connected account.
 *
 * A restricted account gets a 404 for a hidden rom rather than a 403, so absence from a library
 * listing is indistinguishable from deletion by listing alone. `GET /api/permissions/me` is the
 * discriminator, and a server that cannot answer it leaves the client unable to prove a deletion
 * at all - which is [Unavailable], and must be treated as "keep everything".
 */
sealed class RomMVisibility {
    data class Known(
        val hiddenPlatformIds: Set<Long>,
        val hiddenRomIds: Set<Long>,
        val isAdmin: Boolean
    ) : RomMVisibility() {
        fun hides(rommId: Long, platformId: Long): Boolean =
            !isAdmin && (rommId in hiddenRomIds || platformId in hiddenPlatformIds)
    }

    data object Unavailable : RomMVisibility()
}

@Singleton
class RomMVisibilityService @Inject constructor() {

    private var cached: RomMVisibility.Known? = null
    private var cachedAt = 0L
    private var cachedFor: RomMApi? = null

    /**
     * [fetch], but an answer younger than [maxAgeMs] for the same client is reused. Catalog paging
     * needs the hidden sets on every page, and repeating the round trip per page put a full
     * network latency in front of each one.
     */
    suspend fun fetchCached(api: RomMApi?, maxAgeMs: Long = VISIBILITY_CACHE_MS): RomMVisibility {
        if (api == null) return RomMVisibility.Unavailable
        val now = android.os.SystemClock.elapsedRealtime()
        cached?.let { if (cachedFor === api && now - cachedAt < maxAgeMs) return it }
        val fresh = fetch(api)
        if (fresh is RomMVisibility.Known) {
            cached = fresh
            cachedAt = now
            cachedFor = api
        }
        return fresh
    }

    /**
     * Reads the caller's hidden sets once, for a whole sync pass. Any failure at all - endpoint
     * missing on an older server, auth refused, transport error - degrades to [RomMVisibility.Unavailable]
     * rather than an empty hidden set, because an empty set would read as "nothing is hidden" and
     * license the deletion this exists to prevent.
     */
    suspend fun fetch(api: RomMApi?): RomMVisibility {
        if (api == null) return RomMVisibility.Unavailable
        return try {
            val response = api.getMyPermissions()
            if (!response.isSuccessful) {
                Logger.info(TAG, "permissions/me unavailable (HTTP ${response.code()}); deletions will be withheld")
                return RomMVisibility.Unavailable
            }
            val body = response.body() ?: return RomMVisibility.Unavailable
            RomMVisibility.Known(
                hiddenPlatformIds = body.hidden.platforms.toSet(),
                hiddenRomIds = body.hidden.roms.toSet(),
                isAdmin = body.isAdmin
            )
        } catch (e: Exception) {
            Logger.info(TAG, "permissions/me failed (${e.message}); deletions will be withheld")
            RomMVisibility.Unavailable
        }
    }
}
