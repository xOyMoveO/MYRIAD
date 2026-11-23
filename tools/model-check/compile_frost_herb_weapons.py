"""Author the two staff specs, validate editable sources, then export native item models."""
import argparse
import importlib.util
from itertools import groupby
import json
import math
from pathlib import Path

from compile_ink_wind_weapons import compile_item
from PIL import Image, ImageColor, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art_source" / "frost-herb-weapons"
FACES = ("north", "south", "west", "east", "up", "down")
UPRIGHT_FACES = FACES[:4]
ATLAS_SIZE = 512
MIP_GUTTER = 16
PALETTES = {
    "steel": ("#2c3c57", "#6b86a3", "#1b2941"),
    "silver": ("#a1bbce", "#e1f1f6", "#5e7895"),
    "ice": ("#5caac8", "#bdeff5", "#346895"),
    "frost_inlay": ("#175775", "#7de3ec", "#1b3e62"),
    "icecloth": ("#23304a", "#34435b", "#172334"),
    "frost_core": ("#78afca", "#92bed3", "#6294b0"),
    "bark": ("#654330", "#ad8150", "#3f2f2b"),
    "vine": ("#467354", "#9abe73", "#2d4f3e"),
    "jade_rim": ("#3b755a", "#89b77c", "#234c3f"),
    "dew_core": ("#77b59c", "#98c8af", "#609984"),
    "gold": ("#b38142", "#f8d583", "#6d4b2d"),
    "seed": ("#d19747", "#fbdf90", "#905325"),
    "mosscloth": ("#576148", "#879269", "#353f35"),
}
CORE_FACETS = {
    "frost_core": {
        "north": ("#6bd2f3", "#397fb9", "#99eaff", "#f2ffff"),
        "east": ("#58aee3", "#2c669f", "#7ad6fb", "#d8f8ff"),
        "south": ("#62bce8", "#397bae", "#89dffd", "#ddfaff"),
        "west": ("#60b6e6", "#366fa7", "#8cdafa", "#e6fcff"),
    },
    "dew_core": {
        "north": ("#82ccb0", "#5b9f89", "#a5e2c3", "#e2f9d6"),
        "east": ("#5b9d87", "#3f7e6e", "#96c9a6", "#c3ecc4"),
        "south": ("#75b69c", "#4c8a78", "#9ed4ae", "#d5efc7"),
        "west": ("#5a9d83", "#42826f", "#83c8a5", "#bfedc7"),
    },
}


def opacity(color, alpha):
    return f"{color}{alpha:02x}"


def inside(rect, u0, v0, u1, v1):
    x,y,w,h = rect
    return x <= min(u0,u1) <= max(u0,u1) <= x+w and y <= min(v0,v1) <= max(v0,v1) <= y+h


