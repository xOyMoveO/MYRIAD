"""Validate the two item handoffs and render the compiled (not concept) models."""
import argparse
import base64
from io import BytesIO
import json
from pathlib import Path
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

from compile_war_spirit_weapons import OUT
from compile_ink_wind_weapons import ASSETS, element_corners

sys.path.insert(0,str(ASSETS.parents[4]/"tools/assets"))
from render_bbmodel import VIEWS, dot, render

ITEMS = (("chixiao_blade","chixiao_war_dao","CHIXIAO   /   WAR DAO"),
         ("yunsheng_bell","yunsheng_spirit_bell","YUNSHENG   /   JADE BELL"))

def model_for_preview(game, atlas, stretch):
    elements=[]
    for e in game["elements"]:
        r=e.get("rotation",{})
        part={**e,"origin":r.get("origin",[8]*3),"rotation":[r.get(a,0) for a in "xyz"]}
        if stretch != 1:
            assert not any(part["rotation"])
            for key in ("from","to","origin"):
                x,y,z=part[key]
                part[key]=[x,8+(y-8)*stretch,z]
        part["faces"]={f:{"uv":[round(k*32) for k in m["uv"]],"texture":0} for f,m in e["faces"].items()}
        elements.append(part)
    return {"resolution":{"width":512,"height":512},"elements":elements,
            "textures":[{"source":"data:image/png;base64,"+base64.b64encode(atlas).decode()}]}


def compare_dao(output, baseline, edge_only=False):
    stem="assets/myriad/"
    model="models/item/chixiao_blade.json"
    texture="textures/item/chixiao_blade.png"
    new=json.loads((ASSETS/model).read_bytes())
    atlas=(ASSETS/texture).read_bytes()
    with zipfile.ZipFile(baseline) as jar:
        old=json.loads(jar.read(stem+model))
        old_atlas=jar.read(stem+texture)
        for path in ("items/chixiao_blade.json","items/yunsheng_bell.json",
                     "models/item/yunsheng_bell.json","textures/item/yunsheng_bell.png"):
            assert jar.read(stem+path)==(ASSETS/path).read_bytes(), f"Unrelated item asset changed: {path}"

    def blade_length(model):
        edges=[e for e in model["elements"] if e["name"].startswith("silver_edge_")]
        return (max(e["to"][1] for e in edges)-min(e["from"][1] for e in edges))*model["display"]["thirdperson_righthand"]["scale"][1]

    increase=blade_length(new)/blade_length(old)-1
    if edge_only:
        assert abs(increase)<1e-8
        assert all(pose==old["display"][key] for key,pose in new["display"].items()
                   if key.startswith(("firstperson_","thirdperson_")))
        def by_name(model):
            return {e["name"]: (e["from"],e["to"]) for e in model["elements"]}
        old_parts,new_parts=by_name(old),by_name(new)
        for key in old_parts:
            if not key.startswith(("blade_plate_","blade_bevel_","silver_edge_")):
                assert old_parts[key]==new_parts[key], key
        for key, (lo,hi) in new_parts.items():
            if key.startswith("silver_edge_"):
                before_lo,before_hi=old_parts[key]
                assert abs(hi[2]-lo[2]-(before_hi[2]-before_lo[2])*.5)<1e-6
                assert hi[:2]==before_hi[:2] and lo[1]==before_lo[1], key
    else:
        assert .27 < increase < .30, increase
        assert len(new["elements"])>len(old["elements"])*1.4
    for key, pose in new["display"].items():
        if key.startswith(("firstperson_", "thirdperson_")):
            before=old["display"][key]
            assert pose["rotation"]==before["rotation"] and pose["translation"]==before["translation"]
            assert pose["scale"][::2]==before["scale"][::2]

    sheet=Image.new("RGB",(990,790),"#111a23")
    labels=ImageDraw.Draw(sheet)
    font=ImageFont.load_default(size=17)
    labels.text((20,15),"CHIXIAO / WAR DAO     |     THREE-STEP EDGE" if edge_only
                else f"CHIXIAO  /  WAR DAO     |     BLADE +{increase:.0%}",font=font,fill="#e5f3f6")
    for i,(game,bits) in enumerate(((old,old_atlas),(new,atlas))):
        scale=game["display"]["thirdperson_righthand"]["scale"]
        preview=model_for_preview(game,bits,scale[1]/scale[0])
        x=15+315*i
        for col,view in enumerate(("front","isometric")):
            center=tuple(dot((8.7,16,8),axis) for axis in VIEWS[view][:2])
            sheet.paste(render(preview,view=view,size=(155,685),scale=17,center=center,
                               background="#1b2a33").convert("RGB"),(x+156*col,63))
        labels.text((x+10,757),f'{"BEFORE" if i==0 else "AFTER"}  /  {len(game["elements"])} PARTS',font=font,fill="#e5f3f6")
        if i==1:
            center=tuple(dot((10,30,8) if edge_only else (9,14.5,8),axis) for axis in VIEWS["isometric"][:2])
            sheet.paste(render(preview,view="isometric",size=(328,685),scale=43 if edge_only else 48,center=center,
                               background="#23323b").convert("RGB"),(653,63))
            labels.text((667,757),"TIP / EDGE DETAIL" if edge_only else "GUARD / MATERIAL DETAIL",font=font,fill="#e5f3f6")
    sheet.save(output)
    print("Blade length and held display transforms unchanged; silver edge thickness halved."
          if edge_only else f"Same grip and handed poses; displayed blade is {increase:.2%} longer.")
    print("Yunsheng assets are unchanged.")


