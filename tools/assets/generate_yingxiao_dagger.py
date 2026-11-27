"""Build Yingxiao's dagger from its editable geometry and authored surface materials.

Run with python tools/assets/generate_yingxiao_dagger.py (Pillow required).
Optional --preview PATH renders the embedded texture and cuboids from the new source.
"""
from __future__ import annotations

import argparse
import bisect
import hashlib
import importlib.util
import io
import itertools
import json
import math
import random
import uuid
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
NAME = "yingxiao_shadow_dagger"
SOURCE = ROOT / "art_source/yingxiao"
ASSETS = ROOT / "src/main/resources/assets/myriad"
DENSITY = 56
BLADE_DENSITY = 64
BLADE_X = 7.22
BLADE_Y = 17.55
FACES = {"north": (0, 1, "flat"), "south": (0, 1, "flat"),
         "east": (2, 1, "side"), "west": (2, 1, "side"),
         "up": (0, 2, "cap"), "down": (0, 2, "cap")}
PALETTE = {
    "silver": ("#656273", "#d7d7ed"), "silver_violet": ("#4a465e", "#c0b9d7"),
    "steel": ("#353043", "#928d9f"), "gunmetal": ("#2e273a", "#79748a"),
    "blackened": ("#1e192a", "#564961"), "plum": ("#39204b", "#ad67d4"),
    "quill": ("#482959", "#b97bdd"), "vein": ("#443359", "#a985c8"),
    "obsidian": ("#211b31", "#5d4f6d"), "leather_core": ("#201a2a", "#534953"),
    "wrap_dark": ("#282031", "#665471"), "wrap_violet": ("#402750", "#9569a6"),
    "amethyst": ("#38164e", "#ba73e8"), "amethyst_light": ("#752f99", "#edbeff"),
    "glint": ("#ae87c8", "#f7e8ff"),
}


