"""Compile approved cuboid specs into cached, textured runtime faces.

Retains every visible source part. Fully covered axis-aligned faces are removed;
partially covered faces stay intact, avoiding topology growth and UV distortion.
No .bbmodel is authored by this compiler.
"""
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import shutil
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "model-check"))
from held_display_math import blade_edge_forward


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def rotate(p, origin, degrees):
    x, y, z = [p[i] - origin[i] for i in range(3)]
    a, b, c = [math.radians(n) for n in degrees]
    y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    x, z = x * math.cos(b) + z * math.sin(b), -x * math.sin(b) + z * math.cos(b)
    x, y = x * math.cos(c) - y * math.sin(c), x * math.sin(c) + y * math.cos(c)
    return [x + origin[0], y + origin[1], z + origin[2]]


def subtract(rect, blocker):
    x0, y0, x1, y1 = rect
    a, b, c, d = blocker
    a, b, c, d = max(x0, a), max(y0, b), min(x1, c), min(y1, d)
    if a >= c or b >= d:
        return [rect]
    return [r for r in [[x0, y0, a, y1], [c, y0, x1, y1], [a, y0, c, b], [a, d, c, y1]]
            if r[0] < r[2] and r[1] < r[3]]


def face_corners(lo, hi, name):
    x, y, z = lo
    X, Y, Z = hi
    return {
        "north": [[X, y, z], [x, y, z], [x, Y, z], [X, Y, z]],
        "south": [[x, y, Z], [X, y, Z], [X, Y, Z], [x, Y, Z]],
        "east": [[X, y, Z], [X, y, z], [X, Y, z], [X, Y, Z]],
        "west": [[x, y, z], [x, y, Z], [x, Y, Z], [x, Y, z]],
        "up": [[x, Y, Z], [X, Y, Z], [X, Y, z], [x, Y, z]],
        "down": [[x, y, z], [X, y, z], [X, y, Z], [x, y, Z]],
    }[name]


def compile_parts(parts, shade):
    boxes = [(p, p["pos"], [p["pos"][i] + p["size"][i] for i in range(3)]) for p in parts
             if not any(p.get("rot", [0, 0, 0]))]
    faces, removed = [], 0
    for p in parts:
        lo = p["pos"]
        hi = [lo[i] + p["size"][i] for i in range(3)]
        rotated = any(p.get("rot", [0, 0, 0]))
        for name, face in p["faces"].items():
            axis, sign = {"north": (2, -1), "south": (2, 1), "east": (0, 1),
                          "west": (0, -1), "up": (1, 1), "down": (1, -1)}[name]
            plane = hi[axis] if sign > 0 else lo[axis]
            other_axes = [i for i in range(3) if i != axis]
            remaining = [[lo[other_axes[0]], lo[other_axes[1]], hi[other_axes[0]], hi[other_axes[1]]]]
            if not rotated:
                for q, qlo, qhi in boxes:
                    if q["id"] == p["id"]:
                        continue
                    blocks = (qlo[axis] <= plane + 1e-7 and qhi[axis] > plane + 1e-7) if sign > 0 else (
                        qlo[axis] < plane - 1e-7 and qhi[axis] >= plane - 1e-7)
                    if not blocks:
                        continue
                    r = [qlo[other_axes[0]], qlo[other_axes[1]], qhi[other_axes[0]], qhi[other_axes[1]]]
                    remaining = [s for rectangle in remaining for s in subtract(rectangle, r)]
                    if not remaining:
                        break
                if not remaining:
                    removed += 1
                    continue
            corners = [rotate(v, p.get("pivot", lo), p.get("rot", [0, 0, 0]))
                       for v in face_corners(lo, hi, name)]
            a = [corners[1][i] - corners[0][i] for i in range(3)]
            b = [corners[2][i] - corners[0][i] for i in range(3)]
            normal = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
            norm = math.sqrt(sum(n * n for n in normal))
            assert norm > 0, p["id"]
            value = max(.62, min(1, .82 + sum(normal[i] / norm * [-.18, .62, -.35][i] for i in range(3)) * .3)) if shade else 1
            channel = round(value * 255)
            rgb = channel << 16 | channel << 8 | channel
            uv = [n / 128 for n in face["uv"]]
            faces.append([round(n, 7) for v in corners for n in v] + uv + [rgb])
    return {"parts": len(parts), "culled": removed, "faces": faces}


