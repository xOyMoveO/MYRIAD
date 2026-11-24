"""Build the returning windblade spec, editable BBModel and corresponding runtime model."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/sulan-windblade"
TEX = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx/wind_hd_wing_model.png"
JAVA = ROOT / "src/main/java/com/myriad/character/skills/sulan/rework/WindBladeModelParts.java"
MATERIALS = ("celadon", "ivory", "jade", "brass")
SIDES = ("north", "south", "east", "west", "up", "down")


def box(name, center, size, material, yaw=0, pivot=None):
    u = 128*MATERIALS.index(material)
    return dict(id=name, pos=[v-w/2 for v,w in zip(center,size)], size=list(size),
                rot=[0,yaw,0], pivot=list(pivot or center),
                uv_mode="per_face", faces={k:{"uv":[u+1,1,u+127,127]} for k in SIDES})


def specification():
    parts = [
        box("central_mount", (0,0,.25), (4.5,2.5,5.5), "brass"),
        box("raised_socket", (0,1.25,.25), (3,1,3), "brass",45),
        box("inset_jade", (0,2,.25), (2,1,2), "jade",45),
        box("reverse_setting", (0,-1.25,.25), (3,.5,3), "jade",45),
        box("flying_nose", (0,0,4), (2.5,1.5,3), "celadon"),
        box("nose_edge", (0,0,5.5), (1,1,1), "ivory"),
    ]
    for side, suffix in ((-1,"L"),(1,"R")):
        for i, (x,z,w,depth,angle) in enumerate(
                ((4.5,.5,7,5.5,14),(9.5,-1.75,7,4,26),(13.5,-4.5,5.5,2.5,39))):
            p = (side*x,0,z)
            a = side*angle
            parts.extend([
                box(f"ceramic_{suffix}_{i}", p, (w,1.5-i*.5,depth), "celadon",a,p),
                box(f"edge_{suffix}_{i}", (side*x,.75-i*.5,z+depth*.47),
                    (w-.25,.5,1), "ivory",a,p),
                box(f"jade_vein_{suffix}_{i}", (side*x,.75-i*.25,z+depth*.18),
                    (w-.5,.5,.5), "jade",a,p),
            ])
        parts.append(box(f"taper_{suffix}",(side*15.5,-.25,-6.25),(3,.5,1),"ivory",side*47))
    # Long restrained highlights instead of noisy texture details.
    colors = (
        ("#78b6a1", "#a7d2ba", "#4e8a7e", "#65a493"),
        ("#e8e7ca", "#ffffef", "#b8c9b7", "#d6d9c4"),
        ("#316969", "#87d3b9", "#17484a", "#45978b"),
        ("#a88249", "#e0bd78", "#6d543a", "#bf9a5e"),
    )
    regions = []
    for i, (base, light, dark, mid) in enumerate(colors):
        for label, local, color in (
            ("face",[0,0,128,128],base), ("light",[0,5,128,13],light),
            ("glint",[11,27,87,4],mid), ("fleck",[100,27,16,4],mid),
            ("shadow",[0,104,128,18],dark),
        ):
            x,y,w,h = local
            regions.append(dict(id=f"{MATERIALS[i]}_{label}", rect=[i*128+x,y,w,h],fill=color))
    return dict(meta={"name":"sulan_returning_windblade","schema_version":1},target="entity",
                atlas={"width":512,"height":128,"regions":regions},parts=parts,
                face_details=[dict(id=label, part=key, kind="geometry") for label,key in (
                    ("floating_jade","inset_jade"),("leading_tip","nose_edge"),("glazed_wing","ceramic_R_1"))])


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--generator",type=Path,required=True)
    cli.add_argument("--preview",type=Path,required=True)
    args = cli.parse_args()
    ART.mkdir(parents=True,exist_ok=True)
    args.preview.mkdir(parents=True,exist_ok=True)
    spec = ART / "returning_windblade.spec.json"
    bbmodel = ART / "returning_windblade.bbmodel"
    spec.write_text(json.dumps(specification(),indent=2)+"\n",encoding="utf-8")
    subprocess.run([sys.executable,str(args.generator),str(spec),"-o",str(bbmodel),"--png",str(TEX)],check=True)
    sys.path.insert(0,str(ROOT/"tools/assets"))
    from render_bbmodel import render, transform
    model = json.loads(bbmodel.read_text(encoding="utf-8"))
    lines = []
    for el in model["elements"]:
        uvs = {tuple(f["uv"]) for f in el["faces"].values()}
        assert len(uvs)==1 and len(el["faces"])==6
        cell = int(next(iter(uvs))[0]//128)
        vertices = [transform([el["to" if i&(1<<axis) else "from"][axis]
                               for axis in range(3)],el)
                    for i in range(8)]
        lines.append("        new float[]{"+", ".join([str(cell),*(f"{n:.5f}f" for v in vertices for n in v)])+"}")
    JAVA.write_text("package com.myriad.character.skills.sulan.rework;\n\n"
                    "// Generated from the validated Blockbench source by tools/vfx/build_sulan_windblade.py.\n"
                    "final class WindBladeModelParts {\n    private WindBladeModelParts() {}\n"
                    "    static final float[][] PARTS = {\n"+",\n".join(lines)+"\n    };\n}\n",encoding="utf-8")
    image = Image.new("RGB",(960,640),"#17232c")
    image.paste(render(bbmodel,view="top",size=(960,320)).transpose(Image.Transpose.ROTATE_180), (0,0))
    # Show the real material/thickness from an oblique angle in a second view.
    image.paste(render(bbmodel,view="isometric",size=(960,320)), (0,320))
    image.save(args.preview/"returning-windblade-model.png")


if __name__=="__main__":
    main()
