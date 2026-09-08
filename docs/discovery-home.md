# Discovery Home

The default carousel Home becomes a landscape console screen. Home includes every followed platform. A platform tab narrows all game rows. Picks, Favorites, and server shelves are content within Home rather than separate platform tabs.

The first area places game information and actions beside a Continue Playing carousel. With no recent games, it offers catalog games and labels them as exploration. A vertical list continues into Top Rated, Recommendations, and Favorites. Explore, Library, and Favorites select the lower content; Full Library opens the existing full catalog screen.

D-pad left/right moves within the focused control group or game row. Up/down moves between the header, platform tabs, hero actions, hero carousel, feed tabs, and lower game rows. LB/RB switches platforms. LT Search, RT library filter, R3 Surprise Me, Favorite, Details, and context menus retain their existing action paths. Touch selects a game, then activates it on a second tap. Vertical touch scrolling reveals discovery without switching platforms.

Focus belongs to the ViewModel. Loading, errors with retry, empty recent games, no history, no favorites, and an empty library filter must have explicit states. Query scopes respect followed platforms, active platform, hidden games, and local availability. Rows load bounded pages; stale requests must not replace a newer platform's content. Return from details restores focus and vertical position.

Use generated design tokens for dimensions and the blue, white, and midnight palette. Preserve cover loading/repair, download progress, launch/save flows, and existing grid/custom layouts. Dual-screen uses a separate companion process and shared session protocol; assess it explicitly rather than accidentally changing its input semantics.

Verification: build the Android client; run unit tests for focus/scoping; run relevant server tests; install on the existing emulator; verify touch and key navigation, platform filtering, feed selection, game details/back, library filter and offline/empty states. Capture landscape screenshots at 1280x720, inspect them against the approved composition, and iterate. The x86 emulator can validate the launcher UI but cannot execute the installed arm64 emulation cores.

Implementation notes:

- Catalog rankings retain server order across platforms. Available mode filters sources before pagination and chooses the platform with a source. Surprise Me uses the same platform/availability scope and preserves user metadata.
- Existing wallpapers, cover repair, download indicators, game menus, search, Full Library, launch, and save actions remain on their existing paths.
- Companion-screen parity is deferred: its separate state/input protocol does not carry discovery sections or vertical focus zones. This change targets the primary handheld/TV Home and preserves the companion's current navigation. A companion display was not available for testing.
- The new labels have translations for all seven existing locales. The translation checker still reports pre-existing missing settings/library labels; no Discovery labels are missing.
- Server catalog/play suite: 48 tests pass. Black, isort and Ruff pass for changed backend files. Frontend OpenAPI generation and typecheck pass. Mypy still reports 23 existing errors in imported modules (database utilities, auth decorators, user queries and debrid), none in the changed source files. Trunk is not installed in this workspace; its Python format/lint checks were run directly.

Final device verification (1280×720 landscape, Android API 35 emulator):

- Android debug APK builds; all five discovery focus/scoping tests pass.
- Verified D-pad vertical scrolling and horizontal selection, LB/RB platform filtering, touch game selection and Details, Details/Back focus restoration, Search, Full Library, Available/Library filters, and the Gameio menu entry.
- Added and removed a test favorite, confirming that Favorites updates and returns to its empty state. The favorite was restored to its original unselected state.
- Disabled emulator networking and verified cached platform browsing with an offline label. Restored Wi-Fi and mobile data afterward.
- Screenshot review caught clipped hero actions, unreadable platform badges, and cropped cover captions. Reset inherited line spacing, used blue badges and white selection, simplified tabs, and removed redundant hero captions. The selected game's title remains in the information panel; lower rows retain captions.
- Initial server connection now triggers a ranking refresh, replacing initial cached results with the scoped server ranking. Recent games are followed by discovery candidates when the hero has spare space; the selected game's metadata determines its action.
- Emulator verification covers the frontend. The bundled arm64 cores cannot execute on this x86 emulator; real gameplay and companion-display verification remain outside this device's capabilities.
