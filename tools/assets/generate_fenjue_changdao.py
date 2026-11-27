"""Rebuild Fenjue's fitted flame dao, editable Blockbench model, and painted atlas."""

from bisect import bisect_right
import hashlib
import io
import itertools
import json
import math
from pathlib import Path
import sys
import uuid

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / ".agents/skills/minecraft-ai-generate-bbmodel/scripts"))
sys.path.insert(0, str(ROOT / "tools/model-check"))
import generate_bbmodel as bb
from compile_shuo_weapon import hidden

NAME = "fenjue_flame_changdao"
ART = ROOT / "art_source/fenjue"
ASSETS = ROOT / "src/main/resources/assets/myriad"
SIZE = 512
# UV origins at source (0, 0). The four blade panels use 1.5 texels per
# workshop unit, and continue through all sections of the taper.
PANELS = {
    "fold": (36, 541, 1.5),
    "grind": (97, 541, 1.5),
    "edge": (158, 541, 1.5),
    "spine": (219, 541, 1.5),
    "fire": (280, 541, 1.5),
    "bronze": (364, 65, 1),
    "gold": (448, 65, 1),
    "wrap": (364, 194, 1),
    "garnet": (448, 194, 1),
    "iron": (364, 323, 1),
    "coal": (448, 323, 1),
}


def half(value):
    return round(value * 2) / 2


