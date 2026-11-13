"""Compile Hong Lie's reviewed asset specs; does not touch icons or audio.

Run with python (Pillow required). The project-local Blockbench skill supplies the
spec compiler. Existing sub-pixel rails are intentional, so preserve six decimal
places instead of its default half-pixel snapping.
"""
from pathlib import Path
import base64
import importlib.util
import io
import json
import uuid
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/yike"
ASSET = ROOT / "src/main/resources/assets/myriad"
NAMES = ("hong_lie_frame", "hong_lie_petal", "hong_lie_ring", "hong_lie")


def load_compiler():
    path = ROOT / ".agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py"
    module_spec = importlib.util.spec_from_file_location("yike_spec_compiler", path)
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    module._snap = lambda value: round(value, 6)
    return module


def atlas_image(spec):
    """Paint restrained enamel, leather, brass and crystal from named regions."""
    atlas = spec["atlas"]
    image = Image.new("RGBA", (atlas["width"], atlas["height"]), "#282137")
    draw = ImageDraw.Draw(image)
    for region in atlas["regions"]:
        x, y, w, h = region["rect"]
        name = region["id"]
        base = region["fill"]
        dark, light = region["palette"]
        draw.rectangle((x, y, x+w-1, y+h-1), fill=base)
        # Two restrained edge steps are material detail, not baked scene lighting.
        draw.rectangle((x+2, y+2, x+w-3, y+h-3), outline=dark, width=2)
        draw.line((x+5, y+h-6, x+5, y+5, x+w-6, y+5), fill=light, width=1)
        if name == "wrapped_violet":
            for yy in range(y+10, y+h-6, 8):
                draw.line((x+6, yy+3, x+w-7, yy-3), fill=dark, width=2)
                draw.line((x+6, yy+2, x+w-7, yy-4), fill=light, width=1)
        elif name in ("coral_enamel", "coral_root"):
            draw.line((x+32, y+13, x+32, y+50), fill=dark, width=2)
            for yy in (y+20, y+43):
                draw.line((x+32, yy-5, x+37, yy, x+32, yy+5,
                           x+27, yy, x+32, yy-5), fill="#D4A662", width=1)
        elif name == "violet_lining":
            draw.line((x+23, y+10, x+23, y+53), fill=dark, width=2)
            draw.line((x+41, y+10, x+41, y+53), fill=dark, width=2)
            draw.line((x+32, y+18, x+32, y+46), fill=light, width=1)
        elif name in ("brass", "pale_gold", "rail_brass"):
            for xx in (x+15, x+47):
                draw.line((xx, y+9, xx, y+54), fill=dark, width=1)
            for yy in (y+12, y+51):
                draw.rectangle((x+29, yy-1, x+33, yy+1), fill=light)
        elif name == "warm_crystal":
            draw.rectangle((x+25, y+8, x+38, y+55), fill="#FFF9EC")
            draw.line((x+21, y+8, x+21, y+55), fill=dark, width=1)
            draw.line((x+43, y+8, x+43, y+55), fill=light, width=1)
    for detail in spec.get("face_details", []):
        if detail["kind"] == "texture":
            x, y, w, h = detail["uv"]
            draw.rectangle((x, y, x+w-1, y+h-1), fill=detail["color"])
    return image


def runtime_model(model, old_model):
    result = {key: old_model[key] for key in ("ambientocclusion", "gui_light", "textures", "display")}
    result["credit"] = "MYRIAD original; art_source/yike/*.spec.json -> tools/assets/generate_yike_weapon.py"
    result["elements"] = []
    width, height = model["resolution"]["width"], model["resolution"]["height"]
    for part in model["elements"]:
        item = {"name": part["name"], "from": part["from"], "to": part["to"], "faces": {}}
        for side, face in part["faces"].items():
            u1, v1, u2, v2 = face["uv"]
            item["faces"][side] = {"uv": [u1*16/width, v1*16/height, u2*16/width, v2*16/height], "texture": "#weapon"}
        rotations = [(axis, angle) for axis, angle in zip("xyz", part["rotation"]) if angle]
        if rotations:
            assert len(rotations) == 1, "Java item elements use one rotation axis"
            axis, angle = rotations[0]
            assert angle in (-45, -22.5, 22.5, 45), (part["name"], angle)
            item["rotation"] = {"origin": part["origin"], "axis": axis, "angle": angle, "rescale": False}
        result["elements"].append(item)
    return result


def validate(model, runtime, png):
    texture = model["textures"][0]
    assert texture["internal"] and not texture["saved"] and "path" not in texture
    assert base64.b64decode(texture["source"].split(",", 1)[1]) == png
    image = Image.open(io.BytesIO(png))
    assert image.size == (256, 256) and image.getextrema()[3] == (255, 255)
    assert len({p["uuid"] for p in model["elements"]}) == len(model["elements"])
    for source, exported in zip(model["elements"], runtime["elements"]):
        assert all(b > a for a, b in zip(source["from"], source["to"])), source["name"]
        assert source["from"] == exported["from"] and source["to"] == exported["to"]
        assert set(source["faces"]) == {"north", "south", "east", "west", "up", "down"}
        for side, face in source["faces"].items():
            assert face["texture"] == 0
            assert all(0 <= value <= 256 for value in face["uv"])
            assert face["uv"][2] > face["uv"][0] and face["uv"][3] > face["uv"][1]
            assert all(abs(value/16-out) < 1e-10 for value, out in zip(face["uv"], exported["faces"][side]["uv"]))
            uv = face["uv"]
            assert image.getpixel((int((uv[0]+uv[2])/2), int((uv[1]+uv[3])/2)))[3] == 255


def generate():
    compiler = load_compiler()
    atlas = None
    for name in NAMES:
        spec = json.loads((SOURCE / f"{name}.spec.json").read_text())
        compiler.validate_schema(spec)
        compiler.validate_geometry(spec)
        image = atlas_image(spec)
        buf = io.BytesIO()
        image.save(buf, format="PNG")
        png = buf.getvalue()
        if atlas is None:
            atlas = png
        assert atlas == png, "All four parts must share the same material atlas"
        counter = iter(range(1000))
        compiler._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL, name+":"+str(next(counter))))
        model = compiler.build_bbmodel(spec, 256, 256, png, "hong_lie")
        model["meta"].update({"format_version": "4.10", "model_format": "java_block"})
        model["credit"] = "MYRIAD original; reviewed asset spec with preserved sub-pixel geometry"
        findings = compiler.validate_bbmodel(model, spec)
        assert not findings, findings
        path = ASSET / "models/item" / f"{name}.json"
        runtime = runtime_model(model, json.loads(path.read_text()))
        model["display"] = runtime["display"]
        validate(model, runtime, png)
        (SOURCE / f"{name}.bbmodel").write_text(json.dumps(model, indent=2)+"\n")
        path.write_text(json.dumps(runtime, indent=2)+"\n")
        print(f"OK {name}: {len(model['elements'])} cuboids, {len(model['elements'])*6} UV faces, embedded atlas")
    (SOURCE / "hong_lie_atlas.png").write_bytes(atlas)
    (ASSET / "textures/item/hong_lie.png").write_bytes(atlas)


if __name__ == "__main__":
    generate()
