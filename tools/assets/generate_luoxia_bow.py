"""Build the four Guixia bow poses from one grip, feather layout and draw curve."""
from itertools import count
from pathlib import Path
import io
import json
import math
import uuid

from PIL import Image, ImageDraw
from generate_yike_weapon import load_compiler, validate

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art_source/luoxia/bow"
ASSETS = ROOT / "src/main/resources/assets/myriad"
POSES = {"guixia_bow": 0, "guixia_bow_pulling_0": .13,
         "guixia_bow_pulling_1": .52, "guixia_bow_pulling_2": 1}
MATERIALS = {
    "ebony": ("#352840", "#201c30", "#665173"),
    "violet": ("#624078", "#3c2b54", "#ab78a6"),
    "copper": ("#b26942", "#763c31", "#f7ba65"),
    "gold": ("#eba74d", "#b26a39", "#ffe09a"),
    "coral": ("#db6454", "#913d54", "#ffad6c"),
    "amber": ("#f69c4a", "#c15e43", "#ffe19b"),
    "silk": ("#70416f", "#412c54", "#ae718e"),
    "string": ("#ffe5b1", "#b98e7b", "#fff4d1"),
    "sun": ("#ffaf48", "#ed653c", "#fff4ac"),
    "dusk_plume": ("#4b385b", "#282239", "#897194"),
    "quill": ("#dd9564", "#a85a4c", "#ffd486"),
    "binding": ("#775174", "#48334f", "#bb8094"),
}
# Local +X points from the grip toward the string, +Y toward the upper nock.
SPINE = [(0,2.12),(-.30,3.64),(.05,5.22),(1.03,7.25),(2.88,9.46),
         (5.35,11.53),(6.57,12.98),(6.78,13.66),(6.58,14.20),(6.02,14.66)]
WIDTHS = [1.18,1.48,1.37,1.13,.96,.67,.48,.35,.20]
DEPTHS = [1.58,1.72,1.56,1.30,1.12,.80,.60,.48,.36]
# (root, tip, width, material). The inner plumes lean towards the string.
FEATHERS = [
    ((-.30,3.95),(-.12,7.05),.80,"coral"),
    ((-.18,5.12),(.67,8.60),1.02,"coral"),
    ((.40,6.46),(1.82,10.10),1.17,"coral"),
    ((1.40,7.70),(3.53,11.48),1.25,"amber"),
    ((2.87,9.33),(5.69,12.61),1.06,"amber"),
    ((.62,4.76),(2.60,6.50),.68,"violet"),
    ((1.20,6.00),(3.65,7.49),.81,"violet"),
    ((2.13,7.39),(4.86,8.95),.88,"violet"),
    ((3.12,8.84),(5.80,10.25),.74,"ebony"),
]


TILE = 64
PLUME_MATERIALS = {"coral", "amber", "violet", "dusk_plume"}
PLUME_BANDS = [(0, .27, .62), (.27, .50, 1), (.50, .68, .90),
               (.68, .83, .63), (.83, .94, .37), (.94, 1, .17)]


