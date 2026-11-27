"""Build Tianxing's Java item and editable Blockbench model from the same geometry and atlas."""

from __future__ import annotations

import hashlib
import importlib.util
import io
import itertools
import json
import math
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/tianxing"
ASSETS = ROOT / "src/main/resources/assets/myriad"
NAME = "tianxing_thunder_longsword"
BLADE_MATERIALS = {"steel", "edge", "fuller"}
DIRECTIONS = ("north", "south", "east", "west", "up", "down")
METALS = {
    "chased_gold": ("#6b4529", "#ab7440", "#cd9955", "#f1cd89", "#fff0b7"),
    "wing": ("#604229", "#a17148", "#d4a367", "#f4d796", "#fff0c6"),
    "pale_gold": ("#96703e", "#c4985a", "#e3bd79", "#f9dfa0", "#fff3c5"),
    "enamel": ("#152143", "#253b73", "#305da0", "#477fc9", "#8cb7ee"),
    "ink": ("#0d1a34", "#192e50", "#304d71", "#4e7192", "#89aeca"),
    "edge": ("#859ab3", "#b7cede", "#d6e9f5", "#eef8ff", "#f6fdff"),
    "steel": ("#637899", "#8aa3bd", "#b2cadc", "#d1e1ee", "#eaf6ff"),
    "fuller": ("#142343", "#254477", "#365c8c", "#5890bd", "#a1d2e6"),
    "ribbon": ("#344567", "#51678a", "#7892ae", "#acbdd0", "#d5e1e2"),
}


