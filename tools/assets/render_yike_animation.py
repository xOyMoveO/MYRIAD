"""Render Java-exported production poses through the actual Hong Lie cuboids/UVs.

Input: art_source/yike/animation/pose_trace.csv (YikeWeaponPreview.java).
Output: four 50-fps GIFs, their PNG frames, and pose contact sheets. No game/GUI.
"""
from pathlib import Path
import copy
import csv
import json
from PIL import Image, ImageDraw, ImageFont
from render_bbmodel import render, cuboid_faces, transform, dot, VIEWS

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"art_source/yike"
OUT=SOURCE/"animation"
BACKGROUND="#101C27"
SIZE=(420,700)
VIEW="isometric"


def model_for(row,parts):
    result=copy.deepcopy(parts["frame"])
    for element in result["elements"]:
        element["_transform"]=[{"translation":[0,row["recoil"],0]}]
    for index in range(3):
        for source in parts["petal"]["elements"]:
            element=copy.deepcopy(source)
            # Same PoseStack order as YikeWeaponView.parts, applied to vertices.
            element["_transform"]=[
                {"translation":[-8,-8,-8]},
                {"translation":[0,0 if index==0 else row[f"sway{index}"],row["latch"]]},
                {"rotation":[row[f"petal{index}"],0,0]},
                {"rotation":[0,index*120,0]},
                {"translation":[8,13.5+row["recoil"],8]},
            ]
            result["elements"].append(element)
    for source in parts["ring"]["elements"]:
        element=copy.deepcopy(source)
        element["_transform"]=[
            {"translation":[-8,-8,-8]},
            {"rotation":[0,row["ringTwist"],0]},
            {"translation":[8,21.2+row["ringSlide"]+row["recoil"],8]},
        ]
        result["elements"].append(element)
    return result


def main():
    rows=[]
    with (OUT/"pose_trace.csv").open() as source:
        for entry in csv.DictReader(source):
            rows.append({key:value if key=="scenario" else float(value) for key,value in entry.items()})
    parts={name:json.loads((SOURCE/f"hong_lie_{name}.bbmodel").read_text()) for name in ("frame","petal","ring")}
    right,up,_=VIEWS[VIEW]
    bounds=[float("inf"),-float("inf"),float("inf"),-float("inf")]
    # One shared world-space crop and scale across every pose, retaining recoil.
    for row in rows:
        for element in model_for(row,parts)["elements"]:
            for corners in cuboid_faces(element).values():
                for point in corners:
                    point=transform(point,element);x,y=dot(point,right),dot(point,up)
                    bounds=[min(bounds[0],x),max(bounds[1],x),min(bounds[2],y),max(bounds[3],y)]
    center=((bounds[0]+bounds[1])/2,(bounds[2]+bounds[3])/2)
    scale=min((SIZE[0]-64)/(bounds[1]-bounds[0]),(SIZE[1]-144)/(bounds[3]-bounds[2]))
    font=ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf",16)
    for scene in ("fixed","mobile","cancel","shot"):
        folder=OUT/scene;folder.mkdir(parents=True,exist_ok=True);frames=[]
        selected=[row for row in rows if row["scenario"]==scene]
        for row in selected:
            image=Image.new("RGBA",SIZE,BACKGROUND)
            content=render(model_for(row,parts),view=VIEW,size=(SIZE[0],SIZE[1]-80),scale=scale,center=center,background=BACKGROUND)
            image.alpha_composite(content,(0,80));draw=ImageDraw.Draw(image)
            draw.text((18,12),f"HONG LIE / {scene.upper()}",font=font,fill="#5DDEFF")
            status=(f"Q PREP p={row['progress']:.2f}" if row["windup"]==2 else
                    "DEPLOYED" if row["mode"]>0 else f"WINDUP p={row['progress']:.2f}" if row["windup"]>=0 else "CLOSING / IDLE")
            draw.text((18,36),f"t={row['time']/20:.2f}s | {status}",font=font,fill="#9AB6C9")
            draw.text((18,58),"Production pose / source-model render",font=font,fill="#648297")
            image.save(folder/f"frame_{int(row['frame']):03d}.png");frames.append(image.convert("RGB"))
        # A common palette avoids per-frame palette shimmer.
        palette_source=Image.new("RGB",(SIZE[0]*len(frames),SIZE[1]))
        for index,frame in enumerate(frames):palette_source.paste(frame,(index*SIZE[0],0))
        palette=palette_source.quantize(colors=256)
        gif=[frame.quantize(palette=palette,dither=Image.Dither.NONE) for frame in frames]
        gif[0].save(OUT/f"{scene}.gif",save_all=True,append_images=gif[1:],duration=20,loop=0,disposal=2,optimize=False)
        contact=Image.new("RGB",(SIZE[0]*6,SIZE[1]),BACKGROUND)
        for index,frame_index in enumerate((0,8,16,24,45,59)):
            contact.paste(frames[frame_index],(index*SIZE[0],0))
        contact.save(OUT/f"{scene}_contact.png")
        print(f"OK {scene}: {len(frames)} real-model PNG frames and 50-fps GIF")


if __name__=="__main__":
    main()
