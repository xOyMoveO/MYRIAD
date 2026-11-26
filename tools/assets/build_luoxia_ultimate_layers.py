"""Bake the two transparent VFX master sheets into independently timed Minecraft sprite strips."""
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/luoxia/vfx"
OUT = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx"
TRACKS = {
    "volley": ("charge", "crown", "trail", "hit"),
    "lance": ("charge", "release", "trail", "hit"),
}


def crop_cell(image, index):
    half = image.width // 2
    x, y = index % 2 * half, index // 2 * half
    cell = image.crop((x, y, x + half, y + half))
    bounds = cell.getchannel("A").point(lambda a: 255 if a >= 16 else 0).getbbox()
    assert bounds, "empty VFX source cell"
    return cell.crop(bounds)


def bake(motif, name, role):
    line = role in ("trail", "warning")
    w, h = 256, 64 if line else 256
    atlas = Image.new("RGBA", (w * 4, h))
    for frame in range(4):
        t = frame / 3
        if role == "charge":
            width, height, alpha = .84 - .25*t, .72 - .34*t, .64 + .36*t
        elif role in ("crown", "release"):
            width, height, alpha = .45 + .50*t, (.38 + .50*t) if role == "crown" else (.72 - .35*t), 1 - .35*t
        elif line:
            width, height, alpha = .99, (.69 - .40*t), .82 - .26*t
        else:
            width, height, alpha = .48 + .45*t, .45 + .46*t, 1 - .70*t
        max_w, max_h = int(w * width), max(2, int(h * height))
        if line:
            sprite = motif.resize((max_w, max_h), Image.Resampling.NEAREST)
        else:
            sprite = ImageOps.contain(motif, (max_w, max_h), Image.Resampling.NEAREST)
        sprite.putalpha(sprite.getchannel("A").point(lambda a: round(a * alpha)))
        x = 2 if role in ("crown", "release") else (w-sprite.width)//2
        cell = Image.new("RGBA", (w, h))
        cell.alpha_composite(sprite, (x, (h-sprite.height)//2))
        atlas.paste(cell, (w*frame, 0))
    atlas.save(OUT / (name + ".png"), optimize=True)


def main():
    for family, tracks in TRACKS.items():
        master = Image.open(SOURCE / f"{family}-master.png").convert("RGBA")
        for i, track in enumerate(tracks):
            motif = crop_cell(master, i)
            bake(motif, family + "_" + track, track)
            if family == "lance" and track == "trail":
                tinted = ImageOps.colorize(ImageOps.grayscale(motif), "#624a83", "#e19a65").convert("RGBA")
                tinted.putalpha(motif.getchannel("A"))
                bake(tinted, "lance_warning", "warning")
            if family == "lance" and track == "hit":
                muted = ImageOps.colorize(ImageOps.grayscale(motif), "#49364f", "#ba775e").convert("RGBA")
                muted.putalpha(motif.getchannel("A"))
                bake(muted, "lance_graze", "graze")


if __name__ == "__main__":
    main()
