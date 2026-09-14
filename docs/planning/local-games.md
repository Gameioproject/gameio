# Local games and the shared catalog

Copy game files into the configured platform folder. Setup, startup, returning to the app, and the existing storage rescan actions discover them without an add-on or a per-game import form. The global download folder, custom platform folders, and existing platform filesystem aliases share the same root resolution as launching.

Discovery makes an unmatched file playable immediately as a local catalog entry. In the background it asks the metadata catalog for that title and platform, with a bounded result page and timeout. Only a unique normalized exact title match is attached automatically; an ambiguous title or unavailable server leaves the local entry usable. This does not index source archives or download the full catalog.

When a catalog match becomes available, the existing local row keeps its game ID, save ID, local path, selected edition, per-game settings, favorite/play history, and local file hashes. Metadata adds the catalog identity, art, and description. Subsequent catalog refreshes preserve that local state as well.

Before attaching a discovered path, both discovery paths check canonical ownership across game files, variants, discs, and playlists under the catalog identity lock. A filename already owned by another game, including one reached through a storage alias or inside an owned game directory, is not reassigned.

Known region, revision, disc, and language filename suffixes are ignored when comparing names. Meaningful title parentheses and sequel numbers remain. Another copied edition with the same unique title and platform becomes a game-file option on the existing entry; discovery never switches the active edition or replaces the current base file.

CUE, GDI, and M3U references prevent their component tracks/discs from becoming separate games. Existing variant/content category folders remain owned by their scanner. The GDI filename field follows [Flycast's upstream parser](https://github.com/flyinghead/flycast/blob/master/core/imgread/gdi.cpp). Supported playlist platforms come from the existing `M3uManager` registry.

Discovery does not delete files or declare unreadable storage absent. Availability cleanup remains with the existing `FileAccessLayer` and `StorageVolumeHealth` validation. Add-ons can be imported, disabled, or removed independently of these local files.

Add-on transfers use a separate owned directory under each platform root: `.gameio-addons/<game ID>/<source snapshot SHA-256>/content`. The snapshot is the selected source stored with the queue entry. Equal filenames in different games or editions never share a partial, archive, extracted file, or staged destination. An owner marker must match before existing bytes are reused; an unmarked occupied directory is left untouched. Failed add-on rows remain available after restart so retry can preserve the same staging work. A changed source receives a different directory even when its filename and size match.

These directories stay hidden from automatic folder discovery while downloading. Completed games launch through their recorded absolute local path; existing copied files, catalog IDs, variants, and save paths keep their original locations. Removing an add-on does not remove downloaded games. No new storage permission or Storage Access Framework flow is introduced.

This iteration deliberately avoids fuzzy identification, save rekeying, database schema changes, and any source availability requirement for browsing the metadata catalog.
