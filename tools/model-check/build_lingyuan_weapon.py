"""Author Lingyuan's cuboid spec, run the Blockbench generator, export Fabric JSON."""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil

from compile_ink_wind_weapons import thumbnail
from compile_shuo_weapon import hidden

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/lingyuan-weapon"
ASSETS = ROOT / "src/main/resources/assets/myriad"
FACES = ("north", "south", "east", "west", "up", "down")
SIZE = 256
PALETTE = {
    "wrap": ("#211d42", "#353359", "#16162d", "#534e79"),
    "seam": ("#312a57", "#4e4479", "#211a42", "#615781"),
    "gold": ("#a07844", "#d8b370", "#785331", "#f1d79b"),
    "rim": ("#c2995b", "#e4bf7b", "#997343", "#fff0b4"),
    "recess": ("#62472d", "#967044", "#4a3424", "#bf9359"),
    "jade": ("#424391", "#7776c5", "#2b285c", "#a0b4ee"),
    "silver": ("#afbed6", "#d3dfed", "#8293b5", "#e9f6ff"),
    "edge": ("#d4e1ed", "#eef8ff", "#9cb8d3", "#f8fdff"),
    "blue": ("#587093", "#849dbb", "#394e73", "#b2c5d9"),
    "fuller": ("#27274d", "#39375c", "#1c1e3a", "#544e81"),
    "light": ("#8fdde4", "#c7f7f3", "#5d9dab", "#e8ffff"),
}


def make_spec():
    regions, details, parts, swatches = [], [], [], {}

    def rect(mat, facing):
        if (mat, facing) not in swatches:
            i = len(swatches)
            x, y = i % 8 * 32, i // 8 * 32
            base, hi, shade, gleam = PALETTE[mat]
            uv = [x+2, y+2, x+30, y+30]
            swatches[mat, facing] = (uv, (base, hi, shade, gleam))
            regions.append({"id": f"{mat}_{facing}", "rect": [x, y, 32, 32],
                            "fill": (base, shade, hi)[facing]})
        return swatches[mat, facing]

    def box(name, x, y, z, w, h, d, mat, parent="wrapped_grip", broad=None):
        faces = {}
        for face in FACES:
            material = broad if broad and face in ("north", "south") else mat
            facing = 0 if face in ("north", "south") else 2 if face=="up" else 1
            uv, colors = rect(material, facing)
            faces[face] = {"uv": uv}
            if facing != 0 or face != "north" or any(v["id"] == f"grain_{material}" for v in details):
                continue
            u, v = uv[:2]
            for index, (a, b, width, height, color) in enumerate([
                (2, 2, 2, 24, colors[1]), (25, 2, 2, 25, colors[2]),
                (7, 7, 1, 6, colors[1]), (18, 16, 1, 7, colors[2]),
                (4, 2, 1, 9, colors[3]),
            ]):
                key = f"grain_{material}" if index==0 else f"grain_{material}_{index}"
                details.append({"id":key,"part":name,"kind":"texture","face":face,
                                "uv":[u+a,v+b,width,height],"color":color})
        part = {"id":name,"size":[w,h,d],"pos":[x,y,z],"uv_mode":"per_face","faces":faces}
        if parent:
            part["parent"] = parent
        parts.append(part)

    # Source unit = half a Minecraft model unit. Wrapped grip centre is (0, 0, 0).
    box("wrapped_grip", -1.5,-5,-1.5,3,10,3,"wrap",None)
    for i in range(8):
        y=-5+i*1.25
        # Offset on the reverse makes the blue lacing read as a single helical wrap.
        y=round(y*2)/2
        box(f"wrap_front_{i}",-1.5,y,-2,3,.5,.5,"seam")
        box(f"wrap_back_{i}",-1.5,y+.5,1.5,3,.5,.5,"seam")
        edge=1.5 if i%2==0 else -2
        box(f"wrap_knot_{i}",edge,y,-.5,.5,1,1,"wrap",broad="seam")
    box("lower_collar",-2,-6.5,-2,4,1.5,4,"gold")
    box("pommel_step",-2.5,-7.5,-2.5,5,1,5,"rim")
    box("pommel_shoe",-2,-8.5,-2,4,1,4,"gold")
    box("pommel_end",-1,-9,-1,2,.5,2,"recess",broad="jade")
    box("upper_ferrule",-2,5,-2,4,1.5,4,"gold")
    box("ferrule_brass_lip",-2.5,6.5,-2,5,.5,4,"rim")
    box("guard_foundation",-2.5,7,-2,5,3.5,4,"gold")
    box("root_tongue",-1,10.5,-1,2,1.5,2,"gold")
    for side in (-1,1):
        for n,(x,y,w,h) in enumerate([(2.5,7.5,1.5,1),(4,8,1.5,1),(5.5,8.5,2.5,1),
                                     (7.5,9.5,.5,1.5),(5.5,10.5,2,1),(4.5,10,1,1.5),
                                     (4.5,9,.5,1),(5,9.5,1,.5),(8,9,1,.5)]):
            box(f"cloud_{side+1}_{n}",x if side==1 else -x-w,y,-1.5,w,h,3,
                "gold" if n in (0,1,3,5) else "rim")
        for n,(x,y,w,h) in enumerate([(3,8,1, .5),(4.5,8.5,1,.5),(6,9,1,.5),(5.5,11,1.5,.5)]):
            box(f"cloud_carving_{side+1}_{n}",x if side==1 else -x-w,y,-2,w,h,.5,"recess")
    for sign in (-1,1):
        z=-2.5 if sign<0 else 2
        tag="front" if sign<0 else "back"
        for n,(x,y,w,h) in enumerate([(-2,7,4,.5),(-2,10,4,.5),(-2,7.5,.5,2.5),(1.5,7.5,.5,2.5)]):
            box(f"jade_frame_{tag}_{n}",x,y,z,w,h,.5,"rim")
        box(f"jade_socket_{tag}",-1.5,7.5,z,3,2.5,.5,"recess")
        z=-3 if sign<0 else 2.5
        box(f"jade_gem_{tag}",-1,8,z,2,1.5,.5,"jade")

    # Stepped taper follows the concept, with the dark fuller recessed under the bright shoulders.
    for i,(y,height,half) in enumerate([(12,2,3),(14,5,3),(19,6,2.5),(25,6,2.5),
                                       (31,5,2),(36,3,2),(39,2,1.5),(41,2,1.5),
                                       (43,1,1),(44,1,1),(45,1,.5),(46,.5,.5)]):
        if y<41:
            box(f"fuller_{i}",-.5,y,-.5,1,height,1,"fuller")
            if 14<=y<39:
                for side in [-1,1]:
                    box(f"etched_light_{i}_{side+1}",-.25,y,-.75 if side<0 else .5,.5,height,.25,"light")
            for side in (-1,1):
                def lateral(start, width):
                    return start if side>0 else -start-width
                plate=half-.5-.5
                box(f"silver_face_{i}_{side+1}",lateral(.5,plate),y,-1,plate,height,2,"blue",broad="silver")
                box(f"edge_{i}_{side+1}",lateral(half-.5,.5),y,-.5,.5,height,1,"edge")
        else:
            if i==11:
                box(f"tip_core_{i}",-.25,y,-.25,.5,height,.5,"edge")
            else:
                box(f"tip_core_{i}",-half,y,-.5,half*2,height,1,"silver",broad="edge")
            if half>1:
                box(f"tip_ridge_{i}",-.5,y,-1,1,height,2,"silver")
    for name in ("root_tongue","jade_gem_front","jade_gem_back","tip_core_11"):
        details.append({"id":"shape_"+name,"part":name,"kind":"geometry"})
    # Author at 2x to keep the finest quarter-unit edges on the generator's half-unit grid.
    for part in parts:
        for key in ("pos","size"):
            part[key] = [v*2 for v in part[key]]
    return {"meta":{"name":"lingyuan_returning_jian"},"target":"item","atlas":{"width":SIZE,"height":SIZE,"regions":regions},
            "parts":parts,"face_details":details}


