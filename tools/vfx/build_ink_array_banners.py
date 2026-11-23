"""Build two arena banners as editable BBModels and export verified cuboids for the client."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/mochen-array-banners"
JAVA = ROOT / "src/main/java/com/myriad/character/skills/xuanchen/rework/InkBannerParts.java"
TEX = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx/ink_banner_model.png"
MATERIALS = ("inkwood", "brass", "parchment", "jadecloth")
SIDES = ("north","south","down","up","west","east")


def part(name, pos, size, mat, seal=False):
    cell = MATERIALS.index(mat)
    offset = 128*cell
    # A narrow opaque swatch for trim and cloth sides. The emblem is sampled
    # only by the broad front and back of the two dedicated seal elements.
    uv = [offset+6,8,offset+20,120]
    logo = [offset+24,8,offset+122,120]
    return dict(id=name, pos=pos, size=size, uv_mode="per_face",
                faces={f: {"uv": logo if seal and f in ("north","south") else uv} for f in SIDES})


def heaven():
    p = part
    return [
        p("stone_foot",(-8,0,-3),(6,1,6),"inkwood"),
        p("plinth_bevel",(-7.5,1,-2.5),(5,1,5),"inkwood"),
        p("copper_plinth",(-7,2,-2),(4,1,4),"brass"),
        p("main_pole",(-6,3,-1),(2,27,2),"inkwood"),
        p("lower_band",(-6.5,6,-1.5),(3,1,3),"brass"),
        p("upper_band",(-6.5,21,-1.5),(3,1,3),"brass"),
        p("crown_stem",(-6.5,28.5,-1.5),(3,1,3),"inkwood"),
        p("head_crown",(-7,29.5,-2),(4,1,4),"brass"),
        p("head_shoulder",(-6.5,30.5,-1.5),(3,.5,3),"brass"),
        p("head_cap",(-6,31,-1),(2,1,2),"jadecloth"),
        p("crossbar",(-7.5,25,-.75),(15,1.5,1.5),"inkwood"),
        p("crossbar_cap",(6.5,24.5,-1),(2,2.5,2),"brass"),
        p("crossbar_end_pin",(8.5,25,-.5),(.5,1.5,1),"jadecloth"),
        p("loop_left",(-3,24,-1.5),(1,2.5,3),"brass"),
        p("loop_middle",(1,24,-1.5),(1,2.5,3),"brass"),
        p("loop_right",(4.5,24,-1.5),(1,2.5,3),"brass"),
        p("banner_left_fold",(-3.5,10,-.5),(2,14.5,1),"parchment"),
        p("banner_main_fold",(-1.5,7,-1),(6,17.5,2),"parchment",seal=True),
        p("banner_right_fold",(4.5,9.5,-.5),(2,15,1),"parchment"),
        p("stitched_left",(-4,10,-1),(.5,14.5,2),"brass"),
        p("stitched_right",(6.5,9.5,-1),(.5,15,2),"brass"),
        p("side_terminal_left",(-3,9,-.5),(1,1,1),"parchment"),
        p("side_terminal_right",(5,8.5,-.5),(1,1,1),"parchment"),
        p("banner_tail",(-.5,5.5,-.65),(4,1.5,1.3),"parchment"),
        p("banner_tail_tip",(.5,4.5,-.5),(2,1,1),"brass"),
        p("crown_pin",(-5.5,32,-.5),(1,.5,1),"brass"),
    ]


def protection():
    p = part
    boxes = [
        p("crossbar",(-9,13,-.75),(18,1.5,1.5),"inkwood"),
        p("green_center",(-4,4,-1),(8,9,2),"jadecloth"),
        p("green_left_fold",(-6.5,5,-.5),(2.5,8,1),"jadecloth"),
        p("green_right_fold",(4,5,-.5),(2.5,8,1),"jadecloth"),
        p("hem_cloth",(-3.25,3.5,-.65),(6.5,.5,1.3),"jadecloth"),
        p("hem_edge",(-2.25,3,-.5),(4.5,.5,1),"brass"),
        p("seal_mount",(-3,6,-1.5),(6,5.5,3),"brass"),
        p("seal_front",(-2.5,6.5,-2),(5,4.5,.5),"parchment",seal=True),
        p("seal_back",(-2.5,6.5,1.5),(5,4.5,.5),"parchment",seal=True),
        p("seal_lower_tip",(-1,5.5,-1.5),(2,.5,3),"brass"),
    ]
    for i,x in enumerate((-7.5,7.5)):
        boxes += [
            p(f"foot_{i}",(x-2,0,-2),(4,1,4),"inkwood"),
            p(f"bevel_{i}",(x-1.75,1,-1.75),(3.5,.5,3.5),"inkwood"),
            p(f"base_ring_{i}",(x-1.5,1.5,-1.5),(3,1,3),"brass"),
            p(f"post_{i}",(x-1,2.5,-1),(2,12,2),"inkwood"),
            p(f"copper_cap_{i}",(x-1.5,14.5,-1.5),(3,1,3),"brass"),
            p(f"jade_cap_{i}",(x-1,15.5,-1),(2,.5,2),"jadecloth"),
            p(f"pole_lock_{i}",(x-1.25,11,-1.25),(2.5,.5,2.5),"brass"),
            p(f"cloth_clip_{i}",(x*.675-.55,12.5,-1.5),(1.1,2.5,3),"brass"),
        ]
    return boxes


def spec(name, parts):
    regions = []
    def rect(i, xywh, color):
        x,y,w,h = xywh
        regions.append(dict(id=f"paint_{len(regions)}",rect=[i*128+x,y,w,h],fill=color))

    for i,(base,light) in enumerate((
        ("#393037","#443a40"),("#b38850","#c69c60"),
        ("#e8d4ab","#eddcb8"),("#37625a","#426e64"),
    )):
        rect(i,(0,0,128,128),base)
        rect(i,(6,8,3,112),light)
    # One intentional motif, never repeated on the trim or down the slim folds.
    for x,y,w,h in ((50,37,47,5),(50,42,5,37),(92,42,5,37),(50,79,47,5),
                    (62,48,24,5),(62,53,5,14),(81,53,5,17),(69,68,17,5)):
        rect(2,(x,y,w,h),"#51413b")
    rect(2,(89,94,8,8),"#9d5244")
    return dict(meta={"name":name,"schema_version":1},target="entity",parts=parts,
                atlas={"width":512,"height":128,"regions":regions},
                face_details=[dict(id="raised_cloth",part=next(p["id"] for p in parts if p["id"] in
                                   ("banner_main_fold","green_center")),kind="geometry")])


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--generator",type=Path,required=True)
    cli.add_argument("--preview",type=Path,required=True)
    args = cli.parse_args()
    ART.mkdir(parents=True,exist_ok=True)
    args.preview.mkdir(parents=True,exist_ok=True)
    sys.path.insert(0,str(ROOT/"tools/assets"))
    from render_bbmodel import render
    board = Image.new("RGB",(1120,620),"#17232c")
    data = []
    for i,(name,parts) in enumerate((("heaven_standard",heaven()),("protection_standard",protection()))):
        model = ART / f"{name}.bbmodel"
        source = ART / f"{name}.spec.json"
        source.write_text(json.dumps(spec(name,parts),indent=2)+"\n",encoding="utf-8")
        cmd = [sys.executable,str(args.generator),str(source),"-o",str(model)]
        if i == 0:
            cmd += ["--png",str(TEX)]
        subprocess.run(cmd,check=True)
        parsed = json.loads(model.read_text(encoding="utf-8"))
        rows = []
        for el in parsed["elements"]:
            assert len(el["faces"])==6 and not any(el.get("rotation",[]))
            uvs = [n for side in SIDES for n in el["faces"][side]["uv"]]
            rows.append("        {"+", ".join(f"{n:g}" for n in [*el["from"],*el["to"],*uvs])+"}")
        data.append(f"    static final double[][] {'HEAVEN' if i==0 else 'PROTECTION'} = {{\n"+",\n".join(rows)+"\n    };")
        view = render(model, size=(560,620),scale=15)
        # Align the highest visible ground-contact pixel on both same-scale views.
        canvas = view.convert("RGB")
        tile = ImageChops.difference(canvas,Image.new("RGB",canvas.size,"#17232c")).getbbox()
        assert tile is not None
        subject = canvas.crop(tile)
        board.paste(subject,(i*560+(560-subject.width)//2,568-subject.height))
    board.save(args.preview/"ink-array-banners.png")
    JAVA.write_text("package com.myriad.character.skills.xuanchen.rework;\n\n"
                    "// Generated from the validated models by tools/vfx/build_ink_array_banners.py.\n"
                    "final class InkBannerParts {\n    private InkBannerParts() {}\n"
                    +"\n".join(data)+"\n}\n",encoding="utf-8")


if __name__=="__main__":
    main()
