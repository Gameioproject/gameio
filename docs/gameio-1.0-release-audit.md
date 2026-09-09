# Gameio 1.0.0 client audit

Goal: distribute a standalone Android client for the hosted Gameio service. Users sign in, choose platforms, download/play games, and sync their library and saves without configuring a server.

## Baseline

Client fork includes catalog-on-demand browsing, ranked/genre discovery, owned/available filters, console keyboard, account-based save sync, the PS5-style discovery home, inline trailers, and larger covers with optimized scrolling. Server fork provides a catalog compatibility API, platform-aware shelves, download-source resolution, and account/save endpoints. Keep those contracts and account storage intact.

## Required work and evidence

- [ ] Fixed service sign-in: remove address editing/display from onboarding, success, account settings, account switching, errors and help. Keep credential, offline, retry and sync flows.
- [ ] Hide check-in, collections, Jellyfin and social entry points across drawer, quick settings, game menus, settings and companion navigation. Preserve saved data and internal implementations.
- [ ] Audit every settings section and retain only controls with meaningful consumption sites in this client.
- [ ] Home settings describe discovery, not upstream carousel/grid layouts. Preserve platform following, preview enable/delay/mute and useful artwork controls.
- [ ] Theme and Interface controls affect current screens consistently. Verify scale, colors, typography, guide and covers; hide unused controls.
- [ ] Remove RomM product/version/configuration wording from ordinary UI. Preserve open-source attribution and licenses.
- [ ] Version 1.0.0 with increasing Android version code, release signing, updater compatibility and release artifact.
- [ ] Debug and release builds, lint, unit tests, controller/touch device verification, fresh login and existing-account upgrade, library/download/sync smoke tests.
- [ ] Final requirement-by-requirement audit against source, test output and installed release APK.

## Settings inventory and decisions under review

- Account/sync: retain account management, sign out protections, library sync, platform selection, metadata and download preferences. Remove host editing, service version and unsupported server administration.
- Theme/interface: replace upstream home layout choices and no-op controls; keep working customization and navigation options.
- Gameplay: retain emulator/core/video/control/BIOS/driver/RetroAchievements settings; do not change emulator or save semantics.
- Storage: retain games, save caches, paths, cache limits and download controls. Hide media navigation while Jellyfin is unavailable.
- Audio: retain working local audio controls; review service-only music navigation without modifying playback internals.
- System: retain permissions, device settings, restart, credential-free settings backup, diagnostics and licenses. Audit update channel and release identity.

Current audit is in progress; unchecked items are not release-ready claims.

## Settings audit, September 9

| Settings area | Decision and consumption evidence |
| --- | --- |
| Theme: mode, accent, secondary, surface tint, accent footer | Retained. `ThemeViewModel` resolves preferences through `Theme.kt`; DiscoveryTheme respects the custom accent, and FooterHost consumes the shared footer style. |
| Theme: backdrop | Retained. `HomeScreen` renders `surfaceBackdrop` or game artwork according to the background setting. Backdrop settings also render in menus and the companion. Native toggle and nested controls inspected. |
| Theme: fonts | Retained. Imported font slots and scales reach Material typography. Discovery dimensions consume body/display scale; game titles, section titles and branding use display typography. |
| Interface: scale, language, compact guide, controller grip | Retained. Discovery dimensions consume LocalUiScale; common footer height consumes compactFooter; the root layout applies controller-grip insets. Language resources are shared by all screens. |
| Interface: Home | Only artwork/background and preview controls remain. Legacy layout selection, old home rows and compact-cover settings are hidden. Effective layout reads normalize legacy stored configurations to discovery without deleting stored JSON. |
| Interface: Library | Retained grid density, sorting, installed/favorite grouping and default filters. LibraryViewModel reads the preferences and LibraryScreen computes the matching controller grid. |
| Interface: Box Art | Retained shape, corners, borders, platform indicators and effects. Discovery uses the shared shape/aspect ratio and focused GameCard rendering; trailers return to the chosen shape after playback. Library and companion retain the shared style. |
| Navigation | Retained controller layout, swaps, haptics, menu wrapping and Select combinations. InputDispatcher and section handlers consume them. Menu wrapping applies to menus; the discovery feed retains bounded vertical navigation and paging. |
| Audio | Retained UI sounds, playlist/local music and capability-gated Gameio music. Existing playback and file locations are preserved; default music storage is displayed as Default. |
| Displays and LEDs | Retained dimmer and capability-dependent companion/LED settings. DualScreenManager and the existing hardware controllers remain the owners. No physical companion or LED hardware is available on the emulator. |
| Built-in emulator, A/V, controls, shaders, frames, cores | Retained. These configure the emulator runtime rather than the replaced launcher layout. Native root settings inspected; no core, native, save-format or RA compliance behavior changed for this audit. |
| Saves | Retained sync, secure saves, local cache and management actions. Existing account protection and pending-upload checks remain. Live save/state API and on-device file evidence recorded below. |
| RetroAchievements, BIOS, GPU drivers | Retained useful gameplay integrations. Login/proxy settings refer to the separate achievement service; BIOS and driver controls remain capability-aware. |
| Platforms and platform detail | Retained emulator/core, paths, display target, ordering, sync, download defaults and file management. These feed EmulatorResolver/GameLauncher and library sync. Stored platform and emulator identifiers are unchanged. |
| Storage and caches | Retained game/download/cache locations, limits, integrity checks, cache cleanup and guarded resets. Media navigation is hidden. Existing directories and downloaded data are preserved. |
| Gameio and accounts | Fixed-endpoint credential sign-in, account switching/removal, sign-out safeguards, sync and platform/media/download filters retained. Host entry, QR pairing and service-version display are absent from normal screens. |
| Steam | Retained as an explicitly experimental game integration; GameNative prerequisites are displayed. It is independent of the hidden Jellyfin/social integrations. |
| Permissions and Android settings | Retained required storage, usage, notification and overlay permissions; device settings remain available. |
| About and diagnostics | Gameio version, controller-accessible licenses, update channels, settings backup, restart and diagnostics retained. New backups use a Gameio filename while the importer accepts existing backup contents. Errors shown for update checks are user-facing, with technical detail retained internally. |
| Social, Jellyfin, check-in, collections | Hidden from normal drawer, quick settings, settings and game menus, including companion entry points. Collections/social/media repositories, tables and preferences remain intact. |

