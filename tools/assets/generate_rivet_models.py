"""Compile Rivet's approved cuboid specs, painted material atlas and face caches.

Run with python (Pillow required). Geometry remains editable in BBModel; only
fully hidden, axis-aligned faces are omitted from the runtime mesh cache.
"""
from __future__ import annotations

import base64
import copy
import hashlib
import importlib.util
import io
import itertools
import json
from pathlib import Path
import sys
import tempfile
import uuid

from PIL import Image, ImageDraw
from render_bbmodel import render, rotate

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/rivet"
ASSET = ROOT / "src/main/resources/assets/myriad"
sys.path.insert(0, str(ROOT / "tools/vfx"))
from compile_roster_projectiles import compile_models

NAMES = ("hammer", "anchor", "hammer_lod", "anchor_lod")
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, -12], "translation": [0, 0, 0], "scale": [.88, .88, .88]},
    "thirdperson_lefthand": {"rotation": [0, -90, 12], "translation": [0, 0, 0], "scale": [.88, .88, .88]},
    "firstperson_righthand": {"rotation": [0, 75, -25], "translation": [1, -1, -2], "scale": [.55, .55, .55]},
    "firstperson_lefthand": {"rotation": [0, -75, 25], "translation": [1, -1, -2], "scale": [.55, .55, .55]},
    "gui": {"rotation": [15, 0, -35], "translation": [0, 0, 0], "scale": [.42, .42, .42]},
    "ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [.4, .4, .4]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [.5, .5, .5]},
}


def item_rotation(point, angles):
    # Minecraft 26.2 ItemTransform.apply uses JOML rotationXYZ: Z acts first.
    for axis in (2,1,0):
        step=[0,0,0];step[axis]=angles[axis]
        point=rotate(point,step)
    return point


def bind_display_anchors():
    """Account for ItemTransform's final (-8,-8,-8) pixel centering.

    The normal third-person path grips source (0,5.5,0). Inventory, ground and
    fixed contexts center the entire real bounds. First-person custom motions
    use NONE and supply their own grip compensation in RivetWeaponView.
    """
    for name,entry in DISPLAY.items():
        left=name.endswith("lefthand")
        hand="person" in name
        pivot=[0,5.5,0]if hand else[-.25,13.25,0]
        angles=entry["rotation"].copy()
        if left:angles[1]*=-1;angles[2]*=-1
        offset=item_rotation([(pivot[i]-8)*entry["scale"][i]for i in range(3)],angles)
        target=entry["translation"].copy()
        if left:target[0]*=-1
        translation=[target[i]-offset[i]for i in range(3)]
        if left:translation[0]*=-1
        entry["translation"]=[round(value,7)for value in translation]
        applied=entry["translation"].copy()
        if left:applied[0]*=-1
        assert all(abs(applied[i]+offset[i]-target[i])<1e-6 for i in range(3))


