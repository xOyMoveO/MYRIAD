"""Rebuild the 1024px combat HUD atlas with Pillow."""
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/myriad/textures/gui/combat_hud.png"
NEAREST = Image.Resampling.NEAREST


def clipped_rect(draw, bounds, color, cut=1):
    """Draw a rectangular plate with an integer chamfer on each corner."""
    x0, y0, x1, y1 = bounds
    draw.polygon(
        [(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut),
         (x1, y1 - cut), (x1 - cut, y1), (x0 + cut, y1),
         (x0, y1 - cut), (x0, y0 + cut)],
        fill=color,
    )


def panel(low_health=False):
    image = Image.new("RGBA", (64, 32))
    draw = ImageDraw.Draw(image)
    if low_health:
        clipped_rect(draw, (0, 0, 63, 31), "#CA6772", 2)
        clipped_rect(draw, (1, 1, 62, 30), (0, 0, 0, 0), 2)
    else:
        clipped_rect(draw, (0, 0, 63, 31), "#101718", 2)
        clipped_rect(draw, (1, 1, 62, 30), "#485957")
        clipped_rect(draw, (2, 2, 61, 29), (32, 43, 46, 242))
        draw.line((4, 1, 59, 1), fill="#617975")
        draw.line((4, 30, 59, 30), fill="#344344")

    corner = Image.new("RGBA", (4, 4))
    marks = ImageDraw.Draw(corner)
    if low_health:
        marks.line([(1, 3), (1, 1), (3, 1)], fill="#CA6772")
        marks.point((2, 2), fill="#EDB1AF")
    else:
        marks.line([(1, 3), (1, 2), (2, 1), (3, 1)], fill="#967956")
        marks.point((2, 2), fill="#D0B592")
        marks.point((3, 2), fill="#97B4A8")
    for position, rotation in [((0, 0), 0), ((60, 0), 270), ((60, 28), 180), ((0, 28), 90)]:
        image.alpha_composite(corner.rotate(rotation), position)
    return image.resize((128, 64), NEAREST)


def frame(main_color, edge_color, highlight, *, hollow=False):
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    clipped_rect(draw, (0, 0, 15, 15), main_color, 2)
    if hollow:
        clipped_rect(draw, (1, 1, 14, 14), (0, 0, 0, 0), 2)
    else:
        clipped_rect(draw, (1, 1, 14, 14), edge_color)
        draw.rectangle((2, 2, 13, 13), fill=(0, 0, 0, 0))
    for x, y in [(2, 1), (13, 1), (1, 2), (14, 2), (1, 13), (14, 13), (2, 14), (13, 14)]:
        draw.point((x, y), fill=highlight)
    return image.resize((32, 32), NEAREST)


def track(height):
    image = Image.new("RGBA", (16, height))
    draw = ImageDraw.Draw(image)
    clipped_rect(draw, (0, 0, 15, height - 1), "#0C0C0C")
    clipped_rect(draw, (1, 1, 14, height - 2), "#5A5A5A")
    draw.rectangle((2, 2, 13, height - 3), fill="#292929")
    draw.line((2, 2, 13, 2), fill="#202020")
    draw.line((2, height - 3, 13, height - 3), fill="#3C3C3C")
    return image


def shield():
    image = Image.new("RGBA", (16, 16))
    colors = ["#E9C47A", "#D9AD54", "#C29246", "#775025"] + ["#694622"] * 4
    image.putdata([ImageColor.getrgb(colors[(x + y) % 8]) + (255,) for y in range(16) for x in range(16)])
    return image


def glyph(rows, main_color, highlight):
    palette = {
        ".": (0, 0, 0, 0),
        "o": (16, 22, 24, 255),
        "x": ImageColor.getrgb(main_color) + (255,),
        "+": ImageColor.getrgb(highlight) + (255,),
    }
    image = Image.new("RGBA", (8, 8))
    image.putdata([palette[pixel] for row in rows for pixel in row])
    return image.resize((16, 16), NEAREST)


def build_atlas():
    atlas = Image.new("RGBA", (1024, 1024))
    atlas.paste(panel(), (16, 16))
    atlas.paste(panel(low_health=True), (160, 16))
    atlas.paste(frame("#101718", "#87795B", "#BDCAA9"), (16, 96))
    atlas.paste(frame("#101718", "#56686A", "#83978C"), (64, 96))
    atlas.paste(frame("#101718", "#B69868", "#E7C890"), (112, 96))
    atlas.paste(frame("#84CCAD", "#84CCAD", "#C2E4D1", hollow=True), (160, 96))
    for y, height in [(160, 16), (192, 12), (224, 8), (248, 8)]:
        atlas.paste(track(height), (16, y))
    atlas.paste(shield(), (64, 160))

    icons = [
        # Lock
        (16, "#C7C9BF", "#E4E5D6",
         ["..oooo..", ".ox++xo.", ".oxooxo.", "ooxxxoo.",
          "ox+++xo.", "oxxoxxo.", "oxxxxxo.", ".ooooo."]),
        # Mana drop
        (48, "#79CBF2", "#C0EDFF",
         ["...oo...", "...+o...", "..o++o..", ".o++xxo.",
          ".o+xxxo.", ".oxxxxo.", "..oxxo..", "...oo..."]),
        # Charge pip
        (80, "#91D3B4", "#D0F3DA",
         ["........", "...oo...", "..o++o..", ".o++xxo.",
          ".o+xxxo.", "..oxxo..", "...oo...", "........"]),
        # Recast double chevron
        (112, "#B89FE6", "#E7D6FF",
         ["........", "oo.oo...", "o+oo+o..", ".oxooxoo",
          ".oxooxoo", "o+oo+o..", "oo.oo...", "........"]),
        # Control seal
        (144, "#E59182", "#FFD1B5",
         ["...oo...", "..o++o..", ".o+xx+o.", "o++oo+xo",
          "ox+oo++o", ".oxxx+o.", "..oxxo..", "...oo..."]),
        # Coin
        (176, "#DBAB60", "#F6D292",
         ["..oooo..", ".o++++o.", "o++x+xxo", "o+x+x+xo",
          "o+x+x+xo", "oxx+x+xo", ".oxxxxo.", "..oooo.."]),
    ]
    for x, main_color, highlight, shape in icons:
        atlas.paste(glyph(shape, main_color, highlight), (x, 288))
    return atlas


if __name__ == "__main__":
    build_atlas().save(OUTPUT, optimize=True)
