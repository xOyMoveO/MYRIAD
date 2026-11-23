"""Produce the War/Spirit 128px animated layers and validate/export Blockbench models."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/chixiao-yunsheng-vfx"
TEX = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx"
JAVA = ROOT / "src/main/java/com/myriad/character/ability/WarSpiritModelParts.java"
FACES = "north south east west up down".split()
METAL = (("#9a203f", "#fb666c", "#441626"), ("#a9c2cf", "#f9fff3", "#4e687f"),
         ("#553146", "#9c5363", "#28192d"), ("#b17c50", "#f6d098", "#65423f"))
PAPER = (("#bfeada", "#ffffeb", "#789e91"), ("#eef0d5", "#fffde9", "#a0bcae"),
         ("#408b82", "#a6f4cb", "#265655"), ("#b48e53", "#ffe4ab", "#765844"))


def box(name, xyz, whd, cell, yaw=0, roll=0):
    u = cell * 128
    return dict(id=name, pos=[v-w/2 for v,w in zip(xyz,whd)], size=whd, pivot=xyz,
                rot=[0,yaw,roll], uv_mode="per_face", faces={k:{"uv":[u+1,1,u+127,127]} for k in FACES})


def cutting_wave():
    a = [box("lacquer_hub", (0,0,.35), (6,2.6,4),0), box("silver_apex",(0,.1,3.65),(2.4,1.4,3),1),
         box("armored_keel",(0,-.8,-1.5),(4,1.2,4),2), box("brass_center_lock",(0,1.45,1.36),(2,.6,1.5),3)]
    for sign, side in ((-1,"L"),(1,"R")):
        for i,(x,z,w,d,yaw) in enumerate(((3.75,1.8,6,4.2,13),(8.1,.6,5.4,3.5,22),
                                         (12,-1.1,4.6,2.8,30),(15.2,-3.1,3.8,1.65,42))):
            def part(name, yy, zz, width, h, depth, cell):
                a.append(box(f"{name}_{side}_{i}",(sign*x,yy,z+zz),(width,h,depth),cell,sign*yaw))
            part("steel_facet",0,0,w,1.45-i*.24,d,1)
            part("cutting_edge",.12,d*.42,w-.3,.61-i*.09,.85-i*.11,1)
            part("red_spine",.55-i*.08,-d*.23,w-.5,.65-i*.09,.82,0)
            if i<3: part("brass_rivet",.92-i*.12,-d*.24,.68,.37,.67,3)
        a.append(box(f"silver_tip_{side}",(16.5*sign,0,-4.36),(2.9,.28,.68),1,sign*51))
    return a


def paper_crane():
    a = [box("folded_keel",(0,0,0),(3.4,1.6,7),2),box("ivory_fold_ridge",(0,.94,.13),(1.2,1.4,6.8),1),
         box("jade_chest",(0,-.88,2.2),(2.3,1.25,2),0),box("gold_chest_knot",(0,-1.57,2.23),(1,.45,1),3,45),
         box("neck_lower",(0,1.25,3.15),(1.25,1.25,2.1),1),box("neck_upper",(0,2.15,3.64),(1,1.5,1.1),0),
         box("little_head",(0,2.95,4.1),(1.25,.83,1.65),1),box("beak_root",(0,2.8,5.11),(.75,.5,1.1),3),
         box("beak_tip",(0,2.71,5.85),(.34,.22,.75),3)]
    for sign,side in ((-1,"L"),(1,"R")):
        a.append(box("tail_fork_"+side,(sign*1.01,-.1,-4.45),(1,.55,3.45),0,sign*15))
        a.append(box("eye_"+side,(sign*.64,3.05,4.33),(.13,.24,.27),2))
        for i,(x,y,z,w,d,yaw) in enumerate(((2.65,.2,0,3.8,4.6,8),(5.15,.83,-.6,3.7,3.9,15),
                    (7.65,1.6,-1.4,3.7,3.1,24),(10.05,2.23,-2.55,3.3,2.1,34),(11.85,2.7,-3.62,2.45,1.1,42))):
            a.append(box(f"paper_fold_{side}_{i}",(x*sign,y,z),(w,.58-i*.07,d),1,sign*yaw,sign*15))
            a.append(box(f"crease_{side}_{i}",(x*sign,y+.21,z-d*.31),(w-.18,.3-i*.035,.46),0,sign*yaw,sign*15))
            if i<3: a.append(box(f"gold_pin_{side}_{i}",(x*sign,y+.26,z+d*.27),(.54,.23,.65),3,sign*yaw,sign*15))
    return a


def beacon():
    a = [box("pedestal",(0,.65,0),(6.5,1.3,5.8),2),box("chamfered_plinth",(0,1.45,0),(5,1,5),3,45),
         box("bell_crown",(0,6,0),(2.8,1.15,2.8),0,45),box("bell_body",(0,4.8,0),(3.6,1.95,3.6),2),
         box("bell_flare",(0,3.65,0),(4.8,1,4.8),0),box("lip_gilding",(0,3.17,0),(5,.28,5),3),
         box("tongue_stem",(0,2.87,0),(.45,.9,.45),3),box("tongue_pendant",(0,2.44,0),(.83,.45,.83),3,45)]
    for sign,side in ((-1,"L"),(1,"R")):
        a.extend((box("post_"+side,(sign*3,4.4,0),(.62,5.5,.72),3),
                  box("frame_crown_"+side,(sign*2.3,7.15,0),(2.4,.55,.77),3,0,-sign*25),
                  box("paper_tassel_"+side,(sign*3.02,4.55,.57),(.65,2.5,.18),1,0,sign*7),
                  box("jade_inset_"+side,(sign*1.57,4.7,1.84),(.48,1.35,.27),0)))
    a.append(box("frame_peak",(0,7.64,0),(3,.55,.83),2))
    return a


def spec(name, parts, palette):
    regions = []
    for i,(body,light,dark) in enumerate(palette):
        for k,x,y,w,h,color in (("base",0,0,128,128,body),("shadow",0,88,128,40,dark),
                               ("rim",0,6,128,16,light),("streak",10,33,85,5,light),
                               ("end_glint",99,33,17,5,light)):
            regions.append(dict(id=f"m{i}_{k}",rect=[i*128+x,y,w,h],fill=color))
    return dict(meta={"name":name,"schema_version":1}, target="entity",
                atlas=dict(width=512,height=128,regions=regions),parts=parts,
                face_details=[dict(id=f"silhouette_{i}",part=p["id"],kind="geometry") for i,p in enumerate(parts)])


def frames(name, paint):
    image = Image.new("RGBA", (512,128))
    for i in range(4):
        sub = Image.new("RGBA", (128,128))
        paint(ImageDraw.Draw(sub),i)
        assert all(v in range(3,126) for v in sub.getbbox()), (name,sub.getbbox())
        image.paste(sub,(i*128,0))
    image.save(TEX / (name+".png"))
    return image


def fan(draw, i, core):
    # The leading (top) edge is convex; the two tapered points are at the sides.
    y = i*2
    polygon = [(5,91),(13,69),(28,49),(47,35),(64,30),(80,32),(101,44),(115,64),(122,85),
               (111,76),(98,63),(81,54),(63,52),(48,56),(33,65),(19,80)]
    draw.polygon([(x,yy+y) for x,yy in polygon],fill="#e34259d5" if not core else "#b66971f0")
    draw.polygon([(x,yy+y) for x,yy in [(9,88),(18,67),(31,50),(49,37),(65,33),(80,35),(101,47),
                 (115,65),(121,84),(107,66),(95,55),(79,47),(64,44),(49,47),(35,55),(22,70)]],
                 fill="#fff8dbff" if core else "#651933b0")
    for k in range(6):
        x=13+k*18; yy=104-abs(2-k)*4+(k*i)%8
        draw.polygon([(x,yy),(x+6,yy-3),(x+12,yy-3),(x+8,yy+1)],fill="#f4ad83c0")


def ribbon(draw,i,warm):
    pale, mid, shade=("#fff9deff","#f05c69df","#8a243d80") if warm else ("#fff9dcff","#85efd8ed","#3a8d8c8f")
    for n in range(3):
        y=42+22*n
        draw.polygon([(5,y+3),(25+i*4,y-4),(42,y+3),(63,y-8),(84,y-9),(104,y-5),(121,y+1),
                      (103,y+7),(87,y+4),(65,y+6),(44,y+12),(25+i*4,y+4)],fill=(shade,mid,shade)[n])
        if n==1: draw.line([(11,y+3),(30,y),(45,y+5),(65,y-2),(86,y-4),(106,y-1),(120,y+1)],fill=pale,width=3)


def knotted(draw,i,warm):
    pale, mid, shade=("#fff5dfff","#ee6266e9","#842642cd") if warm else ("#fff4cfff","#9de9d2ef","#367976ce")
    for n in (-1,1):
        points=[(64,9),(64+n*(35+i*2),33),(64+n*(44+i),68),(64+n*24,104),(64,118),
                (64+n*8,97),(64+n*27,70),(64+n*24,43)]
        draw.polygon(points,fill=shade,outline=mid,width=3)
        draw.line([(64,12),(64+n*(32+i*2),33),(64+n*(41+i),68),(64+n*23,101)],fill=pale,width=3)
        for k in range(3):
            x=64+n*(17+k*6); y=32+k*17+i*2
            draw.polygon([(x,y),(x+4,y+3),(x,y+7),(x-4,y+3)],fill=pale)


def rim(draw,i,warm):
    pale, mid, shade=("#fff8e0ff","#ee5c60ee","#7d1f3d99") if warm else ("#fff2d5ff","#9cead9ed","#437d7d9f")
    for k in range(8):
        start=k*45+(i*2 if k%2==0 else -i*2)+4
        draw.arc((5,5,122,122),start,start+29,fill=shade,width=6)
        draw.arc((8,8,119,119),start+2,start+26,fill=mid,width=3)
        draw.arc((10,10,117,117),start+7,start+17,fill=pale,width=2)
        if k%2==0: draw.arc((17,17,110,110),start+12,start+30,fill=mid,width=2)


def debris(draw,i,warm):
    a,b,c=("#ffe1adff","#ea6478e9","#9d2c48c0") if warm else ("#fff9d3ff","#bbefe1e9","#488f89c0")
    for k in range(13):
        x=10+(k*37+i*(k%4+1))%98; y=10+(k*53+i*4)%102
        s=(k%4)+2
        draw.polygon([(x,y),(x+s*2,y-s),(x+s+3,y+s),(x-2,y+s+2)],fill=(a,b,c)[k%3])


def main():
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--generator",required=True,type=Path)
    cli.add_argument("--preview",required=True,type=Path)
    args=cli.parse_args()
    args.preview.mkdir(parents=True,exist_ok=True)
    ART.mkdir(parents=True,exist_ok=True)
    sys.path.insert(0,str(ROOT/"tools/assets"))
    from render_bbmodel import render, transform
    models=(("war_hd_wave_model",cutting_wave(),METAL,"WAVE"),
            ("spirit_hd_crane_model",paper_crane(),PAPER,"CRANE"),
            ("spirit_hd_beacon_model",beacon(),PAPER,"BEACON"))
    board=Image.new("RGB",(1200,640),"#192932")
    lines=[]
    for i,(name,parts,colors,key) in enumerate(models):
        source=ART/(name+".spec.json"); edit=ART/(name+".bbmodel")
        source.write_text(json.dumps(spec(name,parts,colors),indent=2)+"\n",encoding="utf-8")
        subprocess.run([sys.executable,str(args.generator),str(source),"-o",str(edit),"--png",str(TEX/(name+".png"))],check=True)
        model=json.loads(edit.read_text(encoding="utf-8")); data=[]
        for el in model["elements"]:
            uvs={tuple(f["uv"]) for f in el["faces"].values()}
            assert len(uvs)==1 and len(el["faces"])==6
            cell=int(next(iter(uvs))[0]//128)
            points=[transform([el["to" if n&(1<<ax) else "from"][ax] for ax in range(3)],el) for n in range(8)]
            data.append("        {"+", ".join([str(cell),*(f"{v:.5f}f" for p in points for v in p)])+"}")
        lines.append(f"    static final float[][] {key} = {{\n"+",\n".join(data)+"\n    };\n")
        for j,view in enumerate(("isometric","top")):
            shot=render(edit,view=view,size=(400,320))
            board.paste(shot,(400*i,320*j))
    JAVA.write_text("package com.myriad.character.ability;\n\n"
                    "// From validated Blockbench models. Regenerate via tools/vfx/build_war_spirit_vfx.py.\n"
                    "final class WarSpiritModelParts {\n    private WarSpiritModelParts() {}\n"+"".join(lines)+"}\n",encoding="utf-8")
    board.save(args.preview/"war-spirit-models.png")
    layer_board=Image.new("RGB",(1024,768),"#243b2c")
    for i,(warm,role) in enumerate(((True,"war"),(False,"spirit"))):
        recipes=[("edge",lambda d,f:fan(d,f,True)),("lacquer",lambda d,f:fan(d,f,False))] if warm else []
        for y,(key,recipe) in enumerate(recipes+[("ribbon",lambda d,f:ribbon(d,f,warm)),("sigil",lambda d,f:knotted(d,f,warm)),
                                   ("rim",lambda d,f:rim(d,f,warm)),("flakes",lambda d,f:debris(d,f,warm))]):
            strip=frames(f"{role}_hd_{key}",recipe)
            layer_board.paste(strip,(i*512,y*128),strip)
    layer_board.save(args.preview/"war-spirit-atlas-review.png")


if __name__=="__main__":
    main()