bind_display_anchors()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def paint_atlas(spec):
    """Flat-face material marks; scene lighting comes from the actual renderer."""
    atlas = spec["atlas"]
    image = Image.new("RGBA", (atlas["width"], atlas["height"]), "#152831")
    draw = ImageDraw.Draw(image)
    for region in atlas["regions"]:
        x, y, w, h = region["rect"]
        name, base = region["id"], region["fill"]
        dark, light = region["palette"]
        draw.rectangle((x, y, x+w-1, y+h-1), fill=base)
        draw.rectangle((x+3, y+3, x+w-4, y+h-4), outline=dark, width=2)
        draw.line((x+5, y+h-6, x+5, y+5, x+w-6, y+5), fill=light, width=1)
        if name == "rubber":
            for yy in range(y+9, y+h-6, 7):
                draw.rectangle((x+4, yy, x+w-5, yy+3), fill=dark)
                draw.line((x+5, yy-1, x+w-6, yy-1), fill=light)
        elif name == "strike":
            for yy in (y+14,y+30,y+46):
                draw.rectangle((x+10,yy,x+w-11,yy+5),fill=dark)
                draw.rectangle((x+12,yy+2,x+w-13,yy+4),fill="#26343b")
                draw.line((x+10,yy-1,x+w-11,yy-1),fill=light)
        elif name == "vent":
            for yy in range(y+13, y+h-9, 12):
                draw.rectangle((x+12, yy, x+w-13, yy+4), fill=dark)
                draw.line((x+12, yy-1, x+w-13, yy-1), fill=light)
        elif name == "panel":
            draw.rectangle((x+10, y+10, x+53, y+40), fill="#1c3039")
            for yy in range(y+13, y+38, 7):
                draw.rectangle((x+13, yy, x+50, yy+2), fill="#101f28")
                draw.line((x+13, yy-1, x+50, yy-1), fill="#6e858a")
            draw.rectangle((x+17, y+46, x+47, y+55), fill="#162a34")
            draw.rectangle((x+20, y+49, x+44, y+52), fill="#63c8ce")
            draw.line((x+20, y+49, x+44, y+49), fill="#b7ece0")
        elif name == "piston":
            for xx, color in [(12, dark), (17, light), (30, light), (44, dark), (48, light)]:
                draw.rectangle((x+xx, y+7, x+xx+2, y+h-8), fill=color)
        elif name == "hazard":
            for yy in range(4, h-4):
                for xx in range(4, w-4):
                    if (xx-yy) % 24 < 9:
                        draw.point((x+xx,y+yy),fill=dark)
        elif name in ("cyan", "amber"):
            draw.rectangle((x+9,y+11,x+w-10,y+h-12),fill=light)
            draw.rectangle((x+12,y+15,x+w-13,y+h-16),fill=base)
        elif name in ("steel", "plate", "frame"):
            draw.line((x+13,y+9,x+13,y+25,x+18,y+30,x+18,y+54),fill=dark,width=1)
            draw.line((x+48,y+9,x+48,y+38,x+43,y+43,x+43,y+54),fill=light,width=1)
        if name in ("yellow", "fastener", "plate", "steel"):
            for xx, yy in [(11,11),(52,11),(11,52),(52,52)]:
                draw.rectangle((x+xx-2,y+yy-2,x+xx+2,y+yy+2),fill=dark)
                draw.line((x+xx-1,y+yy-1,x+xx+1,y+yy-1),fill=light)
                draw.line((x+xx-1,y+yy+1,x+xx+1,y+yy+1),fill=base)
        if name in ("yellow", "steel", "strike", "frame", "plate"):
            # A few deliberate scratches, rather than random texture noise.
            for xx, yy in [(22,8),(50,27),(31,57)]:
                draw.line((x+xx,y+yy,x+xx+4,y+yy-1),fill=light,width=1)
    for detail in spec.get("face_details", []):
        if detail["kind"] == "texture":
            x,y,w,h=detail["uv"]
            draw.rectangle((x,y,x+w-1,y+h-1),fill=detail["color"])
    return image


def hidden_face(part, name, elements):
    """Union coverage test for faces whose outward side is entirely opaque solid.

    Rotated clamps remain untouched; neither holes nor partial faces are guessed.
    """
    if any(part.get("rotation", [])):
        return False
    axis, sign = {"west": (0,-1), "east": (0,1), "down": (1,-1),
                  "up": (1,1), "north": (2,-1), "south": (2,1)}[name]
    plane = part["from"][axis] if sign < 0 else part["to"][axis]
    probe = plane + sign * 1e-6
    a,b = [i for i in range(3) if i != axis]
    lo,hi = part["from"],part["to"]
    covers=[]
    for other in elements:
        if other is part or any(other.get("rotation", [])):
            continue
        lower,upper=other["from"],other["to"]
        if lower[axis] < probe < upper[axis]:
            rect=(max(lo[a],lower[a]),max(lo[b],lower[b]),min(hi[a],upper[a]),min(hi[b],upper[b]))
            if rect[2] > rect[0] and rect[3] > rect[1]:
                covers.append(rect)
    if not covers:
        return False
    us=sorted({lo[a],hi[a]}|{r[i]for r in covers for i in(0,2)})
    vs=sorted({lo[b],hi[b]}|{r[i]for r in covers for i in(1,3)})
    return all(any(r[0] <= (u+U)/2 <= r[2] and r[1] <= (v+V)/2 <= r[3] for r in covers)
               for u,U in zip(us,us[1:]) for v,V in zip(vs,vs[1:]))


def visible_model(model):
    result=copy.deepcopy(model)
    for old,new in zip(model["elements"],result["elements"]):
        new["faces"]={name:face for name,face in old["faces"].items()
                      if not hidden_face(old,name,model["elements"])}
    return result


