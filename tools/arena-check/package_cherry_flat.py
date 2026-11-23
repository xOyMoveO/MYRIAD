"""Verify Cherry Rift terrain read-only and prepare a separate, fresh 3v3 world.

Requires nbtlib. Never opens the original in Minecraft or writes to it.
"""
import argparse
from collections import Counter
from functools import lru_cache
import gzip
from io import BytesIO
import json
from math import ceil, floor, hypot
from pathlib import Path
import shutil
import zlib

import nbtlib

ROOT = Path(__file__).resolve().parents[2]
PRESET = ROOT / "maps/cherry-rift-frontline/3v3-flat"
OVERWORLD = Path("dimensions/minecraft/overworld")
TAG = "sakurafront_static"
FOUNTAIN_PACK = "file/cherry_rift_fountains.zip"
SECTOR = 4096
EMPTY = {"air", "cave_air", "void_air", "short_grass", "tall_grass", "pink_petals",
         "leaf_litter", "moss_carpet", "fern", "dandelion", "poppy", "dead_bush"}
WIDTHS = {"cherry_frost_hare": .82, "cherry_dusk_fox": 1.75, "cherry_scout_bot": 1.46,
          "cherry_jade_beast": 2.45, "cherry_heavy_walker": 2.45, "cherry_rift_crab": 5.45}


def records(region):
    for index in range(1024):
        entry = region[index*4:index*4+4]
        first = int.from_bytes(entry[:3], "big") * SECTOR
        if first:
            length = int.from_bytes(region[first:first+4], "big")
            if not 0 < length <= entry[3]*SECTOR-4:
                raise ValueError("Invalid region record length")
            yield index, region[first:first+4+length]


def decode(record):
    data = {1: gzip.decompress, 2: zlib.decompress, 3: bytes}[record[4]](record[5:])
    return nbtlib.File.parse(BytesIO(data))


def encode(nbt):
    body = BytesIO()
    nbt.write(body)
    data = b"\x02" + zlib.compress(body.getvalue())
    return len(data).to_bytes(4, "big") + data


