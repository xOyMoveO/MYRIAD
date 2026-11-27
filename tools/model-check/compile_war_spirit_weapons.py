"""Build the editable war dao and jade bell, and export their Fabric item assets."""
import argparse
import importlib.util
import json
import math
from pathlib import Path

from compile_frost_herb_weapons import ATLAS_SIZE, FACES, pad_materials
from compile_ink_wind_weapons import compile_item

OUT = Path(__file__).resolve().parents[2] / "art_source/war-spirit-weapons"
PALETTES = {
    "gunmetal": ("#373b48", "#505463", "#282b35", "#606577"),
    "steel": ("#78838b", "#9da9b1", "#555e69", "#bbc6c9"),
    "edge": ("#b6bec5", "#e8edf0", "#75828b", "#f9f5e9"),
    "lacquer": ("#811d2c", "#bb3041", "#4e1122", "#de5661"),
    "bronze": ("#514039", "#79614c", "#352d2d", "#9b7b59"),
    "gold": ("#ac7b3f", "#d4a65f", "#735337", "#f2cf87"),
    "ruby": ("#941d31", "#e04054", "#4c1121", "#ffc0ac"),
    "charcoal": ("#292d38", "#3b3e4c", "#1f2027", "#933044"),
}
BELL_PALETTES = {
    "bronze": ("#685941", "#887657", "#443a30", "#9a8661"),
    "gold": ("#a58a5b", "#bfaa7d", "#766144", "#dac69a"),
    "rim_glint": ("#b8a174", "#d3be92", "#927b54", "#e7d6ac"),
    "celadon": ("#7a9387", "#90a597", "#697f74", "#a4b8a8"),
    "jade": ("#386d63", "#549184", "#244e49", "#83b0a0"),
    "jade_light": ("#548376", "#6c9c8d", "#345f57", "#a0bbac"),
    "lining": ("#243d39", "#3f5750", "#1b2c2b", "#566e63"),
    "teal_wrap": ("#25403f", "#365653", "#182e2e", "#426661"),
    "aqua_glass": ("#83c6b5b6", "#b2e6d4ef", "#5099879f", "#e4faeafc"),
    "cord": ("#617f71", "#8aa491", "#415f55", "#a8bca6"),
    "silk": ("#8a9e8f", "#a4b7a4", "#617c70", "#c0cdb7"),
}


def bell_material_marks(material, base, light, shadow, shine):
    if material in ("gold", "bronze", "rim_glint"):
        # A broad brushed face, with reflected light confined to the bevel.
        return [(0,0,4,30,shadow),(4,0,3,30,light),(7,0,2,30,shine),
                (9,0,3,30,light),(21,0,3,30,shadow),
                (4,0,17,2,light),(4,28,17,2,shadow)]
    if material == "celadon":
        return [(0,0,3,30,shadow),(21,0,3,30,shadow),
                (3,2,18,4,light),(6,3,7,1,shine),(3,27,18,3,shadow)]
    if material in ("jade", "jade_light"):
        return [(0,0,5,30,shadow),(5,0,4,30,light),(9,0,3,30,shine),
                (12,0,2,30,light),(20,0,4,30,shadow),(14,2,6,4,light),
                (5,25,13,3,light)]
    if material == "teal_wrap":
        marks = [(0,0,4,30,shadow),(4,0,5,30,light),(20,0,4,30,shadow)]
        for start in (-8, 2, 12, 22):
            for x in range(1, 22, 3):
                y = start + 7 - x // 3
                if 0 <= y < 29:
                    marks.extend(((x,y,3,1,shadow),(x,y+1,3,1,base if x<5 else light)))
        return marks
    if material == "silk":
        return [(0,0,3,30,shadow),(4,0,3,30,light),(8,0,2,26,shine),
                (13,2,2,28,light),(19,0,2,30,light),(22,0,2,30,shadow)]
    return [(0,0,4,30,shadow),(4,0,5,30,light),(9,0,2,8,shine),(21,0,3,30,shadow)]


