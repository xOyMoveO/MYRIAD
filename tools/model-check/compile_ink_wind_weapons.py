"""Export the approved Folded Ink Standard and Celadon Wind Blade for Fabric."""

import base64
import json
from itertools import product
from pathlib import Path

from compile_shuo_weapon import hidden
from held_display_math import blade_edge_forward, matrix_xyz


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source" / "ink-wind-weapons"
ASSETS = ROOT / "src/main/resources/assets/myriad"


def display(left, rotation, translation, scale):
    if left:
        rotation = [rotation[0], -rotation[1], -rotation[2]]
        translation = [-translation[0], translation[1], translation[2]]
    return {"rotation": rotation, "translation": translation, "scale": [scale] * 3}


def thumbnail(corners, rotation, limit):
    matrix = matrix_xyz(rotation)
    pixels = [[sum(row[i] * (point[i] - 8) for i in range(3)) for row in matrix]
              for point in corners]
    axes = list(zip(*pixels))
    mids = [(min(values) + max(values)) / 2 for values in axes]
    span = max(max(values) - min(values) for values in axes[:2])
    scale = limit / span
    return display(False, rotation, [round(-value * scale, 5) for value in mids], scale)


def element_corners(part):
    corners = product(*zip(part["from"], part["to"]))
    if not any(part["rotation"]):
        yield from corners
        return
    matrix = matrix_xyz(part["rotation"])
    origin = part["origin"]
    for point in corners:
        yield [origin[axis] + sum(matrix[axis][i]*(point[i]-origin[i]) for i in range(3))
               for axis in range(3)]


def compile_item(name, item, scale, sword, *, source=SOURCE, stored_y_scale=None, thirdperson_ring=False):
    data = json.loads((source / f"{name}.bbmodel").read_text(encoding="utf-8"))
    width, height = data["resolution"]["width"], data["resolution"]["height"]
    grip = next(part for part in data["elements"] if part["name"] == "wrapped_grip")
    pivot = [(a + b) / 2 for a, b in zip(grip["from"], grip["to"])]

    native_y = scale if stored_y_scale is None else stored_y_scale
    y_stretch = scale / native_y
    if stored_y_scale is not None and any(any(p["rotation"]) for p in data["elements"]):
        raise ValueError("Axis fitting is only supported for unrotated source cuboids")

    def position(point):
        return [round(8 + (value - origin) * fit, 6)
                for value, origin, fit in zip(point, pivot, (scale, native_y, scale))]

    parts = [{**part, **{key: position(part[key]) for key in ("from", "to", "origin")}}
             for part in data["elements"]]
    stationary = [part for part in parts if not any(part["rotation"])]
    elements = []
    for part in parts:
        faces = {}
        for side, face in part["faces"].items():
            if not any(part["rotation"]) and hidden(part, side, stationary):
                continue
            faces[side] = {"uv": [round(v * 16 / (width if i % 2 == 0 else height), 6)
                                  for i, v in enumerate(face["uv"])], "texture": "#weapon"}
        if not faces:
            continue
        element = {key: part[key] for key in ("name", "from", "to")}
        if part.get("light_emission", 0):
            element["light_emission"] = part["light_emission"]
        assert all(-16 <= value <= 32 for key in ("from", "to") for value in part[key])
        if any(part["rotation"]):
            element["rotation"] = dict(zip(("x", "y", "z"), part["rotation"]),
                                       origin=part["origin"], rescale=False)
        elements.append({**element, "faces": faces})
    poses = {}
    for side, left in (("righthand", False), ("lefthand", True)):
        poses[f"firstperson_{side}"] = display(left, [0, -20 if sword else 7, 15],
                                              [0, -.65, .75], .77 if sword else .82)
        poses[f"thirdperson_{side}"] = display(left, [-8, 0, 0], [0, 0, 1.4], .94)
    if thirdperson_ring:
        joints = [p for p in parts if p["name"].startswith("jade_loop_joint_")]
        if len(joints) != 4:
            raise ValueError("Expected the four corners of the jade carrying ring")
        x, y, z = [sum((p["from"][i] + p["to"][i]) / 2 - 8 for p in joints) / 4
                   for i in range(3)]
        # Vanilla's hand layer rotates -90 X / 180 Y before applying the item pose.
        # A +90 X item rotation leaves the bell mouth below the fist. The extra
        # -2 Y cancels the layer's forward offset to put the ring inside the palm.
        for side, left in (("righthand", False), ("lefthand", True)):
            poses[f"thirdperson_{side}"] = display(left, [90, 0, 0],
                                                  [round(-x*.94, 6), round(-2+z*.94, 6), round(-y*.94, 6)], .94)
    if sword:
        poses = blade_edge_forward(poses)
    corners = [point for part in parts for point in element_corners(part)]
    # Fit the thumbnail to the actual presented proportions, not the compressed JSON bounds.
    if stored_y_scale is not None:
        corners = [(x, 8+(y-8)*y_stretch, z) for x,y,z in corners]
    poses["gui"] = thumbnail(corners, [11, 0, -31], 13.75)
    poses["fixed"] = thumbnail(corners, [0, 180, -12], 13.25)
    poses["ground"] = thumbnail(corners, [0, 0, 90], 9.5)
    poses["ground"]["translation"][1] += 1.25
    for pose in poses.values():
        pose["scale"][1] *= y_stretch
    tex = f"myriad:item/{item}"
    model = {"gui_light": "front", "ambientocclusion": False,
             "textures": {"weapon": tex, "particle": tex}, "elements": elements, "display": poses}
    (ASSETS / "models" / "item" / f"{item}.json").write_text(
        json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    atlas = base64.b64decode(data["textures"][0]["source"].split(",", 1)[1])
    (ASSETS / "textures" / "item" / f"{item}.png").write_bytes(atlas)
    print(f"{item}: {len(elements)} parts, {sum(len(e['faces']) for e in elements)} visible quads")


if __name__ == "__main__":
    compile_item("xuanchen_ink_standard", "zhenqi", .79, False)
    compile_item("sulan_celadon_blade", "fengren", .66, True)
