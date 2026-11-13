"""Generate Hengke's static weapon from its editable spec, retaining thin geometry.

Requires Pillow and the project's minecraft-ai-generate-bbmodel skill. Run with
python tools/assets/generate_keheng_weapon.py from any working directory.
"""
from __future__ import annotations

import base64
import importlib.util
import itertools
import json
import uuid
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from render_bbmodel import render


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/keheng"
ASSET = ROOT / "src/main/resources/assets/myriad"
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, -12], "translation": [0, 0, 0], "scale": [.62, .62, .62]},
    "thirdperson_lefthand": {"rotation": [0, -90, 12], "translation": [0, 0, 0], "scale": [.62, .62, .62]},
    "firstperson_righthand": {"rotation": [0, 80, -32], "translation": [1.8, -1.8, -2.5], "scale": [.54, .54, .54]},
    "firstperson_lefthand": {"rotation": [0, -80, 32], "translation": [1.8, -1.8, -2.5], "scale": [.54, .54, .54]},
    "gui": {"rotation": [0, 0, -36], "translation": [-1, -3, 0], "scale": [.48, .48, .48]},
    "ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [.4, .4, .4]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [.5, .5, .5]},
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def generate():
    helper = ROOT / ".agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py"
    module_spec = importlib.util.spec_from_file_location("bbmodel_generator", helper)
    generator = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(generator)
    # The existing needle and filament are thinner than the default 0.5px grid.
    # Preserve the reverse-engineered dimensions; snapping would erase valid parts.
    generator._snap = lambda value, grid=None: round(value, 6)
    serial = itertools.count()
    generator._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL, f"myriad:hengke/static/{next(serial)}"))
    spec = json.loads((SOURCE / "hengke.spec.json").read_text())
    model, texture, findings = generator.generate(spec)
    if findings:
        raise ValueError(findings)
    if texture is None:
        raise RuntimeError("Pillow is required to embed the finished texture")
    model["meta"] = {"format_version": "4.10", "model_format": "java_block", "box_uv": False}
    model["display"] = DISPLAY
    model["textures"][0].update({"name": "hengke_texture.png", "folder": "item", "namespace": "myriad"})
    elements_by_uuid = {element["uuid"]: element for element in model["elements"]}

    def set_group_pivots(nodes):
        for node in nodes:
            if isinstance(node, dict):
                node["origin"] = elements_by_uuid[node["children"][0]]["origin"]
                set_group_pivots(node["children"])

    set_group_pivots(model["outliner"])
    runtime = {"credit": "Original MYRIAD Hengke / generate_keheng_weapon.py",
               "texture_size": [64, 64],
               "textures": {"0": "myriad:item/hengke", "particle": "myriad:item/hengke"},
               "elements": [], "display": DISPLAY}
    for part, element in zip(spec["parts"], model["elements"]):
        if any(value <= 0 for value in part["size"]):
            raise ValueError(f"Collapsed geometry: {part['id']}")
        if element["from"] != part["pos"] or element["to"] != [round(part["pos"][i] + part["size"][i], 6) for i in range(3)]:
            raise ValueError(f"Geometry drift: {part['id']}")
        exported = {key: element[key] for key in ("name", "from", "to")}
        exported["faces"] = {}
        for name, face in element["faces"].items():
            u1, v1, u2, v2 = face["uv"]
            if not (0 <= min(u1, u2) < max(u1, u2) <= 64 and 0 <= min(v1, v2) < max(v1, v2) <= 64):
                raise ValueError(f"Invalid pixel UV: {element['name']}.{name}")
            exported["faces"][name] = {"uv": [value / 4 for value in face["uv"]], "texture": "#0"}
        nonzero = [(axis, angle) for axis, angle in zip("xyz", element["rotation"]) if angle]
        if nonzero:
            if len(nonzero) != 1 or nonzero[0][1] not in [-45, -22.5, 22.5, 45]:
                raise ValueError(f"Unsupported Java cuboid rotation: {element['name']}")
            exported["rotation"] = {"origin": element["origin"], "axis": nonzero[0][0], "angle": nonzero[0][1]}
        runtime["elements"].append(exported)
    if len({element["uuid"] for element in model["elements"]}) != len(model["elements"]):
        raise ValueError("Duplicate cube UUID")
    if base64.b64decode(model["textures"][0]["source"].split(",", 1)[1]) != texture:
        raise ValueError("Embedded texture differs from delivered PNG")
    (SOURCE / "hengke_texture.png").write_bytes(texture)
    (ASSET / "textures/item/hengke.png").write_bytes(texture)
    write_json(SOURCE / "hengke.bbmodel", model)
    write_json(ASSET / "models/item/hengke.json", runtime)
    for view in ["front", "side", "top", "isometric"]:
        render(model, SOURCE / f"hengke_{view}.png", view,
               size=(720, 1024) if view != "top" else (720, 360))
    sheet = Image.new("RGBA", (1560, 1120), "#17232c")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=22)
    draw.text((36, 24), "HENGKE / TEXTURED MODEL ORTHOGRAPHICS", font=font, fill="#f0ce83")
    for view, x, label in [("front", 0, "FRONT"), ("side", 520, "SIDE"), ("top", 1040, "TOP / ENLARGED")]:
        panel = render(model, view=view, size=(520, 1000), scale=32 if view != "top" else 60)
        sheet.alpha_composite(panel, (x, 80))
        draw.text((x + 30, 78), label, font=font, fill="#a9c4c8")
    draw.text((36, 1080), "Actual cuboids / embedded 64px UV atlas / same blade and grip dimensions", font=font, fill="#a9c4c8")
    sheet.save(SOURCE / "hengke_orthographic.png")
    print(f"Hengke: {len(model['elements'])} cubes, {len(model['elements']) * 6} textured faces; exact geometry, embedded atlas, Java UV conversion validated.")


if __name__ == "__main__":
    generate()
