"""Paint hand-shaped 128px RGBA key frames for the ink and celadon planar effects."""

from math import cos, pi, sin
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw


SIZE = 128
FRAMES = 4
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/myriad/textures/particle/vfx"
INK = (34, 37, 43, 251)
DRY = (76, 79, 84, 225)
ASH = (156, 151, 137, 205)
GOLD = (214, 177, 108, 235)
DEEP = (23, 100, 91, 197)
GREEN = (86, 203, 173, 216)
JADE = (150, 244, 213, 237)
WHITE = (238, 255, 248, 255)
CLEAR = (0, 0, 0, 0)


def polygon(draw, points, color):
    draw.polygon([(round(x), round(y)) for x, y in points], fill=color)


def paper(draw, phase):
    crease = 29 + phase * 3
    polygon(draw, [(26, 5), (93, 8), (99, 17), (95, 95), (101, 110), (87, 122),
                   (34, 119), (26, 109), (29, 93), (24, 38)], (111, 84, 58, 255))
    polygon(draw, [(28, 7), (92, 10), (96, 18), (91, 97), (97, 110),
                   (83, 119), (35, 116), (29, 108), (32, 92)], (243, 220, 172, 255))
    polygon(draw, [(29, 8), (crease + 17, 16), (43, 96), (36, 115), (29, 108),
                   (32, 92)], (196, 159, 111, 255))
    polygon(draw, [(33, 10), (90, 12), (89, 16), (crease + 14, 15)], (255, 239, 200, 255))
    polygon(draw, [(35, 106), (86, 102), (97, 110), (84, 119), (38, 115)],
            (215, 177, 122, 255))
    rng = Random(72)
    for _ in range(70):
        x, y = rng.randrange(34, 88), rng.randrange(20, 106)
        color = (231, 206, 157, 255) if x < 82 else (223, 194, 143, 255)
        draw.line((x, y, min(88, x + rng.randrange(2, 8)), y), fill=color)
    strokes = [(62, 24, 66, 47), (49, 30, 78, 30), (53, 36, 74, 36),
               (48, 46, 60, 38), (66, 38, 82, 46), (54, 57, 78, 57),
               (54, 57, 54, 73), (77, 56, 77, 73), (53, 73, 78, 73),
               (62, 61, 69, 69), (47, 84, 80, 84), (63, 77, 64, 96),
               (62, 87, 52, 97), (66, 88, 77, 95)]
    for a, b, c, d in strokes:
        draw.line((a, b, c, d), fill=(92, 68, 50, 255), width=4)
        draw.line((a, b - 1, c, d - 1), fill=INK, width=2)
    draw.rectangle((75, 95, 84, 104), outline=(153, 58, 47, 255), width=2)
    draw.point([(78, 98), (80, 99), (77, 101), (81, 102)], fill=(153, 58, 47, 255))
    if phase == 3:
        polygon(draw, [(101, 97), (91, 98), (85, 107), (77, 123), (106, 124)], CLEAR)
        draw.line((90, 98, 77, 120), fill=(127, 92, 63, 255), width=2)


def stamp(draw, phase):
    left = [[(58, 17), (16, 17), (16, 51), (46, 51), (46, 31), (31, 31)],
            [(54, 44), (54, 69), (17, 69), (17, 110), (52, 110), (52, 86), (36, 86)]]
    right = [[(68, 17), (109, 17), (109, 50), (81, 50), (81, 31), (96, 31)],
             [(73, 42), (73, 69), (109, 69), (109, 108), (74, 108), (74, 84), (95, 84)]]
    paths = left if phase == 0 else left + right
    for i, path in enumerate(paths):
        if phase == 3 and i % 2:
            path = path[:4]
        offset = -3 if phase >= 2 and i < 2 else 3 if phase >= 2 else 0
        shape = [(x + offset, y) for x, y in path]
        draw.line([(x, y - 1) for x, y in shape], fill=ASH, width=8)
        draw.line(shape, fill=INK, width=7 if phase < 3 else 4)
    if phase == 1:
        draw.line([(56, 57), (70, 57), (72, 63)], fill=INK, width=6)
    if phase >= 2:
        polygon(draw, [(79, 11), (66, 42), (68, 55), (61, 68), (49, 80), (41, 120),
                       (50, 114), (60, 83), (70, 73), (79, 55), (75, 44), (91, 11)], CLEAR)
    for x, y in [(15, 19), (37, 18), (53, 15), (105, 50), (110, 94), (16, 100), (77, 110)]:
        draw.line((x, y, x + 4, y + 1), fill=(198, 183, 149, 174))