def paint_atlas(source):
    img = Image.new("RGB", (SIZE, SIZE), "#29242a")
    pix = img.load()
    rows = source["blade_sections_y_back_edge"]
    starts = [r[0] for r in rows]
    for mat, (cx, cy, density) in PANELS.items():
        blade = density == 1.5
        for v in range(0 if blade else cy - 62, SIZE if blade else cy + 62):
            y = (cy - v - 0.5) / density
            for u in range(cx - (30 if blade else 40), cx + (30 if blade else 40)):
                x = (u + 0.5 - cx) / density
                _, left, right = rows[max(0, bisect_right(starts, y) - 1)]
                t = (x - left) / (right - left)
                grain = math.sin(x * 0.91 + math.sin(y / 16) * 1.7 + y * 0.034)
                if mat == "fold":
                    # Dark folded steel, a long broken specular band, then the
                    # orange vein and its smaller incandescent core.
                    field = t + 0.055 * math.sin(y / 34) + 0.022 * math.sin(y / 7)
                    color = (
                        "#303039"
                        if field < 0.17
                        else "#4e4c53"
                        if field < 0.38
                        else "#62616a"
                        if field < 0.52
                        else "#4c4e59"
                    )
                    if grain > 0.89:
                        color = {
                            "#303039": "#3b3740",
                            "#4e4c53": "#5b5458",
                            "#62616a": "#706c71",
                            "#4c4e59": "#595a65",
                        }[color]
                    vein = 0.20 + 0.065 * math.sin(y / 22) + 0.036 * math.sin(y / 7.8)
                    distance = abs(t - vein)
                    if y < 324 and distance < 0.09:
                        color = "#702528" if distance > 0.06 else "#c54322" if distance > 0.035 else "#fe973b"
                    if y > 326 and t < 0.26:
                        color = "#5a4949"
                elif mat == "grind":
                    stripe = t + 0.07 * math.sin(y / 32)
                    color = "#81838c" if stripe < 0.70 else "#aaabb2" if stripe < 0.84 else "#d1c9b9"
                    if grain > 0.87:
                        color = "#a2a1a5" if stripe < 0.82 else "#dfd1ba"
                elif mat == "edge":
                    color = "#ffe2a3" if math.sin(y / 29) > -0.4 else "#ffc27c"
                elif mat == "spine":
                    color = "#454047" if grain > 0.55 else "#292b32"
                elif mat == "fire":
                    local, root = min(source["flame_roots"], key=lambda pair: abs(y - pair[0] - 12))
                    h = y - local
                    path = root - 1.4 - (3.7 if 6 < h < 12 else 2 if 12 <= h < 19 else 4.7 if h >= 19 else 0)
                    delta = abs(x - path)
                    color = (
                        "#ffe197"
                        if delta < 0.75 and h < 13
                        else "#ffa13c"
                        if delta < 1.6
                        else "#e65b24"
                        if delta < 3.5
                        else "#902d24"
                    )
                elif mat == "wrap":
                    # Lacquered dark braid crosses over the red leather underlay.
                    first, second = (y + x * 0.67) % 10, (y - x * 0.67) % 10
                    color = (
                        "#27232b"
                        if first < 1.2
                        else "#44343c"
                        if first < 3.9
                        else "#68505a"
                        if first < 4.9
                        else "#322730"
                        if second < 3.8
                        else "#8e3c36"
                        if second < 5.0
                        else "#521e29"
                    )
                elif mat in ("bronze", "gold"):
                    rim = mat == "gold"
                    mid = "#ac784c" if rim else "#80503b"
                    high = "#edc58a" if rim else "#ae7651"
                    dark = "#966542" if rim else "#643c32"
                    color = high if (x * 0.48 + y * 0.2) % 13 < 2 else dark if grain < -0.65 else mid
                    if 30 < y < 51 and abs(x) > 11:
                        line = y - (30.6 + (abs(x) - 8) * 0.62)
                        color = "#492e2d" if 0.5 < line < 2 else high if 2 <= line < 3.6 else color
                    if y < 30 and (math.floor(y) % 9 == 0):
                        color = dark
                elif mat == "garnet":
                    diagonal = x * 0.8 + y * 0.45
                    if y > 27:
                        center = y - 40
                        color = (
                            "#ffca73"
                            if x < 0 and center > -1
                            else "#ff9a42"
                            if center > x
                            else "#cd4422"
                            if x > 0
                            else "#eb662d"
                        )
                        if -2 < x < 0 and 1 < center < 4:
                            color = "#ffe3aa"
                    else:
                        color = ("#ffbf65", "#f38d3c", "#dd582a", "#a43d2b")[math.floor(diagonal / 3) % 4]
                elif mat == "iron":
                    color = "#48414a" if grain > 0.45 else "#312d34"
                else:
                    color = "#773632" if grain > 0.65 else "#48272a"
                pix[u, v] = tuple(bytes.fromhex(color[1:]))

    # Four upward flame tongues engraved into the folded steel. Their asymmetric
    # hooks fork out of the continuous vein and point toward the cutting edge.
    cx, cy, density = PANELS["fold"]
    draw = ImageDraw.Draw(img)
    for base, reach, height in [
        (62, 1.04, 34),
        (103, 1.00, 52),
        (168, 0.95, 54),
        (239, 0.78, 53),
        (295, 0.62, 28),
    ]:
        left = rows[bisect_right(starts, base) - 1][1]

        def polygon(coords, color):
            draw.polygon(
                [
                    (round(cx + density * (left + x * reach)), round(cy - density * (base + h * height)))
                    for x, h in coords
                ],
                fill=color,
            )

        polygon(
            [
                (2, 0),
                (6, 0.19),
                (8, 0.36),
                (8.5, 0.47),
                (13, 0.69),
                (11, 0.64),
                (12.5, 0.81),
                (11.5, 1),
                (10, 0.78),
                (7, 0.64),
                (6, 0.52),
                (4, 0.62),
                (4.8, 0.82),
                (2.8, 0.68),
                (2, 0.46),
                (5, 0.32),
                (4, 0.18),
            ],
            "#7c2d29",
        )
        polygon(
            [
                (2, 0),
                (6, 0.23),
                (6.6, 0.38),
                (9, 0.50),
                (12, 0.70),
                (9, 0.60),
                (10.5, 0.79),
                (11.5, 0.93),
                (9, 0.78),
                (7, 0.65),
                (6.2, 0.48),
                (3.5, 0.56),
                (4, 0.68),
                (2.8, 0.54),
                (5.2, 0.34),
                (4, 0.16),
            ],
            "#d14a26",
        )
        polygon(
            [
                (3, 0.09),
                (5.6, 0.26),
                (5.6, 0.39),
                (7.5, 0.48),
                (10, 0.63),
                (7, 0.55),
                (5.8, 0.46),
                (4, 0.48),
                (5, 0.36),
                (5, 0.26),
            ],
            "#ff9d38",
        )
        polygon([(5, 0.33), (6, 0.39), (7, 0.44), (6.1, 0.42), (5.2, 0.40)], "#ffe291")
    # The cast hilt has chased scrolls on each quillon and a metal lip on the
    # stepped ferrules. Paint on its planar UV, not on a stretched generic tile.
    cx, cy, _ = PANELS["bronze"]
    scroll = [
        (12, 34),
        (15, 34),
        (18, 35),
        (18, 37),
        (16, 37),
        (16, 36),
        (19, 36),
        (21, 38),
        (23, 38),
        (25, 40),
        (27, 41),
        (29, 44),
        (31, 46),
    ]
    for side in (-1, 1):
        for offset, color in [(0, "#d09a65"), (1, "#513231")]:
            draw.line([(cx + side * x, cy - y + offset) for x, y in scroll], fill=color, width=1)
    for y in (-40, -34, -30, 30):
        draw.line((cx - 8, cy - y, cx + 8, cy - y), fill="#b18055")
        draw.line((cx - 8, cy - y + 1, cx + 8, cy - y + 1), fill="#5a3a31")
    data = io.BytesIO()
    img.save(data, format="PNG")
    return data.getvalue()


