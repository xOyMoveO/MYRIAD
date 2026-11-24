"""Bake five editable Frost/Herb BBModels into the flight renderer and preview sheet."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art_source/frost-herb-projectiles"
TEXTURES = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx"
JAVA = ROOT / "src/main/java/com/myriad/character/ability/FrostHerbModelParts.java"
SIDES = ("north", "south", "east", "west", "up", "down")
# Double the authoring grid resolution without changing in-game dimensions.
DENSITY = 2
PALETTES = (
    (("#5596c6","#2d6497","#78b8db","#a8ddf2","#dcf7ff"),
     ("#bfdfee","#84adc9","#dfedf1","#effaff","#fbffff"),
     ("#315981","#213d63","#3d729a","#588daf","#88b9d1"),
     ("#71d0e8","#479ebd","#a0e8f3","#c9f8ff","#f4feff")),
    (("#386c52","#244938","#56866a","#75a379","#b1ca87"),
     ("#82aa4f","#527537","#a4bc63","#bed389","#e4eab1"),
     ("#a3864b","#765b36","#b69959","#d4b575","#ead29c"),
     ("#81c9ac","#438d78","#a1dec1","#ccf1d6","#efffe7")),
)


def cube(name, pos, size, mat, rot=(0,0,0), pivot=None):
    uv = [mat*128+1,1,mat*128+127,127]
    scaled = lambda coords: [v*DENSITY for v in coords]
    return dict(id=name,pos=scaled(pos),size=scaled(size),rot=list(rot),pivot=scaled(pivot or pos),
                uv_mode="per_face",faces={side:{"uv":uv} for side in SIDES})


def radial(name, strips, angles):
    """Sweep the same attached few-pixel plates about local +Z."""
    parts = []
    for i, angle in enumerate(angles):
        for label, pos, size, mat in strips:
            parts.append(cube(f"{name}_{i}_{label}",pos,size,mat,(0,0,angle),(0,0,0)))
    return parts


def stone_profile(name, slices):
    """Octagonal cross sections from non-overlapping bands, rather than nested square plugs."""
    parts = []
    for i, (z0,z1,w,h,mat) in enumerate(slices):
        if h <= .75 or w <= .75:
            parts.append(cube(f"{name}_{i}_tip",(-w,-h,z0),(w*2,h*2,z1-z0),mat))
            continue
        # Keep all boundaries on the authored quarter-grid, even on small facets.
        belts = [(-h,-h+.5,w-.75),(-h+.5,-h+1,w-.25),
                 (-h+1,h-1,w),(h-1,h-.5,w-.25),(h-.5,h,w-.75)]
        for j,(bottom,top,half) in enumerate(belts):
            if top<=bottom or half<=0:
                continue
            parts.append(cube(f"{name}_{i}_belt_{j}",(-half,bottom,z0),(half*2,top-bottom,z1-z0),mat))
    return parts


def ice(lance):
    s = 2 if lance else 1
    lo, hi = (-6*s,8*s)
    shape = [
        (lo,-5.5*s,1.25,1.25,2),(-5.5*s,-4.75*s,1.75,1.5,2),
        (-4.75*s,-3.75*s,2.25,2,0),(-3.75*s,-1*s,2.75,2.25,0),
        (-1*s,1.5*s,2.5,2,0),(1.5*s,3*s,2,1.75,0),
        (3*s,4.5*s,1.5,1.5,0),(4.5*s,5.75*s,1.25,1.25,1),
        (5.75*s,6.75*s,1,1,1),(6.75*s,7.5*s,.75,.75,1),
        (7.5*s,hi,.375,.375,1),
    ]
    parts = stone_profile("cut_ice",shape)
    parts += radial("ice_spine",[
        ("rib_root",(1.85,-.5,-4.5*s),(1.1,1,1.25*s),0),
        ("ice_ridge",(2.35,-.55,-3.25*s),(.9,1.1,2*s),1),
        ("running_glint",(2.55,-.25,-1.25*s),(.6,.5,2.25*s),3),
        ("swept_ridge",(2,-.35,1*s),(.8,.7,1.5*s),1),
        ("vanishing_edge",(1.65,-.25,2.5*s),(.55,.5,1*s),1),
    ],(15,105,195,285))
    if lance:
        # Distinct raked fins: each successive plate rides closer to the main shaft.
        parts += radial("wing",[
            ("keel",(2,-.6,-9),(1.7,1.2,2),2),
            ("blade_base",(2.5,-.5,-7),(2.3,1,1.75),0),
            ("edge_base",(4.35,-.35,-7),(1,.7,1.75),1),
            ("blade_mid",(2.35,-.45,-5.25),(2.1,.9,1.5),0),
            ("edge_mid",(3.95,-.25,-5.25),(.95,.5,1.5),1),
            ("tipped_fin",(2.25,-.25,-3.75),(1.65,.5,2),1),
        ],(45,225))
        # Stretch toward the rear so the visible tip keeps its original flight position.
        scales = (.8,.8,1.16)
        tip = hi*DENSITY
        for part in parts:
            for field in ("pos","size","pivot"):
                part[field] = [value*scale for value,scale in zip(part[field],scales)]
            part["pos"][2] += tip*(1-scales[2])
            part["pivot"][2] += tip*(1-scales[2])
    return parts


def bead(herb):
    shape = [
        (-3.75,-3.25,1,1,2),(-3.25,-2.5,1.75,1.75,0),
        (-2.5,-1.5,2.25,2.25,3),(-1.5,1.25,2.5,2.5,3),
        (1.25,2.25,2.25,2.25,3),(2.25,3,1.75,1.75,3),
        (3,3.5,1.25,1.25,3 if herb else 1),
        (3.5,4,.75,.75,3 if herb else 1),
    ]
    parts=stone_profile("dew_glass" if herb else "ice_pearl",shape)
    if herb:
        # The pearl remains uncovered at the nose; the asymmetric leaf cup sits behind it.
        parts += radial("leaf_cup",[
            ("root",(1.15,-1,-3.5),(1,2,1.15),0),
            ("heel",(1.85,-1.35,-2.35),(1.15,2.7,1),0),
            ("green_vein",(2.65,-1.4,-1.35),(1,2.8,1.45),1),
            ("leaf_blade",(3,-1.25,.1),(1,2.5,1.05),1),
            ("lobe",(3.35,-.75,1.15),(.75,1.5,.85),1),
            ("tip",(3.5,-.25,2),(.5,.5,.65),1),
            ("central_rib",(3.85,-.25,.35),(.5,.5,.9),0),
        ],(30,120,210,300))
    else:
        parts += radial("rim_facets",[
            ("dark_seat",(1.5,-.75,-2.75),(.8,1.5,.8),2),
            ("shoulder",(2,-.7,-1.75),(.8,1.4,1.5),0),
            ("silver_bevel",(2.3,-.5,-.25),(.5,1,1.7),1),
            ("soft_glint",(2,-.25,1.45),(.5,.5,.8),1),
        ],(45,135,225,315))
    return parts


def seed():
    parts = stone_profile("seed_kernel",[
        (-4,-3.25,1.25,1.25,2),(-3.25,-2,2,1.75,0),
        (-2,-.5,2.5,2.25,0),(-.5,1.25,2.5,2.25,0),
        (1.25,2.75,2,2,3),(2.75,3.75,1.5,1.5,1),
        (3.75,5,1,1,3),(5,6,.5,.5,1),
    ])
    parts += radial("husk_leaf",[
        ("neck",(1.2,-.75,-4.25),(1,1.5,1.25),2),
        ("basal_husk",(1.8,-1.25,-3),(1.25,2.5,1.25),0),
        ("overlap",(2.45,-1.55,-1.75),(1.25,3.1,1.35),0),
        ("raised_vein",(3.45,-.7,-1.1),(.5,1.4,1.3),1),
        ("front_husk",(2.75,-1.25,-.4),(1,2.5,1.15),0),
        ("spine_base",(3,-.7,.75),(1.3,1.4,1),1),
        ("spine_tip",(3.75,-.3,1.75),(.5,.6,1),1),
        ("curled_inner_leaf",(1.65,-.65,2.75),(1.2,1.3,.8),0),
    ],(45,135,225,315))
    parts += [cube("gold_stem",(-.5,-.5,-5.5),(1,1,1.5),2,(0,10,0),(0,0,-4)),
              cube("split_sprout_l",(-.35,-.4,-6.2),(.7,1.4,1.4),1,(25,0,-39),(0,0,-5)),
              cube("split_sprout_r",(-.35,-.65,-6.0),(.7,1.4,1.35),1,(-12,0,42),(0,0,-5))]
    return parts


def material_tile(palette, pattern):
    base, shadow, mid, light, highlight = palette
    tile = Image.new("RGB",(128,128),base)
    draw = ImageDraw.Draw(tile)
    if pattern == "ice":
        # Broad inclusions under glass, with separate angled layers and a branched frost seam.
        draw.polygon([(0,79),(26,72),(55,39),(93,18),(98,0),(0,0)],fill=mid)
        draw.polygon([(0,119),(19,103),(42,101),(69,77),(99,61),(99,106),(57,128),(0,128)],fill=shadow)
        draw.polygon([(0,65),(25,56),(46,31),(64,27),(79,10),(94,0),(64,0),(39,15),(21,40),(0,43)],fill=light)
        draw.polygon([(19,105),(43,95),(65,76),(78,72),(94,56),(94,64),(69,82),(52,99),(31,108)],fill=mid)
        draw.line([(0,76),(26,68),(45,49),(62,44),(80,25),(97,19)],fill=light,width=4)
        draw.line([(28,66),(38,76),(51,74),(59,80)],fill=mid,width=3)
        draw.line([(5,72),(26,65),(41,51)],fill=highlight,width=2)
        draw.line([(65,42),(79,27),(91,23)],fill=highlight,width=2)
        draw.line([(21,30),(35,19),(43,18)],fill=highlight,width=3)
    elif pattern == "leaf":
        # Green acreage around the vein, with short secondary ribs in the same palette.
        draw.polygon([(0,0),(99,0),(99,39),(77,42),(53,62),(31,66),(12,82),(0,85)],fill=mid)
        draw.polygon([(0,98),(34,83),(59,84),(79,62),(99,61),(99,128),(0,128)],fill=shadow)
        draw.polygon([(5,25),(21,15),(38,17),(47,11),(58,11),(48,26),(32,30),(22,43),(11,48)],fill=light)
        draw.polygon([(44,103),(61,97),(73,83),(89,79),(77,94),(71,106),(55,116),(24,128),(7,128)],fill=base)
        draw.line([(0,101),(25,80),(44,72),(64,57),(80,40),(99,28)],fill=light,width=6)
        for branch in ([(26,80),(22,58),(27,43)],[(43,73),(56,74),(68,87)],
                       [(63,56),(59,42),(64,27)],[(80,40),(87,45),(99,44)]):
            draw.line(branch,fill=mid,width=4)
        draw.line([(0,99),(24,79),(43,70)],fill=highlight,width=2)
        draw.line([(68,52),(80,38),(94,30)],fill=highlight,width=2)
    else:
        # Dew shading is chunky and uninterrupted, with a tight reflected crescent.
        draw.polygon([(0,104),(23,84),(55,78),(81,60),(99,40),(99,128),(0,128)],fill=shadow)
        draw.polygon([(0,31),(16,16),(46,10),(67,14),(86,28),(72,53),(40,73),(17,78),(0,68)],fill=mid)
        draw.polygon([(0,30),(18,17),(51,13),(67,20),(43,21),(20,34),(8,56),(0,60)],fill=light)
        draw.polygon([(8,38),(20,25),(38,23),(28,32),(19,49),(12,52)],fill=highlight)
        draw.polygon([(34,119),(58,111),(83,89),(95,70),(91,95),(69,118),(50,128),(12,128)],fill=mid)
        draw.line([(41,120),(61,113),(81,97)],fill=light,width=3)
    # The trim area carries subdued streaks scaled for the smallest bevels.
    draw.rectangle((100,0,127,127),fill=base)
    draw.polygon([(100,0),(124,0),(110,54),(107,95),(100,128)],fill=mid)
    draw.polygon([(127,23),(120,65),(119,108),(112,128),(127,128)],fill=shadow)
    draw.line([(112,17),(109,32),(107,45)],fill=light,width=2)
    draw.line([(122,85),(121,107),(117,120)],fill=mid,width=2)
    return tile


def atlas_regions(theme):
    """Encode the pixel art as merged runs for the editable texture spec."""
    patterns = ("ice",)*4 if theme == 0 else ("leaf","leaf","leaf","dew")
    regions = []
    for i,(palette,pattern) in enumerate(zip(PALETTES[theme],patterns)):
        tile = material_tile(palette,pattern)
        active = {}
        for y in range(tile.height):
            row = {}
            x = 0
            while x < tile.width:
                color = tile.getpixel((x,y))
                end = x+1
                while end < tile.width and tile.getpixel((end,y)) == color:
                    end += 1
                key = (x,end,color)
                if key in active:
                    region = active[key]
                    region["rect"][3] += 1
                else:
                    region = dict(id=f"texel_run_{len(regions)}",rect=[i*128+x,y,end-x,1],
                                  fill="#%02x%02x%02x" % color)
                    regions.append(region)
                row[key] = region
                x = end
            active = row
    return regions


def map_faces(parts):
    """Spread the large motifs over the body's slices and reserve quiet UVs for edge trim."""
    z_min = min(p["pos"][2] for p in parts)
    z_max = max(p["pos"][2]+p["size"][2] for p in parts)
    for part in parts:
        cell = part["faces"]["north"]["uv"][0]//128*128
        _,_,z = part["pos"]
        width,height,depth = part["size"]
        start,end = ((value-z_min)/(z_max-z_min) for value in (z,z+depth))
        for side in SIDES:
            face_width,face_height = {
                "north":(width,height),"south":(width,height),"east":(depth,height),
                "west":(depth,height),"up":(width,depth),"down":(width,depth),
            }[side]
            if min(face_width,face_height) < 1.35:
                u0,v0,u1,v1 = 103,8+int(52*start),125,68+int(52*end)
            elif side in ("east","west"):
                lo,hi = (start,end) if side == "east" else (1-end,1-start)
                u0,v0,u1,v1 = 4+88*lo,8,4+88*hi,120
            elif side in ("up","down"):
                lo,hi = (start,end) if side == "up" else (1-end,1-start)
                u0,v0,u1,v1 = 4,5+116*lo,95,5+116*hi
            else:
                u0,v0,u1,v1 = 12,12,90,116
            part["faces"][side]["uv"] = [round(u0+cell,3),round(v0,3),round(u1+cell,3),round(v1,3)]