def mix(a, b, t):
    t = max(0, min(1, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def blade_uv(x, y):
    return 6 + BLADE_DENSITY * (x - BLADE_X), 6 + BLADE_DENSITY * (BLADE_Y - y)


def stroke(draw, points, fill, width):
    draw.line([(round(x), round(y)) for x, y in points], fill=fill, width=width, joint="curve")


def blade_panel(image, parts):
    """A continuous XY unwrap keeps temper lines and engraving aligned across layers."""
    draw = ImageDraw.Draw(image)
    layers = {}
    for tag in ("edge", "bevel", "face"):
        rows = [p for p in parts if p["id"].startswith("blade_" + tag + "_")]
        layers[tag] = ([p["pos"][1] for p in rows], rows)

    def bounds(tag, y):
        starts, rows = layers[tag]
        row = rows[max(0, bisect.bisect_right(starts, y) - 1)]
        x0, y0, _ = row["pos"]
        w, h, _ = row["size"]
        if y > y0 + h + .0001 and tag != "edge":
            return None
        return x0, x0 + w

    def smooth_edge(y):
        limits = [bounds("edge", y + j / 40) for j in range(-4, 5)]
        return [sum(v[i] for v in limits) / len(limits) for i in (0, 1)]

    rng = random.Random(12083)
    for py in range(501):
        y = BLADE_Y - (py - 6 + .5) / BLADE_DENSITY
        t = (y - 10.04) / 7.44
        lo, hi = smooth_edge(y)
        bevel, face = bounds("bevel", y), bounds("face", y)
        for px in range(128):
            x = BLADE_X + (px - 6 + .5) / BLADE_DENSITY
            u = (x - lo) / (hi - lo)
            grit = rng.uniform(-.027, .027)
            reflection = .5 + .5 * math.cos(t * 20 + u * 4.5)
            bands = .5 + .5 * math.sin(37*u + 3.4*math.sin(t*20 + 2*u) + t*5)
            if face and face[0] <= x <= face[1]:
                base = mix((26, 22, 40), (82, 55, 100),
                           .16 + .26*reflection + .24*bands**7 + .21*(u > .55) + grit)
            elif bevel and bevel[0] <= x <= bevel[1]:
                base = mix((71, 65, 84), (166, 158, 186),
                           .3 + .36*reflection + .12*bands + .12*(u < .5) + grit)
            else:
                base = mix((121, 119, 141), (231, 227, 243),
                           .39 + .34*reflection + .12*bands + grit)
            image.putpixel((px, py), base)

    def point(u, t):
        y = 10.04 + t*7.44
        lo, hi = smooth_edge(y)
        return blade_uv(lo + u*(hi-lo), y)

    def curve(points, color, width):
        # Cubic segments stay continuous in the tapered, curved blade coordinate system.
        for i in range(0, len(points) - 1, 3):
            quad = points[i:i+4]
            result = []
            for j in range(31):
                s = j / 30
                weights = ((1-s)**3, 3*(1-s)**2*s, 3*(1-s)*s*s, s**3)
                result.append(point(*(sum(w*p[k] for p, w in zip(quad, weights)) for k in (0, 1))))
            stroke(draw, result, color, width)

    center = [(.45,.015),(.70,.08),(.47,.20),(.54,.34),
              (.61,.50),(.43,.61),(.51,.71),(.56,.77),(.55,.81),(.59,.85)]
    curve(center, "#513064", 10)
    curve(center, "#773d94", 6)
    curve(center, "#b274da", 3)
    curve(center[:4], "#d896f7", 1)
    curve(center[6:], "#c186e0", 1)
    # Raised silver-violet vein and three calligraphic barbs echo the feather guard.
    for start, tall in ((.07,.16),(.32,.105),(.55,.12)):
        plume = [(.54,start),(.44,start+tall*.25),(.22,start+tall*.45),(.27,start+tall*.84),
                 (.29,start+tall*.63),(.38,start+tall*.56),(.42,start+tall*.60)]
        counter = [(.55,start+tall*.22),(.67,start+tall*.20),(.78,start+tall*.66),(.71,start+tall)]
        curve(plume, "#563462", 6)
        curve(plume, "#a265c4", 3)
        curve(plume[:4], "#c58bdc", 1)
        curve(counter, "#79518e", 2)
    curve([(.26,.025),(.19,.16),(.29,.22),(.24,.27)], "#70547c", 1)
    curve([(.35,.37),(.30,.46),(.31,.54),(.34,.56)], "#65506f", 1)
    for i in range(38):
        t = rng.uniform(.018,.97)
        u = rng.choice((.03,.07,.91,.965))
        start = point(u, t)
        end = point(u+.012, t+rng.uniform(.002,.012))
        stroke(draw, [start,end], rng.choice(("#dedcea", "#9b91a9", "#e7e1f4")), 1)


def tile_image(material, view, w, h):
    """Paint each physically sized island without stretching a square swatch."""
    dark, light = map(ImageColor.getrgb, PALETTE[material])
    im = Image.new("RGB", (w, h))
    rng = random.Random(f"yingci/{material}/{view}/{w}/{h}")
    fabric = material.startswith("wrap") or material == "leather_core"
    jewel = material.startswith("amethyst") or material == "glint"
    for y in range(h):
        for x in range(w):
            u, v = (x + .5)/w, (y + .5)/h
            if fabric:
                # Short diagonal yarn strands and the tucks between them.
                warp = ((x + 2*y) % 7) / 6
                weft = ((2*x - y) % 9) / 8
                value = .17 + .34*(warp > .56) + .14*(weft < .35)
            elif jewel:
                value = .25 + .25*(u < .48) + .18*(v < .57)
            else:
                value = .29 + .26*math.sin(2.8*u + 1.5*v)**2 + .12*math.cos(x*.7 + y*.05)
            if x == 0 or y == 0:
                value += .18
            if x == w-1 or y == h-1:
                value -= .13
            im.putpixel((x, y), mix(dark, light, value + rng.uniform(-.06,.06)))

    draw = ImageDraw.Draw(im)
    if view == "flat" and jewel and min(w, h) >= 4:
        cx, cy = round(.42*(w-1)), round(.39*(h-1))
        corners = [(0,0),(w//2,0),(w-1,0),(w-1,h-1),(w//2,h-1),(0,h-1),(0,0)]
        for i, intensity in enumerate((.95,.70,.41,.69,.37,.56)):
            draw.polygon([corners[i], corners[i+1], (cx,cy)],fill=mix(dark,light,intensity))
        draw.line([(1,1),(cx,cy),(w-2,1)],fill=mix(light, (255,237,255), .32))
    elif view == "flat" and material.startswith("wrap") and w >= 5 and h >= 10:
        for y in range(3,h-2,6):
            draw.line((1,y,2,y+1),fill=mix(dark,light,.84))
    elif view == "flat" and material in ("plum","quill") and h > w and w >= 4:
        # Silver-edged vanes catch the light on the spine and diagonal barbs.
        shaft = max(1,w//2)
        draw.line((shaft,h-1,shaft,1),fill=mix(dark,light,.82))
        for y in range(4,h-2,6):
            draw.line((1,y-3,shaft,y),fill=mix(dark,light,.58))
            draw.line((shaft,y,w-2,y-4),fill=mix(dark,light,.72))
    elif view == "flat" and material in ("gunmetal","blackened","plum","quill") and min(w, h) >= 8:
        draw.rectangle((2,2,w-3,h-3),outline=mix(dark,light,.23))
        draw.line((3,h-4,3,3,w-4,3), fill=mix(dark,light,.73))
        if w >= 18 and h >= 12:
            for sign in (-1,1):
                ox = (w-1)/2
                stroke(draw, [(ox+sign*w*.33,h*.70),(ox+sign*w*.22,h*.66),
                              (ox+sign*w*.12,h*.38),(ox+sign*w*.15,h*.24)],
                       mix(dark, light, .85), 1)
    return im


def assign_uvs(spec):
    width, height = (spec["atlas"][key] for key in ("width", "height"))
    atlas = Image.new("RGB", (width,height), "#43384c")
    blade_panel(atlas, spec["parts"])
    wanted = {}
    for p in spec["parts"]:
        for side, (a, b, view) in FACES.items():
            if p["group"] == "blade" and view == "flat":
                continue
            key = (p["material"], view, max(2,round(p["size"][a]*DENSITY)),
                   max(2,round(p["size"][b]*DENSITY)))
            wanted[p["id"], side] = key

    boxes = {}
    x, y, row_height = 132, 1, 0
    for key in sorted(set(wanted.values()), key=lambda k: (-k[3],-k[2],k[:2])):
        material, view, w, h = key
        if x+w+4 > width:
            x, y, row_height = 132, y+row_height, 0
        if y+h+4 > height:
            raise ValueError(f"Atlas exhausted by {key}")
        # Two-pixel extrusion protects neighboring UV islands during filtering.
        tile = tile_image(material, view, w, h)
        padded = Image.new("RGB",(w+4,h+4))
        padded.paste(tile,(2,2))
        padded.paste(tile.crop((0,0,1,h)).resize((2,h)), (0,2))
        padded.paste(tile.crop((w-1,0,w,h)).resize((2,h)), (w+2,2))
        padded.paste(padded.crop((0,2,w+4,3)).resize((w+4,2)), (0,0))
        padded.paste(padded.crop((0,h+1,w+4,h+2)).resize((w+4,2)), (0,h+2))
        atlas.paste(padded, (x,y))
        boxes[key] = [x+2,y+2,x+2+w,y+2+h]
        x += w+4
        row_height = max(row_height,h+4)

    for p in spec["parts"]:
        x0, y0, _ = p["pos"]
        dx, dy, _ = p["size"]
        p["uv_mode"], p["faces"] = "per_face", {}
        for side in FACES:
            key = wanted.get((p["id"], side))
            if key is None:
                lo, hi = (x0+dx,x0) if side == "north" else (x0,x0+dx)
                rect = [*blade_uv(lo,y0+dy), *blade_uv(hi,y0)]
            else:
                rect = boxes[key]
            p["faces"][side] = {"uv":[round(n,5) for n in rect]}
    return atlas


def build(preview=None):
    source = SOURCE / f"{NAME}.spec.json"
    spec = json.loads(source.read_text())
    compiler_path = ROOT / ".agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py"
    module = importlib.util.spec_from_file_location("dagger_compiler",compiler_path)
    compiler = importlib.util.module_from_spec(module)
    module.loader.exec_module(compiler)
    # The approved grip and blade already contain sub-half-unit features.
    compiler._snap = lambda value: round(value, 5)
    serial = itertools.count()
    compiler._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL, f"myriad/{NAME}/{next(serial)}"))
    compiler.validate_schema(spec)
    compiler.validate_geometry(spec)
    atlas = assign_uvs(spec)
    buf = io.BytesIO()
    atlas.save(buf,format="PNG")
    png = buf.getvalue()
    model = compiler.build_bbmodel(spec, *atlas.size, png, NAME + ".png")
    if issues := compiler.validate_bbmodel(model,spec):
        raise ValueError(issues)
    model["meta"].update(format_version="4.10",model_format="java_block")
    model["display"] = spec["display"]
    model["textures"][0].update(folder="item",namespace="myriad")
    groups = {p["group"]:[] for p in spec["parts"]}
    for cube, part in zip(model["elements"],spec["parts"]):
        groups[part["group"]].append(cube["uuid"])
    model["outliner"] = [{"name":name,"uuid":compiler._uuid(),"origin":[8,8,8],
                           "export":True,"isOpen":False,"children":ids} for name,ids in groups.items()]
    bbpath = SOURCE / f"{NAME}.bbmodel"
    bbpath.write_text(json.dumps(model, separators=(",",":")) + "\n")
    runtime = {"format_version":"1.21.11","credit":"Myriad original Night Shadow dagger; vanilla reverse grip",
               "myriad_source_sha256":hashlib.sha256(bbpath.read_bytes()).hexdigest(),
               "ambientocclusion":False,"gui_light":"front",
               "textures":{"dagger":f"myriad:item/{NAME}","particle":f"myriad:item/{NAME}"},
               "elements":[],"display":spec["display"]}
    for cube in model["elements"]:
        item = {key:cube[key] for key in ("name","from","to")}
        if any(cube["rotation"]):
            item["rotation"] = dict(zip("xyz", cube["rotation"]), origin=cube["origin"], rescale=False)
        item["faces"] = {side:{"uv":[round(n*16/atlas.size[i%2],8) for i,n in enumerate(face["uv"])],
                               "texture":"#dagger"} for side,face in cube["faces"].items()}
        runtime["elements"].append(item)
    (ASSETS / "models/item" / f"{NAME}.json").write_text(json.dumps(runtime,indent=2) + "\n")
    (ASSETS / "textures/item" / f"{NAME}.png").write_bytes(png)
    if preview:
        from render_bbmodel import render
        # The 3/4 view shows depth; the detail crops use the same source material.
        full = render(model, view="isometric", size=(430,880), background="#292630")
        upper = render(model, view="front", size=(510,475), center=(8.03,12.45), scale=116, background="#292630")
        lower = render(model, view="front", size=(510,405), center=(8,8.95), scale=127, background="#292630")
        sheet = Image.new("RGB",(940,880),"#292630")
        sheet.paste(full,(0,0))
        sheet.paste(upper,(430,0))
        sheet.paste(lower,(430,475))
        draw = ImageDraw.Draw(sheet)
        draw.line((430,0,430,879), fill="#48414e")
        draw.line((430,475,939,475), fill="#48414e")
        preview.parent.mkdir(parents=True,exist_ok=True)
        sheet.save(preview)
    print(f"{len(model['elements'])} cuboids, {atlas.size[0]}px embedded atlas, {len(groups)} groups")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview",type=Path)
    build(parser.parse_args().preview)
