# Save and state sync against the catalog server

The protocol and the reasoning live with the server: `gameio-server/docs/SAVE_SYNC.md`.
This note is the client-side map.

## Where it happens

- `data/sync/strategy/CatalogSyncStrategy.kt` builds the inventory (every downloaded
  game's save channels from `save_sync`, every cached state from `state_cache`), posts it
  to `POST /api/sync/reconcile`, hands the save operations to the existing
  `ReconcileEffectApplier` through a `ReconcilePlan`, and applies the state operations
  itself in `applyStateOperations` (queue an upload, download into the cache, or settle a
  conflict by whichever side is newer).
- `SyncCoordinator.reconcileAll(force)` runs the plan and then drains the queue at once.
  It is called on the connect edge, after every play session, when the Save Sync screen
  opens or scans, and by the six-hourly worker.
- Uploads (`SaveUploader`, `StateCacheManager.uploadStateToRomM`) send the `base_hash` of
  the version they built on; a 409 means another device moved the slot and the next
  reconcile decides.

## Identity rules the client must keep

- The autosave channel is one channel however a row spells it (`null` or `"autosave"`).
  Look rows up with `SaveSyncDao.getByGameEmulatorAndAutosave`, never by the raw string.
- `save_sync.lastUploadedHash` and `state_cache.lastUploadedHash` hold the server's
  SHA-256 of the last synced version. They change only on upload or download. Listings
  never refresh them on a catalog server.
- States upload under the same emulator label as saves (`EmulatorRegistry.toServerEmulator`)
  with explicit `channel` and `slot` query parameters; the server echoes `channel` and
  `state_slot` back, and the file name is only parsed for rows older than that.
- Quick-save slots are `100..109` and sync like any other slot.
- State bodies go up gzip-compressed (`application/gzip` part) and come down with
  `Content-Encoding: gzip`, which OkHttp undoes transparently. Save and state requests use
  the 300 s stall timeout, not the 60 s API timeout.
- Downloads made outside a launch only reach the state cache. `PreLaunchStateSyncUseCase`
  places the newest cached state of each slot into the live slot when the slot is empty, or
  when the cached row is synced, newer than the live file and holds different bytes. A live
  file newer than its cache row is unsynced play and stays.
