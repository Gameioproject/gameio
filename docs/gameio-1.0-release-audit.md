# Gameio 1.0.0 client audit

Goal: distribute a standalone Android client for the hosted Gameio service. Users sign in, choose platforms, download/play games, and sync their library and saves without configuring a server.

## Baseline

Client fork includes catalog-on-demand browsing, ranked/genre discovery, owned/available filters, console keyboard, account-based save sync, the PS5-style discovery home, inline trailers, and larger covers with optimized scrolling. Server fork provides a catalog compatibility API, platform-aware shelves, download-source resolution, and account/save endpoints. Keep those contracts and account storage intact.

## Required work and evidence

- [x] Fixed service sign-in: remove address editing/display from onboarding, success, account settings, account switching, errors and help. Keep credential, offline, retry and sync flows.
- [x] Hide check-in, collections, Jellyfin and social entry points across drawer, quick settings, game menus, settings and companion navigation. Preserve saved data and internal implementations.
- [x] Audit every settings section and retain only controls with meaningful consumption sites in this client.
- [x] Home settings describe discovery, not upstream carousel/grid layouts. Preserve platform following, preview enable/delay/mute and useful artwork controls.
- [x] Theme and Interface controls affect current screens consistently. Verify scale, colors, typography, guide and covers; hide unused controls.
- [x] Remove RomM product/version/configuration wording from ordinary UI. Preserve open-source attribution and licenses.
- [x] Version 1.0.0 with increasing Android version code, release signing, updater compatibility and release artifact.
- [x] Debug and release builds, lint, unit tests, controller/touch device verification, fresh login and existing-account upgrade, library/download/sync smoke tests.
- [x] Final requirement-by-requirement audit against source, test output and installed release APK.

## Settings inventory and decisions

- Account/sync: retain account management, sign out protections, library sync, platform selection, metadata and download preferences. Remove host editing, service version and unsupported server administration.
- Theme/interface: replace upstream home layout choices and no-op controls; keep working customization and navigation options.
- Gameplay: retain emulator/core/video/control/BIOS/driver/RetroAchievements settings; do not change emulator or save semantics.
- Storage: retain games, save caches, paths, cache limits and download controls. Hide media navigation while Jellyfin is unavailable.
- Audio: retain working local audio controls; review service-only music navigation without modifying playback internals.
- System: retain permissions, device settings, restart, credential-free settings backup, diagnostics and licenses. Audit update channel and release identity.

Client audit and APK publication are complete. Physical ARM gameplay, companion displays and LEDs remain hardware-validation limitations; emulator/native behavior was preserved.

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

Native audit captured the root pages for Theme, Interface, Navigation, Audio, Displays, Built-in Emulator, RetroAchievements, BIOS, GPU Drivers, Platforms, Storage, Gameio, Steam, Permissions and About. The audit uses the existing emulator account and does not reset its data. The signed release completed first-run setup through Start Playing and loaded the live catalog. Its drawer, Gameio settings, About, game details and full library were also inspected.

## Live service and local-data proof

- `GET /api/heartbeat`: hosted catalog API version `5.1.0-gameio`.
- `GET /api/saves` and `/api/states`: one existing test save and one state; identifiers, channel, emulator, timestamps, content hash and download paths match the client model. The game response includes its title and cover.
- `POST /api/sync/reconcile` twice with the current hashes: two operations, both `no_op`, on both runs. This is the catalog server's equivalent of save negotiation.
- The save at the database's `localSavePath` is 296,960 bytes; its SHA-256 matches both `lastUploadedHash` and Gameio's `content_hash`.
- The cached quick-state slot 100 exists at the path resolved through `AppPaths.stateCacheDir` and the database's relative cache path.
- The coupling sweep from upstream baseline `23e3bba3` flags settings, sync and API axes. The inventory, model checks, live proofs and signed-APK verification address those axes. Save-path resolver tests also cover identical discover/construct results; no save-format or emulator-path behavior changed in the hosted-settings audit.

## Release identity and upgrade boundary

The production application ID is `com.playgameio.app`; published development previews used `com.playgameio.app.debug`. Production installs alongside the preview and requires sign-in. Keep the preview installed until any unsynced saves have been synced or backed up. Existing production installations retain their data when updated with the same release key.

Version name is `1.0.0`; base Android version code is 331 (universal APK code 3000331). This increases the code over the initial published 1.0.0 candidate. The release must be signed with the persistent Gameio release key. The key and local keystore properties are deliberately excluded from Git. GitHub release builds require the four `GAMEIO_*` signing secrets documented by the workflow; the local signed APK can be built independently of those CI secrets.

## Additional verification

- Debug and release unit suites each passed 1,382 tests after the final slider/menu and translation pass. Debug, instrumentation and signed release APK assembly succeeded; lint completed with zero errors and 1,387 warnings.
- Android UI tests passed: nine tests for sign-in, keyboard, guide and cover transitions; four additional sign-in/keyboard/system-selection tests in light mode and four in portrait. The final APK passed seven targeted device tests covering both slider regressions, guide controls and cover transitions.
- Native UI scale was changed from 100% to 125%; the discovery header, tabs, hero covers, title and actions grew together. The custom accent was observed on the home focus ring and primary actions, then reset through the controller.
- Fresh installation of the optimized, signed production APK succeeded. Sign-in with the existing test account returned 38,302 games across 25 platforms, then advanced to local Android permissions and games-folder setup. No service URL was displayed.
- The first full lint run found 23 missing catalog/keyboard/wallpaper translations inherited from earlier fork changes. They are now translated in all seven supported locales, and the final lint pass confirmed zero errors.
- Fixed vertical swipes across the color slider changing its value; taps and horizontal drags still select color, and Default now resets it through touch as well as controller.

