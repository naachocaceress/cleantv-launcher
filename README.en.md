# CleanTV Launcher

*[Versión en español](README.md)*

A clean home screen for Android TV boxes with **three switchable themes**, wallpapers downloaded on demand, and no ads,
accounts or tracking. The interface follows the TV's language (Spanish or English).

> **Based on other people's work.** A fork of [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup),
> itself a fork of [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). The launcher, the cleanup tools and the
> original idea come from them (MIT licence). See the [Spanish README](README.md#qué-cambió-este-fork) for what this fork
> changed.

| Dock | Circles | Tiles |
|---|---|---|
| ![Dock theme](docs/tema-dock.jpg) | ![Circles theme](docs/tema-circulos.jpg) | ![Tiles theme](docs/tema-mosaicos.jpg) |

## Themes

The layouts are **inspired by** well-known TV home screens; they are not copies and do not use their names or marks.

- **Dock**: up to 8 magnified icons at the bottom, with a name tooltip and a glow in the app's colour; press Down for the
  rest as cards. *Inspired by the macOS dock.*
- **Circles**: one row of round icons hanging from the bottom, scrolling sideways, with the focused app's name as a big
  title. *Inspired by the Google TV home screen.*
- **Tiles**: wide tiles on a translucent plate, the focused one grows without leaving the plate, the next row peeks in below.
  *Inspired by the tvOS home screen.*

The header (greeting, clock, date, connection icon and Settings) is the same in every theme. The connection icon shows
Ethernet, Wi-Fi (with signal bars) or either one crossed out when there is no internet. Settings opens a panel with
**Theme**, **Wallpaper** and **Android settings** buttons.

## Continue watching

A row of cards between the header and the apps with what you watched last, as on Google TV: the app, the title, the
poster and how far you got. It merges two sources, newest first:

- **What apps publish to Android TV's *Watch Next* list.** Netflix, YouTube or Disney+ do on a Google TV, but on a box
  without Google services almost no app publishes anything, so this source is usually empty.
- **The launcher's own history**, which covers that case: a minimal service watches the other apps' media sessions (the
  same data the "now playing" notification uses) and keeps the title, channel or show, poster and position. It works with
  any app that reports what it plays (YouTube, Stremio...). Apps that report no title (Netflix, Prime Video) still show up
  as "you used this app" with their banner. OK opens the app, or the content itself when the app published a link to it.
  Everything stays in the launcher's private storage; no notification is read.

Up from the first row of apps reaches it, OK opens, Down goes back. Settings → Recommendations switches it off. The access
is given once over adb, which `launcher/build-and-install.sh` and `launcher/install.ps1` do:

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaHistoryService
adb shell pm grant com.gathin.tvhome com.android.providers.tv.permission.READ_EPG_DATA
```

## Install

JDK 17 or newer, the Android SDK (platform 34), and the TV on the same network with debugging on.

Windows (builds, installs on one or more TVs, sets the home app and turns the history on):

```powershell
launcher\install.ps1 192.168.0.222 192.168.0.221
```

macOS / Linux:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

The stock launcher is left alone unless you add `DISABLE_STOCK=1`. Gradle works with Android Studio's JDK and a
`launcher/local.properties` whose `sdk.dir` uses forward slashes. Tested only on a ZTE B866V2 (Android 9). Fire TV is
not supported by this fork.

### Updating without losing settings

Android refuses to install a build signed with a different key over the installed one, and uninstalling wipes the
launcher's settings. So **every build is signed with the same key**, committed in `launcher/signing/cleantv.keystore`
(the stock debug key, password `android`). It protects nothing and does not need to: this is a sideloaded home screen,
not a store app. With it, `adb install -r` (or the scripts) updates in place and keeps settings, order and history. A TV
that already runs a build signed with another key needs one uninstall, once.

## Licence

MIT, see [`LICENSE`](LICENSE): © 2026 Gathin and © 2026 Nacho (changes in this fork). Fonts: SIL OFL. Credits in
[`CREDITS.md`](CREDITS.md). Brand names and logos belong to their owners.
