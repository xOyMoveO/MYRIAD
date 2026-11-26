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
            marks = [(1,0,3,30,light),(20,0,3,30,shadow)]
            for v in range(30):
                for u in range(24):
                    band = (v+u//4)%10
                    if band in (0,1,2):
                        marks.append((u,v,1,1,shine if band==1 else shadow))
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
            lines = [(0,0,5,128,dark),(6,0,4,128,"#4b515e"),(19,0,5,128,bright),
                     (25,0,2,128,"#666e7a"),(28,0,4,128,"#48515c")]
            lines += [(u+(v//3), y+v, 3, 1, "#606875") for u,y,n in
                      ((11,38,8),(15,72,7),(10,104,11)) for v in range(n)]
            lines += [(x,y,1,7,"#79818a") for x,y in ((15,38),(18,72),(14,104))]
        elif material == "edge":
            base, bright, dark = "#b6c0c8", "#f5f8f6", "#76838e"
            lines = [(0,0,5,128,dark),(5,0,6,128,"#939fa9"),(11,0,9,128,base),
                     (20,0,6,128,"#d0d8dd"),(26,0,3,128,"#e5ebed"),(29,0,3,128,bright),
                     (13,24,3,12,"#c2ccd3"),(16,66,2,19,"#c2cbd1")]
        else:
            base, bright, dark = "#8d2637", "#bd4550", "#541927"
            lines = [(0,0,5,128,dark),(6,0,3,128,"#742231"),(12,0,7,128,"#a52f41"),
                     (19,4,3,120,bright),(26,0,6,128,dark)]
            for y in (35,66,97):
                lines += [(14,y,4,1,"#d8996e"),(11,y+1,8,1,"#d8996e"),(14,y+2,4,1,"#d8996e")]
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


def war_dao():
    art=Prop("chixiao_war_dao")
    box=art.box
    box("wrapped_grip",0,-7,0,2.5,14,2.5,"charcoal",None)
    for name,y,w,h,d,m in [
        ("grip_neck",6.5,3.5,1,3,"bronze"),("neck_band",7.5,3.5,.5,3,"gold"),
        ("pommel_collar",-8.5,3.5,1.5,3,"bronze"),("pommel_band",-8.5,3.5,.5,3.5,"gold"),
        ("pommel_shank",-9.5,2,1,2,"gold"),("ring_top",-10.5,3.5,1,1.5,"gold"),
        ("ring_base",-14,3.5,1,1.5,"gold"),("guard_body",8,5,2.5,3.5,"bronze"),
        ("guard_crown",10.5,4,.5,3,"gold"),("blade_socket",11,3.5,1.5,2.5,"bronze"),
        ("socket_rim",12,4,.5,2.5,"gold")
    ]:
        box(name,0,y,0,w,h,d,m)
    for s,label in [(-1,"left"),(1,"right")]:
        box(label+"_ring_rail",s*1.5,-13,0,.5,2.5,1.5,"gold","ring_base")
        box(label+"_guard_arm",s*3,8.5,0,3,1.5,2.5,"bronze","guard_body")
        box(label+"_guard_cap",s*3.70,9.5,0,1.5,.5,3,"gold","guard_body")
        box(label+"_guard_drop",s*2.25,7.5,0,1.5,1,2.5,"bronze","guard_body")
        box(label+"_guard_rail",s*2.30,8.5,s*1.5,2.5,.5,.5,"gold","guard_body")
        box(label+"_ruby_frame",0,8,s*1.9,2.5,2.5,.6,"gold","guard_body")
        box(label+"_ruby_bezel",0,8.45,s*2.2,1.8,1.6,.5,"bronze","guard_body")
        box(label+"_ruby",0,8.74,s*2.5,1.2,1.2,.5,"ruby","guard_body")
        for side,tag in [(-1,"front"),(1,"rear")]:
            box(f"{label}_{tag}_guard_corner",s*4,8.75,side*1.25,.75,1,1,"bronze","guard_body")
            box(f"{label}_{tag}_guard_seam",s*3.63,9.5,side*1.5,1,.5,.5,"gold","guard_body")
            box(f"{label}_{tag}_guard_pin",s*2.5,9,side*1.5,.5,.5,.5,"steel","guard_body")
        box(label+"_pommel_side",s*1.75,-13,0,.5,2.5,1,"bronze","ring_base")
    # The visual cutting line is +X. All three strips meet without overlap,
    # keeping the silhouette but tapering the model depth to a narrow bright land.
    profile=[(12.5,2,-1.0,2.0),(14.5,4,-1.0,2.5),(18.5,4,-1.0,3.0),(22.5,4,-1.0,3.5),
             (26.5,4,-1.0,4.0),(30.5,5,-1.0,4.5),(35.5,3,-1.0,5.0)]
    profile += [(38.5+i*.5,.5,-1.0,5.0-i*.5) for i in range(12)]
    for i,(y,h,x1,x2) in enumerate(profile):
        wide=x2-x1
        edge=.5
        bevel=min(1,wide-edge)
        body=wide-edge-bevel
        if body>0:
            plate=box(f"blade_plate_{i:02}",x1+body/2,y,0,body,h,1.5,"gunmetal","blade_socket")
            for face in ("north","south"):
                art.forged_panel(plate,face,"gunmetal")
        if bevel>0:
            slope=box(f"blade_bevel_{i:02}",x2-edge-bevel/2,y,0,bevel,h,1,"steel","blade_socket")
            for face in ("north","south"):
                art.forged_panel(slope,face,"edge",(0,20))
        land=box(f"silver_edge_{i:02}",x2-edge/2,y,.25,edge,h,.5,"edge","blade_socket")
        for face in ("north","south"):
            art.forged_panel(land,face,"edge",(20,32))
    for side,label in [(-1,"front"),(1,"rear")]:
        z=side
        channel=box(label+"_red_inlay",-.48,13,z,1,24.5,.5,"lacquer","blade_socket")
        art.forged_panel(channel,"north" if side<0 else "south","lacquer")
        box(label+"_spine_trim",-.98,13,z,.5,25.5,.5,"gold","blade_socket")
        box(label+"_inner_trim",.23,14,z,.5,22.5,.5,"bronze","blade_socket")
        for i,y in enumerate((13,21.5,29.5,37.5)):
            box(f"{label}_spine_clip_{i}",-.54,y,z*1.25,1.5,.5,.5,"gold","blade_socket")
        box(label+"_top_crook",.27,36.5,z,.5,1.5,.5,"gold","blade_socket")
        for i,y in enumerate((15,36)):
            box(f"{label}_fuller_stud_{i}",.8,y,z,.5,.5,.5,"bronze","blade_socket")
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
