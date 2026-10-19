"""Draw the square dock icons (YouTube, Netflix, Prime Video, AirPlay) and the icon shadow.

The logos are trademarks, so the PNGs are kept out of git: run this locally before building.

Usage:
	python3 -m venv tools/iconvenv && tools/iconvenv/bin/pip install pillow
	tools/iconvenv/bin/python -I tools/make_dock_icons.py \
		launcher/app/src/main/res/drawable-nodpi launcher/app/src/main/res/font/open_sans.ttf

Only Pillow is needed. The brand marks for Disney+, Crunchyroll, Stremio and the rest are downloaded once from the
Dashboard Icons pack into tools/assets/logos/ (needs internet the first time).
"""
import sys, pathlib
from PIL import Image, ImageDraw, ImageFont
OUT = pathlib.Path(sys.argv[1]); FONT = sys.argv[2]
S, K = 256, 4                 # output size, supersample factor
B = S * K
R = int(B * 0.225)            # squircle corner radius (Apple-like)

def canvas(): return Image.new("RGBA", (B, B), (0, 0, 0, 0))
def shape_mask():
    m = Image.new("L", (B, B), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, B - 1, B - 1], R, fill=255); return m
def fill(color):
    img = canvas(); img.paste(Image.new("RGBA", (B, B), color), (0, 0), shape_mask()); return img
def vgradient(top, bottom):
    g = Image.new("RGBA", (1, B))
    for y in range(B):
        t = y / (B - 1); g.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    img = canvas(); img.paste(g.resize((B, B)), (0, 0), shape_mask()); return img
def save(img, name): img.resize((S, S), Image.LANCZOS).save(OUT / name)
def P(*xy): return [(x * B, y * B) for x, y in xy]

# Brand marks for the other apps come from the Dashboard Icons pack (https://github.com/homarr-labs/dashboard-icons,
# Apache-2.0 code; the marks stay the property of their owners). Downloaded once into tools/assets/logos/.
import urllib.request
LOGOS = pathlib.Path(__file__).parent / "assets" / "logos"
def logo(name):
    p = LOGOS / f"{name}.png"
    if not p.exists():
        LOGOS.mkdir(parents=True, exist_ok=True)
        p.write_bytes(urllib.request.urlopen(
            f"https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/{name}.png", timeout=30).read())
    img = Image.open(p).convert("RGBA")
    return img.crop(img.getchannel("A").getbbox())
def white(img, invert=False):
    """The mark as a flat white silhouette (invert=True for dark marks that are meant to be solid black)."""
    out = Image.new("RGBA", img.size, (255, 255, 255, 0)); out.putalpha(img.getchannel("A")); return out
def light_on_dark(img):
    """A white-on-dark logo (e.g. Disney+ on its navy square) as white shapes on transparent."""
    lum = img.convert("L").point(lambda v: max(0, min(255, int((v - 90) * 255 / 120))))
    out = Image.new("RGBA", img.size, (255, 255, 255, 0)); out.putalpha(lum); return out
