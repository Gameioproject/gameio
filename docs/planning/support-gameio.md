# Optional Support Gameio link

The server heartbeat advertises `FRONTEND.SUPPORT_URL` only when the owner has
configured a hosted support page. The client carries that value through its
existing connection capabilities and Settings server state. There is no client
provider default, purchase flow, prompt, or persistent preference.

- Validate the advertised URL as HTTPS without user information before exposing it.
- Add Support Gameio at the end of Settings > System. Hide it when the current
  server does not advertise a valid link; keep the controller focus range in sync
  when the connection changes.
- Touch and controller confirmation call the same ViewModel method and existing
  external-link event. A browser launch failure displays a localized notification.
- Reuse the current Settings list components, tokens, sound dispatch, primary
  navigation, and dual-screen Settings handoff. No separate companion screen.
- Verify heartbeat decoding, validation, missing-link behavior, controller routing,
  and focus recovery with focused tests; root performs integrated device QA.

The investigate skill and coupling map remain absent after the prior scoped
search. This follows AGENTS.md, code-quality, menu-patterns and design-tokens.

## Offline source mode

The adjacent heartbeat integration retains only `SYSTEM.CATALOG_ONLY` in
`CatalogServerModeRepository`, keyed by normalized server URL in the existing
device preference store. Connection attempts restore this mode before waiting
for the network; successful heartbeats replace it. Disconnect clears the active
selection, and an account rebind selects its configured server again. A different
server cannot inherit the previous server's mode. API, repository and connection
facades expose `usesAddonSources()` for source/download eligibility while offline.
`getCapabilities()` remains live-only, so cached mode enables no save-sync or other
network capability. The optional support URL is not persisted.

Verification on 14 September 2026: `ServerFrontendLinksTest` (5),
`SupportGameioSettingsTest` (3), `CatalogServerModeRepositoryTest` (4), and the
existing `RomMCapabilitiesTest` (12) all passed. Production and Android-test Kotlin
compiled, and both debug APKs assembled. Device UI verification remains release QA.