def compare_bell(output, baseline, finish_only=False):
    prefix="assets/myriad/"
    model="models/item/yunsheng_bell.json"
    texture="textures/item/yunsheng_bell.png"
    model_bytes=(ASSETS/model).read_bytes()
    new=json.loads(model_bytes)
    atlas=(ASSETS/texture).read_bytes()
    with zipfile.ZipFile(baseline) as jar:
        old_model_bytes=jar.read(prefix+model)
        old=json.loads(old_model_bytes)
        old_atlas=jar.read(prefix+texture)
        for path in ("items/yunsheng_bell.json","items/chixiao_blade.json",
                     "models/item/chixiao_blade.json","textures/item/chixiao_blade.png"):
            assert jar.read(prefix+path)==(ASSETS/path).read_bytes(), path

    for name, transform in new["display"].items():
        if name.startswith(("firstperson_","thirdperson_")):
            assert transform==old["display"][name], name
    parts={e["name"]:e for e in new["elements"]}
    assert len(parts)==len(new["elements"])
    if finish_only:
        assert model_bytes==old_model_bytes, "Material revision changed the model, UVs, or display poses"
        assert atlas!=old_atlas, "Material revision did not change the atlas"
        assert Image.open(BytesIO(atlas)).getchannel("A").tobytes()== \
               Image.open(BytesIO(old_atlas)).getchannel("A").tobytes(), "Material revision changed alpha"
    else:
        assert len(parts)>len(old["elements"])
    assert [parts[k]["light_emission"] for k in ("crystal_bell_clapper","crystal_end")]==[15,15]
    assert not any("rotation" not in part for key,part in parts.items() if key.startswith("glazed_flare_"))
    assert {key.split("_")[2] for key in parts if key.startswith("glazed_flare_")}=={"north","east","south","west"}

    sheet=Image.new("RGB",(1360,650),"#141d26")
    labels=ImageDraw.Draw(sheet)
    font=ImageFont.load_default(size=17)
    new_label="CELADON & CHAMPAGNE BRASS" if finish_only else "REFINED"
    for idx,(game,bits,title) in enumerate(((old,old_atlas,"BEFORE"),(new,atlas,new_label))):
        preview=model_for_preview(game,bits,1)
        for col,view in enumerate(("front","isometric")):
            center=tuple(dot((8.9,16.5,8),axis) for axis in VIEWS[view][:2])
            tile=render(preview,view=view,size=(263,545),scale=17.5,center=center,background="#21313a")
            sheet.paste(tile.convert("RGB"),(13+idx*540+col*267,63))
        labels.text((13+idx*540,622),f"{title}   /   {len(game['elements'])} PARTS",font=font,fill="#d3ece2")
    labels.text((13,20),"YUNSHENG  /  SPIRIT PACT JADE BELL",font=font,fill="#e5f6ed")
    center=tuple(dot((8.65,18.5,8),axis) for axis in VIEWS["isometric"][:2])
    tile=render(model_for_preview(new,atlas,1),view="isometric",size=(254,545),scale=20,
                center=center,background="#17242e")
    sheet.paste(tile.convert("RGB"),(1093,63))
    labels.text((1093,622),"GLAZE & BRASS",font=font,fill="#d3ece2")
    sheet.save(output)
    print("Bell atlas RGB changed; geometry, UVs, alpha and Chixiao unchanged." if finish_only else
          "Same grip and held poses. Four uninterrupted glaze fields; two full-bright clapper parts. Chixiao unchanged.")


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("--jar",type=Path)
    p.add_argument("--compare-dao",type=Path,help="Path to a baseline JAR; render a same-scale war dao comparison")
    p.add_argument("--compare-edge",type=Path,help="Compare a thickness-only update with its baseline JAR")
    p.add_argument("--compare-bell",type=Path,help="Render the revised bell beside its baseline JAR model")
    p.add_argument("--compare-bell-finish",type=Path,help="Compare a texture-only bell revision with its baseline JAR")
    args=p.parse_args()
    sheet=Image.new("RGB",(996,748),"#121c24")
    draw=ImageDraw.Draw(sheet)
    font=ImageFont.load_default(size=17)
    for col,(item,source,label) in enumerate(ITEMS):
        model_path=ASSETS/f"models/item/{item}.json"
        item_path=ASSETS/f"items/{item}.json"
        texture_path=ASSETS/f"textures/item/{item}.png"
        game=json.loads(model_path.read_bytes())
        src=json.loads((OUT/f"{source}.bbmodel").read_bytes())
        atlas=texture_path.read_bytes()
        assert base64.b64decode(src["textures"][0]["source"].split(",",1)[1])==atlas
        assert json.loads(item_path.read_bytes())["model"]["model"]==f"myriad:item/{item}"
        assert game["textures"]["weapon"]==f"myriad:item/{item}" and len(game["display"])==7
        im=Image.open(BytesIO(atlas)).convert("RGBA")
        assert im.size==(512,512)
        elements=[]
        for part in game["elements"]:
            assert all(-16<=l<h<=32 for l,h in zip(part["from"],part["to"]))
            r=part.get("rotation",{})
            out={**part,"origin":r.get("origin",[8]*3),"rotation":[r.get(a,0) for a in "xyz"],"faces":{}}
            assert all(-16<=value<=32 for point in element_corners(out) for value in point), part["name"]
            for face,face_data in part["faces"].items():
                uv=[round(k*32) for k in face_data["uv"]]
                x0,x1=sorted((uv[0],uv[2]))
                y0,y1=sorted((uv[1],uv[3]))
                assert 0<=x0<x1<=512 and 0<=y0<y1<=512
                alpha=im.crop((x0,y0,x1,y1)).getchannel("A").getextrema()
                assert 0<alpha[0]<255 if part.get("light_emission") else alpha==(255,255)
                out["faces"][face]={"uv":uv,"texture":0}
            elements.append(out)
        grip=next(e for e in elements if e["name"]=="wrapped_grip")
        assert [round((a+b)/2,5) for a,b in zip(grip["from"],grip["to"])]==[8]*3
        for ctx in ("firstperson","thirdperson"):
            r=game["display"][ctx+"_righthand"]
            l=game["display"][ctx+"_lefthand"]
            assert r["scale"]==l["scale"] and r["translation"][1:]==l["translation"][1:]
        if args.jar:
            with zipfile.ZipFile(args.jar) as jar:
                for path in (model_path,item_path,texture_path):
                    assert jar.read("assets/myriad/"+path.relative_to(ASSETS).as_posix())==path.read_bytes()
        x=col*498
        draw.text((x+22,19),label,font=font,fill="#e5f3f6")
        model={**src,"elements":elements}
        for i,view in enumerate(("front","isometric")):
            sheet.paste(render(model,view=view,size=(239,681),background="#1c2a33").convert("RGB"),(x+7+i*244,50))
        print(f"{item}: {len(elements)} parts, grip / UVs / alpha / item mapping OK")
    if args.compare_bell_finish:
        compare_bell(args.output,args.compare_bell_finish,finish_only=True)
    elif args.compare_bell:
        compare_bell(args.output,args.compare_bell)
    elif args.compare_edge:
        compare_dao(args.output,args.compare_edge,edge_only=True)
    elif args.compare_dao:
        compare_dao(args.output,args.compare_dao)
    else:
        sheet.save(args.output)


if __name__=="__main__":
    main()