def pad_materials(parts, regions, details):
    """Give each material a mip-aligned island with edge pixels extended on all four sides."""
    regions = sorted(regions,key=lambda r:r["rect"][3],reverse=True)
    marks = {region["id"]:[] for region in regions}
    for detail in details:
        x,y,w,h = detail["uv"]
        region = next(r for r in regions if inside(r["rect"],x,y,x+w,y+h))
        marks[region["id"]].append(detail)

    packed = []
    strokes = []
    offsets = []
    left = top = row_height = 0
    for region in regions:
        x,y,w,h = region["rect"]
        p = MIP_GUTTER
        slot_w,slot_h = (math.ceil(n/p)*p+p*2 for n in (w,h))
        if left+slot_w > ATLAS_SIZE:
            left,top,row_height = 0,top+row_height,0
        assert top+slot_h<=ATLAS_SIZE, "Staff material atlas is full"
        u,v = left+p,top+p
        offsets.append((region["rect"],u-x,v-y))
        packed.append({**region,"rect":[u,v,w,h]})

        image = Image.new("RGBA",(w,h),region["fill"])
        painter = ImageDraw.Draw(image)
        for detail in marks[region["id"]]:
            a,b,dw,dh = detail["uv"]
            painter.rectangle((a-x,b-y,a-x+dw-1,b-y+dh-1),
                              fill=ImageColor.getcolor(detail["color"],"RGBA"))
            strokes.append({**detail,"uv":[u+a-x,v+b-y,dw,dh]})

        right = slot_w-p-w
        bottom = slot_h-p-h

        def padding(rect, color):
            packed.append({"id":f"pad_{len(packed)}","rect":rect,"fill":"#%02x%02x%02x%02x"%color})

        for j,yy,height in ((0,top,p),(h-1,v+h,bottom)):
            start = u
            for color, run in groupby(image.getpixel((i,j)) for i in range(w)):
                length = sum(1 for _ in run)
                padding([start,yy,length,height],color)
                start += length
        for i,xx,width in ((0,left,p),(w-1,u+w,right)):
            start = v
            for color, run in groupby(image.getpixel((i,j)) for j in range(h)):
                length = sum(1 for _ in run)
                padding([xx,start,width,length],color)
                start += length
            padding([xx,top,width,p],image.getpixel((i,0)))
            padding([xx,v+h,width,bottom],image.getpixel((i,h-1)))

        left += slot_w
        row_height = max(row_height,slot_h)

    for part in parts:
        for face, data in part["faces"].items():
            u0,v0,u1,v1 = data["uv"]
            dx,dy = next((dx,dy) for rect,dx,dy in offsets if inside(rect,u0,v0,u1,v1))
            # Preserve the selected artwork's direction with Blockbench/vanilla's upright UV order.
            if face in UPRIGHT_FACES:
                u0,u1 = u1,u0
            data["uv"] = [u0+dx,v0+dy,u1+dx,v1+dy]
    return packed,strokes


