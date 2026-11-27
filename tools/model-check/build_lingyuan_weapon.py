"""Build Lingyuan's Returning Jian from its layered cuboid geometry and painted UV panels."""
import argparse
import base64
from copy import deepcopy
import importlib.util
import io
import itertools
import json
import math
from pathlib import Path
import uuid

from PIL import Image, ImageDraw

from compile_shuo_weapon import hidden

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/lingyuan-weapon"
ASSETS = ROOT / "src/main/resources/assets/myriad"
GENERATOR = ROOT / ".agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py"
SIZE = 256
FACES = ("north", "south", "east", "west", "up", "down")
# The main body of each stepped section ends exactly where its neighbour begins.
BLADE = [(12, 3), (17, 2.75), (21.5, 2.5), (27, 2.25), (32.5, 2), (37, 1.75),
         (40, 1.5), (42, 1.25), (43.5, 1), (44.75, .75), (45.5, .5), (46, .25)]
METALS = {
    "cord": ("#262741", "#5b567e", "#19192e", "#7b7092"),
    "silk": ("#43405d", "#898097", "#28243d", "#b2a2ab"),
    "gold": ("#ac824d", "#ddba7d", "#765233", "#f5ddab"),
    "rim": ("#c49a61", "#efd09a", "#8c633e", "#fff0c6"),
    "recess": ("#6f4f38", "#977451", "#45352d", "#c49e70"),
    "jade": ("#51528b", "#8c94d0", "#322f59", "#bcd9ed"),
    "silver": ("#abbed3", "#d3e2ec", "#768ca7", "#eaf8ff"),
    "blue": ("#758fa7", "#9eb5c9", "#516785", "#c7dae5"),
    "edge": ("#d3e2eb", "#f1f8fa", "#a0b8c9", "#ffffff"),
    "fuller": ("#34415d", "#58667b", "#252b45", "#849aad"),
    "light": ("#92d5d9", "#c3ebea", "#57899c", "#e3fffc"),
}


def tile(material, facing):
    i = list(METALS).index(material)
    return 4 + i * 22, 188 + facing * 22


def projected_uv(panel, face, x, y, w, h):
    # Atlas pixel edges are defined in local model units. North and south have
    # opposite U directions in Java so adjacent pieces always share their seam.
    xc, zero_y, sx, sy = panel
    left = -x-w if face == "north" else x
    return [round(xc + left*sx), round(zero_y-(y+h)*sy), round(xc+(left+w)*sx), round(zero_y-y*sy)]


