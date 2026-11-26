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
}
FACES = ("north","south","west","east","up","down")
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


def material_atlas():
    atlas = Image.new("RGBA",(256,256),"#2c2236")
    for i,(name,(base,shade,light)) in enumerate(MATERIALS.items()):
        patch = Image.new("RGB",(64,64),base)
        d = ImageDraw.Draw(patch)
        if name in ("coral","amber","violet"):
            d.polygon([(0,0),(19,0),(37,58),(37,64),(0,64)],fill=shade)
            for y in (10,26,42,58):
                d.line((11,y+5,27,y-6,51,y-14),fill=base,width=2)
                d.line((32,y,51,y-11),fill=light,width=2)
            d.line((20,2,37,61),fill=light,width=2)
        elif name=="silk":
            for y in range(-48,120,17):
                d.polygon([(0,y),(64,y-24),(64,y-20),(0,y+4)],fill=shade)
                d.line((0,y+5,64,y-19),fill=light,width=2)
        elif name=="ebony":
            for x in (8,31,49):
                d.line((x,0,x+4,17,x+3,30,x+8,49,x+6,63),fill=shade,width=2)
            d.line((17,4,20,22,19,37),fill=light,width=2)
        elif name=="sun":
            d.polygon([(0,42),(30,4),(64,18),(64,64),(0,64)],fill=light)
            d.polygon([(8,64),(40,26),(64,29),(64,64)],fill=base)
        else:
            d.polygon([(0,0),(13,0),(19,26),(14,64),(0,64)],fill=shade)
            d.polygon([(30,0),(48,0),(31,57),(24,64)],fill=light)
        atlas.paste(patch,(i%4*64,i//4*64))
    return atlas


class Bow:
    def __init__(self, name, draw):
        self.name = name
        self.draw = draw
        self.parts = []

    def box(self, name, material, center, size, angle=0):
        center = [v+8 for v in center]
        i = list(MATERIALS).index(material)
        x,y = i%4*64, i//4*64
        faces = {f:{"uv":[x+2,y+2,x+62,y+62]} for f in FACES}
        self.parts.append({"id":name,"pos":[round(c-s/2,6) for c,s in zip(center,size)],
                           "size":list(size),"pivot":center,"rot":[0,0,angle],
                           "faces":faces,"uv_mode":"per_face"})

    def strip(self, name, material, a, b, width, depth, z=0, overlap=0):
        dx,dy = b[0]-a[0],b[1]-a[1]
        center = ((a[0]+b[0])/2,(a[1]+b[1])/2,z)
        self.box(name,material,center,(width,math.hypot(dx,dy)+overlap,depth),
                 math.degrees(math.atan2(-dx,dy)))

    def limb_point(self, point, sign):
        x,y = point
        bend = max(0,(y-3.64)/(13.66-3.64))**1.55
        return (x+self.draw*1.60*bend,sign*(y-self.draw*1.70*bend))

    def build(self):
        self.box("handle","silk",(0,0,0),(1.22,4.70,1.45))
        for sign,side in [(1,"upper"),(-1,"lower")]:
            def pos(xy):
                return self.limb_point(xy,sign)
            def rod(label,material,a,b,width,depth,z=0,overlap=0):
                self.strip(side+"_"+label,material,pos(a),pos(b),width,depth,z,overlap)

            for i,(a,b) in enumerate(zip(SPINE,SPINE[1:])):
                tip = i>=6
                rod(f"limb_{i}","copper" if tip else "ebony",a,b,WIDTHS[i],DEPTHS[i],overlap=.10)
                # A copper fillet follows the forward edge without obscuring the wood.
                rod(f"fillet_{i}","gold" if tip else "copper",(a[0]-WIDTHS[i]*.36,a[1]),
                    (b[0]-WIDTHS[i]*.36,b[1]),max(.10,WIDTHS[i]*.14),DEPTHS[i]+.045,overlap=.04)
            for i,(a,b,width,mat) in enumerate(FEATHERS):
                # Broad overlapping vanes narrow to individual pointed outer tips.
                for j,(lo,hi,factor) in enumerate([(0,.32,.67),(.31,.67,1),(.66,.85,.63),(.84,1,.25)]):
                    start = tuple(m+(n-m)*lo for m,n in zip(a,b))
                    end = tuple(m+(n-m)*hi for m,n in zip(a,b))
                    thickness = (1.73-.085*i if i<5 else 1.30-.09*(i-5)) - .035*j
                    rod(f"feather_{i}_{j}",mat,start,end,width*factor,thickness,overlap=.035)
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

