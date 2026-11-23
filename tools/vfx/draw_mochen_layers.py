"""Draw phase-locked, transparent 128px ink/gilt layers; each row packs four frames."""
from math import cos, sin, pi
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw

SIZE = 128
RES = Path(__file__).resolve().parents[2] / "src/main/resources/assets/myriad/textures/particle/vfx"


def stream(d, phase, profile, seed):
    rng = Random(seed)
    xs = range(7, 122, 3)
    center = [profile(x, phase) for x in xs]
    for width, color in [(14, (45, 39, 47, 142)), (9, (66, 55, 54, 204)),
                         (4, (155, 105, 58, 231)), (1.7, (252, 210, 130, 250))]:
        up = [(x, y + sin(x*pi/128)**.8*width*(.8+rng.random()*.3)) for x, y in zip(xs, center)]
        down = [(x, y - sin(x*pi/128)**.8*width*(.35+rng.random()*.25)) for x, y in zip(xs, center)]
        d.polygon(up+down[::-1], fill=color)
    for _ in range(42):
        x = rng.randrange(8, 116)
        y = max(6, min(118, profile(x, phase) + rng.randrange(-24, 22)))
        length = min(120-x, rng.randrange(2, 13))
        clr = rng.choice(((113, 91, 78, 161), (222, 168, 105, 190), (255, 234, 179, 234)))
        d.line((x, y, x+length, y-1), fill=clr, width=rng.choice((1, 1, 2)))


def veil(d, phase):
    stream(d, phase, lambda x,p: 96-.59*x + 8*sin(x/28+p*pi/2), 55)
    stream(d, phase, lambda x,p: 109-.44*x + 8*sin(x/25-p*pi/2), 109)


def wave(d, phase):
    stream(d, phase, lambda x,p: 62+5*cos(x/13+p*pi/2)+9*cos(x/48), 91)
    for i in range(10):
        x = 10+i*11
        y = 70 + (i*61+phase*5)%33
        d.polygon([(x,y),(x+5,y-3),(x+8,y+1),(x+2,y+6)], fill=(250,224,168,205))


def weave(d, phase):
    for y in (43, 80):
        for i in range(4):
            x = 8+i*29
            p = [(x,y+7), (x+5,y-1), (x+18,y-1), (x+23,y+7),
                 (x+18,y+15), (x+5,y+15), (x,y+7)]
            for width,color in [(9,(37,32,39,175)),(5,(135,96,57,240)),(2,(251,200,113,247))]:
                d.line(p, fill=color, width=width)
            spark = x+5+(phase*4+i*2)%14
            d.line((spark,y-3,spark+4,y-4),fill=(255,242,202,255),width=2)
    for i in range(8):
        x = 10+i*15
        y = 61+round(cos((i+phase)*.9)*4)
        d.line((x,y,x+8,y+1),fill=(222,179,111,216),width=2)


def main():
    for name, paint in (("gate_veil", veil), ("array_wave", wave), ("boundary_weave", weave)):
        atlas = Image.new("RGBA", (SIZE*4, SIZE))
        for i in range(4):
            frame = Image.new("RGBA", (SIZE,SIZE))
            paint(ImageDraw.Draw(frame), i)
            assert all(3 < x < 125 for x in frame.getbbox())
            atlas.paste(frame, (i*SIZE,0))
        atlas.save(RES/f"ink_{name}.png")


if __name__ == "__main__":
    main()