def spec(name, theme, parts):
    map_faces(parts)
    return dict(meta={"name":name,"schema_version":1},target="entity",parts=parts,
                atlas={"width":512,"height":128,"regions":atlas_regions(theme)},
                face_details=[dict(id="solid_core",part=parts[0]["id"],kind="geometry")])


def bake_rows(model, expected, transform):
    rows = []
    elements = json.loads(model.read_text(encoding="utf-8"))["elements"]
    assert len(elements) == expected
    for el in elements:
        assert len(el["faces"]) == 6
        assert all(a<b for a,b in zip(el["from"],el["to"])), f"Degenerate cuboid after grid snap: {el['name']}"
        corners = [transform([el["to" if k&(1<<j) else "from"][j] for j in range(3)],el) for k in range(8)]
        vertices = ", ".join(f"{n/DENSITY:.5f}f" for vertex in corners for n in vertex)
        pixels = ", ".join(f"{n:.3f}f" for side in SIDES for n in el["faces"][side]["uv"])
        rows.append("            {"+vertices+",\n             "+pixels+"}")
    return rows


def write_model_java(path, generator, models):
    name = path.stem
    declarations = []
    builders = []
    for model,rows in models:
        declarations.append(f"    static final float[][] {model.upper()} = {model}();")
        builders.append(f"    private static float[][] {model}() {{\n        return new float[][] {{\n"
                        +",\n".join(rows)+"\n        };\n    }")
    path.write_text(f"package com.myriad.character.ability;\n\n"
                    f"// Generated by {generator} from validated BBModels.\n"
                    f"final class {name} {{\n    private {name}() {{}}\n"
                    +"\n".join(declarations)+"\n\n"+"\n\n".join(builders)+"\n}\n",encoding="utf-8")


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--generator",type=Path,required=True)
    cli.add_argument("--preview",type=Path,required=True)
    args = cli.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    args.preview.mkdir(parents=True,exist_ok=True)
    sys.path.insert(0,str(ROOT/"tools/assets"))
    from render_bbmodel import render, transform, VIEWS
    # Face the leading +Z tip while retaining the exact model scale and dimensions.
    VIEWS["flight"] = tuple((-x,y,-z) for x,y,z in VIEWS["isometric"])
    board = Image.new("RGB",(1440,365),"#17232c")
    label = ImageDraw.Draw(board)
    data = []
    for i,(name,theme,parts) in enumerate((
        ("ice_volley",0,ice(False)),("glacier_lance",0,ice(True)),("frost_pearl",0,bead(False)),
        ("briar_seed",1,seed()),("dew_bud",1,bead(True)),
    )):
        assert len(parts) == (66,78,51,70,63)[i], "Unexpected authored part count"
        source,model = [OUT/(name+suffix) for suffix in (".spec.json",".bbmodel")]
        source.write_text(json.dumps(spec(name,theme,parts),indent=2)+"\n",encoding="utf-8")
        cmd = [sys.executable,str(args.generator),str(source),"-o",str(model)]
        if i in (0,3):
            cmd += ["--png",str(TEXTURES/("frost_projectile_model.png" if theme==0 else "herb_hd_seed_model.png"))]
        subprocess.run(cmd,check=True)
        data.append((name,bake_rows(model,len(parts),transform)))
        board.paste(render(model,size=(288,310),scale=14/DENSITY,view="flight"),(i*288,24))
        label.text((i*288+14,342),f"{name.replace('_',' ').title()}  /  {len(parts)} parts",fill="#e0edee")
    board.save(args.preview/"frost-herb-projectiles.png")
    write_model_java(JAVA,"tools/vfx/build_frost_herb_projectiles.py",data)


if __name__=="__main__":
    main()
