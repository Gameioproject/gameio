package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wire shapes of the catalog server's `POST /api/sync/reconcile`. One request carries the
 * device's whole inventory of saves and states; the answer is what to do about each unit.
 * The rule is hash-only and lives on the server; see its docs/SAVE_SYNC.md.
 */
@JsonClass(generateAdapter = true)
data class RomMReconcileItem(
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "kind") val kind: String,
    @Json(name = "emulator") val emulator: String,
    @Json(name = "channel") val channel: String,
    @Json(name = "slot") val slot: Int = 0,
    @Json(name = "has_local") val hasLocal: Boolean,
    @Json(name = "local_hash") val localHash: String? = null,
    @Json(name = "base_hash") val baseHash: String? = null,
    @Json(name = "local_changed") val localChanged: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMReconcilePayload(
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "present_roms") val presentRoms: List<Long>,
    @Json(name = "items") val items: List<RomMReconcileItem>
)

@JsonClass(generateAdapter = true)
data class RomMReconcileOperation(
    @Json(name = "action") val action: String,
    @Json(name = "reason") val reason: String = "",
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "kind") val kind: String,
    @Json(name = "emulator") val emulator: String? = null,
    @Json(name = "channel") val channel: String,
    @Json(name = "slot") val slot: Int = 0,
    @Json(name = "asset_id") val assetId: Long? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "server_hash") val serverHash: String? = null,
    @Json(name = "server_updated_at") val serverUpdatedAt: String? = null,
    @Json(name = "server_size") val serverSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class RomMReconcileResponse(
    @Json(name = "operations") val operations: List<RomMReconcileOperation>
)

const val RECONCILE_KIND_SAVE = "save"
const val RECONCILE_KIND_STATE = "state"
