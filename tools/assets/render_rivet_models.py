"""Render Rivet's actual delivery geometry and UVs; never starts a game client.

--before points to a saved original art_source/rivet directory. Comparison panels
use identical cameras and world scales. Wall blocks are plain preview fixtures;
the anchor is the real BBModel with the runtime +Y-normal basis and scale 1.2.
"""
from __future__ import annotations
import argparse
import base64
import copy
import io
import json
import math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from render_bbmodel import render,VIEWS

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"art_source/rivet"
ASSET=ROOT/"src/main/resources/assets/myriad"
BACKGROUND="#101c27"
VIEWS["strike_end"]=((0,0,-1),(0,1,0),(-1,0,0))
VIEWS["cage_end"]=((0,0,1),(0,1,0),(1,0,0))


def font(size):
    return ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf",size)


def load_item():
    """Render the exported Java item, including its culled faces and delivery PNG."""
    data=json.loads((ASSET/"models/item/rivet_hammer.json").read_text())
    png=(ASSET/"textures/item/rivet_metal.png").read_bytes()
    model={"resolution":{"width":256,"height":256},"textures":[{"source":"data:image/png;base64,"+base64.b64encode(png).decode()}],"elements":[]}
    for part in data["elements"]:
        element={key:copy.deepcopy(part[key])for key in("name","from","to")}
        element["faces"]={name:{"uv":[value*16 for value in face["uv"]],"texture":0}for name,face in part["faces"].items()}
        element["rotation"]=[0,0,0];element["origin"]=part["from"]
        if "rotation"in part:
            rotation=part["rotation"];element["rotation"]["xyz".index(rotation["axis"])]=rotation["angle"];element["origin"]=rotation["origin"]
        model["elements"].append(element)
    return model


def panel(model,title,subtitle,size,view="isometric",scale=None,center=None):
    result=Image.new("RGBA",size,BACKGROUND)
    content=render(model,view=view,size=(size[0],size[1]-105),scale=scale,center=center,background=BACKGROUND)
    result.alpha_composite(content,(0,105));draw=ImageDraw.Draw(result)
    draw.text((24,18),title,font=font(25),fill="#e5bf6b")
    draw.text((24,54),subtitle,font=font(16),fill="#9eb8be")
    return result


def fixture_texture():
    image=Image.new("RGBA",(64,64),"#515a5f");draw=ImageDraw.Draw(image)
    for yy in range(0,64,16):
        for xx in range(0,64,16):
            color="#5a6265"if(xx+yy)//16%2==0 else"#4e585e"
            draw.rectangle((xx+1,yy+1,xx+14,yy+14),fill=color)
    out=io.BytesIO();image.save(out,format="PNG")
    return {"source":"data:image/png;base64,"+base64.b64encode(out.getvalue()).decode()}