def brush(draw, phase):
    rng = Random(54 + phase)
    rib = [(6, 72), (16, 57), (36, 51), (63, 56), (87, 50), (109, 48),
           (123, 52), (112, 65), (91, 71), (59, 75), (34, 73), (17, 78)]
    polygon(draw, [(x, y + (phase % 3)) for x, y in rib], DRY)
    polygon(draw, [(5, 72), (22, 58), (44, 56), (66, 60), (98, 53), (121, 55),
                   (95, 65), (74, 65), (52, 73), (38, 71), (18, 76)], INK)
    for _ in range(42):
        x, y = rng.randrange(7, 103), rng.randrange(52, 79)
        length = rng.randrange(2, 17)
        draw.line((x, y, min(124, x + length), y), fill=rng.choice([INK, DRY, DRY, CLEAR]))
    for x, y in [(18, 59), (93, 55), (102, 69), (42, 75)]:
        draw.line((x, y, x + 5, y), fill=ASH)


def chain(draw, phase):
    # Six rectangular links alternate between a broad face and a turned, narrow face.
    for i in range(6):
        x = 7 + i * 18
        y = 64 + round(5 * sin(i * .9 + phase * .65))
        h = 12 if i % 2 == 0 else 7
        p = [(x, y - h + 4), (x + 5, y - h), (x + 20, y - h),
             (x + 24, y - h + 4), (x + 24, y + h - 4), (x + 19, y + h),
             (x + 5, y + h), (x, y + h - 4), (x, y - h + 4)]
        draw.line(p, width=6, fill=ASH)
        draw.line(p, width=4, fill=INK)
        draw.line((x + 6, y - h + 1, x + 11, y - h + 1), fill=GOLD)


def scraps(draw, phase):
    for i in range(13):
        angle = i * 2.399963
        r = (17 + (i * 19) % 35) * (.67 + .11 * phase)
        x, y = 64 + r * cos(angle), 64 + r * sin(angle)
        w = 2 + i % 5
        polygon(draw, [(x - w, y), (x + 2, y - 3), (x + w, y + 1), (x - 1, y + 3)],
                (233, 209, 155, 245 - phase * 24) if i % 3 else DRY)


def ribbon(draw, y_at, width_at, color, start=4, end=124):
    samples = [(x, y_at((x - start) / (end - start)),
                width_at((x - start) / (end - start))) for x in range(start, end + 1, 2)]
    polygon(draw, [(x, y - w) for x, y, w in samples]
            + [(x, y + w) for x, y, w in reversed(samples)], color)


def crescent(draw, phase):
    # +U is the travel vector in the world plane. The convex edge faces +U.
    rows = []
    for y in range(10, 119, 2):
        t = (y - 10) / 108
        weight = sin(pi * t) ** .78
        leading = 29 + (87 + phase) * weight + 16*t
        rows.append((leading, y, 40 * weight))
    for inner, outer, color in [(1, 0, DEEP), (.69, .03, GREEN), (.33, .01, JADE),
                                (.105, 0, WHITE)]:
        polygon(draw, [(x - outer*w, y) for x, y, w in rows]
                + [(x - inner*w, y) for x, y, w in reversed(rows)], color)
    for i in range(6):
        x, y, w = rows[min(50, 3 + i*8 + phase)]
        x -= w + 8 + i % 3
        polygon(draw, [(x - 8, y + 1), (x + 3, y - 1), (x + 9, y - 2), (x, y + 2)], JADE)


