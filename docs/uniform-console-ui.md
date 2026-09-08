# Uniform console UI

The discovery Home, setup flow, virtual keyboard, and system browser now use the same Material surface and text tokens, including the container roles used by the drawer and dialogs. Home retains white primary controls; the shared accent is blue. User-selected accent colors and light mode still apply.

## Screens

- Account setup: a compact Gameio mark, restrained typography, a dark form panel, and the same white actions and blue focus outlines throughout setup. The large background watermark and yellow field outlines are removed.
- Virtual keyboard: white focused keycaps with blue edges, matching surfaces, a masked password preview, and touch controls for Caps and Done. Key sizes adapt to both width and available display height. Modal input captures the controller while open.
- System selection: recognizable bundled console art, game counts, white selected checkmarks, blue focus outlines, a scrolling list, and persistent filter and continue controls. The library's system grid uses the matching surfaces and focus treatment. Its column count is capped by available width so portrait layouts retain readable titles; both rendering and controller movement use the same count.
- Other shared surfaces inherit the updated background, surface, muted text, and default accent tokens. Account screens and setup controls were extracted from the large wizard file.

## Boundaries

Authentication, downloads, server APIs, platform selection persistence, and library navigation remain on their existing paths. No server change is needed. Setup remains on the primary display, following the existing wizard input guard. The shared palette also reaches the companion through its existing theme resolver; a new companion setup flow is outside this change. Physical dual-screen hardware verification is still required.

## Verification

`UniformUiTest` renders the production login, keyboard, and platform-selection composables on Android. It checks login action visibility, masked password input, controller typing/deletion, touch typing/dismissal, modal release, platform selection, scrolling, and the Continue action. Screenshot fixtures use sample account and platform data; they do not change the emulator's signed-in account.

Final verification on Android 15, x86_64 emulator:

- APK and instrumentation build succeeded.
- Four UI tests passed in each configuration: dark landscape (853 × 480 dp), light landscape, and dark portrait (480 × 853 dp), 12 successful runs.
- Three unit tests passed for the shared system-grid column calculation.
- Added-line house-rule checks, XML validation for new translations, and whitespace checks passed.
- Production Home, drawer, and library were also opened for visual review using the existing emulator profile.

APK: `apks/gameio-uniform-ui-debug.apk` in the workspace root.
SHA-256: `32bfef4ca9a86405944ab21465d7d956c4bd848b27ca76f98f4acfef292dbaa1`.

Published through the existing website download manifest as `https://playgameio.com/apk/gameio-f1a96c66.apk`.
