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