def airflow(draw, phase, style):
    base = {"feather": (13, 7, 28), "flow": (5, 1, 25),
            "wall": (-24, 4, 25), "spindle": (2, 0, 32), "needle": (0, 1, 23)}[style]
    lift, tilt, span = base
    shift = (phase - 1.5) * 2
    center = lambda t: 65 - lift * (sin(pi*t) ** .88) + tilt * (1-t)
    swell = lambda t: sin(pi*t) ** .72
    if style in ("spindle", "needle"):
        upper = lambda t: center(t) - (span + shift) * swell(t)
        lower = lambda t: center(t) + (span * .72 - shift) * swell(t)
        for edge in (upper, lower):
            ribbon(draw, edge, lambda t: 3.6 * swell(t), DEEP)
            ribbon(draw, edge, lambda t: 2.3 * swell(t), JADE)
        ribbon(draw, lambda t: upper(t)-4*swell(t), lambda t: 1.2*swell(t), GREEN, 12, 116)
        ribbon(draw, upper, lambda t: 1.1*swell(t), WHITE, 12, 124)
        if style == "needle":
            ribbon(draw, center, lambda t: 3.1 * t * swell(t), WHITE, 28, 124)
    else:
        ribbon(draw, center, lambda t: span*.48*swell(t), DEEP)
        ribbon(draw, lambda t: center(t) + shift * swell(t),
               lambda t: span*.31*swell(t), GREEN)
        ribbon(draw, lambda t: center(t) - span*.22*swell(t),
               lambda t: max(1, span*.12)*swell(t), JADE)
        ribbon(draw, lambda t: center(t) - span*.29*swell(t),
               lambda t: max(.8, span*.065)*swell(t), WHITE)
        for offset in (-1, 1):
            ribbon(draw, lambda t: center(t) + offset * (span*.67 + 6) * swell(t),
                   lambda t: (1.7 if offset == 1 else 1.2)*swell(t),
                   GREEN if offset == 1 else JADE, 9 + 7 * phase, 118)
    for i in range(6):
        x = 16 + i*15 + phase*2
        y = center(x / 124) + (-1 if i % 2 else 1) * (9 + span*.67 + i % 3)
        polygon(draw, [(x - 4, y + 2), (x + 2, y - 1), (x + 9, y - 2), (x + 3, y + 1)],
                JADE if i % 3 == 0 else GREEN)


def ring(draw, phase):
    radius = 54 + phase
    for i in range(12):
        angle = i * pi / 6 + phase * .10
        for bias, width, color in [(-3, 3, DEEP), (0, 3, GREEN), (1, 1, WHITE)]:
            points = [(64 + (radius+bias)*cos(angle+j*.052),
                       64 + (radius+bias)*sin(angle+j*.052)) for j in range(8)]
            draw.line([(round(x), round(y)) for x, y in points], fill=color, width=width)
        if i % 2 == 0:
            x, y = 64 + (radius-6)*cos(angle), 64 + (radius-6)*sin(angle)
            draw.line((round(x), round(y), round(x-4*sin(angle)), round(y+4*cos(angle))),
                      fill=JADE)


def ink_grain(draw, phase):
    rng = Random(148 + phase)
    for i in range(96):
        x = rng.randrange(6, 120)
        y = round(64 + rng.gauss(0, 14 + phase*1.8))
        if not 11 < y < 116:
            continue
        weight = 1 - abs(y - 64)/62
        length = rng.randint(3, 13) * weight
        color = (18, 21, 31, round(180*weight)) if i % 6 else (137, 139, 153, 140)
        polygon(draw, [(x - length, y + 1), (x, y - 1), (min(124, x + length), y),
                       (x + 3, y + (i % 3))], color)


def ink_gilt(draw, phase):
    for i in range(11):
        x = 7 + (i * 39 + phase * 9) % 108
        y = 21 + (i * 53 + phase * 7) % 83
        length = 4 + i % 6
        polygon(draw, [(x - length, y), (x, y - 2), (x + length, y), (x, y+2)],
                (217, 162, 78, 216) if i % 3 else (255, 222, 157, 248))
        if i % 4 == 0:
            draw.line((x, y-4, x, y+4), fill=(241, 204, 132, 184))


def ink_fibers(draw, phase):
    for i in range(13):
        phi = i * 2.399963
        reach = (23 + i * 13 % 36) * (.64 + phase*.11)
        x, y = 64 + cos(phi)*reach, 64 + sin(phi)*reach
        width = 2 + i % 4
        polygon(draw, [(x-width-2,y-1), (x+width,y-3), (x+width+3,y+2), (x,y+3)],
                (171, 126, 82, 224-phase*24))
        polygon(draw, [(x-width,y-1), (x+width,y-2), (x+1,y+2)],
                (249, 225, 177, 247-phase*26))


def wind_filaments(draw, phase):
    for i in range(4):
        offset = -29 + i*19 + phase*2
        ribbon(draw, lambda t: 64 + offset*sin(pi*t)**.69 - 13*sin(2*pi*t),
               lambda t: (1.1+i%2*.85) * sin(pi*t)**.74, (184, 253, 229, 211),
               4 + 11*i, 119 - i*2)


def wind_vapor(draw, phase):
    for i in range(3):
        offset = (i-1)*20 + phase*3
        axis = lambda t: 64 + offset + 13*sin((t-.5)*pi) + 10*sin(t*pi)
        ribbon(draw, axis, lambda t: 7.5*sin(pi*t)**.92, (32, 190, 165, 105),
               4+i*17, 118-i*6)
        ribbon(draw, lambda t: axis(t)-4, lambda t: 2.1*sin(pi*t)**.7, (117, 235, 212, 151),
               12+i*15, 114-i*6)