def material_atlas():
    atlas = Image.new("RGBA", (256, 256), "#2c2236")
    for i, (name, (base, shade, light)) in enumerate(MATERIALS.items()):
        patch = Image.new("RGB", (TILE, TILE), base)
        d = ImageDraw.Draw(patch)
        if name in PLUME_MATERIALS:
            # One feather, tip at the top. Each stepped vane samples a slice of it.
            d.polygon([(0, 0), (33, 0), (30, 42), (32, 64), (0, 64)], fill=shade)
            for y in range(-9, 88, 9):
                d.polygon([(31, y), (20, y-11), (12, y-14), (22, y-4)], fill=base)
                d.line([(31, y), (37, y-5), (42, y-13)], fill=light)
                d.line([(33, y+3), (40, y-1), (48, y-10)], fill=shade)
            d.line([(32, 0), (31, 42), (32, 63)], fill=light, width=2)
        elif name in ("silk", "binding"):
            for y in range(-24, 96, 9):
                d.polygon([(0, y), (64, y-21), (64, y-18), (0, y+3)], fill=shade)
                d.line((0, y+4, 64, y-17), fill=light)
                for x in range(1, 64, 5):
                    d.point((x, y+5-x//3), fill=light)
        elif name == "ebony":
            for x in range(-2, 64, 9):
                d.line([(x, 0), (x+2, 9), (x, 23), (x+3, 35),
                        (x+1, 54), (x+4, 63)], fill=shade, width=2)
                d.line([(x+5, 8), (x+4, 16), (x+5, 22)], fill=light)
            d.polygon([(28, 31), (31, 25), (32, 33), (30, 39)], outline=shade)
        elif name == "sun":
            # Cut amber catches the light along the ledge between two facets.
            d.polygon([(0, 0), (64, 0), (51, 13), (15, 13), (13, 49), (0, 64)], fill=light)
            d.polygon([(0, 64), (13, 49), (50, 51), (50, 13), (64, 0), (64, 64)], fill=shade)
            d.polygon([(15, 13), (50, 13), (39, 31), (28, 35)], fill="#ffce6f")
            d.line((16, 14, 27, 27), fill="#fff4ac", width=2)
        elif name == "string":
            for y in range(-32, 96, 5):
                d.line((0, y, 64, y-30), fill=shade)
                d.line((0, y+1, 64, y-29), fill=light, width=2)
        else:
            # Fine chased lines along metal trims; the wood and feathers stay dominant.
            for x in range(-5, 70, 13):
                d.polygon([(x, 0), (x+4, 0), (x+2, 30), (x+4, 64), (x+1, 64)], fill=shade)
                d.line([(x+7, 3), (x+6, 27), (x+7, 39)], fill=light)
                d.line((x+8, 46, x+8, 56), fill=light)
        atlas.paste(patch, (i % 4*TILE, i // 4*TILE))
    return atlas


def tile_uv(material, rect):
    tile = list(MATERIALS).index(material)
    x, y = tile % 4 * TILE, tile // 4 * TILE
    u1, v1, u2, v2 = rect
    return {"uv": [round(x+u1, 3), round(y+v1, 3), round(x+u2, 3), round(y+v2, 3)]}


def face_uvs(material, size, vane):
    w, h, d = size
    dimensions = {"north": (w,h), "south": (w,h), "east": (d,h),
                  "west": (d,h), "up": (w,d), "down": (w,d)}
    faces = {}
    for face, (width, height) in dimensions.items():
        # Give woodgrain, wire and wraps proportionate pixels on narrow faces and end caps.
        density = min(13, 60/max(width, height))
        u, v = max(.5, width*density/2), max(.5, height*density/2)
        rect = (32-u, 32-v, 32+u, 32+v)
        if face in ("north", "south"):
            if vane is not None:
                lo, hi, half_width = vane
                rect = (32-half_width, 60-56*hi, 32+half_width, 60-56*lo)
            elif material == "sun":
                rect = (2, 2, 62, 62)
        faces[face] = tile_uv(material, rect)
    return faces


def between(a, b, t):
    return tuple(start + (end-start)*t for start, end in zip(a, b))


class Bow:
    def __init__(self, name, draw):
        self.name = name
        self.draw = draw
        self.parts = []

    def box(self, name, material, center, size, angle=0, vane=None):
        center = [v+8 for v in center]
        faces = face_uvs(material, size, vane)
        self.parts.append({"id":name,"pos":[round(c-s/2,6) for c,s in zip(center,size)],
                           "size":list(size),"pivot":center,"rot":[0,0,angle],
                           "faces":faces,"uv_mode":"per_face"})

    def strip(self, name, material, a, b, width, depth, z=0, overlap=0, vane=None):
        dx,dy = b[0]-a[0],b[1]-a[1]
        center = ((a[0]+b[0])/2,(a[1]+b[1])/2,z)
        self.box(name,material,center,(width,math.hypot(dx,dy)+overlap,depth),
                 math.degrees(math.atan2(-dx,dy)), vane)

    def limb_point(self, point, sign):
        x,y = point
        bend = max(0,(y-3.64)/(13.66-3.64))**1.55
        return (x+self.draw*1.60*bend,sign*(y-self.draw*1.70*bend))

    def build(self):
        self.box("handle","silk",(0,0,0),(1.22,4.70,1.45))
        for sign,side in [(1,"upper"),(-1,"lower")]:
            def pos(xy):
                return self.limb_point(xy,sign)
            def rod(label,material,a,b,width,depth,z=0,overlap=0,vane=None):
                self.strip(side+"_"+label,material,pos(a),pos(b),width,depth,z,overlap,vane)

            for i,(a,b) in enumerate(zip(SPINE,SPINE[1:])):
                tip = i>=6
                rod(f"limb_{i}","copper" if tip else "ebony",a,b,WIDTHS[i],DEPTHS[i],overlap=.10)
                # A copper fillet follows the forward edge without obscuring the wood.
                rod(f"fillet_{i}","gold" if tip else "copper",(a[0]-WIDTHS[i]*.36,a[1]),
                    (b[0]-WIDTHS[i]*.36,b[1]),max(.10,WIDTHS[i]*.14),DEPTHS[i]+.045,overlap=.04)
                if i in (5, 6):
                    rod(f"horn_binding_{i}", "gold", between(a,b,.52), between(a,b,.63),
                        WIDTHS[i]+.08, DEPTHS[i]+.14)
            for i, (a, b, width, mat) in enumerate(FEATHERS):
                material = "dusk_plume" if mat == "ebony" else mat
                depth = 1.73-.085*i if i < 5 else 1.30-.09*(i-5)
                for j, (lo, hi, factor) in enumerate(PLUME_BANDS):
                    rod(f"feather_{i}_{j}", material, between(a,b,lo), between(a,b,hi),
                        width*factor, depth-.04*j, overlap=.035,
                        vane=(lo, hi, 28*width/math.dist(a,b)*factor))
                # A proud central shaft is visible on both faces. It seats into the
                # thicker root and ends before the taper of the barbed vane.
                rod(f"quill_{i}", "quill" if i < 5 else "violet", between(a,b,.10),
                    between(a,b,.70), max(.085,.12*width), depth+.065)
            for j, y in enumerate((.50, 1.30)):
                self.box(f"{side}_grip_wrap_{j}", "binding", (0,sign*y,0),
                         (1.28,.25,1.52), -8)
            # Two lips enclose the purple leather at each end of the handle.
            self.box(f"{side}_collar_socket", "ebony", (0,sign*2.09,0), (1.44,.32,1.60))
            for i,y in enumerate((1.9,2.27)):
                self.box(f"{side}_cuff_{i}","copper" if i==0 else "gold",(0,sign*y,0),
                         (1.47+i*.12,.21,1.62+i*.10))
            # The same bronze sunburst is readable on the two faces of the riser.
            for z,face in [(-1,"front"),(1,"back")]:
                self.box(f"{side}_{face}_bezel","copper",(-.14,sign*3.25,z*.77),
                         (1.46,1.46,.37),sign*45)
                self.box(f"{side}_{face}_onyx","ebony",(-.14,sign*3.25,z*.96),
                         (1.13,1.13,.10),sign*45)
                self.box(f"{side}_{face}_sun","sun",(-.14,sign*3.28,z*1.04),
                         (.66,.66,.14),sign*45)
                for k, x in enumerate((-.64, .64)):
                    rod(f"{face}_setting_{k}", "gold", (-.14+x*.44,2.54),
                        (-.14+x,2.96), .145,.12,z*.98)
                for j,dx in enumerate((-.59,0,.59)):
                    rod(f"{face}_ray_{j}","gold",(-.14+dx*.7,3.55),
                        (-.14+dx,3.98-abs(dx)*.35),.12,.12,z*1.02)
            anchor = pos(SPINE[7])
            self.box(f"{side}_nock","silk",(*anchor,0),(.54,.51,.64))
            # Both halves keep their resting length as the limb tips flex inward.
            offset = math.sqrt(max(0,SPINE[7][1]**2-anchor[1]**2))
            rest = (anchor[0]+offset,0)
            self.strip(f"{side}_string","string",anchor,rest,.115,.115)
        # The arrow runs from the serving to the shallow shelf on the riser.
        for side, z in (("left", -.95), ("right", .95)):
            self.box("arrow_shelf_"+side,"gold",(-.1,-.10,z),(.92,.22,.58))
        self.box("serving","silk",(rest[0],0,0),(.22,.47,.24))
        return {"meta":{"name":self.name},"target":"item","parts":self.parts,
                "atlas":{"width":256,"height":256},"face_details":[]}


def transform(rot, shift, scale):
    return {"rotation":rot,"translation":shift,"scale":[scale]*3}


DISPLAY = {
    "gui":transform([4,-19,-34],[-.85,0,0],.54),
    "ground":transform([0,0,-30],[0,2,0],.36),
    "fixed":transform([0,0,-24],[-.6,0,0],.50),
    "firstperson_righthand":transform([0,-72,8],[.75,1.45,.3],.62),
    "firstperson_lefthand":transform([0,72,-8],[.75,1.45,.3],.62),
    "thirdperson_righthand":transform([-82,-90,0],[0,1.2,1.7],.80),
    "thirdperson_lefthand":transform([-82,90,0],[0,1.2,1.7],.80),
}


def export(model):
    result = {"ambientocclusion":False,"gui_light":"front","display":DISPLAY,
              "textures":{"weapon":"myriad:item/guixia_bow","particle":"myriad:item/guixia_bow"},"elements":[]}
    for part in model["elements"]:
        faces = {face:{"uv":[n/16 for n in data["uv"]],"texture":"#weapon"}
                 for face,data in part["faces"].items()}
        entry = {"name":part["name"],"from":part["from"],"to":part["to"],"faces":faces}
        angle = part["rotation"][2]
        if angle:
            entry["rotation"] = {"origin":part["origin"],"axis":"z","angle":angle,"rescale":False}
        result["elements"].append(entry)
    return result


def main():
    compiler = load_compiler()
    SOURCE.mkdir(parents=True,exist_ok=True)
    buf = io.BytesIO()
    material_atlas().save(buf,format="PNG")
    png = buf.getvalue()
    (ASSETS/"textures/item/guixia_bow.png").write_bytes(png)
    for name,draw in POSES.items():
        spec = Bow(name,draw).build()
        (SOURCE/(name+".spec.json")).write_text(json.dumps(spec,separators=(",",":"))+"\n")
        compiler.validate_schema(spec)
        compiler.validate_geometry(spec)
        ids = count()
        compiler._uuid = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL,f"myriad/{name}/{next(ids)}"))
        model = compiler.build_bbmodel(spec,256,256,png,"guixia_bow")
        model["display"] = DISPLAY
        model["meta"].update({"format_version":"4.10","model_format":"java_block"})
        assert not compiler.validate_bbmodel(model,spec)
        runtime = export(model)
        validate(model,runtime,png)
        (SOURCE/(name+".bbmodel")).write_text(json.dumps(model,separators=(",",":"))+"\n")
        (ASSETS/"models/item"/(name+".json")).write_text(json.dumps(runtime,separators=(",",":"))+"\n")
        # Render-only item definitions select the authored pose without starting a vanilla use action.
        path = ASSETS/"items"/(name+"_held.json")
        path.write_text(json.dumps({"model":{"type":"minecraft:model","model":"myriad:item/"+name}}, indent=2)+"\n")
        print(name,len(spec["parts"]),"elements")


if __name__ == "__main__":
    main()

