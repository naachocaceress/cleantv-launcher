# Credits

## Launcher and tools

The launcher, the cleanup guide, the ADB tools and the device lists come from
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT), through
[itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup) and
[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). See their credits for everything not listed here.

## Photos

The four Manhattan photos of the `themes/manhattan` theme come from [Unsplash](https://unsplash.com) and are used under the
[Unsplash License](https://unsplash.com/license): see [`themes/manhattan/CREDITS.md`](themes/manhattan/CREDITS.md) for each
photo and its photographer. They are not MIT-licensed like the rest of the repo.

## Fonts

[Outfit](https://fonts.google.com/specimen/Outfit) (upstream's launcher, `launcher/FONT-LICENSE-OFL.txt`) and
[Open Sans](https://fonts.google.com/specimen/Open+Sans) (the Dock and Circles themes), both under the SIL Open Font
License.

## Logos

The names and logos of the apps shown on Home are trademarks of their owners. The launcher shows each app's own banner;
nothing of theirs is bundled in this repo.

## Wallpapers from the web

None are bundled. `tools/make_wallpaper_catalog.py` builds `launcher/app/src/main/assets/c_wallpapers.json`, a list of links to
freely licensed pictures on [Wikimedia Commons](https://commons.wikimedia.org) (CC0, public domain, CC BY, CC BY-SA). The
launcher downloads one only when asked, keeps its author and licence next to it (`web-….txt`) and shows them when you step
to it.