def wind_shards(draw, phase):
    for i in range(16):
        x, y = 16 + (i*53+phase*8) % 91, 18 + (i*29+phase*9) % 92
        length = 6 + i % 9
        polygon(draw, [(x - length, y + 4), (x + 2, y-2), (min(122,x+length), y-4),
                       (x + 5, y)], (117, 235, 212, 180 + i % 3 * 22))
        if i % 3 == 0:
            draw.line((x - 3, y+1, x + 4,y-1), fill=WHITE)


def wind_sweep_edge(draw, phase):
    rows = []
    for y in range(10, 120, 2):
        t = (y-10)/110
        bulge = sin(pi*t)**.8
        rows.append((31+(86-phase*3)*bulge+12*t, y, (4 + phase*.9)*bulge))
    for offset, color in [(9, (51, 212, 178, 174)), (0, (203, 255, 239, 244))]:
        polygon(draw, [(x+offset,y) for x,y,_ in rows]
                + [(x+offset-width,y) for x,y,width in reversed(rows)], color)


def wind_ring_flecks(draw, phase):
    for i in range(20):
        theta = i*pi/10 + phase*.095
        radius = 47 + (i % 4)*3 + phase
        x,y = 64+radius*cos(theta),64+radius*sin(theta)
        tx,ty = -sin(theta),cos(theta)
        size = 3+i%4
        polygon(draw, [(x-tx*size,y-ty*size),(x+1,y+1),(x+tx*size,y+ty*size),
                       (x-1,y-1)], (103, 227, 193, 199) if i%3 else WHITE)


def projectile_material(draw, facet, paper_face):
    """Opaque keyed facets use the same atlas convention as the planar images."""
    colors = ([(248,226,176),(219,181,125),(165,122,79),(118,87,65)] if paper_face else
              [(228,252,242),(154,216,199),(89,168,152),(51,115,109)])
    base = colors[facet]
    draw.rectangle((0,0,127,127), fill=(*base,255))
    rng = Random(218+facet)
    for _ in range(110):
        x,y = rng.randrange(0,122),rng.randrange(0,127)
        width = rng.randint(3,9)
        bias = rng.choice((-24,-14,8,14))
        toned = tuple(max(0,min(255,v+bias)) for v in base)
        draw.line((x,y,x+width,y),fill=(*toned,255),width=2 if width>7 else 1)
    outline = (*colors[max(0,facet-1)],255)
    bevel = (*colors[min(3,facet+1)],255)
    polygon(draw, [(0,0),(10,0),(127,109),(127,127),(118,127),(0,10)], bevel)
    polygon(draw, [(0,0),(127,0),(127,6),(12,6),(6,115),(0,127)], outline)
    if paper_face:
        for points in [[(33,92),(85,92),(85,61),(56,61),(56,76),(67,76)],
                       [(39,81),(39,49),(64,49),(64,32)]]:
            draw.line(points,fill=(73,57,49,255) if facet<3 else (78,61,56,255),width=4)
    else:
        for i in range(4):
            x,y=31+i*15,83-i*13
            polygon(draw,[(x-10,y+8),(x+14,y-13),(x+10,y-3),(x,y+8)],outline)


ART = {
    "ink_hd_paper": paper, "ink_hd_stamp": stamp, "ink_hd_brush": brush,
    "ink_hd_chain": chain, "ink_hd_scraps": scraps,
    **{f"wind_hd_{style}": lambda draw, phase, s=style: airflow(draw, phase, s)
       for style in ("feather", "flow", "wall", "spindle", "needle")},
    "wind_hd_cut": crescent,
    "wind_hd_ring": ring,
    "ink_hd_grain": ink_grain, "ink_hd_gilt": ink_gilt, "ink_hd_fibers": ink_fibers,
    "wind_hd_filaments": wind_filaments, "wind_hd_vapor": wind_vapor, "wind_hd_shards": wind_shards,
    "wind_hd_sweep_edge": wind_sweep_edge, "wind_hd_ring_flecks": wind_ring_flecks,
    # Solid model atlases are owned by build_mochen_models.py and build_sulan_windblade.py.
}


def main():
    for name, paint in ART.items():
        atlas = Image.new("RGBA", (FRAMES * SIZE, SIZE))
        for phase in range(FRAMES):
            frame = Image.new("RGBA", (SIZE, SIZE))
            paint(ImageDraw.Draw(frame), phase)
            atlas.paste(frame, (phase * SIZE, 0))
        atlas.save(OUT / f"{name}.png", optimize=True)


if __name__ == "__main__":
    main()
