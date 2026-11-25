"""Build Luoxia's +Y projectile sources, shared pixel atlas and runtime mesh cache."""
import io
import json
import math
from pathlib import Path
import sys
import uuid

from PIL import Image, ImageDraw

from generate_yike_weapon import load_compiler

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/luoxia/projectiles"
ASSETS = ROOT / "src/main/resources/assets/myriad"
sys.path.insert(0, str(ROOT / "tools/vfx"))
from compile_roster_projectiles import compile_models

# Shared swatches carry directional grain, feather barbs and faceted highlights.
SWATCHES = {
    "wood": ("#7B5055", "#513848", "#AD8270"),
    "copper": ("#CD824D", "#8B4B37", "#FFCF80"),
    "ivory": ("#F3DEAF", "#CA9982", "#FFFADE"),
    "coral": ("#EF755C", "#B3425B", "#FFB878"),
    "plume": ("#775187", "#492F65", "#C293B7"),
    "crystal": ("#A58ACA", "#6A599C", "#F1E6FF"),
    "sun": ("#FFB250", "#D26C39", "#FFF0A2"),
    "ink": ("#48374F", "#30273E", "#7E687D"),
}


def atlas():
    im = Image.new("RGB", (128, 64))
    for i, (name, (base, shade, light)) in enumerate(SWATCHES.items()):
        x, y = i % 4 * 32, i // 4 * 32
        tile = Image.new("RGB", (32,32), base)
        t = ImageDraw.Draw(tile)
        if name in ("ivory", "coral", "plume"):
            for row in range(-8, 44, 10):
                t.line((1,row+6,15,row,30,row+4),fill=shade)
                t.line((6,row+3,15,row-1), fill=light)
            t.line((15,0,15,31),fill=light,width=2)
        elif name in ("wood","ink"):
            for col in (4,14,26):
                t.line((col,0,col,8,col+3,14,col+3,22,col+1,31),fill=shade)
            t.line((8,3,8,11,10,14,10,20),fill=light)
        else:
            t.polygon([(0,0),(9,0),(15,11),(13,31),(0,31)],fill=shade)
            t.polygon([(16,0),(23,0),(20,19),(15,31)],fill=light)
            t.line((27,5,23,21), fill=light)
        im.paste(tile,(x,y))
    return im


# Each head is a sequence of contiguous tapered sections (width, height, depth).
# Fins are individually swept away from the axis. Their stepped trailing edges
# stay visible from behind when the owner is looking down the flight path.
DESIGNS = {
    "basic": ("wood","copper","ivory", [(4,2,3),(7,3,3),(5,3,2),(3,3,2),(1,2,1)], 3, 5, 11),
    "quick": ("wood","coral","coral", [(3,2,2),(4,2,2),(2,4,1),(1,3,1)], 2, 8, 15),
    "full_draw": ("ink","crystal","plume", [(4,2,4),(7,4,6),(6,6,5),(4,6,4),(2,5,2),(1,3,1)], 3, 6, 15),
    "dusk_mark": ("wood","sun","ivory", [(3,2,3),(4,3,2),(2,3,2),(1,2,1)], 2, 6, 12),
    "feather_burst": ("wood","copper","coral", [(3,2,2),(5,4,2),(3,5,2),(1,4,1)], 3, 4, 10),
    "last_sun": ("ink","copper","plume", [(5,3,4),(9,4,4),(11,3,4),(9,4,3),(6,4,3),(3,4,2),(1,3,1)], 3, 6, 13),
    "thousand_feathers": ("wood","sun","coral", [(4,3,3),(6,2,3),(4,3,2),(2,3,2),(1,2,1)], 3, 10, 13),
    "sunset_line": ("ink","ivory","crystal", [(5,3,4),(4,7,3),(3,9,3),(2,6,2),(1,5,1)], 4, 5, 13),
}