def wall_mounted(model,yaw=0):
    result=copy.deepcopy(model);amount=16*1.2/28
    for element in result["elements"]:
        for key in("from","to","origin"):
            if key in element:element[key]=[v*amount for v in element[key]]
        # +Y becomes the surface's outward normal, with local +Z kept world-up.
        element["_transform"]=[{"rotation":[-90,0,0]},{"translation":[0,0,-.24]}, {"rotation":[0,yaw,0]}]
    result["textures"].append(fixture_texture())
    wall={"name":"preview_wall_fixture","from":[-8,-8,0],"to":[8,8,1.5],
          "faces":{name:{"uv":[0,0,result["resolution"]["width"],result["resolution"]["height"]],"texture":len(result["textures"])-1}for name in("north","south","east","west","up","down")},
          "_transform":[{"rotation":[0,yaw,0]}]}
    result["elements"].append(wall)
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--before",type=Path);parser.add_argument("--output",type=Path,default=SOURCE)
    args=parser.parse_args();out=args.output;out.mkdir(parents=True,exist_ok=True)
    hammer=load_item();anchor=json.loads((SOURCE/"anchor.bbmodel").read_text())
    for key in("hammer","anchor"):
        model=hammer if key=="hammer"else anchor
        sheet=Image.new("RGBA",(1800,1000),BACKGROUND)
        for index,view in enumerate(("front","rear","isometric")):
            title=key.upper()+" / "+view.upper()
            sheet.alpha_composite(panel(model,title,"Actual delivery geometry + UV atlas",(600,1000),view), (index*600,0))
        sheet.save(out/(key+"_orthographic.png"))
    head=copy.deepcopy(hammer);head["elements"]=[e for e in head["elements"]if e["from"][1]>=18.5]
    sheet=Image.new("RGBA",(1800,700),BACKGROUND)
    for index,(view,label)in enumerate((("front","SIDE / OPEN PISTON CAGE"),("strike_end","STRIKING DIE / THREE GROOVES"),("cage_end","REAR / LOAD-BEARING FRAME"))):
        sheet.alpha_composite(panel(head,label,"Exported item model / actual material pixels",(600,700),view), (index*600,0))
    sheet.save(out/"hammer_detail_preview.png")
    wall=wall_mounted(anchor)
    sheet=Image.new("RGBA",(1600,900),BACKGROUND)
    for index,view in enumerate(("front","isometric")):
        sheet.alpha_composite(panel(wall,"WALL ANCHOR / "+view.upper(),"+Y outward | source 28px | scale 1.2 | gap 0.015 block",(800,900),view,scale=36), (index*800,0))
    sheet.save(out/"anchor_wall_preview.png")
    sheet=Image.new("RGBA",(1600,1000),BACKGROUND)
    for index,key in enumerate(("hammer","hammer_lod")):
        model=json.loads((SOURCE/(key+".bbmodel")).read_text())
        sheet.alpha_composite(panel(model,key.upper(),"Same scale / open cage retained",(600,1000),scale=26), (index*600,0))
    for index,key in enumerate(("anchor","anchor_lod")):
        model=json.loads((SOURCE/(key+".bbmodel")).read_text())
        sheet.alpha_composite(panel(model,key.upper(),"Same scale / tow aperture retained",(400,500),scale=22), (1200,index*500))
    sheet.save(out/"model_lod_comparison.png")
    # Actual +Y-to-normal basis for all four legal vertical surfaces.
    mounts=Image.new("RGBA",(1600,900),BACKGROUND)
    for index,(name,yaw)in enumerate((("NORTH / -Z",0),("EAST / +X",-90),("SOUTH / +Z",180),("WEST / -X",90))):
        angle=math.radians(yaw)
        def turn(v):return (v[0]*math.cos(angle)+v[2]*math.sin(angle),v[1],-v[0]*math.sin(angle)+v[2]*math.cos(angle))
        view="mount_"+str(index);VIEWS[view]=tuple(turn(v)for v in VIEWS["isometric"])
        content=panel(wall_mounted(anchor,yaw),name,"Rotated normal + matching camera / world axes at left",(800,450),view,scale=17)
        draw=ImageDraw.Draw(content);origin=(82,340);right,up,_=VIEWS[view]
        for axis,label,color in[((1,0,0),"+X","#df9b7e"),((0,1,0),"+Y","#9bc293"),((0,0,1),"+Z","#83b7da")]:
            endpoint=(origin[0]+45*sum(axis[i]*right[i]for i in range(3)),origin[1]-45*sum(axis[i]*up[i]for i in range(3)))
            draw.line((origin,endpoint),fill=color,width=2)
            draw.ellipse((endpoint[0]-2,endpoint[1]-2,endpoint[0]+2,endpoint[1]+2),fill=color)
            draw.text((endpoint[0]+4,endpoint[1]-8),label,font=font(15),fill=color)
        mounts.alpha_composite(content,((index%2)*800,(index//2)*450))
    mounts.save(out/"anchor_four_wall_normals.png")
    if args.before:
        comparison=Image.new("RGBA",(1800,1400),BACKGROUND)
        for index,(key,label)in enumerate((("hammer","HAMMER"),("anchor","WALL ANCHOR"))):
            old=json.loads((args.before/(key+".bbmodel")).read_text());new=hammer if key=="hammer"else anchor
            if key=="anchor":old=wall_mounted(old);new=wall_mounted(new)
            for col,(model,version)in enumerate(((old,"BEFORE / PR #33"),(new,"AFTER / APPROVED DESIGN"))):
                comparison.alpha_composite(panel(model,label+" / "+version,"Offline actual-model render | matched camera and scale",(900,700),scale=19 if key=="hammer"else 29), (col*900,index*700))
        comparison.save(out/"model_before_after.png")
    print("OK: offline item/BBModel renders, details, mounted anchor, four normals, LOD, optional matched baseline")


if __name__=="__main__":main()