def compile_held_item(body, source_hash, art, project):
    """26.2 supports native Euler XYZ element rotations; no custom renderer needed.

    Grip centre (-10.5 source Y) becomes model (8,8,8). All extents fit the real
    vanilla [-16,32] parser. Keep the hand centred on the grip, not on the blade.
    """
    def position(v):
        return [round(8 + v[0] * .2, 7), round(8 + (v[1] + 10.5) * .2, 7), round(8 + v[2] * .2, 7)]

    elements = []
    for p in body["parts"]:
        lo = position(p["pos"])
        hi = position([p["pos"][i] + p["size"][i] for i in range(3)])
        assert all(-16 <= n <= 32 for n in lo + hi), p["id"]
        element = {"name": p["id"], "from": lo, "to": hi,
                   "faces": {k: {"uv": [n / 8 for n in face["uv"]], "texture": "#blade"}
                             for k, face in p["faces"].items()}}
        if any(p.get("rot", [0, 0, 0])):
            element["rotation"] = dict(zip(["x", "y", "z"], p["rot"]))
            element["rotation"].update(origin=position(p.get("pivot", p["pos"])), rescale=False)
        elements.append(element)
    model = {"format_version": "1.21.11", "credit": "Myriad original V3 cuboid source",
             "myriad_source_sha256": source_hash, "ambientocclusion": False, "gui_light": "front",
             "textures": {"blade": "myriad:item/lingyuan_jade_sword", "particle": "myriad:item/lingyuan_jade_sword"},
             "elements": elements, "display": {
                 "thirdperson_righthand": {"rotation": [-10, 0, 0], "translation": [0, -1.327, 1.439], "scale": [1, 1, 1]},
                 "thirdperson_lefthand": {"rotation": [-10, 0, 0], "translation": [0, -1.327, 1.439], "scale": [1, 1, 1]},
                 "firstperson_righthand": {"rotation": [0, -25, 20], "translation": [1.13, -.867, -.35], "scale": [.78, .78, .78]},
                 "firstperson_lefthand": {"rotation": [0, 25, -20], "translation": [1.13, -.867, -.35], "scale": [.78, .78, .78]},
                 "gui": {"rotation": [0, 0, -35], "translation": [-2.88, -4.11, 0], "scale": [.62, .62, .62]},
                 "ground": {"rotation": [0, 0, 85], "translation": [-3.63, 1.18, 0], "scale": [.45, .45, .45]},
                 "fixed": {"rotation": [0, 180, -35], "translation": [2.88, -4.11, 0], "scale": [.62, .62, .62]}}}
    model["display"] = blade_edge_forward(model["display"])
    target = project / "src/main/resources/assets/myriad/models/item/lingyuan_sword.json"
    target.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    texture = project / "src/main/resources/assets/myriad/textures/item/lingyuan_jade_sword.png"
    shutil.copyfile(art / "lingyuan_jade_sword_v3.png", texture)
    print(f"held item: {len(elements)} exact source parts, centred handle, native Euler rotations, 7 display contexts")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--art", type=Path, required=True)
    parser.add_argument("--project", type=Path, required=True)
    args = parser.parse_args()
    base_file = args.art / "lingyuan_jade_sword_v3.spec.json"
    body = read(base_file)
    lod = copy.deepcopy(read(args.art / "lingyuan_jade_sword_v2.spec.json"))
    for p in lod["parts"]:
        for k in ["pos", "size", "pivot"]:
            p[k] = [n * 2 for n in p[k]]
    effects = [read(args.art / "fx_layers" / (name + ".spec.json")) for name in [
        "lingyuan_trail_layers_v1", "lingyuan_sigil_layers_v1", "lingyuan_afterimage_layers_v1"]]
    meshes = {"body": compile_parts(body["parts"], True), "body_lod": compile_parts(lod["parts"], True)}
    roots = {"trail_cyan_root": "trail_cyan", "trail_blue_root": "trail_blue", "sigil_ring_root": "sigil",
             "spark_root": "spark", "afterimage_root": "afterimage"}
    all_effects = [p for s in effects for p in s["parts"]]
    by_id = {p["id"]: p for p in all_effects}
    for root, key in roots.items():
        group = []
        for p in all_effects:
            ancestor = p
            while ancestor.get("parent"):
                ancestor = by_id[ancestor["parent"]]
            if ancestor["id"] == root:
                group.append(p)
        meshes[key] = compile_parts(group, False)
    data = {"version": 1, "source_sha256": hashlib.sha256(base_file.read_bytes()).hexdigest(),
            "source_length": 112, "center_y": 30, "meshes": meshes}
    target = args.project / "src/main/resources/assets/myriad/vfx/lingyuan_sword_mesh.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, separators=(",", ":")) + "\n", encoding="utf-8")
    texture = args.project / "src/main/resources/assets/myriad/textures/particle/vfx/lingyuan_pixel_atlas.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.art / "lingyuan_flight_showcase_v1.png", texture)
    compile_held_item(body, data["source_sha256"], args.art, args.project)
    for name, mesh in meshes.items():
        print(f"{name}: {mesh['parts']} source cuboids -> {len(mesh['faces'])} textured faces, {mesh['culled']} hidden faces removed")


if __name__ == "__main__":
    main()