Native audit captured the root pages for Theme, Interface, Navigation, Audio, Displays, Built-in Emulator, RetroAchievements, BIOS, GPU Drivers, Platforms, Storage, Gameio, Steam, Permissions and About. The audit uses the existing emulator account and does not reset its data. Further final-build checks are still pending.

## Live service and local-data proof

- `GET /api/heartbeat`: hosted catalog API version `5.1.0-gameio`.
- `GET /api/saves` and `/api/states`: one existing test save and one state; identifiers, channel, emulator, timestamps, content hash and download paths match the client model. The game response includes its title and cover.
- `POST /api/sync/reconcile` twice with the current hashes: two operations, both `no_op`, on both runs. This is the catalog server's equivalent of save negotiation.
- The save at the database's `localSavePath` is 296,960 bytes; its SHA-256 matches both `lastUploadedHash` and Gameio's `content_hash`.
- The cached quick-state slot 100 exists at the path resolved through `AppPaths.stateCacheDir` and the database's relative cache path.
- The coupling sweep from upstream baseline `23e3bba3` flags settings, sync and API axes. The inventory and live proofs above address those axes; final APK verification is still required.

## Release identity and upgrade boundary

The production application ID is `com.playgameio.app`; published development previews used `com.playgameio.app.debug`. Production installs alongside the preview and requires sign-in. Keep the preview installed until any unsynced saves have been synced or backed up. Existing production installations retain their data when updated with the same release key.

Version name is `1.0.0`; base Android version code is 329 (universal APK code 3000329). The release must be signed with the persistent Gameio release key. The key and local keystore properties are deliberately excluded from Git. GitHub release builds require the four `GAMEIO_*` signing secrets documented by the workflow; the local signed APK can be built independently of those CI secrets.

## Additional verification

- Debug and release unit suites each passed 1,382 tests after the final slider/menu and translation pass. Debug, instrumentation and signed release APK assembly succeeded; lint completed with zero errors and 1,387 warnings.
- Android UI tests passed: nine tests for sign-in, keyboard, guide and cover transitions; four additional sign-in/keyboard/system-selection tests in light mode and four in portrait. The final APK passed seven targeted device tests covering both slider regressions, guide controls and cover transitions.
- Native UI scale was changed from 100% to 125%; the discovery header, tabs, hero covers, title and actions grew together. The custom accent was observed on the home focus ring and primary actions, then reset through the controller.
- Fresh installation of the optimized, signed production APK succeeded. Sign-in with the existing test account returned 38,302 games across 25 platforms, then advanced to local Android permissions and games-folder setup. No service URL was displayed.
- The first full lint run found 23 missing catalog/keyboard/wallpaper translations inherited from earlier fork changes. They are now translated in all seven supported locales, and the final lint pass confirmed zero errors.
- Fixed vertical swipes across the color slider changing its value; taps and horizontal drags still select color, and Default now resets it through touch as well as controller.
