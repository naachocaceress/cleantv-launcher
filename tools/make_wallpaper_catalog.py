"""Build launcher/app/src/main/assets/c_wallpapers.json: a list of free-licensed wallpapers on Wikimedia Commons.

The launcher downloads the pictures on demand ("New wallpaper from the web"); nothing is bundled. Each entry keeps the
title, the author and the licence so the launcher can credit it. Only 16:9-ish pictures at least 3000 px wide, under a
free licence (CC0, public domain, CC BY, CC BY-SA), from Commons' "Featured pictures" categories.

Usage:  python3 -I tools/make_wallpaper_catalog.py launcher/app/src/main/assets/c_wallpapers.json [per_category]
"""
import json, re, sys, time, urllib.parse, urllib.request

OUT = sys.argv[1]
PER = int(sys.argv[2]) if len(sys.argv) > 2 else 12
UA = "tvhome-launcher/2.0 (personal-use wallpaper catalog builder)"
API = "https://commons.wikimedia.org/w/api.php"
CATEGORIES = {   # Commons category -> label
    "Featured pictures of landscapes": "Landscape",
    "Featured pictures of mountains": "Mountains",
    "Featured pictures of nature": "Nature",
    "Featured pictures of astronomy": "Space",
    "Featured pictures of architecture": "Architecture",
    "Featured pictures of cityscapes": "City",
}
FREE = re.compile(r"^(CC0|Public domain|PD|CC BY(-SA)? [0-9.]+)", re.I)


def call(params):
    url = API + "?" + urllib.parse.urlencode(dict(params, format="json"))
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
            time.sleep(2.5)   # be polite: Commons rate-limits quick bursts
            return data
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(15 * (attempt + 1))
    sys.exit("rate limited too long")


def clean(s):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", s or "")).strip()


catalog, seen = [], set()
for cat, label in CATEGORIES.items():
    got, cont = 0, {}
    while got < PER:
        data = call(dict({"action": "query", "generator": "categorymembers", "gcmtitle": "Category:" + cat.replace(" ", "_"),
                          "gcmtype": "file", "gcmlimit": "50", "prop": "imageinfo", "iiprop": "url|size|extmetadata",
                          "iiurlwidth": "1920", "iiextmetadatafilter": "LicenseShortName|Artist|ObjectName"}, **cont))
        for page in (data.get("query", {}).get("pages", {}) or {}).values():
            info = (page.get("imageinfo") or [None])[0]
            if not info or got >= PER:
                continue
            w, h = info["width"], info["height"]
            meta = info.get("extmetadata", {})
            lic = clean(meta.get("LicenseShortName", {}).get("value", ""))
            if w < 3000 or not (1.55 <= w / h <= 2.1) or not FREE.match(lic) or not info.get("thumburl"):
                continue
            if info["thumburl"] in seen:
                continue
            seen.add(info["thumburl"])
            title = clean(meta.get("ObjectName", {}).get("value", "")) or re.sub(r"^File:|\.\w+$", "", page["title"]).replace("_", " ")
            catalog.append({"title": title[:80], "category": label, "url": info["thumburl"],
                            "credit": f'{clean(meta.get("Artist", {}).get("value", "Unknown"))[:60]}, {lic}',
                            "page": info.get("descriptionshorturl", "")})
            got += 1
        if "continue" not in data:
            break
        cont = data["continue"]
    print(f"{label}: {got}")

with open(OUT, "w", encoding="utf-8") as f:
    json.dump(catalog, f, ensure_ascii=False, indent=1)
print("wrote", len(catalog), "wallpapers to", OUT)
