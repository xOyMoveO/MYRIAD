"""Paint four transparent 128px frames per planar layer and four facet tiles per model."""
from math import cos, pi, sin
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx"
SIZE = 128

COLD = ((17, 60, 119, 194), (38, 160, 218, 216), (135, 231, 255, 238), (237, 255, 255, 255))
JADE = ((20, 74, 53, 206), (67, 154, 82, 231), (144, 218, 124, 244), (249, 252, 190, 255))
HONEY = ((47, 108, 67, 182), (144, 172, 80, 219), (232, 211, 113, 244), (255, 250, 204, 255))

def polygon(draw, points, color):
    draw.polygon([(round(x), round(y)) for x, y in points], fill=color)


def paths(draw, lines, palette, width):
    for part in lines:
        coords = [(round(x), round(y)) for x,y in part]
        draw.line(coords, fill=palette[0], width=width+4)
        draw.line(coords, fill=palette[1], width=width)
        draw.line([(x, y - 1) for x,y in coords[:-1]], fill=palette[2], width=max(1, width//2))
        draw.line(coords[1:-1], fill=palette[3], width=1)


def polar(angle, radius, center=(64,64)):
    return center[0] + radius*cos(angle), center[1] + radius*sin(angle)


def fissure(draw, frame):
    rng = Random(619)
    pattern = []
    for j in range(6):
        angle = (j + .23) * pi/3
        distance = 8 + frame * 2
        steps = [(64,64), polar(angle + .10, distance + 11),
                 polar(angle - .10, distance + 25), polar(angle + .025, distance + 38),
                 polar(angle - .056, distance + 47)]
        pattern.append(steps[:min(5, 3 + frame)])
        x,y = steps[min(3,2 + frame)]
        for sign in (-1,1):
            spread = angle + sign * .6
            pattern.append([(x,y),polar(spread, 8+frame*2,(x,y)),polar(spread+.23, 15+frame*2,(x,y))])
        px,py = polar(angle + .25, rng.randint(25,46))
        polygon(draw, [(px-1,py+3),(px-2,py),(px+5,py-3),(px+3,py+2)], COLD[2])
    paths(draw, pattern, COLD, 4)


def perimeter(draw, frame, plant):
    colors = HONEY if plant else COLD
    count = 12 if plant else 16
    for i in range(count):
        if (i + frame) % 7 == 0:
            continue
        a = i * 2*pi/count + frame*.025
        fan = 53 + ((i + frame) % 3) * 1.3
        points = [polar(a-.14,fan-3),polar(a-.10,fan+2),polar(a+.10,fan+2),polar(a+.17,fan-2)]
        paths(draw,[points],colors,4 if plant else 5)
        if i % 2 == 0:
            x,y = polar(a, fan-5)
            stem = polar(a+.35, fan-16)
            paths(draw,[[(x,y),stem,polar(a-.08,fan-20)]],colors,2)
            if plant:
                petal_shape(draw,stem[0],stem[1],-.65+a,9,4,JADE)


def wisp(draw, frame, plant, mist=False):
    colors = JADE if plant else COLD
    for lane, (offset,width) in enumerate(((-12,5),(0,11),(15,3))):
        points = []
        for x in range(5,124,3):
            t=(x-5)/119
            y=64+offset + (9 if plant else 5)*sin(t*pi*2+frame*.70+lane*.8)*(1-t*.65)
            radius = max(1, width*(sin(t*pi)**.73))
            points.append((x,y,radius))
        if mist:
            band=[(x,y-r*1.7) for x,y,r in points]+[(x,y+r*1.7) for x,y,r in points[::-1]]
            polygon(draw,band,(102,201,248,72+lane*12))
        else:
            for color, thickness in zip(colors,(1.28,1,.50,.14)):
                band=[(x,y-r*thickness) for x,y,r in points]+[(x,y+r*thickness) for x,y,r in points[::-1]]
                polygon(draw,band,color)
    if mist:
        for x in range(20+frame*5,118,13):
            draw.line((x, 52+(x%12), x+4, 52+(x%12)), fill=(219,247,255,103), width=2)


def petal_shape(draw, x, y, angle, length, width, palette):
    f=(cos(angle),sin(angle))
    side=(-f[1],f[0])
    def point(long, lat):
        return x+f[0]*long+side[0]*lat, y+f[1]*long+side[1]*lat
    shape=[point(-length,0),point(-length*.4,-width*.72),point(length*.4,-width),
           point(length,0),point(length*.4,width*.69),point(-length*.4,width)]
    polygon(draw,shape,palette[0])
    polygon(draw,[point(-length*.95,0),point(-length*.4,-width*.62),point(length*.35,-width*.9),
                  point(length*.94,0),point(-length*.2,width*.25)],palette[1])
    polygon(draw,[point(-length*.85,0),point(length*.31,-width*.26),point(length*.94,0),point(length*.23,width*.26)],palette[2])
    draw.line([point(-length*.84,0),point(length*.77,0)],fill=palette[3],width=1)


def flakes(draw, frame, plant, shape):
    colors = HONEY if shape=="dew" else JADE if plant else COLD
    count=13 if shape!="dew" else 9
    rng=Random(178 + (19 if plant else 0))
    for i in range(count):
        position=(rng.randrange(15,112),rng.randrange(18,111))
        x=position[0]+cos(i*2.4)*frame*2
        y=position[1]-(frame*(2.1 if plant else .95))
        angle=i*2.33+frame*.22
        if shape=="shard":
            length=4+i%6
            polygon(draw,[polar(angle,length,(x,y)),polar(angle+2.13,length*.67,(x,y)),
                          polar(angle+3.48,length*.70,(x,y))],colors[1+(i%2)])
            draw.line([polar(angle,length,(x,y)),polar(angle+3.48,length*.7,(x,y))],fill=colors[3],width=2)
        elif shape=="dew":
            r=3 + i%4
            polygon(draw,[(x,y-r*1.7),(x+r,y),(x+r*.7,y+r),(x-r*.7,y+r),(x-r,y)],colors[1])
            polygon(draw,[(x,y-r*1.56),(x+r*.66,y),(x,y+r*.7),(x-r*.46,y)],colors[2])
            draw.rectangle((int(x-1),int(y-2),int(x+1),int(y)),fill=colors[3])
        else:
            petal_shape(draw,x,y,angle,5+i%6,2+i%3,colors)


def plate_or_leaf(draw, frame, plant):
    if plant:
        for i,(x,y,a,l,w) in enumerate(((55,72,-1.4,47,17),(37,65,-2.19,28,8),(81,69,-.78,31,11))):
            petal_shape(draw,x,y,a+frame*.1,l,w,JADE)
            if i:
                draw.line((x-4,y+1,x+3,y-6), fill=HONEY[3], width=2)
        return
    c = 64 + (frame-1)*2
    shape=[(c,7),(102,26),(111,55),(99,97),(c,118),(27,95),(15,52),(31,24)]
    polygon(draw,[(31,49),(62,31),(93,53),(90,81),(60,102),(42,80)],(65,157,215,97))
    polygon(draw,[(36,57),(60,37),(64,65),(47,84)],(119,217,241,108))
    draw.line(shape+[shape[0]],fill=COLD[0],width=12)
    draw.line(shape+[shape[0]],fill=COLD[1],width=8)
    draw.line(shape[6:]+shape[:4],fill=COLD[3],width=3)
    paths(draw,[[(34,33),(59,40),(61,57),(90,64)],[(61,57),(51,77),(54,99)],
                [(59,40),(63,22)],[(51,77),(33,75)]],COLD,4)
    polygon(draw,[(31,28),(63,13),(59,40),(34,34)],(102,174,231,113))
    polygon(draw,[(89,72),(103,67),(96,96),(65,111)],(99,186,225,96))


def stalk(draw, frame, plant):
    if plant:
        path=[(7,83),(27,70),(40,72),(57,58),(65,60),(83,46),(100,49),(120,32)]
        path=[(x,y+round(3*sin(frame*.6+i))) for i,(x,y) in enumerate(path)]
        paths(draw,[path],JADE,5)
        for i,(x,y) in enumerate(path[1:-1]):
            petal_shape(draw,x,y-.2,-1.9+i*.56,12+i%3,5,JADE)
    else:
        paths(draw,[[(10,91),(26,81),(35,67),(61,62),(77,44),(96,41),(115,24)],
                    [(35,67),(31,48-frame*2),(45,33)],[(77,44),(86,56),(104,57)]],COLD,4)


def sprout(draw, frame):
    sway=frame*2
    central=[(63,115),(62,92),(68,70),(62+sway,43),(67+sway,21)]
    left=[(65,87),(47,70),(38-sway,44),(26-sway,31)]
    right=[(65,70),(79+sway,56),(88+sway,34),(101+sway,23)]
    paths(draw,[central,left,right],JADE,5)
    for i,(x,y,angle) in enumerate(((26-sway,31,-2.23),(42-sway,55,-1.9),
        (68+sway,24,-1.43),(99+sway,24,-.69),(80+sway,55,-.80),(55,81,-2.4))):
        petal_shape(draw,x,y,angle,13+i%3*3,5+i%2,HONEY if i%3==0 else JADE)


def grove(draw, frame):
    for i in range(9):
        angle=i*2*pi/9
        at=polar(angle,50)
        stamp=[polar(angle-.28,50),polar(angle-.11,54),polar(angle+.02,49),polar(angle+.19,54)]
        paths(draw,[stamp],JADE,3)
        for wing in (-1,1):
            x,y=polar(angle+wing*.11,44)
            petal_shape(draw,x,y,angle+wing*.32+frame*.075,10,4+int((i+frame)%3==0),JADE)
        x,y=polar(angle-.12,41+(i+frame)%5)
        draw.rectangle((round(x)-1,round(y)-1,round(x)+1,round(y)+1),fill=HONEY[3])


DRAW = {
    "frost_hd_fissure": fissure,
    "frost_hd_rim": lambda d,f: perimeter(d,f,False),
    "frost_hd_stream": lambda d,f: wisp(d,f,False),
    "frost_hd_shiver": lambda d,f: stalk(d,f,False),
    "frost_hd_shard": lambda d,f: flakes(d,f,False,"shard"),
    "frost_hd_plate": lambda d,f: plate_or_leaf(d,f,False),
    "frost_hd_mist": lambda d,f: wisp(d,f,False,mist=True),
    "herb_hd_vine": lambda d,f: stalk(d,f,True),
    "herb_hd_rim": lambda d,f: perimeter(d,f,True),
    "herb_hd_thread": lambda d,f: wisp(d,f,True),
    "herb_hd_dew": lambda d,f: flakes(d,f,True,"dew"),
    "herb_hd_leaf": lambda d,f: plate_or_leaf(d,f,True),
    "herb_hd_petal": lambda d,f: flakes(d,f,True,"petal"),
    "herb_hd_sprout": sprout,
    "herb_hd_grove_border": grove,
}


def author():
    for name, painter in DRAW.items():
        atlas=Image.new("RGBA",(SIZE*4,SIZE))
        seen=set()
        for index in range(4):
            cell=Image.new("RGBA",(SIZE,SIZE))
            painter(ImageDraw.Draw(cell),index)
            pixels=cell.tobytes()
            assert pixels not in seen and cell.getbbox(), (name,index)
            seen.add(pixels)
            atlas.paste(cell,(index*SIZE,0))
        atlas.save(OUT / (name+".png"))


if __name__ == "__main__":
    author()