def make_spec():
    parts, details = [], []
    regions = []
    for mat, colors in METALS.items():
        for side in range(3):
            u, v = tile(mat, side)
            regions.append({"id": f"{mat}_{side}", "rect": [u-2, v-2, 20, 20],
                            "fill": colors[(0, 2, 1)[side]]})
    for name,rect,colour in [
            ("silver_forging", [5,3,54,145], "#acbfd2"),
            ("cloud_gilding", [70,10,81,42], "#b58b52"),
            ("indigo_braid", [161,54,30,88], "#414564"),
            ("jade_facets", [210,34,27,23], "#737ab6"),
            ("channel_inlay", [198,30,7,98], "#94cfd5")]:
        regions.append({"id":name,"rect":rect,"fill":colour})
    panels = {"blade": (32, 192, 8, 4), "cloud": (110, 106, 4, 8),
              "hilt": (176, 98, 8, 8), "stone": (223, 115, 8, 8),
              "glint": (201, 192, 8, 4)}

    def box(name, x, y, z, w, h, d, mat, parent="wrapped_grip", panel=None, broad=None):
        faces = {}
        for face in FACES:
            side = 0 if face in ("north", "south") else 2 if face == "up" else 1
            u, v = tile(broad if broad and side == 0 else mat, side)
            uv = [u, v, u+16, v+16]
            if panel and side == 0:
                uv = projected_uv(panels[panel], face, x, y, w, h)
            faces[face] = {"uv": uv}
        part = {"id": name, "pos": [2*a for a in (x,y,z)], "size": [2*a for a in (w,h,d)],
                "uv_mode": "per_face", "faces": faces}
        pivots = {"wrapped_grip": [0,0,0], "upper_ferrule":[0,10,0], "lower_collar":[0,-10,0],
                  "guard_foundation":[0,14,0], "root_tongue":[0,21,0]}
        if name in pivots:
            part["pivot"] = pivots[name]
        if parent:
            part["parent"] = parent
        parts.append(part)

    # Five alternating turns sit against the original square tang. Tang centre
    # is both the author's origin and the hand rig's fixed grip after fitting.
    box("wrapped_grip", -1.5,-5,-1.5,3,10,3,"cord",None,panel="hilt")
    for i in range(5):
        y = -5 + 2*i
        for side, start_z in [("front", -1.75), ("back", 1.5)]:
            for step in range(3):
                x = -1.5 + step
                rise = step if side == "front" else 2-step
                box(f"braid_{side}_{i}_{step}", x,y+.25+rise*.25,start_z,1,.5,.25,"silk",panel="hilt")
        box(f"braid_selvedge_l_{i}", -1.75,y+.25,-1.5,.25,.5,3,"cord")
        box(f"braid_selvedge_r_{i}", 1.5,y+.75,-1.5,.25,.5,3,"cord")
    box("lower_collar",-2,-6.25,-2,4,1.25,4,"gold")
    box("pommel_neck",-1.75,-6.75,-1.75,3.5,.5,3.5,"recess","lower_collar")
    # The wide old pommel now has clipped corners in plan and a stepped finial.
    box("pommel_belt",-2.5,-7.5,-1.5,5,.75,3,"rim","lower_collar")
    for sign in (-1,1):
        box(f"pommel_face_{sign+1}",-1.5,-7.5,-2.5 if sign<0 else 1.5,3,.75,1,"gold","lower_collar")
    box("pommel_shoe",-2,-8.25,-1.5,4,.75,3,"gold","lower_collar")
    box("pommel_foot",-1.5,-8.75,-1.25,3,.5,2.5,"recess","lower_collar",broad="jade")
    box("pommel_end",-.75,-9,-.75,1.5,.25,1.5,"rim","lower_collar")
    box("upper_ferrule",-1.75,5,-1.75,3.5,.5,3.5,"recess")
    box("ferrule_band",-2,5.5,-2,4,1,4,"gold","upper_ferrule")
    box("ferrule_brass_lip",-2.5,6.5,-2,5,.5,4,"rim","upper_ferrule")
    box("guard_foundation",-2.5,7,-2,5,3.5,4,"gold","upper_ferrule",panel="cloud")
    box("root_tongue",-1,10.5,-1,2,1.5,2,"gold","guard_foundation")
    for sign in (-1,1):
        tag = "l" if sign<0 else "r"

        def wing(name, x, y, w, h, depth, mat, panel="cloud"):
            box(f"{name}_{tag}", x if sign>0 else -x-w,y,-depth/2,w,h,depth,mat,"guard_foundation",panel)

        # Split the gilt ribbon into a thick root, hollow curled tip and raised
        # inner volute. The openings keep the original cloud-guard silhouette.
        for n,(x,y,w,h,d) in enumerate([(2.5,7.5,1.5,1.25,3),(4,8,1.5,1,2.5),
                (5.5,8.5,1.5,1,2.5),(7,8.75,1,.75,2),(8,9,1,.5,1),
                (7.5,9.5,.5,1.25,2),(6.75,10.5,.75,.75,2),
                (5.5,10.75,1.25,.75,2.5),(4.75,10.25,.75,1,2.5),
                (4.5,9.5,.5,.75,2),(5,9.5,1,.5,1.5)]):
            wing(f"cloud_arc_{n}",x,y,w,h,d,"gold")
        for n,(x,y,w,h) in enumerate([(2.5,8,1,.5),(3.5,8.5,.5,.5)]):
            for face,z in [("front",-1.75),("back",1.5)]:
                box(f"relief_{tag}_{n}_{face}",x if sign>0 else -x-w,y,z,w,h,.25,"rim","guard_foundation")

    for face, sign in [("front",-1), ("back",1)]:
        def boss(name, x,y,w,h,offset,d,mat,panel=None):
            z = -offset-d if sign<0 else offset
            box(f"{name}_{face}",x,y,z,w,h,d,mat,"guard_foundation",panel)
        boss("bezel",-1.75,7.25,3.5,3,2,.25,"rim")
        boss("socket",-1.5,7.5,3,2.5,2.25,.25,"recess")
        boss("jade_heart",-1.25,8,2.5,1.5,2.5,.5,"jade","stone")
        boss("jade_crown",-.75,9.5,1.5,.5,2.5,.25,"jade","stone")
        boss("jade_base",-.75,7.5,1.5,.5,2.5,.25,"jade","stone")
        for j,(x,y) in enumerate([(-1.5,7.75),(1.25,7.75),(-1.5,9.25),(1.25,9.25)]):
            boss(f"claw_{j}",x,y,.25,.5,2.5,.25,"rim")

    for face,sign in [("front",-1),("back",1)]:
        # Two short gilded tongues collar the narrow tang, then close to a point
        # over the fuller without changing the silver cutting edges.
        for i,(y,w,h) in enumerate([(11.5,2,1),(12.5,1.5,.75),(13.25,1,.5),(13.75,.5,.5)]):
            box(f"languet_{face}_{i}",-w/2,y,-1.25 if sign<0 else 1,w,h,.25,
                "rim" if i==3 else "gold","root_tongue")

    for i,(y,half) in enumerate(BLADE):
        h = (BLADE[i+1][0] if i+1<len(BLADE) else 46.5) - y
        if half <= .5:
            box(f"tip_core_{i}",-half,y,-.25,half*2,h,.5,"edge","root_tongue")
            continue
        channel = .5 if y<40 else .25
        box(f"fuller_{i}",-channel,y,-.5,2*channel,h,1,"fuller","root_tongue")
        if 14 <= y < 40:
            for face, z in [("front",-.75),("back",.5)]:
                box(f"etched_light_{i}_{face}",-.25,y,z,.5,h,.25,"light","root_tongue",panel="glint")
        for sign in (-1,1):
            side = "l" if sign<0 else "r"
            def facet(name, start, end, depth, mat, panel=None):
                if end > start:
                    x = start if sign>0 else -end
                    box(f"{name}_{i}_{side}",x,y,-depth/2,end-start,h,depth,mat,"root_tongue",panel)
            inner = half-.5
            facet("silver_facet",channel,inner,2,"silver","blade")
            facet("honed_bevel",max(inner,channel),half-.25,1.5,"blue", "blade")
            facet("cutting_edge",half-.25,half,1,"edge")
    for name in ("wrapped_grip","pommel_belt","cloud_arc_7_l","cloud_arc_7_r","jade_heart_front","jade_heart_back",
                 "root_tongue","tip_core_11"):
        details.append({"id":"shape_"+name,"part":name,"kind":"geometry"})
    # Name the face bindings separately from the full painted panels so that
    # the editable spec also identifies the UVs occupied by each focal detail.
    for name,part,uv,colour in [
            ("forte_engraving","silver_facet_0_l",[44,133,1,1],"#607d94"),
            ("silk_thread","wrapped_grip",[175,91,1,1],"#8688ac"),
            ("stone_facet","jade_heart_front",[222,43,1,1],"#9cadd9"),
            ("channel_filament","etched_light_1_front",[200,113,1,1],"#c0eae7")]:
        details.append({"id":name,"part":part,"kind":"texture","face":"north","uv":uv,"color":colour})
    return {"meta":{"name":"lingyuan_returning_jian"},"target":"item","atlas":{"width":SIZE,"height":SIZE,"regions":regions},
            "parts":parts,"face_details":details}


