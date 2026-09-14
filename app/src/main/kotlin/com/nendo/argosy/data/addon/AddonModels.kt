package com.nendo.argosy.data.addon

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AddonManifest(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val adapter: String,
    val lookup: AddonLookup,
    val allowedHosts: List<String>
)

@JsonClass(generateAdapter = true)
data class AddonLookup(val key: String, val partition: String, val urlTemplate: String)

@JsonClass(generateAdapter = true)
data class AddonShard(val schemaVersion: Int, val entries: Map<String, List<AddonSource>>)

@JsonClass(generateAdapter = true)
data class AddonSource(
    val id: String,
    val kind: String,
    val filename: String,
    val locator: AddonLocator,
    val size: Long? = null,
    val md5: String? = null,
    val sha1: String? = null,
    val region: String? = null
)

@JsonClass(generateAdapter = true)
data class AddonLocator(
    val item: String? = null,
    val path: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null
)

@JsonClass(generateAdapter = true)
data class InstalledAddon(val manifest: AddonManifest, val enabled: Boolean = true)

@JsonClass(generateAdapter = true)
data class AddonSourceMatch(
    val addonId: String,
    val addonName: String,
    val source: AddonSource,
    val manifestFingerprint: String,
    val catalogKey: String
)

data class AddonLookupResult(
    val sources: List<AddonSourceMatch> = emptyList(),
    val failures: List<AddonLookupFailure> = emptyList(),
    val enabledAddonCount: Int = 0
) {
    val isConfirmedMissing: Boolean
        get() = enabledAddonCount > 0 && sources.isEmpty() && failures.isEmpty()
}

data class AddonLookupFailure(val addonId: String, val reason: AddonFailure)

enum class AddonFailure {
    INVALID_MANIFEST, UNSUPPORTED_VERSION, INVALID_SOURCE, FILE_UNREADABLE,
    TOO_LARGE, UNTRUSTED_HOST, NETWORK, NOT_FOUND, STORAGE, REMOVED,
    ACCOUNT_REQUIRED, ACCOUNT_REJECTED, SOURCE_NOT_READY, NO_ADDONS, INTEGRITY
}

class AddonException(val reason: AddonFailure, cause: Throwable? = null) : Exception(reason.name, cause)
