# Gameio

**Stremio for games.** Explore the whole library of every system you love, and let add-ons find the files.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0+-green.svg)](https://developer.android.com/about/versions/oreo)

Gameio is a controller-first Android launcher for handhelds. Instead of starting from the files you already have, it starts from the games: every system you follow shows its full catalog, with covers, descriptions, ratings and trailers, whether or not a game is on your device.

Like Stremio does for movies, Gameio doesn't host any games. **Add-ons** supply the download links. When you open a game, each add-on you've imported is asked where that game can be downloaded, and Gameio fetches it from there.

## How it works

1. **Sign in and pick your systems.** Home fills with every game the catalog knows for them.
2. **Import an add-on**, a small JSON file, during setup or under **Settings → Add-ons**.
3. **Open any game.** Gameio lists the sources your add-ons found. Pick one, and it downloads, verifies and launches with your emulator.
4. **Already have games?** Put them in your platform folders and Gameio shows them alongside the catalog.

Add-on sources can be direct HTTPS links, Internet Archive files, or single files inside torrents. Torrents are resolved through your own Real-Debrid account. The format and tools for building add-ons live in the `gameio-addons` repo.

## Features

- Browse, search and filter the full catalog per system, and discover top-rated and hidden games
- Favorites, recently played and collections
- Save sync across devices with your Gameio account
- Comments on game pages, with replies and likes
- Emulator auto-detection, built-in cores, RetroAchievements, dual-screen support
- Fully usable with a gamepad, and touch-friendly too

## Get it

Download the latest APK from **[playgameio.com](https://playgameio.com)**. It runs on Android 8.0 and up and is designed for handhelds such as Retroid, AYN Odin and Anbernic devices. Gameio needs a Gameio account and the emulators for the systems you play.

## Build

```bash
./gradlew :app:assembleDebug
```

The server behind the catalog, accounts and save sync is [gameio-server](https://github.com/naifqarni/gameio-server).

## Credits and license

Gameio is a fork of [Argosy](https://github.com/nendotools/argosy-launcher) and is licensed under the [GNU GPL v3](LICENSE).
