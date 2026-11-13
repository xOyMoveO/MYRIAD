"""Compile reviewed +Y photon projectile specs, embed their shared material atlas,
and export the actual cuboid UV faces through the common roster mesh compiler.
No held weapon, icon, audio or gameplay file is regenerated.
"""
from pathlib import Path
import importlib.util
import io
import json
import sys
import uuid
from PIL import Image,ImageDraw,ImageFont
from generate_yike_weapon import load_compiler,atlas_image
from render_bbmodel import render

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"art_source/yike/projectiles"
ASSET=ROOT/"src/main/resources/assets/myriad"
sys.path.insert(0,str(ROOT/"tools/vfx"))
from compile_roster_projectiles import compile_models


def main():
    compiler=load_compiler();paths={};shared=None
    for key in ("basic","basic_lod","shell","shell_lod","heavy","heavy_lod"):
        spec=json.loads((SOURCE/(key+".spec.json")).read_text());compiler.validate_schema(spec);compiler.validate_geometry(spec)
        atlas=atlas_image(spec);buf=io.BytesIO();atlas.save(buf,format="PNG");png=buf.getvalue()
        if shared is None:shared=png
        assert shared==png
        counter=iter(range(1000));compiler._uuid=lambda:str(uuid.uuid5(uuid.NAMESPACE_URL,key+":"+str(next(counter))))
        model=compiler.build_bbmodel(spec,256,256,png,"yike_projectiles_atlas")
        assert not compiler.validate_bbmodel(model,spec)
        path=SOURCE/(key+".bbmodel");path.write_text(json.dumps(model,indent=2)+"\n");paths[key]=path
        print(f"OK {key}: {len(model['elements'])} real cuboids / {len(model['elements'])*6} UV faces")
    (SOURCE/"yike_projectiles_atlas.png").write_bytes(shared)
    (ASSET/"textures/particle/vfx/yike_projectiles_atlas.png").write_bytes(shared)
    result=compile_models(paths,ASSET/"vfx/yike_projectiles_mesh.json")
    assert all(mesh["tip_y"]==64 for mesh in result["meshes"].values())
    # Same source scale for all three models exposes their authored cross-section.
    sheet=Image.new("RGBA",(1440,1000),"#101C27")
    font=ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf",22)
    for i,key in enumerate(("basic","shell","heavy")):
        image=render(paths[key],view="isometric",size=(480,900),scale=12,background="#101C27")
        label=Image.new("RGBA",(480,1000),"#101C27");label.alpha_composite(image,(0,90));draw=ImageDraw.Draw(label)
        draw.text((24,20),key.upper()+" / +Y FORWARD",font=font,fill="#5DDEFF")
        draw.text((24,54),str(result["meshes"][key]["parts"])+" textured cuboids",font=font,fill="#9AB6C9")
        label.save(SOURCE/(key+"_model_preview.png"));sheet.alpha_composite(label,(i*480,0))
    sheet.save(SOURCE/"projectile_models_preview.png")


if __name__=="__main__":main()