def put_logo(tile, lg, frac):
    """Centre the mark on the tile, scaled so its longer side is `frac` of the tile."""
    s = frac * B / max(lg.size); lg = lg.resize((max(1, int(lg.width * s)), max(1, int(lg.height * s))), Image.LANCZOS)
    tile.alpha_composite(lg, ((B - lg.width) // 2, (B - lg.height) // 2))

# YouTube: white tile, red rounded play button
img = fill((255, 255, 255, 255)); d = ImageDraw.Draw(img)
d.rounded_rectangle([0.17 * B, 0.29 * B, 0.83 * B, 0.71 * B], int(0.11 * B), fill=(255, 0, 0, 255))
d.polygon(P((0.43, 0.39), (0.43, 0.61), (0.61, 0.50)), fill=(255, 255, 255, 255))
save(img, "dock_youtube.png")

# Netflix: black tile, red N (two dark bars, bright diagonal ribbon)
img = fill((0, 0, 0, 255)); d = ImageDraw.Draw(img)
dark, red = (177, 6, 15, 255), (229, 9, 20, 255)
d.polygon(P((0.33, 0.18), (0.44, 0.18), (0.44, 0.82), (0.33, 0.85)), fill=dark)
d.polygon(P((0.56, 0.18), (0.67, 0.18), (0.67, 0.85), (0.56, 0.82)), fill=dark)
d.polygon(P((0.33, 0.18), (0.44, 0.18), (0.67, 0.85), (0.56, 0.82)), fill=red)
save(img, "dock_netflix.png")

# Prime Video: blue tile with the "prime video" wordmark + smile in white, centred
img = vgradient((31, 168, 236), (0, 120, 200))
put_logo(img, white(logo("prime-video")), 0.74)
save(img, "dock_prime.png")

# AirPlay: graphite tile, white screen outline open at the bottom, solid triangle
img = vgradient((88, 88, 94), (40, 40, 44))
sym = Image.new("L", (B, B), 0); s = ImageDraw.Draw(sym)
s.rounded_rectangle([0.20 * B, 0.24 * B, 0.80 * B, 0.66 * B], int(0.06 * B), outline=255, width=int(0.055 * B))
s.polygon(P((0.32, 0.80), (0.50, 0.56), (0.68, 0.80)), fill=0)        # gap in the frame around the triangle
s.polygon(P((0.37, 0.78), (0.50, 0.61), (0.63, 0.78)), fill=255)
img.paste(Image.new("RGBA", (B, B), (255, 255, 255, 255)), (0, 0), sym)
save(img, "dock_airplay.png")

# Disney+: navy gradient tile, white logo (taken from the pack's navy square)
img = vgradient((20, 66, 170), (3, 14, 62))
src = logo("disney-plus")   # the navy square, cropped to its edges; trim its rounded corners before keying out the navy
inner = src.crop((int(src.width * .08), int(src.height * .08), int(src.width * .92), int(src.height * .92)))
put_logo(img, light_on_dark(inner), 0.80)
save(img, "dock_disney.png")

# Crunchyroll: orange tile, white mark
img = fill((244, 117, 33, 255)); put_logo(img, white(logo("crunchyroll")), 0.58); save(img, "dock_crunchyroll.png")

# Stremio: deep violet tile, the app's own diamond mark
img = vgradient((38, 30, 84), (14, 11, 38)); put_logo(img, logo("stremio"), 0.66); save(img, "dock_stremio.png")

# Kodi, Spotify, Jellyfin: dark graphite tile with the mark in colour
for name, key, frac in (("kodi", "kodi", 0.64), ("spotify", "spotify", 0.64), ("jellyfin", "jellyfin", 0.62)):
    img = vgradient((38, 40, 46), (14, 15, 18)); put_logo(img, logo(key), frac); save(img, f"dock_{name}.png")

# Plex: charcoal tile, yellow chevron (the pack's mark sits on a dark square, so the tile takes its colour)
plex = logo("plex"); img = fill(plex.getpixel((6, plex.height // 2))[:3] + (255,))
put_logo(img, plex, 1.0); save(img, "dock_plex.png")

# Twitch, Max, Apple TV+, Paramount+: flat brand colour with the mark in white
for name, key, bg, frac in (("twitch", "twitch", (145, 70, 255, 255), 0.52), ("max", "max", (0, 43, 231, 255), 0.72),
                            ("appletv", "apple-tv-plus", (0, 0, 0, 255), 0.66), ("paramount", "paramount-plus", (0, 100, 255, 255), 0.62)):
    img = fill(bg); put_logo(img, white(logo(key)), frac); save(img, f"dock_{name}.png")

# Soft drop shadow drawn behind each dock icon: a wide, diffuse blur of a rounded square slightly larger than
# the 56dp icon, on a 120dp canvas so the blur fades out before the edges.
from PIL import ImageFilter
CANVAS_DP, BODY_DP, BLUR_DP, ALPHA = 120, 60, 10, 140
SH = CANVAS_DP * K * 2                 # 2x density, supersampled
sh = Image.new("L", (SH, SH), 0)
inset = (CANVAS_DP - BODY_DP) / 2 / CANVAS_DP * SH
ImageDraw.Draw(sh).rounded_rectangle([inset, inset, SH - inset, SH - inset], int(SH * BODY_DP / CANVAS_DP * 0.225), fill=ALPHA)
sh = sh.filter(ImageFilter.GaussianBlur(SH * BLUR_DP / CANVAS_DP))
shadow = Image.new("RGBA", (SH, SH), (0, 0, 0, 0)); shadow.putalpha(sh)
shadow.resize((CANVAS_DP * 2, CANVAS_DP * 2), Image.LANCZOS).save(OUT / "dock_shadow.png")