def burnished_tile(material, w, h, decorated):
    palette = METALS[material]
    image = Image.new("RGB", (w, h))
    for y in range(h):
        v = (y + 0.5) / h
        for x in range(w):
            u = (x + 0.5) / w
            band = 0.20 + 0.68 * (1 - v) + 0.15 * math.cos(5.4 * u + 2.5 * v)
            tone = max(0, min(4, int(band * 4)))
            image.putpixel((x, y), tuple(bytes.fromhex(palette[tone][1:])))
    d = ImageDraw.Draw(image)
    if h >= 4:
        d.line([(0, 0), (w - 1, 0)], fill=palette[3])
        d.line([(0, h - 1), (w - 1, h - 1)], fill=palette[0])
    if decorated and w >= 7 and h >= 6:
        # Pairs of incised hooks alternate their direction along the quillons.
        if material in {"wing", "chased_gold"}:
            y = h // 2
            path = [
                (0, y + 1),
                (w // 3, y + 1),
                (w // 2, y - 1),
                (w // 2 + 2, y - 1),
                (w // 2 + 2, y + 1),
                (w - 3, y + 1),
                (w - 1, y - 1),
            ]
            d.line([(x, yy + 1) for x, yy in path], fill=palette[3])
            d.line(path, fill=palette[0])
        elif material == "enamel":
            d.line([(2, h - 2), (w // 2, h - 2), (w - 3, 2)], fill=palette[3])
    return image


def detail_tile(material, w, h, decorated):
    if material in METALS:
        return burnished_tile(material, w, h, decorated)
    image = Image.new("RGB", (w, h))
    if material == "stone":
        # In the model each square gemstone is set on a diagonal. Its asymmetric
        # facets survive mirroring and show the change of depth towards the table.
        colors = ["#1b3065", "#284c97", "#3069bc", "#3889d3", "#80cbe9", "#d6fbfc"]
        d = ImageDraw.Draw(image)

        def poly(points, color):
            d.polygon(
                [(round(x * (w - 1)), round(y * (h - 1))) for x, y in points],
                fill=color,
            )

        near, far = 0.22, 0.77
        poly([(0, 0), (1, 0), (far, near), (near, near)], colors[4])
        poly([(0, 0), (near, near), (near, far), (0, 1)], colors[3])
        poly([(0, 1), (near, far), (far, far), (1, 1)], colors[0])
        poly([(1, 0), (1, 1), (far, far), (far, near)], colors[1])
        poly([(near, near), (far, near), (far, far), (near, far)], colors[2])
        poly([(near, near), (far, near), (0.46, 0.48), (near, far)], colors[3])
        if min(w, h) >= 7:
            d.line([(w // 4, h // 4), (w // 2, h // 4)], fill=colors[5])
        if min(w, h) >= 11:
            # A cut lightning seal lies across the large stone table.
            poly(
                [
                    (0.22, 0.68),
                    (0.49, 0.35),
                    (0.47, 0.48),
                    (0.71, 0.34),
                    (0.44, 0.66),
                    (0.46, 0.53),
                ],
                colors[5],
            )
    elif material == "weave":
        palette = ["#14233e", "#243956", "#2d496b", "#3a5a7b", "#7391ab"]
        for y in range(h):
            for x in range(w):
                turn, strand = divmod((y + x) % 12, 6)
                cross = (y - x) % 12
                index = 0 if strand == 0 else (3 if strand == 1 else 1 + (strand < 4))
                if turn and cross in (2, 3):
                    index = 4 if cross == 2 else 2
                image.putpixel((x, y), tuple(bytes.fromhex(palette[index][1:])))
    else:
        light = material == "silk_blue"
        palette = (
            ("#204684", "#3569b7", "#6293d3")
            if light
            else ("#162a50", "#213e68", "#446489")
        )
        for y in range(h):
            for x in range(w):
                k = (x + y // 8) % 5
                image.putpixel(
                    (x, y), tuple(bytes.fromhex(palette[min(2, k // 2)][1:]))
                )
    return image


def blade_panel(part, density):
    # A single XY island per blade section lets the edge, body and raised fuller
    # share the same engraving continuously despite their different depths.
    x0, y0, _ = part["pos"]
    w, length, _ = part["size"]
    y1 = y0 + length
    width, height = max(2, round(w * density)), max(1, round(length * density))
    image = Image.new("RGB", (width, height))
    cold = ["#69839e", "#819db9", "#9ab3ca", "#b4c9d9", "#cbdde9", "#e1edf3"]
    for j in range(height):
        y = y1 - (j + 0.5) * length / height
        for i in range(width):
            x = x0 + (i + 0.5) * w / width - 8
            t = 2 * x / w
            if t < -0.81:
                color = "#e9f5f9" if t < -0.92 else "#cedeeb"
            elif t > 0.81:
                color = "#d6e6ee" if t > 0.91 else "#b2c9d8"
            else:
                # Long, interrupted polishing marks run with the steel.
                band = 2.25 + 1.0 * math.sin(y * 1.13 + t * 1.8) - t * 0.95
                if math.floor(y * 4 + x * 11) % 24 == 0:
                    band += 0.6
                color = cold[max(0, min(5, int(band + 0.6)))]
            image.putpixel((i, j), tuple(bytes.fromhex(color[1:])))
    draw = ImageDraw.Draw(image)

    def xy(x, y):
        return (
            round((8 + x - x0) / w * width - 0.5),
            round((y1 - y) / length * height - 0.5),
        )

    def stroke(points, color, pen=1):
        draw.line([xy(x, y) for x, y in points], fill=color, width=pen)

    for side in [-1, 1]:
        stroke(
            [
                (side * 0.21, 11.65),
                (side * 0.21, 26.8),
                (side * 0.08, 28.68),
                (0, 29.12),
            ],
            "#526f92",
        )
        stroke(
            [(side * 0.24, 11.78), (side * 0.24, 26.62), (side * 0.115, 28.59)],
            "#c2d8e5",
        )
    anchors = [
        (0, 11.35),
        (-0.065, 12.23),
        (0.11, 13.16),
        (-0.11, 13.03),
        (0.06, 15.38),
        (-0.115, 16.38),
        (0.11, 16.19),
        (-0.04, 18.12),
        (0.10, 18.96),
        (-0.13, 18.78),
        (0.06, 21.12),
        (-0.105, 22.12),
        (0.11, 21.97),
        (-0.06, 23.65),
        (0.1, 24.75),
        (-0.11, 24.58),
        (0.055, 26.32),
        (0, 28.65),
    ]
    stroke(anchors, "#223558", 5)
    stroke(anchors, "#306fb7", 3)
    stroke(anchors, "#a3eaf3")
    for base in [13.15, 18.9, 24.68]:
        for side in [-1, 1]:
            lift = 0.22 if side > 0 else -0.10
            rune = [
                (side * 0.03, base),
                (side * 0.27, base + 0.10 + lift),
                (side * 0.24, base + 0.28 + lift),
                (side * 0.46, base + 0.62 + lift),
                (side * 0.32, base + 0.52 + lift),
                (side * 0.39, base + 0.82 + lift),
            ]
            stroke(rune, "#5c639f", 2)
            stroke([(side * 0.19, base + 0.15), (side * 0.38, base + 0.42)], "#a9a4e0")
    return image


def atlas_faces(spec):
    size = spec["atlas"]["width"]
    density = spec["atlas"]["texels_per_unit"]
    parts = spec["parts"]
    sections = {}
    tiles, placements = {}, {}
    for p in parts:
        if p["material"] != "steel":
            continue
        i = p["detail"]
        edges = [e for e in parts if e["material"] == "edge" and e["detail"] == i]
        x0 = min(e["pos"][0] for e in edges)
        full_width = 2 * (8 - x0)
        panel = {"pos": [x0, p["pos"][1], 0], "size": [full_width, p["size"][1], 0]}
        tiles["blade", i] = blade_panel(panel, density)
        sections[i] = (x0, full_width)

    def face_key(p, face):
        w, h, d = p["size"]
        mat = p["material"]
        upright = face in {"north", "south"}
        if upright and mat in BLADE_MATERIALS:
            return ("blade", p["detail"])
        u, v = (w, h) if upright else (d, h) if face in {"east", "west"} else (w, d)
        key = (mat, max(1, round(u * density)), max(1, round(v * density)), upright)
        if key not in tiles:
            tiles[key] = detail_tile(*key)
        return key

    for p in parts:
        for face in DIRECTIONS:
            face_key(p, face)
    # A pair of extruded border texels keeps neighbouring islands off filtered rims.
    atlas = Image.new("RGB", (size, size), "#819db9")
    x = y = row = 0
    pad = 2
    for key, im in sorted(tiles.items(), key=lambda item: -item[1].height):
        w, h = im.size
        if x + w + 2 * pad > size:
            x = 0
            y += row
            row = 0
        if y + h + 2 * pad > size:
            raise ValueError(f"Atlas full at {key}")
        u, v = x + pad, y + pad
        atlas.paste(im, (u, v))
        for n in range(1, pad + 1):
            atlas.paste(im.crop((0, 0, 1, h)), (u - n, v))
            atlas.paste(im.crop((w - 1, 0, w, h)), (u + w + n - 1, v))
        wide = atlas.crop((x, v, x + w + 2 * pad, v + h))
        for n in range(1, pad + 1):
            atlas.paste(wide.crop((0, 0, wide.width, 1)), (x, v - n))
            atlas.paste(wide.crop((0, h - 1, wide.width, h)), (x, v + h + n - 1))
        placements[key] = (u, v, w, h)
        x += w + 2 * pad
        row = max(row, h + 2 * pad)
    for p in parts:
        faces = {}
        for face in DIRECTIONS:
            key = face_key(p, face)
            u, v, w, h = placements[key]
            u0, u1 = u, u + w
            if key[0] == "blade":
                x0, full_width = sections[p["detail"]]
                u0 = u + (p["pos"][0] - x0) * w / full_width
                u1 = u0 + p["size"][0] * w / full_width
                if face == "north":
                    u0, u1 = u1, u0
            elif p.get("detail") == "left" and face in ("north", "south"):
                u0, u1 = u1, u0
            faces[face] = {"uv": [round(u0, 6), v, round(u1, 6), v + h]}
        p.update(uv_mode="per_face", faces=faces)
    return atlas


def main():
    spec_file = ART / f"{NAME}.spec.json"
    spec = json.loads(spec_file.read_text())
    spec_hash = hashlib.sha256(spec_file.read_bytes()).hexdigest()
    atlas = atlas_faces(spec)
    buf = io.BytesIO()
    atlas.save(buf, format="PNG")
    texture = buf.getvalue()
    helper = (
        ROOT
        / ".agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py"
    )
    loader = importlib.util.spec_from_file_location("bbmodel_generator", helper)
    generator = importlib.util.module_from_spec(loader)
    loader.loader.exec_module(generator)
    generator._snap = lambda value, grid=None: round(value, 6)
    sequence = itertools.count()
    generator._uuid = lambda: str(
        uuid.uuid5(uuid.NAMESPACE_URL, f"myriad/tianxing/{next(sequence)}")
    )
    generator.validate_schema(spec)
    findings = generator.validate_geometry(spec)
    model = generator.build_bbmodel(
        spec, atlas.width, atlas.height, texture, NAME + ".png"
    )
    findings += generator.validate_bbmodel(model, spec)
    if findings:
        raise ValueError(findings)
    model["meta"] = {
        "format_version": "4.10",
        "model_format": "java_block",
        "box_uv": False,
    }
    model["display"] = spec["display"]
    model["textures"][0].update(folder="item", namespace="myriad")
    groups = {}
    runtime = {
        "ambientocclusion": False,
        "textures": {"particle": "#layer0", "layer0": "myriad:item/" + NAME},
        "elements": [],
        "display": spec["display"],
        "myriad_source_sha256": spec_hash,
    }
    for p, el in zip(spec["parts"], model["elements"]):
        group = groups.setdefault(p["group"], [])
        group.append(el["uuid"])
        item = {k: el[k] for k in ("name", "from", "to")}
        if p.get("rot"):
            item["rotation"] = {
                "origin": el["origin"],
                "axis": "z",
                "angle": el["rotation"][2],
            }
        item["faces"] = {
            k: {
                "uv": [round(n * 16 / atlas.width, 9) for n in f["uv"]],
                "texture": "#layer0",
            }
            for k, f in el["faces"].items()
        }
        runtime["elements"].append(item)
    model["outliner"] = [
        {
            "name": name,
            "origin": [8, 8, 8],
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "myriad/tianxing/" + name)),
            "export": True,
            "isOpen": False,
            "children": children,
        }
        for name, children in groups.items()
    ]
    for dst, data in [
        (ART / (NAME + ".bbmodel"), model),
        (ASSETS / "models/item/leitingjian.json", runtime),
    ]:
        dst.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    (ASSETS / "textures/item" / (NAME + ".png")).write_bytes(texture)
    print(
        f"{len(model['elements'])} cuboids, {len(groups)} editable groups, {len(atlas.getcolors(65536))} atlas colors"
    )


if __name__ == "__main__":
    main()
