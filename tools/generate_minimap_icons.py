"""Generate crisp 16x16 minimap sprites. Re-run after palette/shape changes."""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).parents[1] / "src/main/resources/assets/myriad/textures/gui/sprites/minimap"
OUT.mkdir(parents=True, exist_ok=True)

INK = (25, 27, 28, 255)
GOLD = (238, 193, 86, 255)
PALE = (255, 239, 178, 255)
JADE = (88, 224, 203, 255)
WHITE = (238, 249, 247, 255)
STEEL = (151, 166, 169, 255)


def make(name, painter):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    painter(ImageDraw.Draw(image))
    image.save(OUT / f"{name}.png", optimize=True)


def tower(d):
    d.rectangle((3, 13, 12, 14), fill=INK)
    d.rectangle((4, 12, 11, 13), fill=GOLD)
    d.rectangle((5, 7, 10, 12), fill=INK)
    d.rectangle((6, 7, 9, 11), fill=PALE)
    d.rectangle((2, 6, 13, 7), fill=INK)
    d.rectangle((4, 5, 11, 5), fill=GOLD)
    d.rectangle((5, 2, 10, 4), fill=INK)
    d.rectangle((6, 1, 9, 3), fill=PALE)


def crystal(d):
    d.polygon(((7, 0), (11, 4), (10, 11), (7, 15), (3, 11), (2, 4)), fill=INK)
    d.polygon(((7, 1), (10, 5), (8, 12), (7, 14), (4, 10), (3, 5)), fill=JADE)
    d.polygon(((7, 2), (8, 5), (6, 10), (4, 9), (4, 5)), fill=WHITE)
    d.rectangle((2, 14, 12, 15), fill=GOLD)


def melee(d):
    d.rectangle((5, 2, 10, 5), fill=INK)
    d.rectangle((6, 2, 9, 4), fill=PALE)
    d.rectangle((4, 6, 11, 11), fill=INK)
    d.rectangle((5, 7, 10, 10), fill=STEEL)
    d.rectangle((4, 12, 6, 15), fill=INK)
    d.rectangle((9, 12, 11, 15), fill=INK)
    d.line((12, 3, 3, 14), fill=INK, width=3)
    d.line((12, 2, 3, 13), fill=WHITE, width=1)


def ranged(d):
    d.rectangle((6, 4, 9, 7), fill=PALE)
    d.rectangle((5, 8, 10, 13), fill=INK)
    d.rectangle((6, 8, 9, 12), fill=STEEL)
    d.arc((1, 1, 13, 14), 270, 90, fill=GOLD, width=2)
    d.line((7, 2, 7, 14), fill=WHITE, width=1)
    d.line((7, 8, 14, 8), fill=INK, width=2)
    d.point((14, 8), fill=PALE)


def magic(d):
    d.ellipse((3, 2, 12, 11), fill=INK)
    d.rectangle((5, 4, 10, 9), fill=JADE)
    d.rectangle((7, 2, 8, 11), fill=WHITE)
    d.rectangle((3, 6, 12, 7), fill=WHITE)
    d.rectangle((6, 12, 9, 14), fill=GOLD)
    d.rectangle((4, 14, 11, 15), fill=INK)


def siege(d):
    d.rectangle((3, 2, 12, 6), fill=INK)
    d.rectangle((4, 3, 11, 5), fill=PALE)
    d.rectangle((2, 7, 13, 13), fill=INK)
    d.rectangle((4, 8, 11, 12), fill=STEEL)
    d.rectangle((0, 8, 3, 11), fill=GOLD)
    d.rectangle((12, 8, 15, 11), fill=GOLD)
    d.rectangle((3, 14, 6, 15), fill=INK)
    d.rectangle((9, 14, 12, 15), fill=INK)


for sprite_name, painter in {
    "tower": tower,
    "crystal": crystal,
    "melee_minion": melee,
    "ranged_minion": ranged,
    "magic_minion": magic,
    "siege_minion": siege,
}.items():
    make(sprite_name, painter)
