"""Crop two-row illustrated branch sheets, stage icons, and audit the release archive."""
import argparse
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import json
import re
from zipfile import ZipFile

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ROLES = {"yingxiao": "夜影", "tianxing": "天刑", "fenjue": "焚诀", "chixiao": "赤霄", "yunsheng": "云笙"}
ORDER = ["P-A", "P-B", "N1", "N2", "M-A", "M-B", "U-A", "U-B"]
ASSET = "assets/myriad/textures/gui/sprites/skill_icons/generated"


def definitions():
    data = ROOT / "src/main/resources/data/myriad/role_rework"
    all_skills = [s for group in ("first_group", "second_group")
                  for s in json.loads((data / f"{group}.json").read_text("utf-8"))["skills"]]
    result = {}
    for role in ROLES:
        skills = sorted((s for s in all_skills if s["role"] == role), key=lambda s: s["index"])
        if [s["category"] for s in skills] != ORDER:
            raise ValueError(f"{role}: live candidate order changed")
        result[role] = skills
    return result


def greens(rgb, strict=False):
    pixels = rgb.get_flattened_data() if hasattr(rgb, "get_flattened_data") else rgb.getdata()
    return Image.frombytes("L", rgb.size,
                           bytes(255 if g >= 166 and g-r >= 70 and g-b >= 65
                                 and (not strict or max(r,b) <= 77 and g-max(r,b) >= 95) else 0
                                 for r, g, b in pixels))


def intervals(flags):
    start = None
    for i, flag in enumerate([*flags, False]):
        if flag and start is None:
            start = i
        elif not flag and start is not None:
            yield start, i
            start = None