class Prop:
    def __init__(self, name):
        self.name = name
        self.parts, self.regions, self.marks = [], [], []
        self.tiles, self.glow = {}, set()

    def mark(self, name, face, origin, x, y, w, h, color):
        self.marks.append({"id": f"tex_{len(self.marks)}", "kind": "texture", "part": name,
                           "face": face, "uv": [origin[0]+x, origin[1]+y, w, h], "color": color})

    def paint(self, name, face, material, lit):
        key = material, lit
        if key in self.tiles:
            return self.tiles[key]
        index = len(self.tiles)
        x, y = (index%10)*28, (index//10)*34
        bell = self.name == "yunsheng_spirit_bell"
        base, light, shadow, shine = (BELL_PALETTES if bell else PALETTES)[material]
        if not lit:
            base, light = shadow, base
            if bell and material != "aqua_glass":
                shine = light
        self.regions.append({"id": f"tile_{index}", "rect": [x,y,24,30], "fill": base})
        marks = [(0,0,3,30,light),(21,0,3,30,shadow),(3,0,18,2,light),(3,28,18,2,shadow)]
        if bell and material != "aqua_glass":
            marks = bell_material_marks(material, base, light, shadow, shine)
        elif material == "charcoal":
            marks = [(0,0,4,30,shadow),(4,0,3,30,light),(21,0,3,30,shadow)]
        elif material in ("bronze", "gold"):
            # The recessed bronze stays warm and dark beside narrow polished arrises.
            marks = [(0,0,3,30,shadow),(3,0,2,30,light),(5,0,1,30,shine),
                     (6,0,4,30,light),(19,0,3,30,base),(22,0,2,30,shadow),
                     (6,2,13,1,light),(11,26,9,1,shadow),(7,24,4,1,light)]
        elif material in ("ruby", "aqua_glass"):
            marks += [(3,2,5,9,shine),(8,2,12,3,light),(17,16,3,10,light),(6,24,14,3,light)]
        elif material == "gunmetal":
            marks = [(1,0,3,30,shadow),(4,0,4,30,light),(16,0,1,30,light),(20,0,4,30,shadow)]
        elif material == "edge":
            marks = [(0,0,4,30,shadow),(12,0,6,30,light),(18,0,5,30,shine)]
        else:
            marks += [(3,2,8,3,shine),(5,5,2,17,light),(16,18,2,7,shadow)]
        for rect in marks:
            self.mark(name,face,(x,y),*rect)
        self.tiles[key]=[x,y,x+24,y+30]
        return self.tiles[key]

    def box(self,name,x,y,z,w,h,d,mat,parent="wrapped_grip",rotation=None,pivot=None):
        lo=[x-w/2+8,y,z-d/2+8]
        a=[math.floor(n*2+.5)/2 for n in lo]
        b=[math.floor((n+s)*2+.5)/2 for n,s in zip(lo,(w,h,d))]
        part={"id":name,"pos":a,"size":[max(.5,j-i) for i,j in zip(a,b)],
              "uv_mode":"per_face","faces":{}}
        if parent is not None:
            part["parent"]=parent
        if rotation is not None:
            part["rot"]=rotation
            part["pivot"]=[pivot[0]+8,pivot[1],pivot[2]+8]
        for f in FACES:
            part["faces"][f]={"uv":self.paint(name,f,mat,f in ("north","up","east"))}
        self.parts.append(part)
        if mat=="aqua_glass":
            self.glow.add(name)
        return part

    def beam(self,name,a,b,width,depth,mat,z=0,parent="wrapped_grip"):
        dx,dy=b[0]-a[0],b[1]-a[1]
        self.box(name,a[0],a[1],z,width,math.hypot(dx,dy),depth,mat,parent,
                 [0,0,math.degrees(math.atan2(-dx,dy))],(a[0],a[1],z))

    def cloud_panel(self,part,face):
        shaded=face in ("south","west")
        key=f"cloud_{int(shaded)}"
        loc=(304+104*int(shaded),0)
        if key not in self.tiles:
            u,v=loc
            self.tiles[key]=True
            self.regions.append({"id":key,"rect":[u,v,88,72],
                                 "fill":"#637e74" if shaded else "#829d90"})
            # Pooled glaze at the shoulder and a soft, narrow window reflection.
            glaze = [(0,0,88,9,"#536e64","#6a887b"),
                     (0,9,88,8,"#5b756c","#749184"),
                     (0,17,88,7,"#607b71","#7d998b"),
                     (0,47,88,9,"#678277","#89a495"),
                     (0,56,88,9,"#6e897d","#93ac9c"),
                     (0,65,88,7,"#728c82","#9ab2a1"),
                     (28,12,5,16,"#708a7e","#98afa0"),
                     (29,15,2,8,"#8da295","#b2c2b3"),
                     (63,48,3,14,"#769085","#9eb7a6")]
            cloud=[
                (30,54,13,4),(27,50,5,7),(25,45,5,8),(28,40,10,6),(32,38,12,5),
                (38,42,5,9),(35,47,6,4),(39,52,10,4),(47,49,8,5),
                (48,44,6,6),(45,40,9,5),(37,35,18,6),(40,31,19,5),
                (44,28,16,4),(47,24,15,5),(52,21,11,4),(58,18,7,4),(61,15,5,4),
                (54,32,11,4),(60,28,7,4),(51,37,12,5),(58,38,7,4),
                (62,35,5,4),(64,32,5,3),(55,43,7,4),(57,47,6,3)]
            for x,y,w,h,dark,light in glaze:
                self.mark(part["id"],face,loc,x,y,w,h,dark if shaded else light)
            for x,y,w,h in cloud:
                self.mark(part["id"],face,loc,x+1,y+1,w,h,"#506d63" if shaded else "#6f8c7e")
            for x,y,w,h in cloud:
                self.mark(part["id"],face,loc,x,y,w,h,"#bfc3ae" if shaded else "#dcdac6")
            for x,y,w,h in ((33,39,5,2),(44,31,8,2),(54,23,5,2),(60,18,3,2)):
                self.mark(part["id"],face,loc,x,y,w,h,"#cdd0bb" if shaded else "#e7e5d2")
        tangential=0 if face in ("north","south") else 2
        low=part["pos"][tangential]
        high=low+part["size"][tangential]
        u0,u1=(loc[0]+round((i-.5)*88/15) for i in (low,high))
        if face in ("south","west"):
            u0,u1=(2*loc[0]+88-u1,2*loc[0]+88-u0)
        y=part["pos"][1]
        v0,v1=(loc[1]+round((18-i)*9) for i in (y+part["size"][1],y))
        part["faces"][face]["uv"]=[u0,v0,u1,v1]

    def forged_panel(self, part, face, material, span=(0,32)):
        # One continuous surface per material, sampled by height so a section joint
        # does not reset the grind highlight or reproduce the same decorative scratch.
        place = {"gunmetal": 354, "edge": 398, "lacquer": 442}
        origin = (place[material], 0)
        name = part["id"]
        if material == "gunmetal":
            base, bright, dark = "#414652", "#555c6a", "#2e323d"
            # The darker longitudinal trough and its lip occupy the same physical
            # X along the blade, even where the breadth of the plate changes.
            lines = [(0,0,6,128,dark),(6,0,4,128,"#343944"),(10,0,2,128,"#65707c"),
                     (12,0,5,128,"#485160"),(23,0,5,128,bright),(28,0,2,128,"#69727c"),
                     (30,0,2,128,"#58616d")]
            for u, y, count in ((10,8,7),(14,31,10),(18,56,7),(17,83,6),(10,105,9)):
                for j in range(count):
                    lines.append((u+j//3,y+j,2,1,"#68717c"))
                    if j<count-2:
                        lines.append((u-1+j//3,y+j+1,1,1,"#373d49"))
            for y in (39,71,103):
                # Three short chisel cuts fan away from the copper spine fittings.
                lines.extend([(12,y,4,1,"#ad8062"),(14,y-1,4,1,"#b9916f"),
                              (16,y-2,4,1,"#76695d"),(12,y+3,3,1,"#83776a")])
        elif material == "edge":
            base, bright, dark = "#b6c0c8", "#f5f8f6", "#76838e"
            lines = [(0,0,3,128,dark),(3,0,4,128,"#909ba7"),(7,0,4,128,"#9faab5"),
                     (11,0,8,128,base),(19,0,6,128,"#cbd5dd"),(25,0,3,128,"#dfe6ec"),
                     (28,0,2,128,bright),(30,0,2,128,"#ccd3d8")]
            for x,y,length in ((7,13,8),(14,36,13),(9,63,10),(19,87,7),(12,109,11)):
                for j in range(length):
                    lines.append((x+j//4,y+j,2,1,"#c4cdd3"))
            for y in (24,51,77,110):
                lines.extend([(27,y,5,1,"#a5b0ba"),(29,y+1,3,1,"#e9eef0")])
        else:
            base, bright, dark = "#8d2637", "#bd4550", "#541927"
            lines = [(0,0,4,128,dark),(4,0,3,128,"#742231"),(13,0,9,128,"#a52f41"),
                     (23,0,3,128,bright),(29,0,3,128,dark)]
            for y in (37,69,101):
                for j,w in enumerate((3,5,7,5,3)):
                    lines.append((16-w//2,y+j,w,1,"#d69a70" if j<3 else "#b67554"))
        key = "continuous_"+material
        if key not in self.tiles:
            self.tiles[key]=True
            self.regions.append({"id":key,"rect":[*origin,32,128],"fill":base})
            for line in lines:
                self.mark(name,face,origin,*line)
        y=part["pos"][1]
        height=part["size"][1]
        v0,v1=(round((44.5-k)*4) for k in (y+height,y))
        u0,u1=span
        if material=="edge" and face=="south":
            u0,u1=u1,u0
        part["faces"][face]["uv"]=[origin[0]+u0,v0,origin[0]+u1,v1]

    def spec(self):
        regions,details=pad_materials(self.parts,self.regions,self.marks)
        return {"meta":{"name":self.name},"target":"item","parts":self.parts,"face_details":details,
                "atlas":{"width":ATLAS_SIZE,"height":ATLAS_SIZE,"regions":regions}}


def dao_grip_wrap(art, grip):
    # A single unwrapped strip follows the four walls, joining at the north-west
    # corner. Each opposing set of bindings advances one turn per circumference.
    u,v=540,0
    art.regions.append({"id":"oxblood_braid","rect":[u,v,48,80],"fill":"#262730"})
    for x in range(0,48,3):
        light = x%12 in (3,6)
        for turn in range(-1,7):
            for pitch, color in ((-1,"#65333d" if light else "#54313b"),(1,"#864049" if light else "#71333f")):
                y=16*turn+pitch*(x//3)
                for dy, height, shade in ((-1,1,"#1d2027"),(0,3,color),(3,1,"#a85958" if light and pitch==1 else "#58313c")):
                    top=max(0,y+dy)
                    bottom=min(80,y+dy+height)
                    if bottom>top:
                        art.mark(grip["id"],"north",(u,v),x,top,3,bottom-top,shade)
    for i,face in enumerate(("north","east","south","west")):
        grip["faces"][face]["uv"]=[u+12*i,v,u+12*(i+1),v+80]


def dao_guard_engraving(art, plates):
    # The paired hooked scrolls are incised across the joined shoulders.
    u,v=610,0
    art.regions.append({"id":"incised_guard","rect":[u,v,72,32],"fill":"#674a3e"})
    marks=[(0,0,72,7,"#977252"),(0,7,72,2,"#be9166"),(0,9,72,2,"#835c46"),
           (0,22,72,3,"#533a35"),(0,25,72,7,"#423334")]
    spiral=[(1,10,7,2),(5,12,6,2),(8,14,7,2),(12,16,5,2),
            (16,16,2,5),(12,20,5,2),(10,18,3,2),(18,14,5,2),
            (21,12,5,2),(24,10,4,2),(25,17,5,1),(27,15,4,1)]
    for x,y,w,h in spiral:
        for left in (x,72-x-w):
            marks.extend(((left,y+1,w,h,"#3c2e2d"),(left,y,w,h,"#c69868")))
    for mark in marks:
        art.mark(plates[0]["id"],"north",(u,v),*mark)
    for part in plates:
        x,y,_=part["pos"]
        w,h,_=part["size"]
        left=round(8*(x-3.5))
        right=left+round(8*w)
        top,bottom=(round(8*(11.5-i)) for i in (y+h,y))
        for face,a,b in (("north",left,right),("south",72-right,72-left)):
            part["faces"][face]["uv"]=[u+a,v+top,u+b,v+bottom]


def dao_garnet(art, table, face):
    # A cushion-cut stone and its four short claws occupy a single face so its
    # facets follow the cut instead of restarting at each step of the setting.
    u,v=700,0
    if "garnet_cut" not in art.tiles:
        art.tiles["garnet_cut"]=True
        art.regions.append({"id":"garnet_cut","rect":[u,v,16,16],"fill":"#623f36"})
        rows=[
            (4,12, 7,10,"#af554e","#d77565","#a24b49"),
            (2,14, 6,12,"#b95652","#d76b66","#913244"),
            (1,15, 5,11,"#c75c5e","#d86b69","#a64050"),
            (1,15, 4,10,"#bf5156","#e07a75","#9b3448"),
            (0,16, 4,11,"#a23849","#d05a63","#8d2e44"),
            (0,16, 4,12,"#923048","#c54755","#82273d"),
            (0,16, 3,12,"#8c2c43","#bd394d","#79243c"),
            (0,16, 3,12,"#84263f","#b3324a","#711d37"),
            (0,16, 3,12,"#79253e","#a72c45","#72233c"),
            (0,16, 4,12,"#752139","#992b42","#79243d"),
            (0,16, 4,11,"#79233c","#8c293e","#8a3347"),
            (0,16, 5,10,"#852b41","#842239","#a74c55"),
            (1,15, 6,10,"#8a3244","#782039","#963e50"),
            (1,15, 7,10,"#8b3947","#712039","#873545"),
            (2,14, 7,10,"#7e3643","#691f34","#74303c"),
            (4,12, 7,10,"#6c2a3a","#631e32","#692739")]
        for y,(start,end,left,right,a,b,c) in enumerate(rows):
            for lo,hi,color in ((start,left,a),(left,right,b),(right,end,c)):
                if hi>lo:
                    art.mark(table["id"],face,(u,v),lo,y,hi-lo,1,color)
        for rect in ((4,3,4,1,"#eab196"),(5,2,1,3,"#fdd8b3"),
                     (12,11,2,1,"#ca696b"),(1,1,2,1,"#b9905b"),
                     (13,1,2,1,"#b9905b"),(1,14,2,1,"#957044"),(13,14,2,1,"#957044")):
            art.mark(table["id"],face,(u,v),*rect)
    table["faces"][face]["uv"]=[u,v,u+16,v+16]


def war_dao():
    art=Prop("chixiao_war_dao")
    box=art.box
    grip=box("wrapped_grip",0,-7,0,2.5,14,2.5,"charcoal",None)
    dao_grip_wrap(art,grip)

    def block(name, x0,x1, y0,y1, z0,z1, material, parent="wrapped_grip"):
        return box(name,(x0+x1)/2,y0,(z0+z1)/2,x1-x0,y1-y0,z1-z0,material,parent)

    # The hand surrounds the same centre section. Bronze sleeves only clasp
    # the very ends of the red-black binding.
    for tag, y0, y1 in (("heel",-7,-6.5),("throat",6,7)):
        block(tag+"_braid_ferrule",-1.5,2,y0,y1,-1.5,2,"bronze")
    for name,y,w,h,d,m in [
        ("grip_neck",7,3.5,.5,3,"bronze"),("neck_band",7.5,3.5,.5,3,"gold"),
        ("pommel_shank",-9.5,2,1,2,"gold"),
        ("ring_top",-10.5,3.5,1,1.5,"gold"),("ring_base",-14,3.5,1,1.5,"gold"),
        ("guard_body",8,4,2.5,3.5,"bronze"),("guard_crown",10.5,4,.5,3,"gold"),
        ("blade_socket",11,3.5,1.5,2.5,"bronze")
    ]:
        box(name,0,y,0,w,h,d,m)
    block("pommel_collar",-1.5,2,-8.5,-7,-1,1.5,"bronze")
    block("pommel_front_band",-1.5,2,-8.5,-8,-1.5,-1,"gold")
    block("pommel_rear_band",-1.5,2,-8.5,-8,1.5,2,"gold")
    block("socket_front_band",-1.5,2,12,12.5,-1.5,-1,"gold","blade_socket")
    block("socket_back_band",-1.5,2,12,12.5,1.5,2,"gold","blade_socket")

    shoulders=[]
    for sign,label in ((-1,"left"),(1,"right")):
        def across(name, x0,x1, y0,y1, z0,z1, mat):
            x0,x1=sorted((sign*x0,sign*x1))
            return block(label+"_"+name,x0,x1,y0,y1,z0,z1,mat,"guard_body")
        shoulders.append(across("guard_root",2,3,7.5,10,-1.5,1.5,"bronze"))
        shoulders.append(across("guard_wing",3,4,8,10,-1,1,"bronze"))
        shoulders.append(across("guard_terminal",4,4.5,8.5,10,-1,1,"bronze"))
        across("wing_glint",2,3,10,10.5,-1.5,1.5,"gold")
        across("wing_cap",3.5,4.5,10,10.5,-1,1,"gold")
        rail_min,rail_max=sorted((sign,sign*1.5))
        rim_min,rim_max=sorted((sign*1.5,sign*2))
        block(label+"_ring_rail",rail_min,rail_max,-12.5,-10.5,-.5,1,"bronze","ring_base")
        block(label+"_ring_joint",rail_min,rail_max,-13,-12.5,-.5,1,"gold","ring_base")
        block(label+"_pommel_side",rim_min,rim_max,-13,-10.5,-.5,.5,"bronze","ring_base")
        for face, z0,z1 in (("front",-1.5,-1),("rear",1,1.5)):
            across(face+"_cap_pin",3.5,4,9,9.5,z0,z1,"gold")
    dao_guard_engraving(art,shoulders)
    # Three nested housings read as a dark-red cabochon framed by cut brass;
    # adjacent steps share edges but never share an exposed face.
    for name,z0,z1,polarity in (("front",-2,-1.5,-1),("rear",2,2.5,1)):
        outside=z0 if polarity<0 else z1
        def faceplate(tag,x0,x1,y0,y1,d0,d1,mat):
            a,b=sorted((outside+polarity*d0,outside+polarity*d1))
            return block(name+"_"+tag,x0,x1,y0,y1,a,b,mat,"guard_body")
        for tier,(half,y0,y1) in enumerate(((1,7.5,8),(1.5,8,10),(1,10,10.5))):
            block(f"{name}_setting_{tier}",-half,half,y0,y1,z0,z1,"gold","guard_body")
        gem=faceplate("garnet_table",-1,1,8,10,0,.5,"ruby")
        dao_garnet(art,gem,"north" if polarity<0 else "south")
        for x in (-1,1):
            block(f"{name}_shank_pin_{x+1}",x-.5,x,11.5,12,
                  -1.5 if polarity<0 else 1.5,-1 if polarity<0 else 2,
                  "gold","blade_socket")

    # Unchanged +X cutting profile. The spine, main grind and silver land have
    # different section thicknesses, and the tip keeps its original coordinates.
    profile=[(12.5,2,-1.0,2.0),(14.5,4,-1.0,2.5),(18.5,4,-1.0,3.0),(22.5,4,-1.0,3.5),
             (26.5,4,-1.0,4.0),(30.5,5,-1.0,4.5),(35.5,3,-1.0,5.0)]
    profile += [(38.5+i*.5,.5,-1.0,5.0-i*.5) for i in range(12)]
    for i,(y,h,x1,x2) in enumerate(profile):
        edge=.5
        bevel=min(1,x2-x1-edge)
        body=x2-x1-edge-bevel
        if body>0:
            plate=block(f"blade_plate_{i:02}",x1,x1+body,y,y+h,-1,1,"gunmetal","blade_socket")
            right=round(32*body/4.5)
            for face,span in (("north",(0,right)),("south",(32-right,32))):
                art.forged_panel(plate,face,"gunmetal",span)
        if bevel>0:
            slope=box(f"blade_bevel_{i:02}",x2-edge-bevel/2,y,0,bevel,h,1,"steel","blade_socket")
            for face in ("north","south"):
                art.forged_panel(slope,face,"edge",(0,20))
        land=box(f"silver_edge_{i:02}",x2-edge/2,y,.25,edge,h,.5,"edge","blade_socket")
        for face in ("north","south"):
            art.forged_panel(land,face,"edge",(20,32))

    for sign,label in ((-1,"front"),(1,"rear")):
        def relief(tag,x0,x1,y0,y1,depth0,depth1,mat):
            z0,z1=sorted((sign*depth0,sign*depth1))
            return block(label+"_"+tag,x0,x1,y0,y1,z0,z1,mat,"blade_socket")
        channel=relief("red_inlay",-.5,.5,13,37.5,1,1.5,"lacquer")
        art.forged_panel(channel,"north" if sign<0 else "south","lacquer")
        relief("spine_trim",-1,-.5,13,38.5,1,1.5,"gold")
        relief("upper_hook",-.5,0,37.5,38.5,1,1.5,"bronze")
        relief("lower_socket",-.5,.5,12.5,13,1,1.5,"bronze")
        for i,y in enumerate((14,21,27.5,33.5)):
            relief(f"copper_lock_{i}",-1,.5,y,y+.5,1.5,2,"bronze")
            relief(f"lock_pin_{i}",-.5,0,y,y+.5,2,2.5,"gold")
        for i,y in enumerate((17.5,25.5,33.5)):
            relief(f"engraved_spur_{i}",.5,1,y,y+.5,1,1.5,"bronze")
    return art


def jade_bell():
    art=Prop("yunsheng_spirit_bell")
    box=art.box
    box("wrapped_grip",0,-4.5,0,2.5,9,2.5,"teal_wrap",None)
    for name,y,w,h,d,mat in [
        ("grip_foot",-7,2.5,.5,2.5,"bronze"),("grip_foot_ring",-6.5,3.5,1.5,3.5,"gold"),
        ("foot_glint",-5,3,.5,3,"rim_glint"),("grip_neck",4.5,3,1,3,"gold"),
        ("neck_glint",5.5,3.5,.5,3.5,"rim_glint"),("bell_mount",6,2.5,11,2.5,"lining"),
        ("cap_plinth",16.5,5.5,1,5.5,"jade"),("cap_enamel",17.5,4.5,1,4.5,"celadon"),
        ("cap_plate",18.5,3.5,1,3.5,"gold"),("ring_socket",19.5,2.5,1,2.5,"bronze"),
        ("loop_bail",20.5,1.5,2,2,"gold"),("loop_bail_glint",21.5,1,.5,2.5,"rim_glint"),
    ]:
        box(name,0,y,0,w,h,d,mat)
    # Coplanar glazed strips taper at the corners; one UV field per face keeps
    # the cloud intact. Corner ribs cover the small assembly seams.
    angle=math.degrees(math.atan2(4.5,6.5))
    strips=[(0,5,8),*((side*(2.75+i*.5),.5,7.5-i*.75)
                      for side in (-1,1) for i in range(10))]
    for axis in ("x","z"):
        for s,label in ((-1,"neg"),(1,"pos")):
            face=("west" if s<0 else "east") if axis=="x" else ("north" if s<0 else "south")
            spin=[0,0,s*angle] if axis=="x" else [-s*angle,0,0]
            pivot=(s*7.5 if axis=="x" else 0,10,s*7.5 if axis=="z" else 0)
            for i,(offset,w,h) in enumerate(strips):
                part=box(f"glazed_flare_{face}_{i}",s*7.25 if axis=="x" else offset,10,
                         s*7.25 if axis=="z" else offset,.5 if axis=="x" else w,h,
                         .5 if axis=="z" else w,"celadon","bell_mount",spin,pivot)
                art.cloud_panel(part,face)
                # Do not draw either shared seam face. End grain is covered by the bands.
                for end in (("north","south") if axis=="x" else ("west","east")):
                    part["faces"].pop(end)
            for i,(y,w,height,mat) in enumerate([(7.5,11,.5,"bronze"),(8,12.5,.5,"lining"),
                    (8.5,14,.5,"bronze"),(9,15.5,1,"gold"),(10,15,.5,"jade_light"),
                    (16,7,.5,"jade_light"),(16.5,7.5,1,"celadon"),(17.5,5.5,.5,"celadon")]):
                pos=s*(w/2-.5)
                box(f"rim_{axis}_{label}_{i}",pos if axis=="x" else 0,y,pos if axis=="z" else 0,
                    1 if axis=="x" else w,height,w-2 if axis=="x" else 1,
                    mat,"bell_mount")
            # Raised, segmented gold trim on the lower lip; dark interior stays open.
            for j,(offset,extent,mat) in enumerate([(-4.75,2.5,"gold"),(0,6,"rim_glint"),(4.75,2.5,"gold")]):
                box(f"rim_inlay_{axis}_{label}_{j}",s*7.5 if axis=="x" else offset,9.5,
                    s*7.5 if axis=="z" else offset,.5 if axis=="x" else extent,.5,
                    .5 if axis=="z" else extent,mat,"bell_mount")
    for sx in (-1,1):
        for sz in (-1,1):
            name=f"brace_{sx+1}_{sz+1}"
            box(name+"_foot",sx*7.5,8.5,sz*7.5,1,2,1,"gold","bell_mount")
            box(name+"_neck",sx*3,16,sz*3,1,1.5,1,"gold","bell_mount")
            box(name+"_stud",sx*7.5,9.5,sz*8,1,.5,.5,"rim_glint","bell_mount")
            dx,dy,dz=-sx*4.5,6.5,-sz*4.5
            p=(sx*7.5,10,sz*7.5)
            spin=[math.degrees(math.atan2(dz,math.hypot(dx,dy))),0,math.degrees(math.atan2(-dx,dy))]
            box(name+"_gold_rib",p[0],p[1],p[2],1,math.sqrt(dx*dx+dy*dy+dz*dz),1,
                "gold","bell_mount",spin,p)
            p=(sx*8,10,sz*7.5)
            box(name+"_fine_inlay",p[0],p[1],p[2],.5,9,.5,"rim_glint","bell_mount",spin,p)
    loop=((0,21),(-3,24.5),(0,28),(3,24.5),(0,21))
    for i,(a,b) in enumerate(zip(loop,loop[1:])):
        # Anchor descending sides at their low endpoint so the unrotated
        # element also stays within Minecraft's cuboid parse bounds.
        if a[1]>b[1]:
            a,b=b,a
        art.beam(f"jade_open_loop_{i}",a,b,1.25,1.5,"jade",parent="ring_socket")
        art.beam(f"jade_loop_polish_{i}",a,b,.75,.5,"jade_light",-1,"ring_socket")
    for i,(x,y) in enumerate(loop[:-1]):
        box(f"jade_loop_joint_{i}",x,y-.5,0,1.25,1,1.5,"jade_light","ring_socket")
    box("clapper_link",0,8,-2.5,.5,5,.5,"gold","bell_mount")
    box("clapper_collar",0,8.5,-2.5,1,.5,1,"rim_glint","clapper_link")
    box("crystal_bell_clapper",0,7,-2.5,1.5,1.5,1.5,"aqua_glass","clapper_link",
        [0,35,0],(0,7,-2.5))
    box("crystal_end",0,6.5,-2.5,1,.5,1,"aqua_glass","crystal_bell_clapper",
        [0,35,0],(0,6.5,-2.5))
    box("tassel_eye",7,8,-6.5,1.5,1.5,1,"gold","bell_mount")
    box("eye_dark_inset",7,8.5,-7, .5,.5,.5,"lining","tassel_eye")
    art.beam("tassel_cord",(7.5,6),(7,8.5),.5,.5,"cord",-7,"tassel_eye")
    for i,(a,b) in enumerate([((6,4.5),(7,5.5)),((5,5.5),(6,6.5)),
                               ((6,4.5),(5,5.5)),((7,5.5),(6,6.5))]):
        art.beam(f"woven_knot_{i}",(a[0]+1.5,a[1]),(b[0]+1.5,b[1]),.5,.5,"cord",-7,"tassel_eye")
    for i,side in enumerate((-1,1)):
        x=7.5+side
        box(f"knot_ear_{i}_rim",7.5+side*1.5,5.5,-7,.5,.5,.5,"cord","tassel_eye")
        for j,y in enumerate((5,6)):
            box(f"knot_ear_{i}_{j}",7.5+side*1.25,y,-7,1,.5,.5,"cord","tassel_eye")
        start=(7.5+side*.5,5)
        cap=(x,3.8)
        art.beam(f"tassel_drop_{i}",cap,start,.5,.5,"cord",-7,"tassel_eye")
        spin=[0,0,-side*7]
        box(f"tassel_band_{i}",x,3.45,-7,1,.75,1,"gold","tassel_eye",spin,(x,3.5,-7))
        p=(x,3.5,-7)
        box(f"tassel_silk_head_{i}",x,2.5,-7,1,1,1,"silk","tassel_eye",spin,p)
        box(f"tassel_silk_flare_{i}",x,.75,-7,1.5,1.75,1.5,"silk","tassel_eye",spin,p)
        for j in range(3):
            box(f"tassel_fibre_{i}_{j}",x+(j-1)*.5,.25+.5*(j==1),-7.25,.5,.75,.5,
                "silk","tassel_eye",spin,p)
    return art


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("generator",type=Path)
    p.add_argument("--only",choices=("chixiao_blade","yunsheng_bell"))
    args=p.parse_args()
    spec=importlib.util.spec_from_file_location("bb_generator",args.generator)
    bb=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bb)
    OUT.mkdir(parents=True,exist_ok=True)
    for author,item,scale,edge,y_fit in ((war_dao,"chixiao_blade",.62,True,.53),
                                         (jade_bell,"yunsheng_bell",.79,False,None)):
        if args.only and item!=args.only:
            continue
        art=author()
        source=art.spec()
        model,_,findings=bb.generate(source,strict=True)
        if findings:
            raise ValueError(findings)
        for part in model["elements"]:
            if part["name"] in art.glow:
                part["light_emission"]=15
        for suffix,data in [("spec.json",source),("bbmodel",model)]:
            (OUT/f"{art.name}.{suffix}").write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8",newline="\n")
        compile_item(art.name,item,scale,edge,source=OUT,stored_y_scale=y_fit,
                     thirdperson_ring=item=="yunsheng_bell")


if __name__=="__main__":
    main()
