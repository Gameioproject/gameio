# Add-ons and community release work

## Required result

- Import-only JSON add-ons in setup and settings: import/reimport, enable/disable,
  remove, and useful validation errors. Local folder games always remain usable.
- Catalog metadata remains independent of sources. Opening an unavailable game
  looks up only its mapping shard, with bounded caching, cancellation and retry.
- Download selection resolves on the client, uses the existing persistent queue,
  and preserves catalog/platform, installed file and save identity.
- The current server source dataset becomes the controlled test add-on. No server
  credentials or temporary resolved links are exported. Disable legacy source
  indexing/proxy dependency deliberately only after the client path is proven.
- Setup optionally selects favorite games and uses them for recommendations.
- Game details include normal comments, likes, replies, Top/Newest, own edits and
  deletion, and report/block actions with server enforcement.
- Voluntary support is accessible from Settings and the website using a real
  hosted payment destination. Destination requested from the owner.
- Release verification includes migrations, API contracts, meaningful unit and
  integration tests, touch/controller and device behavior, installation upgrade,
  and a signed release build. Public APK updates only after verification.

## Current evidence

- Baseline committed and pushed in both repositories on feature/addons-community.
- Live source measurement: 10,403 mappings, 5,915 games, six platforms; 3,121
  Internet Archive mappings and 7,282 torrent mappings. All torrents have file
  index and info hash. Static SHA256 prefix shards avoid a large PS2 platform file.
- Implementation and regression builds are complete; final interaction checks,
  publication and live cutover remain with the release owner.

## Work ownership

- Root: add-on client, source/download integration, local discovery, UI integration
  and release audit.
- Add-on export agent: deterministic static export and contract tests.
- Comments agent: server comment API, auth, migrations and tests.
- Setup agent: optional favorites step and recommendation seeding.

## Verification ledger

Record commands and observed results here as features land. Pending tests are not
passing evidence. Retain the full requirements above when work spans turns.

### Historical schema fixture repair (14 September 2026)

The first emulator instrumentation run passed credentials (2), the new 180→181
download migration (1), and three registry checks including full-chain Room open.
The per-step registry check failed at 11→12. Audit traced this to commit
`b03791b6924ea7bc32fc07d5dd6c5eb24ab6f278`: it correctly bumped production to v13
and added the nullable-field rebuild in 12→13, but also overwrote `12.json` with
v13 nullability and identity. Both `packageName` and `displayName` were affected.

Restored only `app/schemas/com.nendo.argosy.data.local.ALauncherDatabase/12.json`
from that commit's parent, with its original identity
`a20df8dd059766c403240bbdd21090d2`. Production migration SQL and DB version are
unchanged. An in-memory check of the exact six 12→13 SQL statements preserves a
seeded configuration and accepts nullable values afterward. Existing installed
upgrade paths already include that rebuild; this fixes historical test fidelity.
Both debug APKs rebuilt successfully and were installed with `-r`, after a fresh
private app-data backup. The expanded ten-case instrumentation run passed nine:
credentials (2), private add-on storage (3), 180→181 download migration (1), and
three registry checks including full-chain Room open. Per-step validation now
passes 11→12 and advances to 33→34, where archived `34.json` expected
`state_cache.screenshotPath` even though SQL adds it in 34→35. The new 180→181
migration passes independently.

The bounded history audit inspected all edits to existing schema exports and
restored these further files byte-for-byte from the last correct historical
parent, keeping their original identity hashes:

| Fixture | Restore source | Original identity hash | Premature change removed |
| --- | --- | --- | --- |
| 34 | `3f33cd768f5bd82033dd0c57b7d0703f826a27e9^` | `33d8cc4f1853022f06e13e678e5cd77a` | Screenshot path belongs to 34→35 |
| 36 | `ff73788c^` (`beabc082586fa0e39815a605745a795981c6f790`) | `b1c1d3e34a36223eb2369bd66fa19701` | Integer platform IDs belong to 36→37 |
| 51 | `6e11609e^` (`e6b017911b63305ef52f1555a7b98e7baf472640`) | `8c35f1903ddc20d14aeef848737d3dfe` | `cheatsFetched` belongs to 51→52 |
| 154 | `9ffdfce1^` (`f6abe2c9df4ad8c286017592edde09c4dbdcb5b0`) | `1c644cb645bc2459b6a015e253c4ef04` | Save ownership reshape belongs to 154→156 |

The registry test now traverses every migration while validating canonical
exported checkpoints. Versions 155 and 157 were never exported. Version 86 has
a real same-version variant: commit `483ed6a0` added core options before
`0004b865` moved their creation to 86→87. Versions 60/61 exports omit two state
cache indices already created by 59→60; 62 has them. These intermediate versions
are grouped as 59→62, 85→87, 154→156 and 156→158 without fabricating schemas or
changing production SQL. A focused instrumentation case covers both 85 and the
exported 86 variant: session data at 87 and retained core options after Room opens
the current database. The final device run passes this case and every retained
checkpoint.

