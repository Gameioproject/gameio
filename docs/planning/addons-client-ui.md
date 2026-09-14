# Add-on import and account UI

This implementation shares one AddonsScreen between first-run and Settings.
The setup step follows storage permissions because the existing FileBrowser uses
FileAccessLayer/Manage Storage. Continue is available without importing anything;
the games-folder step still follows it. No store, directory or auto-import exists.

Settings > Library > Add-ons routes through the existing Settings navigation event,
so touch and the Settings confirmation router open exactly the same screen.
Settings currently renders only through NavGraph on the primary activity; add-ons
follows this shared settings route instead of adding a separate companion editor.
On dual-screen hardware, it uses the existing primary-owned settings input path.

- File selection only shows JSON files. AddonRepository validates and saves its
  private copy; reimport supplies expectedId, checked before changing any record.
- Each record exposes enable/disable, reimport and remove. Remove uses the existing
  captured confirmation modal, explaining that catalog, files, favorites and saves
  remain. Corrupt records surface a repair-by-reimport warning.
- Account setup is optional. The client validates the user's premium Real-Debrid
  token through AddonDebridResolver and stores it via its encrypted credential
  store. Input is masked, supports the console keyboard and explicit clipboard
  paste, and is cleared on success or dismissal. Tokens never enter a URL or log.
- Base screen, file picker, confirmation and account dialog each capture all input
  via the existing modal stack. Controller focus stays in the ViewModel; touch uses
  the same commands. Every displayed list is lazy and resources cover all locales.
- AddonFailure.messageRes / toNotificationText centralize useful errors for screens
  and download notifications. A failed mutation leaves the original item available
  to retry; reload recovers hydration errors. Account validation can be cancelled.

Validation on 14 September 2026: production and Android-test Kotlin compilation,
debug APK assembly, and all 11 AddonsViewModel/input tests passed. The common
resource families pass key/placeholder parity across all eight locales, and the
added-line agentic-smell check is clean. Integrated device layout, JSON
import/reimport/remove, controller and cancellation checks remain release QA.
