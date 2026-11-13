"""Render actual Hong Lie part geometry and UVs, using current held-pose transforms.

These are orthographic source-model previews, not game screenshots. No game or GUI
is started. Run with python after generate_yike_weapon.py (Pillow required).
"""
from pathlib import Path
import copy
import json
from PIL import Image, ImageDraw, ImageFont
from render_bbmodel import render

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/yike"
BACKGROUND = "#201D2B"


def assemble(opening, mobile=False):
    frame = json.loads((SOURCE / "hong_lie_frame.bbmodel").read_text())
    petal = json.loads((SOURCE / "hong_lie_petal.bbmodel").read_text())
    ring = json.loads((SOURCE / "hong_lie_ring.bbmodel").read_text())
    for index in range(3):
        angle = 0 if mobile and index == 0 else 30 * opening
        for source in petal["elements"]:
            element = copy.deepcopy(source)
            element["_transform"] = [
                {"translation": [-8, -8, -8]},
                {"rotation": [angle, 0, 0]},
                {"rotation": [0, index * 120, 0]},
                {"translation": [8, 13.5, 8]},
            ]
            frame["elements"].append(element)
    for source in ring["elements"]:
        element = copy.deepcopy(source)
        element["_transform"] = [{"translation": [0, 13.2 + opening * 1.35, 0]}]
        frame["elements"].append(element)
    return frame


def font(size):
    return ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", size)


def label(image, title, subtitle=None):
    draw = ImageDraw.Draw(image)
    draw.text((24, 22), title, font=font(24), fill="#F3C879")
    if subtitle:
        draw.text((24, 55), subtitle, font=font(15), fill="#B9AFC6")
    return image


def panel(model, title, subtitle, view, size, scale=None):
    image = Image.new("RGBA", size, BACKGROUND)
    content = render(model, view=view, size=(size[0], size[1]-100),
                     scale=scale, background=BACKGROUND)
    image.alpha_composite(content, (0, 100))
    return label(image, title, subtitle)


def main():
    panels = []
    for name, title, amount, mobile in (
        ("closed", "CLOSED", 0, False),
        ("mobile", "MOBILE / 55%", .55, True),
        ("deployed", "DEPLOYED", 1, False),
    ):
        model = assemble(amount, mobile)
        image = panel(model, title, "Actual cuboids + atlas | held-part assembly",
                      "isometric", (640, 1080), scale=44)
        image.save(SOURCE / f"hong_lie_{name}_preview.png")
        panels.append(image)
    parts = Image.new("RGBA", (1920, 740), BACKGROUND)
    for index, (name, title, view) in enumerate((
        ("hong_lie_frame", "FRAME + CRYSTAL", "isometric"),
        ("hong_lie_petal", "PETAL + HINGE", "isometric"),
        ("hong_lie_ring", "OPEN FOCUS RING", "top"),
    )):
        image = panel(SOURCE / f"{name}.bbmodel", title,
                      "Local origin and part interface preserved", view, (640, 740))
        parts.alpha_composite(image, (index * 640, 0))
    parts.save(SOURCE / "hong_lie_parts_preview.png")
    sheet = Image.new("RGBA", (1920, 1820), BACKGROUND)
    for index, image in enumerate(panels):
        sheet.alpha_composite(image, (index * 640, 0))
    sheet.alpha_composite(parts, (0, 1080))
    sheet.save(SOURCE / "hong_lie_model_preview.png")
    # Head-on and oblique views expose the aperture, ring depth, core and lining.
    detail = Image.new("RGBA", (1440, 920), BACKGROUND)
    for index, view in enumerate(("top", "isometric")):
        model = assemble(1)
        model["elements"] = [part for part in model["elements"]
                             if part["name"] not in ("exposed_wrapped_grip", "lower_shaft", "counterweight", "counterweight_heart")
                             and not part["name"].startswith("shaft_collar")]
        image = panel(model, "MUZZLE / " + view.upper(),
                      "True open geometry; crystal remains recessed", view, (720, 920))
        detail.alpha_composite(image, (index * 720, 0))
    detail.save(SOURCE / "hong_lie_muzzle_preview.png")
    print("OK: closed / mobile / deployed, parts, model sheet and muzzle views")


if __name__ == "__main__":
    main()