A host SQLite diagnosis ran the literal migration DDL against historical
exports, comparing tables, column affinity/nullability, declared defaults,
primary/foreign keys and explicit indices. The cumulative 6→181 structure is
consistent at the retained checkpoints. This is diagnostic evidence, not a
replacement for Android Room validation or data-copy tests. It also identified
baseline upgrade debt in independently created old fixtures: fresh v35 retains
an extra obsolete state-cache unique index; fresh v60/v61 lack two indices after
the chain. Those findings are separate from the new 180→181 migration and remain
outside this feature's production changes. No save-sync/native code was changed.

### Translation validation (14 September 2026)

The initial checker emitted 210 findings (30 per locale). Re-running it against
an isolated extraction of every `HEAD` resource produced the exact same report.
The English keys exist in non-`strings*` XML files, but the checker loaded only
`values/strings*.xml` while loading every translated XML file. Its English glob
now loads all `values/*.xml`; the existing parser still selects only strings and
plurals. `python3 scripts/ci/check-translations.py` passes all seven locales with
5,344 English keys, including placeholder and plural checks. New families have
exact key parity in every locale: Add-ons 53, comments 69, setup favorites 16,
Support 3 and game sources 9.

### Regression and contract evidence before final release gate

- Website frontend: `npm test` / `vitest run` passes all 475 tests in 27 files
  (`/tmp/gameio-frontend-tests.log`), including the six new hosted-support tests.
- Backend: the final isolated PostgreSQL run passes 244 tests covering comments,
  account isolation, support, catalog/assets, cutover gates and static export.
  See the server's `docs/addons-community-verification.md` for the exact test
  areas, MariaDB/PostgreSQL migration evidence and static-tool results.
- API model proof: real responses from the isolated running catalog pass through
  the compiled Android `RomMRom` Moshi adapter, retaining catalog identity,
  artwork, ratings, genres, screenshots, video IDs and viewer properties.
- Existing save contract proof: an isolated 39-byte save round-trips through list,
  detail and content requests with matching asset ID/hash/bytes. Both subsequent
  identical-hash reconciliation requests return `no_op`. This backend uses
  reconciliation, so the proof uses that advertised endpoint rather than claiming
  a legacy negotiation request was made.
- Earlier client JVM coverage exercised add-on parsing/validation/caching,
  credentials/resolver transport, download selection, local discovery/ownership,
  setup favorites, recommendations, comments and Settings/input routes. The
  combined 157-case run found eight HTTP-test dependency failures; aligning
  MockWebServer with the resolved OkHttp version fixed them, and the focused
  HTTP/comments rerun plus both debug APK builds passed
  (`/tmp/gameio-transport-tests.log`). The final unfiltered debug/release suites
  are recorded separately after completion; no aggregate passing count is
  inferred by adding overlapping runs.
- The controlled exporter corpus was accepted by the Android parser: all 256
  shards and 10,403 source mappings, using the private corpus environment path.
  Export files and account credentials are not implementation artifacts.
- Full changed-plus-untracked client scan: zero smell findings and zero coupling
  blocks. The sole coupling warning requests live RomM model proof, supplied
  above. Manual input/modal review found one missing new header-list key and
  filesystem work in the new owned-download directory path without an IO
boundary; both were corrected before the successful serial release gate. No new
  UI game/platform DAO access, domain Android/Compose imports, or mutable UI
  state fields were introduced.

### Final build and device verification

The initial parallel release gate hit a resource/OOM failure
(`/tmp/gameio-release-gate.log`). The serial retry succeeds in 8m56s
(`/tmp/gameio-release-gate-serial.log`): lint reports zero errors, all 1,534 debug
and 1,534 release JVM tests pass in 200 suites per variant, and no tests are
skipped. Debug, instrumentation and signed release APKs assemble successfully.
The universal release artifact is 30,011,883 bytes, version 1.1.0/code 3000334;
its signing certificate matches the existing Gameio release certificate. The
later resource-only cleanup removes unused hints and unnecessary Chinese plural
forms; its final build is tracked by the release owner.

The refreshed universal debug and instrumentation APKs were installed with `-r`
after a private full app-data backup. All eleven instrumentation cases pass in
3.274s: credentials (2), add-on store recovery/persistence (3), 180→181 download
migration (1), and registry/retained-data checks (5). The prior failures at
11→12 and 33→34 are resolved. The test-only databases were removed afterward;
the existing app database, account setup and imported add-on were preserved.

The owner's preceding device check imports the controlled JSON through the file
picker. Import makes zero shard requests; opening the chosen game fetches only
its `cf.json` shard. Selecting its Internet Archive source downloads the real
8,388,608-byte game into the per-game/source owned directory, and its SHA1/MD5
match the source metadata. This proves client-side on-demand lookup and direct
transfer in addition to the static corpus parser checks.

Upgrade verification confirms that both catalog rows and game 1's identity/local
path are unchanged, and the imported add-on private file is byte-identical. The
existing downloaded file remains 8,388,608 bytes with SHA1
`9bef1128717f958171a4afac3ed78ee2bb4e86ce` and MD5
`20b854b239203baf6c961b850a4a51a2`. The debug APK was then reinstalled with
`--abi arm64-v8a` for the owner's native-bridge gameplay checks, retaining app
data. Evidence and backups are private under `/tmp/gameio-final-device-velfqgvr`.
No app-data clear or emulator wipe occurred.

A real support destination remains an owner configuration item; absent
configuration keeps the support action hidden. Public APK publication and live
source cutover are separate owner-controlled release actions.