def measure(rgb):
    """Measure each row anew and measure all four columns independently inside each row."""
    mask = greens(rgb)
    m = mask.load()
    w, h = mask.size
    row_is_art = [sum(bool(m[x, y]) for x in range(w//20, 19*w//20, 3)) < .72*((19*w//20-w//20+2)//3)
                  for y in range(h)]
    rows = [(a,b) for a,b in intervals(row_is_art) if b-a >= h*.25]
    if len(rows) != 2:
        raise ValueError(f"Expected two measured bands, got {rows}; inspect source and provide --bounds")
    bounds = []
    for y0,y1 in rows:
        ystart,yend = y0+(y1-y0)//14, y1-(y1-y0)//14
        col_is_art = [sum(bool(m[x, y]) for y in range(ystart,yend, 3)) < .72*((yend-ystart+2)//3)
                      for x in range(w)]
        cols = [(a,b) for a,b in intervals(col_is_art) if b-a >= w*.14]
        if len(cols) != 4:
            raise ValueError(f"Expected four measured columns per band, got {cols}; use --bounds")
        bounds.extend((a,y0,b,y1) for a,b in cols)
    return bounds


def opaque_edge(icon):
    """Dematte the thin outer bevel left by the staging gutters, never the subject."""
    mask = greens(icon, strict=True)
    size = icon.width
    border=min(18,round(size*.071))
    if mask.crop((border,border,size-border,size-border)).getbbox():
        raise ValueError("Key green inside the illustration, not an edge")
    pixels=icon.load()
    key=mask.load()
    probe=((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1),(-1,1),(1,-1))
    for y in range(size):
        for x in range(size):
            if not key[x,y]:
                continue
            replacement=None
            for dist in range(1,7):
                for dx,dy in probe:
                    xx,yy=x+dx*dist,y+dy*dist
                    if 0<=xx<size and 0<=yy<size and not key[xx,yy]:
                        replacement=pixels[xx,yy]
                        break
                if replacement is not None:
                    break
            pixels[x,y]=replacement or (16,19,26)
    return icon


def crop(source, manual=None, size=64):
    with Image.open(source) as raw:
        if raw.format != "PNG" or "A" in raw.mode and raw.getchannel("A").getextrema() != (255,255):
            raise ValueError(f"{source} must be an opaque PNG")
        rgb = raw.convert("RGB")
    bounds = manual or measure(rgb)
    if len(bounds) != 8:
        raise ValueError(f"{source} does not have eight measured panels")
    icons, fitted = [], []
    for x0,y0,x1,y1 in bounds:
        if not (0<=x0<x1<=rgb.width and 0<=y0<y1<=rgb.height):
            raise ValueError(f"Out-of-image crop: {(x0,y0,x1,y1)}")
        ratio = (x1-x0)/(y1-y0)
        if not .86 <= ratio <= 1.16 or min(x1-x0,y1-y0)<64:
            raise ValueError(f"Panel is not sufficiently square: {(x0,y0,x1,y1)}")
        panel = (x0,y0,x1,y1)
        # Generated border bevels can leave a matte pixel in a rounded outer corner. Cut
        # the minimum inset on the affected edge(s), never stretch a neighboring cell in.
        limit = max(2, round(min(x1-x0,y1-y0)*.018))
        for _ in range(limit*2):
            icon = rgb.crop(panel).resize((64,64),Image.Resampling.NEAREST)
            mask = greens(icon, strict=True)
            marked = [(x,y) for y in range(64) for x in range(64) if mask.getpixel((x,y))]
            if not marked:
                break
            distances = [ [x,y,63-x,63-y] for x,y in marked ]
            if any(min(v)>1 for v in distances):
                raise ValueError(f"Matte crosses the illustration in {panel}; use measured bounds")
            trim = [any(min(v)==v[side] for v in distances) for side in range(4)]
            p,q,s,t=panel
            panel=(p+trim[0],q+trim[1],s-trim[2],t-trim[3])
            if any(abs(n-m)>limit for n,m in zip(panel,(x0,y0,x1,y1))):
                raise ValueError(f"Matte remnant in {panel}; adjust the crop")
        else:
            raise ValueError(f"Matte remnant in {panel}; adjust the crop")
        if size!=64:
            icon=opaque_edge(rgb.crop(panel).resize((size,size),Image.Resampling.NEAREST))
        icons.append(icon)
        fitted.append(panel)
    return icons, fitted


def preview(rows, target, font):
    w,h=163,178
    sheet=Image.new("RGB",(132+8*w,48+len(rows)*h),"#121925")
    d=ImageDraw.Draw(sheet)
    lab=ImageFont.truetype(str(font),16)
    small=ImageFont.load_default(13)
    d.text((18,16),"MYRIAD     /     BRANCH SKILL ICONS     /     40 ASSETS",fill="#e8ddbe",font=small)
    for i, (role, skills, icons) in enumerate(rows):
        y=42+i*h
        d.text((16,y+28), ROLES[role],fill="#e8ddbe",font=lab)
        d.text((16,y+54), role,fill="#a2aebf",font=small)
        for j,(s,icon) in enumerate(zip(skills,icons)):
            x=122+j*w
            sheet.paste(icon.resize((112,112),Image.Resampling.NEAREST),(x,y+8))
            sheet.paste(icon.resize((32,32),Image.Resampling.NEAREST),(x+116,y+53))
            d.text((x,y+128),s["name"],fill="#eef0f5",font=lab)
            d.text((x,y+153),s["category"],fill="#a2aebf",font=small)
    sheet.save(target)


def audit(jar, baseline=None, size=256):
    defs=definitions()
    java=(ROOT/"src/main/java/com/myriad/client/SkillIcon.java").read_text("utf-8")
    mapping=dict(re.findall(r'Map.entry\("([^"]+)", "([^"]+)"\)',java))
    expected={}
    for role, skills in defs.items():
        for s in skills:
            skill=s["id"]
            path=f"{role}/{skill}"
            if mapping.get(skill)!=path:
                raise ValueError(f"{skill}: no unique direct mapping")
            expected[f"{ASSET}/{path}.png"]=skill
    digests=set()
    with ZipFile(jar) as z:
        for path,skill in expected.items():
            with Image.open(BytesIO(z.read(path))) as icon:
                if icon.format!="PNG" or icon.mode!="RGB" or icon.size!=(size,size):
                    raise ValueError(f"{skill}: expected an opaque {size}px PNG")
                digest=sha256(icon.tobytes()).digest()
                if digest in digests or greens(icon, strict=True).getbbox() is not None:
                    raise ValueError(f"{skill}: repeated artwork or matte")
                digests.add(digest)
        if baseline:
            with ZipFile(baseline) as base:
                old={e.filename:e.CRC for e in base.infolist() if not e.is_dir()}
                new={e.filename:e.CRC for e in z.infolist() if not e.is_dir()}
                changed={n for n in old.keys() | new.keys() if old.get(n)!=new.get(n)}
                if changed != set(expected)|{"com/myriad/client/SkillIcon.class"}:
                    raise ValueError(f"Unexpected package delta: {changed ^ (set(expected)|{'com/myriad/client/SkillIcon.class'})}")
    print(f"PASS: all 40 live candidate IDs map to unique packaged opaque {size}px icons")


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--sheet",action="append",default=[],metavar="ROLE=PATH")
    p.add_argument("--bounds",action="append",default=[],metavar="ROLE=X0,Y0,X1,Y1;...")
    p.add_argument("--stage",type=Path)
    p.add_argument("--font",type=Path)
    p.add_argument("--verify-jar",type=Path)
    p.add_argument("--baseline",type=Path)
    p.add_argument("--size", type=int, choices=(64,256),default=256)
    a=p.parse_args()
    if a.verify_jar:
        return audit(a.verify_jar,a.baseline,a.size)
    sources=dict(pair.split("=",1) for pair in a.sheet)
    if not a.stage or not a.font or set(sources)!=set(ROLES):
        p.error("Staging requires all five role sheets, --stage and --font")
    manual={role:[tuple(map(int,b.split(","))) for b in boxes.split(";")]
            for role,boxes in (pair.split("=",1) for pair in a.bounds)}
    defs=definitions()
    rows=[]
    for role in ROLES:
        icons,bounds=crop(sources[role],manual.get(role),a.size)
        print(f"{role} {bounds}")
        rows.append((role,defs[role],icons))
    for role,skills,icons in rows:
        saved=a.stage/role
        saved.mkdir(parents=True,exist_ok=True)
        for skill,icon in zip(skills,icons):
            icon.save(saved/f"{skill['id']}.png",optimize=True)
            if a.size==256:
                small_dir=a.stage.parent/"64px"/role
                small_dir.mkdir(parents=True,exist_ok=True)
                icon.resize((64,64),Image.Resampling.NEAREST).save(small_dir/f"{skill['id']}.png",optimize=True)
    preview(rows,a.stage.parent/"skill-icon-review.png",a.font)


if __name__=="__main__":
    main()