def runtime_item(model):
    width,height=model["resolution"]["width"],model["resolution"]["height"]
    result={"credit":"Original MYRIAD Rivet / approved reference / generate_rivet_models.py",
            "ambientocclusion":False,"gui_light":"front","texture_size":[width,height],
            "textures":{"0":"myriad:item/rivet_metal","particle":"myriad:item/rivet_metal"},
            "elements":[],"display":DISPLAY}
    for element in visible_model(model)["elements"]:
        if not element["faces"]:
            continue
        part={key:element[key] for key in("name","from","to")}
        part["faces"]={side:{"uv":[v*16/(width if i%2==0 else height)for i,v in enumerate(face["uv"])],"texture":"#0"}
                       for side,face in element["faces"].items()}
        angles=[(axis,angle)for axis,angle in zip("xyz",element["rotation"])if angle]
        if angles:
            assert len(angles)==1 and angles[0][1] in(-45,-22.5,22.5,45)
            part["rotation"]={"origin":element["origin"],"axis":angles[0][0],"angle":angles[0][1],"rescale":False}
        result["elements"].append(part)
    return result


def main():
    helper=ROOT/".agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py"
    module=importlib.util.spec_from_file_location("rivet_model_generator",helper)
    compiler=importlib.util.module_from_spec(module);module.loader.exec_module(compiler)
    specs={key:json.loads((SOURCE/(key+".spec.json")).read_text())for key in NAMES}
    atlas_spec=copy.deepcopy(specs["hammer"])
    atlas_spec["face_details"]=[detail for spec in specs.values()for detail in spec["face_details"]]
    image=paint_atlas(atlas_spec);buf=io.BytesIO();image.save(buf,format="PNG");atlas=buf.getvalue()
    models={}
    for key in NAMES:
        spec=specs[key]
        compiler.validate_schema(spec)
        assert not compiler.validate_geometry(spec)
        serial=itertools.count()
        compiler._uuid=lambda:str(uuid.uuid5(uuid.NAMESPACE_URL,"myriad:rivet/"+key+"/"+str(next(serial))))
        png=atlas
        model=compiler.build_bbmodel(spec,256,256,png,"rivet_metal")
        model["meta"].update({"format_version":"4.10","model_format":"java_block","box_uv":False})
        if key.startswith("hammer"):model["display"]=DISPLAY
        findings=compiler.validate_bbmodel(model,spec)
        assert not findings,findings
        assert image.size==(256,256) and image.getextrema()[3]==(255,255)
        assert base64.b64decode(model["textures"][0]["source"].split(",",1)[1])==png
        assert len({e["uuid"]for e in model["elements"]})==len(model["elements"])
        for part,element in zip(spec["parts"],model["elements"]):
            assert element["from"]==part["pos"],part["id"]
            assert element["to"]==[part["pos"][i]+part["size"][i]for i in range(3)],part["id"]
            assert all(element["to"][i]>element["from"][i]for i in range(3))
        write(SOURCE/(key+".bbmodel"),model);models[key]=model
        render(model,SOURCE/(key+"_preview.png"),view="isometric",size=(720,900)if key.startswith("hammer")else(720,720),background="#101c27")
    runtime=runtime_item(models["hammer"])
    write(ASSET/"models/item/rivet_hammer.json",runtime)
    write(ASSET/"items/rivet_hammer.json",{"model":{"type":"minecraft:model","model":"myriad:item/rivet_hammer"}})
    for path in(SOURCE/"rivet_metal.png",ASSET/"textures/item/rivet_metal.png",ASSET/"textures/particle/vfx/rivet_metal.png"):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(atlas)
    with tempfile.TemporaryDirectory(prefix="rivet-mesh-")as directory:
        paths={}
        for key,model in models.items():
            path=Path(directory)/(key+".bbmodel");write(path,visible_model(model));paths[key]=path
        target=ASSET/"vfx/rivet_models_mesh.json"
        result=compile_models(paths,target,source_length=28)
        for key,mesh in result["meshes"].items():
            mesh["source_sha256"]=hashlib.sha256((SOURCE/(key+".bbmodel")).read_bytes()).hexdigest()
            count=len(mesh["faces"])
            print(f"Rivet {key}: {mesh['parts']} cuboids, {count} exposed faces, tip_y={mesh['tip_y']}")
            assert count <= (136 if key=="hammer"else 160 if key=="anchor"else 112),(key,count)
        target.write_text(json.dumps(result,separators=(",",":"))+"\n")
    print("OK: shared opaque atlas, exact source geometry, embedded PNG, visible-face cache, normal item binding")


if __name__=="__main__":
    main()
