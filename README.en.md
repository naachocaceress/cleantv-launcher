# CleanTV Launcher

*[Versión en español](README.md)*

A clean home screen for Android TV boxes, with no ads, accounts or tracking. It is a fork of
**[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)**: the base launcher, the TV cleanup guide, 25 languages,
themes, the app library, module cards and the remote-key helpers all come from there, plus a few additions of this fork.

> **Based on other people's work.** The lineage is [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) →
> [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup) → this repository, and the current
> launcher was brought up to date with [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT licence).
> Everything not listed under [What this fork adds](#what-this-fork-adds) is theirs and documented in their README, in
> [`docs/`](docs/) and in [`themes/`](themes/).

## What this fork adds

### Continue watching

A row of cards between the header and the dock with what you watched last, as on Google TV: the app, the title, the poster
and how far you got. It merges two sources, newest first:

- **What apps publish to Android TV's *Watch Next* list.** Netflix, YouTube or Disney+ do on a Google TV, but on a box
  without Google services almost no app publishes anything, so this source is usually empty.
- **The launcher's own history**, which covers that case: a minimal service watches the other apps' media sessions (the
  same data the "now playing" notification uses) and keeps the title, channel or show, poster and position. It works with
  any app that reports what it plays (YouTube, Stremio...). Apps that report no title (Netflix, Prime Video) still show up
  as "you used this app" with their banner. OK opens the app, or the content itself when the app published a link to it.
  Everything stays in the launcher's private storage; no notification is read.

The row shows while the dock is focused: Up from the dock reaches it, Up again reaches the top buttons and Down goes back to
the dock. *Launcher settings → Continue watching* switches it off; with nothing to show it stays hidden.

The access is given once over adb, and the install scripts do it. By hand:

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaHistoryService
```

### Updating without losing settings

Android refuses to install a build signed with a different key over the installed one, and uninstalling wipes the
launcher's settings. So **every build is signed with the same key**, committed in `launcher/signing/cleantv.keystore` (the
stock debug key, password `android`). It protects nothing and does not need to: this is a sideloaded home screen, not a store
app. With it, `adb install -r` (or the scripts) updates in place and keeps settings, order and history. A TV that already
runs a build signed with another key needs one uninstall, once.

### Install from Windows with `adb.exe`

[`launcher/install.ps1`](launcher/install.ps1) builds, installs on one or more TVs, sets the home app and turns the history
on. It needs no Python (upstream's scripts do) and leaves the stock launcher alone:

```powershell
launcher\install.ps1 192.168.0.222 192.168.0.221
```

### Manhattan theme

[`themes/manhattan/`](themes/manhattan/) brings back the Manhattan photos this fork used (morning, afternoon, evening and
night, picked by the launcher's own time-of-day rule), six columns and a dock with Netflix, Prime Video, YouTube, Disney+,
Crunchyroll and Stremio. It is an ordinary upstream theme, pushed without rebuilding (see [`themes/README.md`](themes/README.md)).
Without Python, by hand:

```powershell
$d = "/sdcard/Android/data/com.gathin.tvhome/files"
adb shell mkdir -p $d/wallpapers
adb push themes\manhattan\config.json $d/config.json
Get-ChildItem themes\manhattan\wallpapers | ForEach-Object { adb push $_.FullName "$d/wallpapers/$($_.Name)" }
adb shell am force-stop com.gathin.tvhome
```

The photos are from Unsplash (not MIT): see [`themes/manhattan/CREDITS.md`](themes/manhattan/CREDITS.md).

### Defaults

[`config.xml`](launcher/app/src/main/res/values/config.xml) ships this fork's dock (Netflix, Prime Video, YouTube, Disney+,
Crunchyroll, Stremio), with `launcher_settings_button` and `tile_menu` on (OK on *Settings* opens the launcher settings, and
holding OK on an app offers Move and App info), and hides TV-box shortcuts nobody uses.

## Install

JDK 17 or newer, the Android SDK (platform 34), and the TV on the same network with debugging on.

Windows: `launcher\install.ps1 <TV ip>` (above). macOS and Linux, or Windows with Python, with upstream's scripts:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

The release build is installed and set as Home. The stock launcher is detected and only disabled **after** this one is
confirmed to start (see upstream's guide); `STOCK_LAUNCHER=none` touches nothing. Gradle works with Android Studio's JDK and a
`launcher/local.properties` whose `sdk.dir` uses forward slashes. `-PlauncherApplicationId=com.gathin.tvhomedev` installs a
test copy beside the real one without touching Home. Tested only on two ZTE B866V2 (Android 9). **Fire TV is not supported.**

## Staying in sync with upstream

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # once
git fetch upstream && git merge upstream/main
```

This fork touches few upstream files, to keep merges cheap: `MainActivity.java` (the *Continue watching* section plus three
hooks in `onResume`, `onTileFocused` and `onTileVerticalKey`), `activity_main.xml` (the `recoBlock`),
`LauncherSettingsActivity.java` and its layout (one row), `AndroidManifest.xml` (permissions and the service), `config.xml`
(dock, hidden apps, two switches), `app/build.gradle` (signing) and the install scripts (two lines). Everything else is a new
file: `RecoStore`, `RecoAdapter`, `HistoryStore`, `MediaHistoryService`, `install.ps1`, `themes/manhattan/`.

Upstream also ships `.github/` (its CI, with a 480 KB APK cap, and a scheduled maintenance bot), `CLAUDE.md`, `AGENTS.md` and
`GEMINI.md` (its maintainers' rules for agents). This fork does not use them: after each merge,
`git rm -r .github CLAUDE.md AGENTS.md GEMINI.md`.

Removed from this fork's earlier version because upstream has an equivalent: the Dock, Circles and Tiles themes (upstream's
glass plate is the Tiles theme), the Wikimedia wallpaper download, the photo screensaver (upstream has the clock one),
moving apps (card menu and *Reorder apps*) and the fork's own translations.

## Licence

MIT, see [`LICENSE`](LICENSE): © 2026 Gathin (original work) and © 2026 Nacho (this fork's changes). Credits are in
[`CREDITS.md`](CREDITS.md). App names and logos belong to their owners.
