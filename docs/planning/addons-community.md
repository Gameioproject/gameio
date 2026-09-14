# Add-ons and community implementation baseline

The accompanying `gameio-product-proposal-v2.docx` records the product proposal
and screen concepts discussed on 14 September 2026. It is a proposal, not a
record of implemented capabilities. Technical details must be checked against
the current repository before implementation.

## Agreed direction

- The first add-on experience is import only. There is no store or add-on browser.
- The existing Gameio catalog stays independent of add-ons.
- Local games remain usable alongside any imported add-on.
- Use the current server source links as the first controlled test dataset.
- Look up sources when a game is opened, then cache the result. Do not index
  every archive during setup or catalog scrolling.
- Keep the manifest small. Relevant static mappings or archive adapters are
  implementation candidates; measure the actual dataset before choosing.
- Comments belong inside game details with likes, replies and Top/Newest
  sorting. They are for ordinary social conversation, not structured reviews.
- Optional favorite-game selection and voluntary support remain in the plan.

The current UI work is the baseline. Add-on and comment implementation has not
started in this checkpoint. Preserve installed files, catalog identity and
save compatibility during the transition.
