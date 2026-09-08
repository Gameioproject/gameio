# Hideable button guide

On Home, press the right stick (R3) once to hide the guide. Press it again to restore
the guide. Select + D-pad Down remains available throughout the launcher. The shortcut follows the existing
Start/Select swap preference. Existing Select taps and Select+bumper shortcuts
keep their actions; repeated presses while held do not toggle the guide again.

The shared app-root footer controls Home, Library, setup, and modal guides.
Hiding it releases the footer spacer, with the existing spacer animation. The bottom-right chevron has been removed; no corner button remains when the guide is hidden. Visibility stays with
the launcher navigation and survives saved activity recreation. A fresh launch
starts with the guide visible.

The separate companion-display UI does not use FooterHost. Its independent
inline guides are deferred; this change targets the primary handheld frontend.
No server or gameplay behavior changes are required.

Verification: FooterGuideTest covers shortcut hide/restore, released content
space, repeat suppression, unrelated input, and saved-state restoration.

Live emulator checks confirmed Select+Down hides the guide without moving the
selected game or opening a menu; Library navigation retains the hidden state;
the shortcut restores it. Screenshots are in the workspace
`screenshots/button-guide/` directory.

Final verification: `assembleDebug` and `assembleDebugAndroidTest` succeeded;
`FooterGuideTest` passed both instrumentation tests. The diff smell checker and
`git diff --check` are clean.

Published APK: https://playgameio.com/apk/gameio-70e95b4f.apk

SHA-256: `4f30c67c3bb1549fdefb16750fd820990999d6c7822ae0e74377bded9bf5e217`

Home now labels R3 as Hide guide; it no longer starts Surprise Me. The small
Explore below prompt has been removed from the hero area.

R3 update validation: the two HomeGuideShortcutTest cases passed. Live Home
checks verified R3 hide, R3 restore, hold-without-repeated-toggle, unchanged game
selection, the Hide guide label, and removal of the Explore below button.

Latest APK: https://playgameio.com/apk/gameio-befb414c.apk

SHA-256: `1e060cfb4b37801bb25e6e7ad7598e9d802decf0f4f44e13900907cceb67a192`