def surface_uv(material, face, lo, hi):
    x, y, z = lo
    X, Y, Z = hi
    cx, cy, density = PANELS[material]
    # North faces run from model X+ to X-. Both blade faces align with the same
    # engraved line. End faces sample a short Z-depth strip at the real height.
    if face == "north":
        coords = (X, Y, x, y)
    elif face == "south":
        coords = (x, Y, X, y)
    elif face == "east":
        coords = (Z, Y, z, y)
    elif face == "west":
        coords = (z, Y, Z, y)
    else:
        h = Y if face == "up" else y
        coords = (x, h - z, X, h - Z) if face == "up" else (x, h + z, X, h + Z)
    a, b, c, d = coords
    return [
        cx + density * a,
        max(0.5, min(511.5, cy - density * b)),
        cx + density * c,
        max(0.5, min(511.5, cy - density * d)),
    ]


def build_parts(source):
    parts = []

    def box(name, low, high, material, parent=None):
        low, high = list(low), list(high)
        assert all(a < b for a, b in zip(low, high)), name
        part = {
            "id": name,
            "pos": low,
            "size": [b - a for a, b in zip(low, high)],
            "uv_mode": "per_face",
            "faces": {face: {"uv": surface_uv(material, face, low, high)} for face in bb.FACE_DIRS},
        }
        if parent:
            part["parent"] = parent
        parts.append(part)

    def plate(name, side, x, y, X, Y, near, far, material, parent):
        z, Z = sorted([near * side, far * side])
        box(name + ("_front" if side < 0 else "_back"), (x, y, z), (X, Y, Z), material, parent)

    box("grip", (-6.5, -28, -5), (6.5, 28, 5), "wrap")
    for i, y in enumerate(range(-25, 26, 10)):
        box(f"braid_welt_{i}", (-7, y, -5.5), (7, y + 1.5, 5.5), "wrap", "grip")
    for name, y, Y in [("heel", -32, -28), ("throat", 28, 32)]:
        box(name, (-8, y + 1, -6), (8, Y, 6), "bronze", "grip")
        box(name + "_fillet", (-8.5, y, -6.5), (8.5, y + 1, 6.5), "gold", name)
    box("pommel", (-8, -40, -6), (8, -32, 6), "bronze", "grip")
    box("pommel_lip", (-8.5, -42, -6.5), (8.5, -40, 6.5), "gold", "pommel")
    box("coal_socket", (-5, -52, -3), (5, -42, 3), "iron", "pommel")
    for side in (-1, 1):
        plate("coal", side, -3.5, -50.5, 3.5, -43, 3, 3.5, "garnet", "pommel")
        plate("coal_glint", side, -1.5, -48.5, 1.5, -45, 3.5, 4, "gold", "pommel")
    box("guard", (-12, 32, -2), (12, 44, 2), "bronze", "grip")
    wings = source["guard_wings_x_y"]
    cuts = sorted({x for wing in wings for x in wing[:2]})
    for i, (x, X) in enumerate(zip(cuts, cuts[1:])):
        sections = [w for w in wings if w[0] < X and w[1] > x]
        y = min(w[2] for w in sections)
        Y = max(w[3] for w in sections)
        for side in (-1, 1):
            a, b = sorted([side * max(12, x), side * X])
            label = f"quillon_{'left' if side<0 else 'right'}_{i}"
            box(label, (a, y, -2.5), (b, Y - 1.5, 2.5), "bronze", "guard")
            box(label + "_lip", (a, Y - 1.5, -3), (b, Y, 3), "gold", label)
    # Raised copper frame, dark enamel seat, and a bevel-cut ember in its centre.
    for side in (-1, 1):
        for j, (low, high, width) in enumerate([(32, 47, 10), (47, 50, 7), (50, 52, 4)]):
            plate(f"hearth_frame_{j}", side, -width, low, width, high, 2, 3, "gold", "guard")
        for j, (low, high, width) in enumerate(
            [(30, 33, 3), (33, 35, 5.5), (35, 45, 7.5), (45, 48, 5), (48, 50, 2.5)]
        ):
            plate(f"hearth_enamel_{j}", side, -width, low, width, high, 3, 3.5, "coal", "guard")
        for j, (low, high, width) in enumerate(
            [(32, 34, 1.5), (34, 36, 3), (36, 39, 4.5), (39, 43, 5), (43, 46, 3.5), (46, 49, 1.5)]
        ):
            plate(f"ember_facet_{j}", side, -width, low, width, high, 3.5, 4.5, "garnet", "guard")
        for direction in (-1, 1):
            x, X = sorted((direction * 5.5, direction * 7.5))
            plate(f"claw_{direction}", side, x, 38, X, 42, 3.5, 4, "gold", "guard")
    rows = source["blade_sections_y_back_edge"]
    for i, (y, left, right) in enumerate(rows):
        Y = rows[i + 1][0] if i + 1 < len(rows) else source["tip_y"]
        width = right - left
        # A thicker back, a raised central face, a ground bevel, and a narrow
        # hot lip share boundaries instead of stacking coplanar painted boxes.
        boundaries = [
            left,
            half(left + width * 0.095),
            half(left + width * 0.65),
            half(left + width * 0.90),
            right,
        ]
        if width <= 3:
            box(f"point_{i:02}", (left, y, -2), (right, Y, 2), "edge" if width <= 1.5 else "grind", "fold_00")
            continue
        for name, a, b, depth in zip(
            ("spine", "fold", "grind", "edge"), boundaries, boundaries[1:], (2, 2.5, 1.5, 0.5)
        ):
            if a < b:
                box(f"{name}_{i:02}", (a, y, -depth), (b, Y, depth), name, "guard" if i == 0 else "fold_00")

    for i, (y, x) in enumerate(source["flame_roots"]):
        # A hooked flame on the back edge. Disjoint height bands keep the
        # silhouette open and give the base more depth than the fading tip.
        profile = [
            (0, 3, -3.5, 2, 1.5),
            (3, 6, -4, 1.5, 1.5),
            (6, 9, -7, 0, 2),
            (9, 12, -7, -0.5, 1.5),
            (12, 15, -5.5, -2, 1.5),
            (15, 18, -5, -1, 1),
            (18, 21, -6, -2.5, 1),
            (21, 24, -7, -4.5, 0.5),
        ]
        for j, (a, b, l, r, depth) in enumerate(profile):
            box(
                f"flare_{i}_{j}",
                (x + l, y + a, -depth),
                (x + r, y + b, depth),
                "fire",
                "fold_00" if j == 0 else f"flare_{i}_0",
            )
    return parts


