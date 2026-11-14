"""Small orthographic texture renderer for static Blockbench source previews.

Uses the model's actual cuboids, pivots, Euler rotations and embedded UV atlas.
Optional element ``_transform`` entries apply extra rotations/translations in order,
which lets a preview assemble separately exported item parts without game code.
"""
from __future__ import annotations

import base64
import io
import json
import math
from pathlib import Path

from PIL import Image


VIEWS = {
    "front": ((1, 0, 0), (0, 1, 0), (0, 0, -1)),
    "rear": ((-1, 0, 0), (0, 1, 0), (0, 0, 1)),
    "side": ((0, 0, 1), (0, 1, 0), (1, 0, 0)),
    "top": ((1, 0, 0), (0, 0, -1), (0, 1, 0)),
    "isometric": ((.819152, 0, .573576), (-.196175, .939693, .280166),
                  (.538986, .342020, -.769751)),
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1),
           "east": (1, 0, 0), "west": (-1, 0, 0),
           "up": (0, 1, 0), "down": (0, -1, 0)}


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def rotate(point, angles, origin=(0, 0, 0)):
    x, y, z = [point[i] - origin[i] for i in range(3)]
    for axis, degree in enumerate(angles):
        c, s = math.cos(math.radians(degree)), math.sin(math.radians(degree))
        if axis == 0:
            y, z = y * c - z * s, y * s + z * c
        elif axis == 1:
            x, z = x * c + z * s, -x * s + z * c
        else:
            x, y = x * c - y * s, x * s + y * c
    return tuple(v + origin[i] for i, v in enumerate((x, y, z)))


def transform(point, element, normal=False):
    value = rotate(point, element.get("rotation", [0, 0, 0]),
                   (0, 0, 0) if normal else element.get("origin", [0, 0, 0]))
    for step in element.get("_transform", []):
        if "rotation" in step:
            value = rotate(value, step["rotation"],
                           (0, 0, 0) if normal else step.get("origin", [0, 0, 0]))
        if "translation" in step and not normal:
            value = tuple(value[i] + step["translation"][i] for i in range(3))
    return value


def cuboid_faces(element):
    x1, y1, z1 = element["from"]
    x2, y2, z2 = element["to"]
    return {
        "north": [(x1, y2, z1), (x2, y2, z1), (x2, y1, z1), (x1, y1, z1)],
        "south": [(x2, y2, z2), (x1, y2, z2), (x1, y1, z2), (x2, y1, z2)],
        "east": [(x2, y2, z1), (x2, y2, z2), (x2, y1, z2), (x2, y1, z1)],
        "west": [(x1, y2, z2), (x1, y2, z1), (x1, y1, z1), (x1, y1, z2)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }


def texture_images(model, source_dir):
    textures = []
    for entry in model["textures"]:
        source = entry.get("source", "")
        if source.startswith("data:image/"):
            textures.append(Image.open(io.BytesIO(base64.b64decode(source.split(",", 1)[1]))).convert("RGBA"))
        else:
            textures.append(Image.open(source_dir / entry["path"]).convert("RGBA"))
    return textures


def render(model_path, output_path=None, view="isometric", size=(720, 1024),
           scale=None, background="#17232c", center=None):
    """Render a path or in-memory bbmodel; larger depth values face the camera."""
    if isinstance(model_path, dict):
        model, source_dir = model_path, Path.cwd()
    else:
        model_path = Path(model_path)
        model, source_dir = json.loads(model_path.read_text()), model_path.parent
    textures = texture_images(model, source_dir)
    right, up, toward = VIEWS[view]
    surfaces, projected = [], []
    for element in model["elements"]:
        for face, corners in cuboid_faces(element).items():
            if face not in element["faces"] or element["faces"][face].get("texture") is None:
                continue
            world = [transform(p, element) for p in corners]
            normal = transform(NORMALS[face], element, normal=True)
            points = [(dot(p, right), dot(p, up), dot(p, toward)) for p in world]
            projected.extend(points)
            if dot(normal, toward) <= 1e-7:
                continue
            surfaces.append((element["faces"][face], points, normal))
    min_x, max_x = min(p[0] for p in projected), max(p[0] for p in projected)
    min_y, max_y = min(p[1] for p in projected), max(p[1] for p in projected)
    width, height = size
    scale = scale or min((width - 64) / max(.001, max_x - min_x),
                         (height - 64) / max(.001, max_y - min_y))
    center_x, center_y = (min_x + max_x) / 2, (min_y + max_y) / 2
    if center is not None:
        center_x, center_y = center
    canvas = Image.new("RGBA", size, background)
    pixels = canvas.load()
    depths = [-float("inf")] * (width * height)
    light = (-.32, .65, -.69)
    atlas_width = model["resolution"]["width"]
    atlas_height = model["resolution"]["height"]
    for face, points, normal in surfaces:
        screen = [(width / 2 + (p[0] - center_x) * scale,
                   height / 2 - (p[1] - center_y) * scale, p[2]) for p in points]
        u1, v1, u2, v2 = face["uv"]
        uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
        rotation = (face.get("rotation", 0) // 90) % 4
        uvs = uvs[rotation:] + uvs[:rotation]
        texture = textures[int(face["texture"])]
        texels = texture.load()
        tw, th = texture.size
        shade = min(1.10, .74 + .36 * max(0, dot(normal, light)))
        for indices in [(0, 1, 2), (0, 2, 3)]:
            a, b, c = [screen[i] for i in indices]
            ua, ub, uc = [uvs[i] for i in indices]
            denominator = ((b[1] - c[1]) * (a[0] - c[0]) +
                           (c[0] - b[0]) * (a[1] - c[1]))
            if abs(denominator) < 1e-10:
                continue
            for y in range(max(0, math.floor(min(a[1], b[1], c[1]))),
                           min(height, math.ceil(max(a[1], b[1], c[1])))):
                for x in range(max(0, math.floor(min(a[0], b[0], c[0]))),
                               min(width, math.ceil(max(a[0], b[0], c[0])))):
                    px, py = x + .5, y + .5
                    wa = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / denominator
                    wb = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / denominator
                    wc = 1 - wa - wb
                    if min(wa, wb, wc) < -1e-7:
                        continue
                    depth = wa * a[2] + wb * b[2] + wc * c[2]
                    index = y * width + x
                    if depth < depths[index] - 1e-8:
                        continue
                    u = (wa * ua[0] + wb * ub[0] + wc * uc[0]) * tw / atlas_width
                    v = (wa * ua[1] + wb * ub[1] + wc * uc[1]) * th / atlas_height
                    color = texels[max(0, min(tw - 1, int(u))), max(0, min(th - 1, int(v)))]
                    if color[3] == 0:
                        continue
                    pixels[x, y] = tuple(min(255, round(value * shade)) for value in color[:3]) + (color[3],)
                    depths[index] = depth
    if output_path:
        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        canvas.save(output_path)
    return canvas


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model")
    parser.add_argument("output")
    parser.add_argument("--view", choices=VIEWS, default="isometric")
    arguments = parser.parse_args()
    render(arguments.model, arguments.output, arguments.view)
