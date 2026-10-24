# CleanTV Launcher

*[Versión en español](README.md)*

A clean home screen for Android TV boxes, with no ads, accounts or tracking. It is a fork of
**[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)**: the base launcher, the TV cleanup guide, 25 languages,
themes, the app library, module cards and the remote-key helpers all come from there, plus several additions of this fork.

> **Based on other people's work.** The lineage is [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) →
> [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup) → this repository, and the current
> launcher was brought up to date with [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT licence).
> Everything not listed under [What this fork adds](#what-this-fork-adds) is theirs and documented in their README, in
> [`docs/`](docs/) and in [`themes/`](themes/).

## What this fork adds

### Three themes: Dock, Circles and Tiles

- **Tiles** is upstream's home as it is (a glass plate with the pinned apps, rows of cards below) and the default.
- **Dock** and **Circles** are this fork's own two themes: a compact dock of icons with the app's name above it, and one long
  row of circles with the big name and "Your apps". They are the code they always were, isolated in `ClassicHome` and its
  `Classic…` classes (with `c_…` resources) so they never mix with upstream's files.
- Those two themes can **move apps** (hold OK on an app, or Menu): inside the dock, the grid and between them. Tiles uses
  upstream's card menu and *Reorder apps*.
- The theme is changed in the sidebar: *Settings → Theme*. Each theme keeps its own pinned apps and order.

### Settings sidebar

The **Settings** button opens a panel on the right edge with Home dimmed behind it, in all three themes: *Theme*,
*Wallpaper*, *Recommendations* (the row below), *More launcher settings* (Tiles) and *Android settings*, plus *Inputs* when the
TV has them. Holding OK on the button still opens upstream's menu (modules, TV settings...). Upstream's long settings page
(apps per row, rows below the dock, animations, wallpaper) is also a side panel instead of a full screen.

### Wikimedia wallpapers

*Settings → Wallpaper → New wallpaper from the web* downloads a picture from a list of freely licensed images on Wikimedia
Commons (CC0, public domain, CC BY, CC BY-SA), crops it to 1920×1080 and keeps it with its author and licence. It joins the
theme's `wallpapers/` folder with a `web-` prefix, so **upstream rotates or pins it exactly like the theme's own pictures**, in
any theme of the launcher. The panel also steps to the next or previous wallpaper, removes the downloaded one and goes back to
the theme's. The list holds only links: a picture is fetched when you ask (the one reason, with the posters of *Continue
watching*, that the launcher has the internet permission). Rebuild the list with
[`tools/make_wallpaper_catalog.py`](tools/make_wallpaper_catalog.py), or add your own with `c_wallpaper_catalog_url` in
[`classic_config.xml`](launcher/app/src/main/res/values/classic_config.xml).

### Continue watching

A row of cards between the header and the apps with what you watched last, as on Google TV: the app, the title, the poster
and how far you got. It merges two sources, newest first:

- **What apps publish to Android TV's *Watch Next* list.** Netflix, YouTube or Disney+ do on a Google TV, but on a box
  without Google services almost no app publishes anything, so this source is usually empty.
- **The launcher's own history**, which covers that case: a minimal service watches the other apps' media sessions (the
  same data the "now playing" notification uses) and keeps the title, channel or show, poster and position. It works with
  any app that reports what it plays (YouTube, Stremio...). Apps that report no title (Netflix, Prime Video) still show up
  as "you used this app" with their banner. OK opens the app, or the content itself when the app published a link to it.
  Everything stays in the launcher's private storage; no notification is read.