def paint_atlas(original):
    image = Image.open(io.BytesIO(original)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    for material, (base, hi, shade, gleam) in METALS.items():
        for facing in range(3):
            x,y = tile(material, facing)
            draw.line((x+1,y,x+1,y+15), fill=hi)
            draw.line((x+14,y,x+14,y+15), fill=shade)
            draw.line((x+2,y+2,x+2,y+6), fill=gleam if facing==0 else hi)
            draw.line((x+9,y+10,x+9,y+13), fill=base if facing else hi)
    # One longitudinal map for all blade sections. Adjacent faces pick up the
    # same folded-steel line across the taper instead of restarting a swatch.
    for v in range(3,148):
        height=(192-v-.5)/4
        half=next((r for (y,r) in reversed(BLADE) if y<=height),3)
        for u in range(5,59):
            x=(u+.5-32)/8
            fraction=min(1,abs(x)/half)
            left=x<0
            ramp=(["#8ea3bc","#aebfd0","#cad9e3","#e2edf1"] if left
                  else ["#697f9f","#90a5ba","#b2c5d4","#d0dfe7"])
            band=min(3,int(fraction*4))
            colour=ramp[band]
            # Broken forge-water seams and one long specular edge on either bevel.
            fold=.56+.12*math.sin(v*.075)+.04*math.sin(v*.19)
            if abs(fraction-fold)<.037:
                colour=("#b9cbd8" if left else "#a9becd")
            if fraction>.90:
                colour="#e5f3f6" if left else "#c0d4df"
            image.putpixel((u,v), tuple(bytes.fromhex(colour[1:]))+(255,))
    for v in range(30,128):
        taper=min(1,max(0,(v-32)/9),max(0,(124-v)/11))
        for u in range(198,205):
            width=abs(u+.5-201)
            strength=max(0,min(1,taper*2-width*.35))*taper
            low=(58,75,102)
            high=(192,234,231) if u==200 else (130,192,202)
            image.putpixel((u,v),tuple(round(a+(b-a)*strength) for a,b in zip(low,high))+(255,))
    # Paired cloud-scroll engravings at the forte fade out before mid-blade.
    for side in (-1,1):
        for offsets,colour in [
            ([(6,140),(12,134),(15,134),(15,130),(12,128),(9,131),(8,131),(8,126),(13,120),(13,116)],"#607d94"),
            ([(7,140),(13,134),(16,134),(16,130)],"#e1edf0"),
            ([(7,126),(10,122),(10,119),(7,116),(7,111)],"#829caf"),
            ([(9,107),(9,104),(11,101)],"#c9dce4")]:
            draw.line([(32+side*x,y) for x,y in offsets],fill=colour)
    # An antique gilt cloud scroll is painted through both continuous wing UVs.
    draw.rectangle((70,10,150,52), fill="#b58b52")
    for v in range(10,52):
        for u in range(70,151):
            x=abs((u+.5-110)/4)
            height=(106-v-.5)/8
            crest=7.65+.23*x
            t=height-crest
            shade=("#74553b" if t<-.05 else "#986e44" if t<.25 else "#c5985f" if t<.7
                   else "#dfb879" if t<1.2 else "#a87d4e" if t<1.8 else "#ecd098")
            image.putpixel((u,v),tuple(bytes.fromhex(shade[1:]))+(255,))
    for sign in (-1,1):
        for path,col in [([(9,39),(15,36),(19,36),(23,32),(28,32),(31,29)],"#765638"),
                         ([(9,37),(15,34),(19,34),(23,30),(28,30)],"#f1ce8d"),
                         ([(18,26),(20,22),(24,20),(29,20),(29,22)],"#f8dfa2")]:
            draw.line([(110+sign*x,y) for x,y in path],fill=col)
    # The grip's front and reverse sample one eight-strand weave in real size.
    for v in range(54,142):
        for u in range(161,191):
            x=u-164
            a=(v+x//2)%16
            b=(v-x//2+3)%16
            if a<4:
                col=["#8688ac","#5d6087","#4c5277","#2f324e"][a]
            elif b<5:
                col=["#302e4f","#61638c","#4d516f","#424565","#2d304d"][b]
            else:
                col="#222740" if (u+v)%4 else "#333954"
            image.putpixel((u,v),tuple(bytes.fromhex(col[1:]))+(255,))
    # Facets of the small sapphire glass stone are continuous across its
    # stepped crown and foot; each claw and the dark seating remain geometric.
    draw.rectangle((210,34,236,56),fill="#42436d")
    for points,color in [
        ([(211,40),(223,35),(218,44),(212,48)],"#8c98cc"),
        ([(223,35),(234,40),(225,44),(218,44)],"#b7c6e5"),
        ([(212,48),(218,44),(225,44),(222,51),(215,53)],"#737ab6"),
        ([(225,44),(234,40),(235,48),(223,54),(222,51)],"#51598e"),
        ([(218,44),(222,40),(227,41),(225,45),(220,47)],"#9cadd9")]:
        draw.polygon(points, fill=color)
    draw.line((215,42,221,38), fill="#e4f6fa",width=1)
    buf=io.BytesIO()
    image.save(buf,format="PNG")
    return buf.getvalue()


def write(path, value):
    path.write_text(json.dumps(value, indent=2)+"\n", encoding="utf-8", newline="\n")


def fitted(point):
    return [8+point[0]*.25, round(8+point[1]*.25*(27.3-8)/(31.25-8),7), 8+point[2]*.25]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("generator", type=Path, nargs="?", default=GENERATOR)
    args = parser.parse_args()
    module_spec = importlib.util.spec_from_file_location("bb_generator",args.generator)
    bb = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(bb)
    serial = itertools.count()
    bb._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL, f"myriad:lingyuan/returning-jian/{next(serial)}"))
    source = make_spec()
    model, atlas, findings = bb.generate(source, strict=True)
    if findings or not atlas:
        raise ValueError(findings or "A textured build requires Pillow")
    atlas = paint_atlas(atlas)
    model["textures"][0].update({"name":"lingyuan_jade_sword.png","id":"blade","folder":"item","namespace":"myriad",
                                "source":"data:image/png;base64,"+base64.b64encode(atlas).decode("ascii")})
    by_id = {part["uuid"]: part for part in model["elements"]}
    def author_group_pivots(nodes):
        for node in nodes:
            if isinstance(node,dict):
                node["origin"] = by_id[node["children"][0]]["origin"]
                author_group_pivots(node["children"])
    author_group_pivots(model["outliner"])
    write(ART/"lingyuan_returning_jian.spec.json", source)
    write(ART/"lingyuan_returning_jian.bbmodel", model)
    (ART/"lingyuan_returning_jian.png").write_bytes(atlas)

    output = ASSETS/"models/item/lingyuan_sword.json"
    display = json.loads(output.read_text(encoding="utf-8"))["display"]
    item = deepcopy(model)
    item["meta"].update({"format_version":"4.10","model_format":"java_block"})
    item.update({"name":"lingyuan_sword","display":display,"ambientocclusion":False,"front_gui_light":True})
    parts = item["elements"]
    for part in parts:
        for key in ("from","to","origin"):
            part[key] = fitted(part[key])
    elements = []
    for p in parts:
        visible = {face:data for face,data in p["faces"].items() if not hidden(p,face,parts)}
        if not visible:
            raise ValueError(f"Entirely obscured author part: {p['name']}")
        # Blockbench reloads omitted cube faces with its defaults. Disable them
        # explicitly in this editable Java copy to reproduce the exported item.
        p["faces"] = {face:(entry if face in visible else {**entry,"texture":None}) for face,entry in p["faces"].items()}
        elements.append({"name":p["name"],"from":p["from"],"to":p["to"],
                         "faces":{k:{"uv":[v*16/SIZE for v in f["uv"]],"texture":"#blade"}
                                  for k,f in visible.items()}})
    def group_pivots(nodes):
        for node in nodes:
            if isinstance(node,dict):
                node["origin"] = fitted(node["origin"])
                group_pivots(node["children"])
    group_pivots(item["outliner"])
    write(output.with_suffix(".bbmodel"),item)
    texture = "myriad:item/lingyuan_jade_sword"
    write(output, {"credit":"MYRIAD Returning Jian / Lingyuan","gui_light":"front","ambientocclusion":False,
                   "textures":{"blade":texture,"particle":texture},"elements":elements,"display":display})
    (ASSETS/"textures/item/lingyuan_jade_sword.png").write_bytes(atlas)
    print(f"{len(parts)} editable parts, {sum(len(e['faces']) for e in elements)} visible faces")


if __name__ == "__main__":
    main()
