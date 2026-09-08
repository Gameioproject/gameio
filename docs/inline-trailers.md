# Inline Home trailers

Discovery Home renders the selected game's trailer inside its cover tile, in both the hero carousel and discovery rows. The portrait cover stays visible during the existing preview delay and while YouTube loads. Only the player's PLAYING callback expands the tile to a 16:9 presentation and crossfades the cover over 500 ms. The row height stays fixed, and the expanded width is capped to the available rail width on narrow displays.

Moving focus, opening the game menu, leaving Home, or a playback error disposes the player and restores the cover. Each playback attempt carries a game ID, video ID and generation; stale ready/error callbacks cannot change a newer attempt, even when returning to the same game. Games without trailer metadata retain their cover. Failed attempts are retried after selecting the game again.

Home's header, background, discovery feed and guide stay visible during playback. R3 still toggles the guide. Touch taps and long presses remain game actions; the embedded player cannot take controller focus. The existing preview enabled, delay and mute preferences are reused; their settings labels now say Video previews. No server API or database migration is needed.

The Discovery component is used by the primary Home surface. Existing non-Discovery carousel backgrounds retain their presentation, with the shared player receiving the same stale-callback and lifecycle protections. Companion screens do not currently host Discovery's rails; no additional player is created there, avoiding duplicate playback/audio across displays.

Verification includes unit coverage for stale requests, revisiting the same game, errors after playback starts, and mute state; Compose coverage for expansion, restored geometry, touch input and narrow rails; and live YouTube playback on the Android emulator in the hero and discovery rows with controller navigation, R3 and touch Quick Actions.