def write(path, value):
    path.write_text(json.dumps(value, indent=2)+"\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("generator", type=Path)
    args = parser.parse_args()
    module_spec = importlib.util.spec_from_file_location("bb_generator",args.generator)
    bb = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(bb)
    source = make_spec()
    ART.mkdir(parents=True, exist_ok=True)
    write(ART/"lingyuan_returning_jian.spec.json", source)
    model, atlas, findings = bb.generate(source, strict=True)
    if findings:
        raise ValueError(findings)
    write(ART/"lingyuan_returning_jian.bbmodel", model)
    (ART/"lingyuan_returning_jian.png").write_bytes(atlas)

    output = ASSETS/"models/item/lingyuan_sword.json"
    display = json.loads(output.read_text(encoding="utf-8"))["display"]
    parts = [{**p, **{key: [8+v*.25 for v in p[key]] for key in ("from","to")}} for p in model["elements"]]
    elements = [{"name":p["name"],"from":p["from"],"to":p["to"],
                 "faces":{k:{"uv":[v*16/SIZE for v in f["uv"]],"texture":"#blade"}
                          for k,f in p["faces"].items() if not hidden(p,k,parts)}} for p in parts]
    elements = [e for e in elements if e["faces"]]
    from itertools import product
    corners = [v for p in parts for v in product(*zip(p["from"],p["to"]))]
    grip = next(e for e in elements if e["name"]=="wrapped_grip")
    assert [(a+b)/2 for a,b in zip(grip["from"],grip["to"])] == [8,8,8]
    assert max(v[1] for v in corners) == 31.25
    # Compress only upper and lower length to the prior first-person tip and comfortable hilt.
    # A uniform fit about the grip preserves rotation, the palm pivot and the blade axis.
    y_fit = (27.3-8)/(31.25-8)
    for p in elements:
        for key in ("from","to"):
            p[key][1] = round(8+(p[key][1]-8)*y_fit,7)
    corners = [(x,8+(y-8)*y_fit,z) for x,y,z in corners]
    for key,rotation,span in [("gui",[10,0,-35],14),("ground",[0,0,85],10.4),("fixed",[0,180,-35],13.2)]:
        display[key] = thumbnail(corners,rotation,span)
    display["ground"]["translation"][1] += 1.1
    assert all(-16<=v<=32 for p in elements for key in ("from","to") for v in p[key])
    texture = "myriad:item/lingyuan_jade_sword"
    write(output, {"credit":"MYRIAD Returning Jian / Lingyuan","gui_light":"front","ambientocclusion":False,
                   "textures":{"blade":texture,"particle":texture},"elements":elements,"display":display})
    shutil.copyfile(ART/"lingyuan_returning_jian.png",ASSETS/"textures/item/lingyuan_jade_sword.png")
    print(f"{len(parts)} editable parts, {len(elements)} visible parts, {sum(len(e['faces']) for e in elements)} faces")


if __name__ == "__main__":
    main()
