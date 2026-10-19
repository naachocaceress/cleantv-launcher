# Credits

## Photos

The background and screensaver photos in `launcher/app/src/main/res/drawable-nodpi/` come from
[Unsplash](https://unsplash.com) and are used under the [Unsplash License](https://unsplash.com/license).
They are cropped to 1920×1080.

| File | Used as | Photo | Photographer |
|---|---|---|---|
| `saver_01.jpg` | Screensaver | [Skyline from Hoboken](https://unsplash.com/photos/new-york-city-skyline-from-hoboken-kZokA2VTKn4) | [@mikechv](https://unsplash.com/@mikechv) |
| `saver_02.jpg`, `bg_morning.jpg` | Screensaver, morning background | [Skyline over the water](https://unsplash.com/photos/a-city-skyline-with-a-body-of-water-in-the-foreground-KWTHWm7JPao) | [@philton88](https://unsplash.com/@philton88) |
| `saver_03.jpg` | Screensaver | [Skyline across the water by day](https://unsplash.com/photos/city-skyline-across-body-of-water-during-daytime-cSNKRFoYREE) | [@notsphinx](https://unsplash.com/@notsphinx) |
| `saver_04.jpg` | Screensaver | [City across the harbor](https://unsplash.com/photos/a-large-body-of-water-with-a-city-in-the-background-wymdBxTP-CI) | [@marttisalmi](https://unsplash.com/@marttisalmi) |
| `saver_05.jpg` | Screensaver | [Skyline across the water by day](https://unsplash.com/photos/city-skyline-across-body-of-water-during-daytime-ApKGyT9GHzI) | [@siegfriedpoepperl](https://unsplash.com/@siegfriedpoepperl) |
| `saver_06.jpg`, `bg_afternoon.jpg` | Screensaver, afternoon background | [High-rise buildings](https://unsplash.com/photos/brown-and-gray-high-rise-buildings-MMdf6NlivGs) | [@eejermaine](https://unsplash.com/@eejermaine) |
| `saver_07.jpg`, `bg_evening.jpg` | Screensaver, evening background | [Bird's-eye view at sunset](https://unsplash.com/photos/birds-eye-photography-of-high-rise-buildings-DitA7EZdgoM) | [@emilianobar](https://unsplash.com/@emilianobar) |
| `saver_08.jpg` | Screensaver | [Brooklyn Bridge at dusk](https://unsplash.com/photos/black-and-gray-concrete-bridge-Ufh2j_ChvcM) | [@rustyspear11](https://unsplash.com/@rustyspear11) |
| `saver_09.jpg` | Screensaver | [Brooklyn Bridge panorama](https://unsplash.com/photos/panoramic-photography-of-brooklyn-bridge-_QdFx92MO2U) | [@lucabravo](https://unsplash.com/@lucabravo) |
| `saver_10.jpg` | Screensaver | [Skyline at night](https://unsplash.com/photos/city-skyline-during-night-time-4WbKrmdJfU0) | [@ndrf](https://unsplash.com/@ndrf) |
| `saver_11.jpg` | Screensaver | [Aerial view at night](https://unsplash.com/photos/aerial-photography-of-city-fPaCAQKkRqY) | [@jonathanroger](https://unsplash.com/@jonathanroger) |
| `saver_12.jpg`, `bg_night.jpg` | Screensaver, night background | [Midtown from above](https://unsplash.com/photos/aerial-phto-of-buildings-2SZIcxGzTj8) | [@ruddymedia](https://unsplash.com/@ruddymedia) |

## Fonts

[Outfit](https://fonts.google.com/specimen/Outfit) and [Open Sans](https://fonts.google.com/specimen/Open+Sans),
under the SIL Open Font License.

## Logos

The dock icons for YouTube, Netflix, Prime Video and AirPlay are trademarks of their owners and are not in
this repo. `tools/make_dock_icons.py` draws them on your machine. The Prime Video wordmark comes from
[Simple Icons](https://simpleicons.org) (CC0).

## Dock icons and wallpapers (this fork)

- **Dock icons.** `tools/make_dock_icons.py` draws a rounded-square tile per app on your machine; the brand marks it
  uses come from the [Dashboard Icons](https://github.com/homarr-labs/dashboard-icons) pack (Apache-2.0 for the pack's
  code; each mark remains the trademark of its owner). The tiles are not committed.
- **Wallpapers.** None are bundled beyond the original Unsplash photos. `tools/make_wallpaper_catalog.py` builds
  `assets/wallpapers.json`, a list of links to freely licensed pictures on [Wikimedia Commons](https://commons.wikimedia.org)
  (CC0, public domain, CC BY, CC BY-SA). The launcher downloads one only when asked and shows its author and licence.
