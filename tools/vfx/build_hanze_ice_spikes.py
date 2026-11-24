"""Build the upright Absolute Zero and Frozen Expanse models on the shared ice atlas."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import sys

from build_frost_herb_projectiles import (
    ROOT, JAVA, TEXTURES, SIDES, atlas_regions, bake_rows, cube, write_model_java,
)

SOURCE = ROOT / "art_source" / "hanze-ice-spikes"


def prism(parts, name, root, profile, lean, material):
    x, y, z = root
    bottom = 0
    total = profile[-1][0]
    for i, (top, width, depth, vein) in enumerate(profile):
        # Three strips give the taller sections a beveled cross-section.
        cuts = ((0,depth,width,vein),) if min(width,depth)<1.5 else (
            (0,.5,width-.5,vein),(.5,depth-1,width,material),(depth-.5,.5,width-.5,1))
        for j, (offset, thickness, span, cell) in enumerate(cuts):
            part = cube(f"{name}_{i}_{j}",(x-span/2,y+bottom,z-depth/2+offset),
                        (span,top-bottom,thickness),cell,lean,root)
            for face in SIDES:
                side = face not in ("up","down")
                u0,u1 = (7,94) if span>=1.5 and side else (104,124)
                v0,v1 = (8+112*(1-top/total),8+112*(1-bottom/total)) if side else (26,105)
                part["faces"][face]["uv"] = [cell*128+u0,round(v0,3),cell*128+u1,round(v1,3)]
            parts.append(part)
        bottom = top


def zero():
    parts = []
    prism(parts,"glacial_heart",(0,0,0),[
        (2.25,5.5,4.5,2),(6,4.75,4,0),(10,4,3.5,0),(13.5,3.25,3,3),
        (17,2.5,2.25,3),(20,1.75,1.5,1),(23,.95,1,1),(24,.5,.5,1),
    ],(0,9,-4),0)
    ridges = [(2,3,2.5,2),(5.5,2.75,2.25,0),(8.75,2,1.75,3),
              (11,1.25,1,3),(13.25,.5,.5,1)]
    for i,(root,lean) in enumerate((
        ((-2.8,0,1.2),(13,-14,32)),((2.8,0,0),(-17,17,-36)),((0,0,-2.5),(-38,0,9)),
    )):
        prism(parts,f"crown_rib_{i}",root,ridges,lean,0 if i!=1 else 2)
    prism(parts,"front_split",(.7,0,3.1),[(1.75,2,2,2),(4.75,1.65,1.5,3),(8,.5,.5,1)],(23,0,-13),0)
    return parts


def expanse():
    parts = []
    # Every rib points towards local +Z, away from the middle of the ice field.
    profile = [(1.75,3.5,3.75,2),(4.25,3,3.25,0),(7.5,2.5,2.75,3),
               (10.75,1.75,2,3),(13.25,1.25,1.25,1),(15,.5,.5,1)]
    for i,(x,z,scale,tilt,roll) in enumerate((
        (0,-1,1.55,37,0),(-3.7,-3,.93,43,13),(4,-.25,1.16,46,-14),
        (-4.6,.65,.68,48,18),(2.15,4,.61,41,-4),(-1,3.5,.45,41,6),
    )):
        prism(parts,f"frozen_rib_{i}",(x,0,z),
              [(top*scale,max(.5,w*scale),max(.5,d*scale),cell) for top,w,d,cell in profile],
              (tilt,0,roll),2 if i in (1,3) else 0)
    return parts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generator", type=Path, required=True)
    parser.add_argument("--preview", type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0,str(ROOT/"tools/assets"))
    from render_bbmodel import render, transform
    from PIL import Image, ImageDraw
    SOURCE.mkdir(parents=True,exist_ok=True)
    args.preview.mkdir(parents=True,exist_ok=True)
    atlas = {"width":512,"height":128,"regions":atlas_regions(0)}
    png = (TEXTURES/"frost_projectile_model.png").read_bytes()
    board = Image.new("RGB",(1000,420),"#17232c")
    labels = ImageDraw.Draw(board)
    baked = []
    for i,(name,parts) in enumerate((("absolute_zero",zero()),("frozen_expanse",expanse()))):
        spec = dict(meta={"name":name},target="entity",parts=parts,atlas=atlas,
                    face_details=[dict(id="solid_core",part=parts[0]["id"],kind="geometry")])
        data, model = [SOURCE/(name+suffix) for suffix in (".spec.json",".bbmodel")]
        data.write_text(json.dumps(spec,indent=2)+"\n",encoding="utf-8")
        subprocess.run([sys.executable,str(args.generator),str(data),"-o",str(model)],check=True)
        texture = json.loads(model.read_text(encoding="utf-8"))["textures"][0]["source"]
        assert base64.b64decode(texture.split(",",1)[1]) == png, "Embedded ice atlas must match the game atlas"
        baked.append((name,bake_rows(model,len(parts),transform)))
        board.paste(render(model,size=(500,375),scale=7,view="isometric"),(500*i,0))
        labels.text((500*i+24,392),name.replace("_"," ").title(),fill="#deecf1")
    board.save(args.preview/"hanze-ice-spikes.png")
    write_model_java(JAVA.with_name("HanzeIceSpikeParts.java"),"tools/vfx/build_hanze_ice_spikes.py",baked)


if __name__ == "__main__":
    main()
