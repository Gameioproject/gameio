# Continuous genre discovery

Explore keeps the existing Continue playing area, Top rated, Recommended, and
Favorites. Below them, genre rows arrive as the viewport or controller focus
approaches the bottom. Each request loads up to three rows of twelve games.

The rotation includes Sports, Adventure, Racing, RPG, Platformers, Puzzle,
Fighting, Shooters, Arcade, Strategy, Simulation, Music and rhythm, Beat ’em ups,
Tactical, Card and board games, and Indie. Later passes fetch the next page of
each genre and mark the row as more to discover. Game ids are deduplicated
within each genre across pages; a multi-genre game can appear in both relevant
categories. Empty/exhausted genres are skipped. Once the available catalog is
exhausted, the feed offers an end message instead of looping old games.

The existing server `/api/roms` genre, platform, owned, offset, and limit query
supports the feed without a backend update. Ranking uses its stable rating /
rating-count / game-id ordering. Genre tokens were checked against the running
catalog. Local-only browsing uses paged Room queries that match every genre in
the metadata and exclude hidden games. The existing UI eligibility filter also
applies to every genre row.

The ViewModel-owned loader serializes append requests, retains completed rows
on failure, and cancels/reset requests when the platform or library scope
changes. A failed tail offers Retry by touch or controller confirm. Appending
rows retains existing row keys and controller positions. Only the visible rows
are composed. The separate companion Home UI remains outside this primary
handheld Explore layout.

Tests cover multiple rounds, duplicate ids, exhaustion, concurrent requests,
retry progress, cancelled requests, and the local genre query's paging,
secondary genres, platform, playability, and hidden-game behavior.

Live checks also exposed the library sync method returning the pre-insert
entity's zero id for newly discovered titles. Its return value now carries the
id read back from Room, so genre pages (and other callers of the same sync
method) receive usable game ids on their first fetch. Stored content and save
files are unchanged.

Validation so far: ten paging/navigation unit tests and the instrumented Room
query test passed. The launcher was cold-started successfully after placing the
new delegate before observer initialization. Live controller navigation reached
the second Sports/Adventure cycle; touch scrolling loaded further rows and the
SNES filter restricted the feed to SNES games.

Final build: `assembleDebug` and all ten targeted unit tests passed. A fresh
PSP genre fetch displayed full Sports and Adventure rows immediately after the
stored-id correction. Screenshots are saved under the workspace
`screenshots/explore-genres/` directory.

Published: https://playgameio.com/apk/gameio-1d66cb1b.apk

SHA-256: `723643b447b3705d70bae7a2a44d595851c46b6e9b8c35fa405fc0407f7dbb14`
