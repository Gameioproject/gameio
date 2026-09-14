# Favorite games during setup

Scope: an optional step after emulator/core setup, before the completion screen.
It uses existing catalog game IDs and the account favorite overlay. No new database
schema, private preference list, simulated play history, or source resolution.

- Suggestions and search fetch one metadata page at a time through the existing
  catalog synchronization service. Search is across the catalog, independent of
  add-ons or installed files. Queries are debounced and superseded requests cancel.
- Existing favorites are retained. Tapping a game or confirming its focused row
  writes the normal favorite overlay and queues the existing server sync.
- Continue and Skip remain available with zero choices and on network errors.
  Leaving cancels catalog work, but a pending favorite write finishes first.
- Loading, no results, offline retry, saving, and favorite-write failure are visible.
- Controller focus is held by a ViewModel-owned delegate. Search uses the existing
  console keyboard modal, which captures every input direction. Touch uses the same
  commands through clickableNoFocus. The lazy list scrolls to controller selection.
- First-run is currently hosted on the primary setup surface before companion Home
  is active. This step follows that same lifecycle; no separate companion wizard.
- The recommendation generator includes true favorites as explicit taste signals,
  while play-time boosts continue to use actual play history only.

Verification: delegate tests for preserved selection, actual writes, failures,
search cancellation, paging, skip and controller routing; recommendation tests for
favorite-only profiles; compile plus device input/layout checks with the root agent.

The repository's investigate skill and coupling map were absent from .claude after
search. This scoped plan follows AGENTS.md, code-quality, menu-patterns and
design-tokens instructions directly.

## Implementation verification

- `:app:compileDebugKotlin` passed on 14 September 2026.
- `FirstRunFavoritesDelegateTest`: 9 tests passed (persistence, prior favorites,
  write failure/retry, cancel/skip, request supersession, pagination, controller).
- `FavoriteRecommendationTest`: 4 tests passed, including a new account with
  favorites and no played games, and skipped optional platform following.
- `SetupFavoritesRepositoryTest`: 2 tests passed for stable catalog search IDs
  and invalidating the old recommendation cache before writing a true favorite.
- Android resource compilation passed for all existing translated locales.
- Integrated device setup/layout verification remains part of release QA.