class Terrain:
    def __init__(self, world):
        self.region = world / OVERWORLD / "region"

    @lru_cache(None)
    def chunks(self, rx, rz):
        path = self.region / f"r.{rx}.{rz}.mca"
        return dict(records(path.read_bytes())) if path.is_file() else {}

    @lru_cache(None)
    def sections(self, cx, cz):
        rec = self.chunks(cx//32,cz//32).get(cz%32*32+cx%32)
        return {int(s["Y"]):s for s in decode(rec)["sections"]} if rec else {}

    @lru_cache(None)
    def block(self, x, y, z):
        section = self.sections(x//16,z//16).get(y//16, {})
        states = section.get("block_states")
        if not states:
            return "air"
        palette = states["palette"]
        index = 0
        if len(palette)>1:
            bits = max(4,(len(palette)-1).bit_length())
            num = 64//bits
            cell = ((y%16)*16+z%16)*16+x%16
            index = (int(states["data"][cell//num]) >> ((cell%num)*bits)) & ((1<<bits)-1)
        value = palette[index]
        name = value.get("Name", value.get("")) if isinstance(value,nbtlib.Compound) else value
        return str(name).removeprefix("minecraft:")

    def check_clear(self, name, x, y, z, half, height, require_floor=True):
        for px in range(floor(x-half),floor(x+half)+1):
            for pz in range(floor(z-half),floor(z+half)+1):
                for py in range(y,y+height):
                    block = self.block(px,py,pz)
                    if block not in EMPTY:
                        raise ValueError(f"{name}: obstructed at {(px,py,pz)}: {block}")
                floor_block = self.block(px,y-1,pz)
                if require_floor and floor_block in EMPTY|{"water","lava","None"}:
                    raise ValueError(f"{name}: no dry floor at {(px,y-1,pz)}: {floor_block}")

    def sight(self,a,b):
        steps=ceil(hypot(*(v-u for u,v in zip(a,b)))*10)
        return all(self.block(*(floor(u+(v-u)*i/steps) for u,v in zip(a,b))) in EMPTY
                   for i in range(1,steps))


def xyz(row):
    return tuple(row[k] for k in ("x","y","z"))


def marker(entity):
    if str(entity.get("id"))!="minecraft:item_display" or TAG not in entity.get("Tags",[]):
        return None
    item=entity["item"]
    model=str(item.get("components",{}).get("minecraft:item_model",""))
    ident="cherry_"+model.removeprefix("myriad_arena:") if model.startswith("myriad_arena:") else str(item["id"])
    return ident, *(floor(v) for v in entity["Pos"])


def validate(world):
    path=PRESET/"myriad_arenas/3v3"
    lane=json.loads((path/"myriad_lane_layout.json").read_text("utf-8"))
    camps=json.loads((path/"myriad_monster_spawns.json").read_text("utf-8"))["points"]
    area=json.loads((path/"myriad_minimap.json").read_text("utf-8"))["corners"]
    route=[xyz(p) for p in lane["paths"]["mid"]]
    if [(-x,y,-z) for x,y,z in route]!=route[::-1]:
        raise ValueError("Route endpoints and traverse are not mirrored")
    t=Terrain(world)
    samples=[]
    for (a,y,b),(x,q,z) in zip(route,route[1:]):
        if q!=y:
            raise ValueError("This map uses one continuous floor")
        step=max(1,ceil(hypot(x-a,z-b)*4))
        for i in range(step+1):
            u=i/step
            sample=(a+.5+(x-a)*u,y,b+.5+(z-b)*u)
            t.check_clear("mid swept corridor",*sample,.76,2)
            samples.append(sample)
    for camp in camps:
        x,y,z=xyz(camp)
        t.check_clear(camp["id"],x+.5,y,z+.5,WIDTHS[camp["type"]]/2+.02,3)
    for row in lane["fountains"]:
        x,y,z=xyz(row)
        t.check_clear(row["faction"]+" fountain",x+.5,y,z+.5,.4,3)
        if not t.block(x,y-1,z).endswith("_stained_glass"):
            raise ValueError("Fountain does not sit on its original lit center")
    for row in lane["towers"]+lane["crystals"]:
        x,y,z=xyz(row)
        t.check_clear("structure center",x+.5,y,z+.5,.49,3)
        allowed=row["minionPlatformRadius"]+1.8
        at=(x+.5,y+.78*(8 if "tier" in row else 5),z+.5)
        reachable=any(hypot(a+.0-at[0],b+.0-at[2])<=allowed
                      and t.sight((a,p+1.33,b),at) for a,p,b in samples)
        if not reachable:
            raise ValueError(f"No line of sight from melee corridor to elevated objective {row}")
    lx,hx=min(c["x"] for c in area),max(c["x"] for c in area)
    lz,hz=min(c["z"] for c in area),max(c["z"] for c in area)
    for x,_,z in route+[xyz(r) for r in camps+lane["towers"]+lane["crystals"]+lane["fountains"]]:
        if not (lx<x<hx and lz<z<hz):
            raise ValueError("Facility outside minimap bounds")
    want={(camp["type"],*xyz(camp)) for camp in camps}
    for row in lane["towers"]:
        item={1:"tower_01_qingque",3:"tower_03_tianque"}[row["tier"]]
        want.add(("myriad:"+item+("_red" if row["faction"]=="destruction" else ""),*xyz(row)))
    for row in lane["crystals"]:
        want.add(("myriad:crystal_lingyuan"+("_red" if row["faction"]=="destruction" else ""),*xyz(row)))
    actual=Counter(marker(e) for f in (world/OVERWORLD/"entities").glob("*.mca")
                   for _,r in records(f.read_bytes()) for e in decode(r).get("Entities",[]) if marker(e) in want)
    if actual!=Counter(dict.fromkeys(want,1)):
        raise ValueError(f"Static marker count mismatch: {actual}")
    print(f"PASS: {len(samples)} siege-width floor samples; 4 towers, 2 crystals, 2 fountains, 13 camps")
    return want


def replace_region(file,want):
    old=file.read_bytes()
    header=bytearray(old[:SECTOR*2])
    body=bytearray()
    removed=Counter()
    for index, record in records(old):
        chunk=decode(record)
        changed=False
        for entity in list(chunk.get("Entities",[])):
            p=marker(entity)
            if p in want:
                chunk["Entities"].remove(entity)
                removed[p]+=1
                changed=True
        if changed:
            record=encode(chunk)
        first=len(body)//SECTOR+2
        sectors=ceil(len(record)/SECTOR)
        header[index*4:index*4+4]=first.to_bytes(3,"big")+bytes([sectors])
        body.extend(record)
        body.extend(bytes(sectors*SECTOR-len(record)))
    if removed:
        file.write_bytes(header+body)
    return removed


def package(source,dest,name,want):
    if dest.exists() or source in dest.parents or dest in source.parents:
        raise ValueError("Destination must be a new sibling/copy, never the original")
    dest.mkdir()
    for path in ("data","dimensions","datapacks","resourcepacks"):
        if (source/path).is_dir():
            shutil.copytree(source/path,dest/path)
    if (source/"icon.png").is_file():
        shutil.copy2(source/"icon.png",dest/"icon.png")
    level=nbtlib.load(source/"level.dat")
    data=level["Data"]
    data["LevelName"]=nbtlib.String(name)
    data.pop("singleplayer_uuid",None)
    packs=data["DataPacks"]
    if FOUNTAIN_PACK in packs["Enabled"]:
        packs["Enabled"].remove(FOUNTAIN_PACK)
    if FOUNTAIN_PACK not in packs["Disabled"]:
        packs["Disabled"].append(nbtlib.String(FOUNTAIN_PACK))
    level.save(dest/"level.dat")
    if nbtlib.load(dest/"level.dat")["Data"]["LevelName"]!=name:
        raise ValueError("Copied level name failed its read back")
    replaced=Counter()
    for file in (dest/OVERWORLD/"entities").glob("*.mca"):
        replaced.update(replace_region(file,want))
    if replaced!=Counter(dict.fromkeys(want,1)):
        raise ValueError("Static conversion failed")
    for file in PRESET.rglob("*.json"):
        target=dest/file.relative_to(PRESET)
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(file,target)
    print("PASS: copy retains all terrain, removes 19 exact static placeholders, preserves original")


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("source",type=Path)
    p.add_argument("--dest",type=Path)
    p.add_argument("--name")
    args=p.parse_args()
    src=args.source.resolve()
    markers=validate(src)
    if args.dest:
        package(src,args.dest.resolve(),args.name or args.dest.name,markers)