def main():
    source_path = ART / (NAME + ".spec.json")
    source = json.loads(source_path.read_text())
    texture = paint_atlas(source)
    spec = {
        "meta": {"name": NAME},
        "target": "item",
        "parts": build_parts(source),
        "atlas": {"width": SIZE, "height": SIZE},
    }
    bb.validate_schema(spec)
    bb.validate_geometry(spec, strict=True)
    ids = itertools.count()
    bb._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL, f"myriad/{NAME}/{next(ids)}"))
    model = bb.build_bbmodel(spec, SIZE, SIZE, texture, NAME + ".png")
    bb.validate_bbmodel(model, spec)
    elements = model["elements"]
    unit = source["source_units_per_model_unit"]
    for element in elements:
        for key in ("from", "to", "origin"):
            element[key] = [8 + n / unit for n in element[key]]
    model["meta"]["model_format"] = "java_block"
    model["display"] = source["display"]
    game = []
    for element in elements:
        faces = {
            face: {"texture": "#layer0", "uv": [value * 16 / SIZE for value in data["uv"]]}
            for face, data in element["faces"].items()
            if not hidden(element, face, elements)
        }
        if faces:
            game.append({**{k: element[k] for k in ("name", "from", "to")}, "faces": faces})
    item = {
        "ambientocclusion": False,
        "textures": {"particle": "#layer0", "layer0": "myriad:item/" + NAME},
        "display": source["display"],
        "myriad_source_sha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
        "elements": game,
    }
    (ART / (NAME + ".bbmodel")).write_text(json.dumps(model, separators=(",", ":")) + "\n")
    (ASSETS / "models/item/lieyanjian.json").write_text(json.dumps(item, indent=2) + "\n")
    (ASSETS / ("textures/item/" + NAME + ".png")).write_bytes(texture)
    print(
        f"{NAME}: {len(elements)} editable cubes, {len(game)} exported cubes, {sum(len(e['faces']) for e in game)} exported faces"
    )


if __name__ == "__main__":
    main()
