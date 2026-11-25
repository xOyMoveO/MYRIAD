"""Normalize the three inspected Luoxia raster sources and author four-frame pixel VFX atlases."""
from pathlib import Path
import argparse
import math
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("portrait", type=Path)
parser.add_argument("rapid", type=Path)
parser.add_argument("precision", type=Path)
parser.add_argument("--detailed-icons", action="store_true", help="Use the measured frames of the second-edition skill sheets.")
parser.add_argument("--preview", type=Path)
args = parser.parse_args()
asset = Path(__file__).resolve().parents[1] / "src/main/resources/assets/myriad/textures"
portraits = asset / "gui/sprites/portraits"
icons = asset / "gui/sprites/skill_icons/generated/luoxia"
icons.mkdir(exist_ok=True, parents=True)
portrait = Image.open(args.portrait).convert("RGB")
assert portrait.width == portrait.height
portrait.resize((256,256), Image.Resampling.NEAREST).save(portraits / "luoxia.png")
icons64 = []
strips = [
    (args.rapid, [(13,142,438,568),(446,142,870,568),(876,142,1301,568),(1305,142,1731,568),(1737,142,2161,568)],
     ["chase_dusk","dusk_mark","slant_step","feather_burst","thousand_feathers"]),
    (args.precision, [(12,12,713,712),(735,12,1437,712),(1460,12,2161,712)], ["steady_dusk","last_sun","sunset_line"]),
]
for source, bounds, ids in strips:
    sheet = Image.open(source).convert("RGB")
    if args.detailed_icons:
        if len(ids) == 5:
            bounds = [(9,104,434,550), (442,104,867,550), (874,104,1298,550),
                      (1307,104,1731,550), (1739,104,2164,550)]
        else:
            bounds = [(8,6,715,716), (733,6,1439,716), (1457,6,2164,716)]
    for box, key in zip(bounds, ids, strict=True):
        assert 0 <= box[0] < box[2] <= sheet.width and 0 <= box[1] < box[3] <= sheet.height
        icon = sheet.crop(box)
        icon.resize((256,256), Image.Resampling.NEAREST).save(icons / f"luoxia_{key}.png")
        icons64.append(icon.resize((64,64), Image.Resampling.NEAREST))

def sprite(kind, frame, size=128):
    image = Image.new("RGBA", (size,size))
    pix = image.load()
    for y in range(2,size-2):
        for x in range(2,size-2):
            u, v = ((x+.5)/size-.5)*2, ((y+.5)/size-.5)*2
            if kind == "wake":
                # Two tapered feather-shaped membranes around one needle-thin white spine.
                wrap = (1-u*u) * max(0., min(1.,(.95-u)*3))
                shift = .16*u*u - .06 + frame * .012
                edge = min(abs(v-shift), abs(v+shift+.13))
                shape = max(0., 1 - edge/(.10+wrap*.27))*max(0., wrap)
                core = max(0., 1-abs(v-shift)/(.045+wrap*.045))*wrap
                grain = .86 if ((x//3+y//4+frame*2)%9==0) else 1
            else:
                # Expanding sunset flash. Dark gaps separate the five branches and the broken ring.
                radius, angle = math.hypot(u,v), math.atan2(v,u)
                ray = max(0., math.cos(5*angle+.3))**14
                pull = .20 + frame*.15
                shell = max(0., 1-abs(radius-pull)/.115)
                shape = max(shell*(.35+.65*math.cos(angle*3)**2), ray*max(0.,1-radius/(pull+.29)))
                core = max(0.,1-radius/max(.04,.26-frame*.062)) + ray*shell*.48
                grain = .8 if ((x//2+y//2+frame*3)%7==0) else 1
            density = max(shape*.84, core)
            alpha = int(min(1.,density)*230*grain* (1-frame*.13 if kind=="impact" else 1-frame*.12))
            alpha = (alpha//12)*12
            if alpha > 0:
                rgb = (255,242,203) if core>.42 else (255,198,93) if density>.46 else (255,118,91) if density>.17 else (98,74,131)
                pix[x,y] = (*rgb,alpha)
    return image

strips_out = []
for kind in ["wake","impact"]:
    atlas = Image.new("RGBA",(512,128))
    for f in range(4):
        image = sprite(kind,f)
        assert image.getchannel("A").getbbox()
        atlas.paste(image, (128*f,0))
    atlas.save(asset / f"particle/vfx/luoxia_{kind}.png")
    strips_out.append(atlas)
if args.preview:
    preview = Image.new("RGB",(528,375),(27,25,39))
    for i, icon in enumerate(icons64):
        preview.paste(icon,(8+65*i,8))
    for i, atlas in enumerate(strips_out):
        for x in range(8,520):
            for y in range(80+140*i,208+140*i):
                preview.putpixel((x,y), (119,144,99) if x<264 else (29,28,49))
        preview.paste(atlas,(8,80+140*i),atlas)
    preview.save(args.preview)