## Final runtime checks

- Source commit `e2e77d82`: signed production update installed over the first 1.0 candidate without clearing data. The existing account remained signed in and the downloaded game still displayed Play.
- A game with an available source downloaded through the app: Ocarina of Time, 33,554,432 bytes, N64 header `80371240`, SHA-256 `49acd3885f13b0730119b78fb970911cc8aba614fe383368015c21565983368d`. A title without a source correctly failed without crashing.
- The full library loaded 144 Nintendo 64 catalog entries; game details retained platform, artwork/description, rating and download/play controls. No collection action appeared in the audited drawer or game menus.
- All 11 targeted UI tests passed on the hosted-settings debug APK before the final label-only pass: four sign-in/keyboard/system-selection, two guide, three cover-transition and two slider tests. Earlier light/portrait variants passed four each.
- Debug upgrade retained the existing 296,960-byte save and quick state; its save hash still matched the local database and live Gameio account.
- Stability sweep hits are existing ViewModel/delegate lifecycle fields and controller callbacks, not mutable properties on composable state data classes. No native or save-format files changed for the hosted-client audit.
- Physical handheld gameplay, LEDs and companion-display hardware were unavailable. The x86 emulator verifies frontend flows; native ARM execution remains a hardware validation limitation.

## Published release

- Source: `af17dc1a2646ec910214d39a2dab1567f5dc291f`.
- APK: [Gameio 1.0.0 universal APK](https://playgameio.com/apk/gameio-b0dcd388.apk); 29,725,587 bytes.
- Version: 1.0.0, Android code 3000331. Production package `com.playgameio.app`, non-debuggable, signed by the persistent Gameio release certificate.
- SHA-256: `787149b455e41ef62a006934616791e210aac1039fe44ad910a75f54f8baf43d`. The public download was fetched in full and matched this digest and size.
- The universal package contains both ARM64 and ARMv7 emulator libraries. Both ABI-specific APKs were also built.
- Published at `2026-09-09T03:56:27Z` using the existing atomic APK publisher. The landing-page download buttons consume `/apk/latest.json`, which points at this immutable file.
- Final signed release assembly and lint passed; lint reported zero errors and 1,356 warnings. Debug and release suites each passed 1,382 tests before the final display-only label corrections. Those corrections received release compilation/lint, XML/plural validation, packaged-resource checks and native signed-APK verification.
- Production code 3000330 updated to 3000331 without losing the account, chosen ROM folder or downloaded game. Its file hash remained unchanged. The older preview uses a separate debug package and is preserved.
- The license overlay renders above the settings page. Touch open/close and controller open/back/scroll were verified in the signed release candidate, including scrolling to Emulator Cores. Upstream attribution remains visible.
- Drawer connection status uses accent-colored CloudDone and muted CloudOff icons. Both were visually inspected. Offline Home and full Library remained accessible with the account retained; reconnection needed no additional sign-in. Final Save Sync labels show Gameio 1.0.0 and Connected/Offline without the backend version. Test Wi-Fi and mobile-data settings were restored.
- The final source smell check is clean. The coupling sweep flags settings, saves and API work; the inventory and live proofs above cover those checks. A live ROM response confirmed correctly typed user properties, file/multiple-file flags and sibling-list fallback, with cover metadata populated.

## Final setup and display checks

- Code 3000330 completed the actual Start Playing action after selecting an external games folder and the default image cache. Storage showed `Internal/Download/GameioReleaseAudit`; the selection survived process restart. Ocarina of Time then downloaded into that folder with the expected 33,554,432-byte size, N64 header and SHA-256 listed above. Save Sync opened without the disabled-sync notice. An earlier test had interrupted the wizard before Start Playing; it was not evidence that folder preferences had been committed.
- Both debug and release build/unit/lint checks completed successfully for source `0c37bae4`: 1,382 tests per variant, no failures/errors/skips, debug lint had zero errors and 1,389 warnings, and release lint had zero errors and 1,355 warnings.
- Source `bfc9bab4` makes two final display-only corrections: onboarding points to Gameio settings in all eight resource locales, and This device in Save Sync shows the installed Gameio version plus connection status. Device registration identifiers, account data, server models and sync behavior are unchanged.

- The first final-label APK passed signed upgrade and online/offline device checks, which exposed a remaining server-setup instruction in the disconnected Save Sync screen. Source `af17dc1a` replaces that instruction with connection/sign-in guidance and names Gameio in the pending-save sign-out confirmation, in all eight locales. Plural counts and sign-out safety behavior remain unchanged.

## Completion

All requested client changes and website publication are complete. The final requirement review covers fixed-endpoint login, hidden unsupported entry points, settings consumption in discovery, preserved attribution, account/upgrade/download/save proofs, release identity and the verified public APK. Server source and save/emulator formats were not changed. CI signing secrets and physical-device execution are not claimed as configured or tested.
