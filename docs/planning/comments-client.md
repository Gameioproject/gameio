# Game detail comments

The Comments action in game details opens a shared overlay attached to the game's
stable catalog IGDB ID. Normal and dual-screen detail layouts expose the same
action. A dual-screen action opens the primary-owned overlay through the existing
handoff route and restores the companion when it closes.

The conversation supports multiple comments per person, likes, one level of
replies, Top/Newest sorting, and explicit pagination. The empty state invites the
first comment. Reply loading, page loading and failed requests have separate
states, so a failed request keeps the current conversation and draft available.
Changing games cancels the previous request. Reopening after an account change
clears the old account's draft and viewer permissions before reloading.

Selecting a comment opens its actions. Authors can edit or delete their text;
other participants can report it or block its author. Options exposes the blocked
people list with Unblock. Admin accounts additionally see Reported comments with
the game title, original reported text, author, reporter and reason, plus Dismiss
and Remove actions. The server enforces ownership, roles, blocks and rate limits.

Bodies are plain text. A spoiler option hides the body until revealed. Text limits
count Unicode code points and preserve emoji when the console keyboard deletes
or truncates input. Writing supports the existing console keyboard and native
text input. Modal input capture consumes all controller actions; directional
navigation selects actions and comments, triggers scroll long content, and Back
returns through a nested panel before closing the conversation.

The repository uses the current Gameio server connection and its authenticated
API. Requests remain bound to that session, including account changes during a
request. Avatars use the known authenticated user-avatar route, with bounded
reads and a local initials fallback. Errors show localized messages and a
Retry-After duration when supplied, without displaying server response bodies.
All comments resources are present in the app's eight locales.

## Verification

On 14 September 2026, both detail layouts and the debug/test APKs compile. The
first 13 comments JVM cases pass, covering pagination, replies, permissions,
blocking, moderation, retained drafts, cancellation, controller capture, Unicode,
session-bound writes and authenticated avatars. The fourteenth case exercises
reopening a cached conversation after switching accounts; execution is pending
the final integrated test batch. Device touch/controller and dual-screen behavior
remain part of release QA.

The isolated emulator pass exercised posting and reading a spoiler comment,
revealing it, liking it, editing its body and spoiler setting, posting a one-level
reply, and deleting that own reply. Touch and controller navigation both reached
the composer and comment actions. Sorting, reply pagination, report/block menus,
admin moderation and dual-screen interaction still need the remaining device pass.
That pass stopped when the shared database engine stalled during the separately
authorized production migration; the API error retained the draft. A touch
capture gap on the overlay's blank background was identified and patched after
this pass, so the corrected overlay still requires device confirmation.

`CommentsQaSetupTest` is an opt-in instrumentation helper for that QA. It accepts
only the designated loopback test server, requires fresh app data or the same
verified isolated QA account, reads a private temporary credential fixture, uses
real client-token authentication and catalog sync, then deletes the credential
fixture. Ordinary instrumentation runs
skip it. It does not add a production login override or modify production users.
The helper passed on the emulator with the isolated QA player account. Its runner
is `com.playgameio.app.debug.test/androidx.test.runner.AndroidJUnitRunner` and its
class is
`com.nendo.argosy.ui.screens.gamedetail.comments.CommentsQaSetupTest`.