The row shows while the dock (the first row) is focused: Up reaches it, Up again reaches the top buttons and Down goes back.
Switch it off in the sidebar (*Recommendations*); with nothing to show it stays hidden. The access is given once over adb, and
the install scripts do it. By hand:

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaHistoryService
```

### Updating without losing settings

Android refuses to install a build signed with a different key over the installed one, and uninstalling wipes the
launcher's settings. So **every build is signed with the same key**, committed in `launcher/signing/cleantv.keystore` (the
stock debug key, password `android`). It protects nothing and does not need to: this is a sideloaded home screen, not a store
app. With it, `adb install -r` (or the scripts) updates in place and keeps settings, order, theme and history. A TV that
already runs a build signed with another key needs one uninstall, once.

### Install from Windows with `adb.exe`

[`launcher/install.ps1`](launcher/install.ps1) builds, installs on one or more TVs, sets the home app and turns the history
on. It needs no Python (upstream's scripts do) and leaves the stock launcher alone:

```powershell
launcher\install.ps1 192.168.0.222 192.168.0.221
```

### Manhattan theme (photos and dock)

[`themes/manhattan/`](themes/manhattan/) is an ordinary upstream theme with the Manhattan photos this fork used (morning,
afternoon, evening and night, picked by the launcher's own time-of-day rule), six columns and a dock with Netflix, Prime
Video, YouTube, Disney+, Crunchyroll and Stremio. It works in all three themes of the launcher (they all draw the wallpaper
with upstream's engine). It is pushed without rebuilding, see [`themes/README.md`](themes/README.md). Without Python, by hand:

```powershell
$d = "/sdcard/Android/data/com.gathin.tvhome/files"
adb shell mkdir -p $d/wallpapers
adb push themes\manhattan\config.json $d/config.json
Get-ChildItem themes\manhattan\wallpapers | ForEach-Object { adb push $_.FullName "$d/wallpapers/$($_.Name)" }
adb shell am force-stop com.gathin.tvhome
```

The photos are from Unsplash (not MIT): see [`themes/manhattan/CREDITS.md`](themes/manhattan/CREDITS.md). With no theme the
background is upstream's sky (Tiles) or black (Dock and Circles).

### Defaults and translations

[`config.xml`](launcher/app/src/main/res/values/config.xml) ships this fork's dock (Netflix, Prime Video, YouTube, Disney+,
Crunchyroll, Stremio) with upstream's card menu (`tile_menu`) on, and hides TV-box shortcuts nobody uses. The texts of the
themes and the sidebar are in English and Spanish (`classic_strings.xml`); in upstream's other 24 languages those few strings
show in English.

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
test copy beside the real one without touching Home. The application id stays `com.gathin.tvhome`, as in the original, which
keeps your settings on update: a TV that had Dock or Circles saved keeps opening in it. Tested only on two ZTE B866V2
(Android 9). **Fire TV is not supported.**

## Staying in sync with upstream

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # once
git fetch upstream && git merge upstream/main
```

This fork touches few upstream files, to keep merges cheap: `MainActivity.java` (the *Continue watching* and sidebar
sections, plus one line at the start of each life-cycle method that hands over to `ClassicHome` when the theme is Dock or
Circles), `activity_main.xml` (the `recoBlock`), `LauncherSettingsActivity.java` and its layout (one row and the panel look),
`AndroidManifest.xml` (permissions, the service and the panel theme), `values/styles.xml` and `config.xml` (dock, hidden apps,
card menu), `app/build.gradle` (signing) and the install scripts (two lines). Everything else is a new file: `ClassicHome`,
`ClassicAdapter` and friends, `ConfigSidebar`, `WallpaperStore`, `RecoStore`, `RecoAdapter`, `HistoryStore`,
`MediaHistoryService`, the `c_…` resources and `classic_*.xml`, `install.ps1`, `themes/manhattan/`.

Upstream also ships `.github/` (its CI, with a 480 KB APK cap, and a scheduled maintenance bot), `CLAUDE.md`, `AGENTS.md` and
`GEMINI.md` (its maintainers' rules for agents). This fork does not use them: after each merge,
`git rm -r .github CLAUDE.md AGENTS.md GEMINI.md`.

Left out because upstream has an equivalent: the photo screensaver (upstream has the clock one) and the brand icons drawn on
your machine (every app shows its own banner).

## Licence

MIT, see [`LICENSE`](LICENSE): © 2026 Gathin (original work) and © 2026 Nacho (this fork's changes). Credits are in
[`CREDITS.md`](CREDITS.md). App names and logos belong to their owners.