class Staff:
    def __init__(self, name):
        self.name = name
        self.parts = []
        self.regions = []
        self.details = []
        self.swatches = {}
        self.emissive_parts = set()

    def surface(self, material, face, part):
        # Broad faces, side bevels and caps have purposeful color steps.
        exposure = 0 if face in ("north", "east") else 1 if face == "up" else 2
        if material == "icecloth" and face not in ("up", "down"):
            return self.frost_wrap(exposure, face, part)
        key = material, exposure
        if key in self.swatches:
            return self.swatches[key]
        index = len(self.swatches)
        x, y = 2 + (index % 10) * 24, 2 + (index // 10) * 26
        base, light, shade = PALETTES[material]
        fill = (base, light, shade)[exposure]
        # Keep horizontal steps see-through even when several crystal tiers align.
        if material in CORE_FACETS:
            fill = opacity(light if face == "up" else base, 76 if face == "up" else 68)
        self.regions.append({"id": f"swatch_{index}", "rect": [x, y, 20, 22],
                             "fill": fill})
        uv = [x + 1, y + 1, x + 19, y + 21]
        self.swatches[key] = uv
        marks = [(2, 2, 2, 16, light), (15, 3, 2, 17, shade)]
        if material == "mosscloth":
            marks = [(u, v, 3, 2, light) for u in (2,8,14) for v in (4,12,18)]
            marks += [(u, v, 3, 3, shade) for u in (5,11,16) for v in (1,8,15)]
        elif material == "ice":
            marks = [(2,1,3,20,light),(13,1,5,20,shade),(5,2,8,2,light),(6,17,5,2,light)]
        elif material == "steel":
            marks = [(2,1,2,20,light),(4,1,4,20,"#445a77"),(16,1,3,20,shade)]
        elif material == "silver":
            marks = [(1,1,3,20,light),(15,1,4,20,shade),(4,1,6,3,light),(8,16,7,2,"#819db6")]
        elif material == "frost_inlay":
            marks = [(1,1,4,20,shade),(15,1,4,20,shade),(7,1,5,20,"#33849b"),(8,1,2,20,light)]
        elif material in CORE_FACETS:
            marks = []
        elif material == "jade_rim":
            marks = [(1,1,18,4,light),(1,16,18,5,shade)]
        elif material == "bark":
            marks = [(1,1,5,20,"#8d6443"),(1,1,2,20,light),(14,1,5,20,shade),
                     (7,1,2,7,"#805437"),(8,8,2,7,"#805437"),(9,15,2,6,"#805437"),
                     (11,8,2,5,"#4a3329"),(12,10,2,4,"#4a3329")]
        elif material == "vine":
            marks += [(7,2,2,18,"#68994f"),(10,4,1,12,"#80a45e")]
        else:
            marks += [(7, 7, 6, 1, light), (8, 8, 4, 6, shade), (9, 9, 2, 3, light)]
        self.paint(part, face, x, y, marks)
        return uv

    def frost_wrap(self, exposure, face, part):
        key = "icecloth", exposure, "side"
        if key in self.swatches:
            return self.swatches[key]
        # One turn of the wrap per face. The ridge ends at the same height modulo its pitch.
        x, y, width, height = 2 + exposure * 48, 132, 40, 88
        dark = exposure == 2
        base = "#253347" if dark else "#2c3c51"
        ridge = "#354459" if dark else "#3c4d62"
        hem = "#48586c" if dark else "#53647a"
        seam = "#1e2b3d" if dark else "#233246"
        self.regions.append({"id": f"grip_wrap_{exposure}", "rect": [x - 1, y - 1, width + 2, height + 2],
                             "fill": base})
        strokes = []
        for u in range(width):
            offset = u * 15 // (width - 1)
            for v in range(offset - 15, height, 15):
                for row, color in ((v, seam), (v + 1, ridge), (v + 2, ridge),
                                   (v + 3, hem if u % 5 in (1, 2) else ridge)):
                    if 0 <= row < height:
                        strokes.append((u, row, 1, 1, color))
        self.paint(part, face, x, y, strokes)
        uv = [x, y, x + width, y + height]
        self.swatches[key] = uv
        return uv

    def paint(self, part, face, x, y, marks):
        for dx, dy, width, height, color in marks:
            self.details.append({"id": f"detail_{len(self.details)}", "part": part, "kind": "texture",
                                 "face": face, "uv": [x + dx, y + dy, width, height], "color": color})

    def core_surface(self, material, face, part, low, high):
        if face not in CORE_FACETS[material]:
            return self.surface(material, face, part)
        frost = material == "frost_core"
        tile = (140 if frost else 4) + FACES.index(face) * 28
        top = 132
        key = material, face
        if key not in self.swatches:
            base, shade, light, glint = CORE_FACETS[material][face]
            # Transparent body, denser bevel and specular streaks.
            base, shade, light, glint = (opacity(c, a) for c, a in
                                        zip((base, shade, light, glint), (116, 142, 177, 244)))
            self.regions.append({"id": "core_" + face, "rect": [tile, top, 27, 98], "fill": base})
            if frost:
                strokes = [(0,0,5,98,shade),(5,0,6,98,light),(22,0,5,98,shade)]
                for v in range(8,82):
                    u = 7+v//10
                    strokes.append((u,v,3 if v<55 else 2,1,glint))
                    if 45<=v<67:
                        strokes.append((max(1,18-(v-45)//3),v,2,1,light))
                strokes.extend((21, v, 1, 1, glint) for v in range(18,63))
            else:
                strokes = []
                # A glass meniscus and one reflected arc follow the round droplet.
                for v in range(98):
                    shadow = min(10,4+abs(50-v)//10)
                    strokes += [(0,v,shadow,1,shade),(27-shadow,v,shadow,1,shade)]
                    if 23<=v<=55:
                        u=9+((v-31)**2)//140
                        strokes += [(u-1,v,5,1,light),(u,v,2,1,glint)]
                    if 63<=v<=83:
                        u=18-(v-63)//5
                        strokes.append((u,v,3,1,light))
                strokes.extend((17+(v-62)//6, v, 2, 1, glint) for v in range(62,71))
            self.paint(part, face, tile, top, strokes)
            self.swatches[key] = True
        axis = 0 if face in ("north", "south") else 2
        begin, end = low[axis], high[axis]
        if face in ("south", "west"):
            begin, end = 16 - end, 16 - begin
        u0, u1 = (tile+1+round((point-5.75)*5.3) for point in (begin, end))
        ceiling, span = (37,17) if frost else (31,9)
        v0, v1 = (top+1+round((ceiling-point)*95/span) for point in (high[1],low[1]))
        assert tile <= u0 < u1 <= tile + 27 and top <= v0 < v1 <= top + 98
        return [u0, v0, u1, v1]

    def box(self, name, x, y, z, width, height, depth, material, parent="shaft", *,
            rotation=None, pivot=None):
        position = [x + 8, y, z + 8]
        size = [width, height, depth]
        low = [math.floor(v * 2 + .5) / 2 for v in position]
        high = [math.floor((v + span) * 2 + .5) / 2 for v, span in zip(position, size)]
        part = {"id": name, "size": [max(.5, b-a) for a,b in zip(low,high)], "pos": low,
                "uv_mode": "per_face", "faces": {}}
        if parent is not None:
            part["parent"] = parent
        if rotation is not None:
            part["rot"] = rotation
            part["pivot"] = [pivot[0]+8,pivot[1],pivot[2]+8]
        for face in FACES:
            uv = (self.core_surface(material, face, name, low, high)
                  if material in CORE_FACETS else self.surface(material, face, name))
            part["faces"][face] = {"uv": uv}
        self.parts.append(part)
        if material in CORE_FACETS:
            self.emissive_parts.add(name)
        return part

    def center(self, name, cx, y, cz, width, height, depth, material, parent="shaft", **pose):
        return self.box(name,cx-width/2,y,cz-depth/2,width,height,depth,material,parent,**pose)

    def beam(self, name, start, end, width, depth, material, z=0, parent="shaft"):
        x,y = start
        dx,dy = end[0]-x,end[1]-y
        angle = math.degrees(math.atan2(-dx,dy))
        self.center(name,x,y-.25,z,width,math.hypot(dx,dy)+.5,depth,material,parent,
                    rotation=[0,0,angle],pivot=(x,y,z))

    def jewel(self, name, y, profile, material, diamond=False):
        for i, (height,width,depth) in enumerate(profile):
            piece=f"{name}_{i:02}"
            if diamond:
                self.center(piece,0,y,0,width,height,depth,material,rotation=[0,45,0],pivot=(0,y,0))
            else:
                self.center(piece,0,y,0,width,height,depth-.5 if depth>=1.5 else depth,material)
            if not diamond and depth>=1.5:
                for side, tag in ((-1,"front"),(1,"rear")):
                    self.center(f"{piece}_{tag}",0,y,side*depth/2,width-.5,height,.5,material,piece)
            y += height

    def leaf_tile(self, face, part):
        front = face=="north"
        x,y = (120 if front else 188),132
        key = "leaf",face
        if key in self.swatches:
            return x,y
        base,light,edge,vein = (("#5f9f5d","#80b970","#3c7750","#d6d995") if front
                               else ("#4c815a","#6c9c6d","#316348","#a5b77b"))
        self.regions.append({"id":f"leaf_{face}","rect":[x,y,65,108],"fill":base})
        marks = []
        for v in range(108):
            stem = 30+v//27
            marks.append((stem,v,65-stem,1,light))
            marks.append((0,v,7+v//16,1,edge))
            marks.append((stem-1,v,2 if v<26 else 3,1,vein))
            for root in (42,63,84,103):
                distance=root-v
                if 1<=distance<=17:
                    width = 1 if distance>=12 else 2
                    marks.append((stem-distance,v,width,1,vein))
                    marks.append((stem+distance,v,width,1,vein))
        self.paint(part,face,x,y,marks)
        self.swatches[key]=True
        return x,y

    def leaf(self, name, cx, y, cz, angle, scale=1):
        widths = (.75,1.5,2.75,3.5,4,3.75,3,2.25,1.25,.5)
        step = .75*scale
        for i,width in enumerate(widths):
            piece=f"{name}_{i:02}"
            part=self.center(piece,cx,y+i*step,cz,max(.5,width*scale),step,.5,"jade_rim",
                             rotation=[0,0,angle],pivot=(cx,y,cz))
            inset=round((1-width/max(widths))*31)
            for face in ("north","south"):
                x,v=self.leaf_tile(face,piece)
                part["faces"][face]["uv"]=[x+1+inset,v+1+round(105*(9-i)/10),
                                           x+64-inset,v+1+round(105*(10-i)/10)]

    def build(self):
        regions,details = pad_materials(self.parts,self.regions,self.details)
        return {"meta":{"name":self.name},"target":"item","parts":self.parts,"face_details":details,
                "atlas":{"width":ATLAS_SIZE,"height":ATLAS_SIZE,"regions":regions}}


def frost_staff():
    art = Staff("hanze_frost_spine")
    art.center("shaft",0,-16,0,1.75,34,1.75,"steel",None)
    art.center("wrapped_grip", 0, 1, 0, 3, 6, 3, "icecloth")
    for name,y,height in (("lower", -13,12),("upper",8,8.5)):
        for i,z in enumerate((-1,1)):
            art.center(f"{name}_conduit_{i}",0,y,z,1,height,.5,"frost_inlay")
        for i,x in enumerate((-1,1)):
            art.center(f"{name}_silver_rib_{i}",x,y+.5,0,.5,height-.75,.5,"silver")
    for i,y in enumerate((.5,7,16.5)):
        art.center(f"machined_collar_{i}",0,y,0,3.25,.5,3.25,"silver")
    art.center("pommel_ferrule",0,-16.5,0,2.5,1.25,2.5,"silver")
    for i,(y,w,h) in enumerate(((-20,.5,1),(-19,1,1),(-18,1.5,1),(-17,2,1))):
        art.center(f"pommel_ice_{i}",0,y,0,w,h,w,"ice",rotation=[0,45,0],pivot=(0,y,0))
    art.center("crown_socket",0,17,0,4.5,2.5,3.5,"steel")
    art.center("crown_bezel",0,19,0,4,1.5,3.5,"silver")
    art.jewel("ice_lancet",20,[(1,1.5,1.5),(2,2.5,2.5),(3.5,3.75,3.75),
                               (3.5,3.25,3.25),(2.5,2.5,2.5),(2,1.75,1.75),
                               (1.5,1,1),(1,.5,.5)],"frost_core",diamond=True)
    for side,tag in ((-1,"left"),(1,"right")):
        art.beam(f"{tag}_guard",(side*1.25,18),(side*3.75,22.75),1.75,1.5,"steel")
        art.beam(f"{tag}_guard_edge",(side*1.25,18),(side*3.75,22.75),.75,.5,"silver",-.75)
        art.beam(f"{tag}_guard_tip",(side*3.75,22.25),(side*3.25,26.5),1,.85,"silver")
        art.beam(f"{tag}_guard_ice",(side*3.75,22.5),(side*3.5,25.5),.5,.5,"ice",-.55)
        art.center(f"{tag}_socket_bolt",side*1.65,18.5,-1.75,.75,.75,.5,"silver")
    return art


def herb_staff():
    art = Staff("qingluo_dew_bough")
    art.center("shaft",0,.5,0,2.5,7,2.5,"bark",None)
    art.center("wrapped_grip", 0, 1, 0, 3.5, 6, 3.5, "mosscloth")
    lower = ((-1,-19),(.5,-13),(-.5,-7),(0,1))
    upper = ((0,7),(-.5,12),(0,16.5),(1.5,20),(-1.5,25),(-2.5,29),(-.5,31))
    for prefix,points in (("lower",lower),("upper",upper)):
        for i,(start,end) in enumerate(zip(points,points[1:])):
            width = 2.25 if i<3 else 1.75-i*.1
            art.beam(f"{prefix}_heartwood_{i}",start,end,width,1.75,"bark")
    for i,(start,end) in enumerate((((.5,-17),(-.4,-13)),((-.4,-13),(-1,-11)),((-.6,-8),(.45,-5)),
                                     ((.45,-5),(.9,-2)),((.8,7),(-1.25,10)),((-1.25,10),(-1.4,13)),
                                     ((-1.4,13),(.7,16.5)),((.7,16.5),(1.8,19.25)),
                                     ((1.8,19.25),(.5,22)),((.5,22),(-1.25,24.5)))):
        art.beam(f"climbing_vine_{i}",start,end,.6,.5,"vine",-.8)
    for i,y in enumerate((-.5,7.5)):
        art.center(f"reed_lashing_{i}",0,y,0,2.9,.5,2.85,"vine")
    art.beam("bud_stem",(.9,19.7),(2.6,24),.75,1.25,"bark")
    art.beam("outer_bough",(2.6,23),(4.6,24.75),.85,1,"bark")
    art.beam("left_bough",(-1.25,24.5),(-3.75,26.25),.9,1.25,"bark")
    art.center("dew_leaf_seat",0,22.25,0,2.5,.75,1.75,"jade_rim")
    art.jewel("dew_in_leaf_cup",22.75,[(.5,.75,.75),(1,2,1.5),(1.25,3,2.5),
                                      (1.25,2.75,2),(1.25,1.75,1.5),(.75,.75,1)],"dew_core")
    for name,x,y,z,angle,scale in (
        ("canopy_leaf",-.5,30.75,.25,-30,1),("left_broad_leaf",-3.5,26,-.25,52,1),
        ("right_broad_leaf",4.25,24.5,0,-61,.96),("left_inner_leaf",-2,28,.6,25,.69),
        ("front_cup_leaf",-.5,22.25,-1.15,56,.66),("right_cup_leaf",.75,22.5,-1.15,-63,.65),
        ("lower_sprout",.6,15,.1,-55,.69),
    ):
        art.leaf(name,x,y,z,angle,scale)
    for i,(x,y) in enumerate(((2.5,19),(3.25,18.25),(2,17.5))):
        art.beam(f"seed_pedicel_{i}",(1.25,20.25),(x,y+.75),.5,.5,"vine")
        art.center(f"amber_seed_{i}",x,y,0,1,1.15,1,"seed")
    art.beam("root_vein",(-1,-19),(-.65,-16),.75,.75,"vine",-.5)
    return art


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("generator", type=Path, help="Path to the Blockbench skill's generate_bbmodel.py")
    parser.add_argument("--staff", choices=("frost", "herb"), help="Only rebuild the selected staff")
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("generate_bbmodel", args.generator)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    OUT.mkdir(parents=True, exist_ok=True)
    for role, author, item in (("frost", frost_staff, "shuangbing"), ("herb", herb_staff, "qingluo_staff")):
        if args.staff and role != args.staff:
            continue
        staff = author()
        source = staff.build()
        (OUT / f"{staff.name}.spec.json").write_text(json.dumps(source, indent=2) + "\n", encoding="utf-8")
        model, _, findings = module.generate(source)
        if findings:
            raise ValueError(findings)
        for part in model["elements"]:
            if any(low >= high for low, high in zip(part["from"], part["to"])):
                raise ValueError(f"{staff.name}: degenerate part {part['name']}")
            if part["name"] in staff.emissive_parts:
                part["light_emission"] = 15
        print(f"{staff.name}: {len(staff.parts)} editable parts")
        (OUT / f"{staff.name}.bbmodel").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        compile_item(staff.name, item, .69, False, source=OUT)


if __name__ == "__main__":
    main()