def make_spec(key, design):
    shaft, metal, feather, head, fins, span, fin_length = design
    parts = []

    def box(name, material, center, size, rotation=(0,0,0), pivot=None):
        ix = list(SWATCHES).index(material)
        u, v = ix % 4 * 32, ix // 4 * 32
        part = dict(id=name, size=list(size), pos=[round(c-s/2,5) for c,s in zip(center,size)],
                    pivot=list(pivot or center), rot=list(rotation), uv_mode="per_face",
                    faces={face: {"uv":[u+1,v+1,u+31,v+31]} for face in
                           ("north","south","east","west","up","down")})
        if parts:
            part["parent"] = "shaft"
        parts.append(part)

    # Points on one local wing plane, then spin the cuboid around the shaft.
    def vane(name, material, x, y, width, height, thickness, yaw):
        box(name, material, (x,y,0), (width,height,thickness), (0,yaw,0), (0,y,0))

    base = 64 - sum(h for _,h,_ in head)
    box("shaft",shaft,(0,base/2,0),(2,base,2))
    for name,y,h,w in [("nock",1,2,2.8),("binding",14,1.6,2.7),("serving",17,.8,2.5),
                       ("throat",base-4.2,1.1,2.8),("ferrule",base-1.25,2.5,3.3)]:
        box(name,"copper",(0,y,0),(w,h,w))
    for n in range(fins):
        yaw = n * 360 / fins
        for j, (inner,outer,bottom,top) in enumerate([
            (.13,.45,.15,1),(.45,.73,.04,.79),(.73,1,-.08,.54)]):
            vane(f"flight_{n}_{j}", feather if j!=1 or key!="feather_burst" else "ivory",
                 span*(inner+outer)/2, 2+fin_length*(bottom+top)/2,
                 span*(outer-inner), fin_length*(top-bottom), 1.2-j*.15, yaw)
    y = base
    for i,(width,height,depth) in enumerate(head):
        if i == len(head)-1:
            # A bevel cut diagonally across a diamond prism reaches a sharp apex at +64.
            bevel = min(height, width)
            stem = height - bevel / 2
            box("point_ridge",metal,(0,y+stem/2,0),(bevel,stem,depth))
            facet = bevel / math.sqrt(2)
            box("apex",metal,(0,y+stem,0),(facet,facet,depth*.9),(0,0,45))
        else:
            box(f"point_{i}",metal,(0,y+height/2,0),(width,height,depth))
        y += height

    if key == "quick":
        for sign in (-1,1):
            box(f"swift_{'l' if sign<0 else 'r'}","coral",(sign*6.6,3,0),(1.6,8,1),
                (0,0,-sign*18))
    elif key == "full_draw":
        for y in (20,24,30):
            box(f"charge_band_{y}","sun",(0,y,0),(3.6,1.5,3.6))
    elif key == "dusk_mark":
        # Hollow lozenge around a violet pupil, viewed equally well from both faces.
        for i,(x,y,angle) in enumerate([(-3.2,41,36),(3.2,41,-36),(-3.2,49,-36),(3.2,49,36)]):
            box(f"seal_arc_{i}","sun",(x,y,0),(1.8,10,2), (0,0,angle))
        box("pupil","crystal",(0,45,0),(3.8,4,3.8),(0,0,45))
    elif key == "feather_burst":
        for i in range(3):
            box(f"quill_notch_{i}","coral",(-2.5,47+i*3.5,0),(1.8,5,1.4),(0,0,-32))
    elif key == "last_sun":
        box("solar_bezel","sun",(0,48.5,0),(6.6,6.6,5.3),(0,0,45))
        box("solar_heart","coral",(0,48.5,0),(4.3,4.3,6),(0,0,45))
    elif key == "thousand_feathers":
        for n in range(fins):
            for j in range(2):
                vane(f"pinion_{n}_{j}","sun" if j==0 else "ivory",
                     10.8+j*2,5-j*2.2,2,7-j,1,n*360/fins)
    elif key == "sunset_line":
        for sign,side in [(-1,"l"),(1,"r")]:
            for j in range(2):
                box(f"rail_{side}_{j}","crystal",(sign*(3.1-j*.6),37+j*9,0),(1.6,7-j,2))
            box(f"bridge_{side}","sun",(sign*2.2,34,0),(3,1.5,2))
    return {"meta":{"name":"luoxia_"+key},"target":"entity","atlas":{"width":128,"height":64},
            "parts":parts,"face_details":[]}


def main():
    SOURCE.mkdir(parents=True, exist_ok=True)
    compiler = load_compiler()
    buf = io.BytesIO()
    atlas().save(buf,format="PNG")
    png = buf.getvalue()
    (ASSETS/"textures/particle/vfx/luoxia_arrow_atlas.png").write_bytes(png)
    paths = {}
    for key, design in DESIGNS.items():
        spec = make_spec(key, design)
        (SOURCE/(key+".spec.json")).write_text(json.dumps(spec,separators=(",",":"))+"\n")
        compiler.validate_schema(spec)
        compiler.validate_geometry(spec)
        counter = iter(range(1000))
        compiler._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL,f"myriad/luoxia/{key}/{next(counter)}"))
        model = compiler.build_bbmodel(spec,128,64,png,"luoxia_arrow_atlas")
        assert not compiler.validate_bbmodel(model,spec)
        path = SOURCE/(key+".bbmodel")
        path.write_text(json.dumps(model,separators=(",",":"))+"\n")
        paths[key] = path
    meshes = compile_models(paths,ASSETS/"vfx/luoxia_arrows.json")["meshes"]
    for key,mesh in meshes.items():
        assert abs(mesh["tip_y"] - 64) < .00001 and len(mesh["faces"]) <= 192, key
        print(key,mesh["parts"],"cuboids")


if __name__ == "__main__":
    main()

