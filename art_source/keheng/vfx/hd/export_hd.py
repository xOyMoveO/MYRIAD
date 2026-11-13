"""Export authored keheng HD / pixel VFX components.

Artwork: OpenAI built-in image_gen, 2026-11-13, exec-41c78855-d57a-478b-978e-120e829debbf.png.
A native alpha-cleanup edit (exec-a2d44e10-d2e2-4b43-a7e2-3a92f1514997.png) was inspected; the original
retained sharper small details and is the accepted artwork source.
Normalization only: remove alpha 0..8 quantization noise; preserve 9..255,
crop each separately measured component, resize once, add 8px transparent
padding. No procedural replacement or hand-painted texture content.
HD components use Lanczos; 64px pixel accents use nearest-neighbor.
UV: stream tail at low U, pointed head at high U; veil longitudinal along V.
Shield top at low V / pointed base at high V; ring/arc are front-on planes.
"""
from pathlib import Path
from math import hypot
from PIL import Image, ImageDraw

GENERATION_PROMPT = r"""Use case: stylized-concept.
Asset type: actual production 2D VFX texture components on REAL TRANSPARENT RGBA background. This is a texture sheet to slice and map onto independently oriented planes and curved surfaces in a 3D game, NOT a scene, concept art, screenshot, icon set or UI.
Create exactly EIGHT isolated component sprites in a FOUR-COLUMN by TWO-ROW grid on one wide approximately 2048x1024 transparent canvas. All cells independently square, 8% safe transparent space inside every cell edge and wide truly transparent gutters. No borders, labels, shadows, backdrop, black rectangle, white rectangle, visible checkerboard pattern, framing, or decorative page. Every unused pixel must have alpha zero. Preserve real semitransparent alpha on soft light edges and filaments, not just a binary cutout. Internal holes must be truly transparent too.
Quality and density: six HD hand-painted luminous effect components with rich fine filament texture, overlapping contour strands, bright sharply concentrated cores, layered semi-transparent feathered edges and broken micro-detail. Substantial authored surface texture, much richer than a few flat colored lines, but avoid opaque blobs or fog. The final two sprites are crisp chunky PIXEL accent textures intended for 64x64, with square pixel clusters and transparent empty space. No physical weapon, solid metal part, character, hands, terrain, architecture, HUD, debug lines or scene lighting.
Exact reading order:
Top row 1 RING: flat straight-on/top-down annular effect texture, with multiple imperfect nested contour strands and interrupted outer detail; LARGE genuinely transparent open center occupying about half the diameter; outer edge also transparent. A richly textured annular component, not an opaque disc.
Top row 2 ARC: one broad open C-shaped angular sweeping crescent band with densely layered inner filaments and a sharply tapered end; more than half of the square and the entire central opening remain transparent. Must be visibly different from the full annulus.
Top row 3 STREAM: a HORIZONTAL energy ribbon running LEFT TO RIGHT, long staggered filament tails on the LEFT converging toward a sharper bright pointed HEAD ON THE RIGHT. Several longitudinal layers, no circular shape and no background. Keep both endpoints safely inside the cell.
Top row 4 IMPACT: one concentrated small sharp central impact star surrounded by asymmetrical layered short radial cuts, expanding broken shards and delicate disconnected filaments. Bright compact center with real empty gaps through the outer burst, not an opaque explosion disk.
Bottom row 1 VEIL: a tall VERTICAL curved energy-sheet component, three displaced translucent flowing thin bands with rich internal hairline striation, irregular open gaps, broken upper/lower ends, large transparent areas. An upright curtain-strip texture part, not a complete bubble or an orb.
Bottom row 2 SHIELD: a single standing curved shield surface component, with a pointed/polygonal outline, multiple layered edge filaments, fractured interior lattice and genuinely transparent missing patches. The center is mostly translucent or open, not a flat solid filled shield emblem. No person.
Bottom row 3 MOTES: a sparse cluster of small crisp square pixel motes, tiny narrow diamond pixels and short stepped streaks, with large transparent gaps; clear deliberate 64x64 pixel-art silhouette, no soft fog.
Bottom row 4 SPARK: one sharp tiny asymmetric pixel starburst, a long diagonal needle-like main streak, two smaller crossing glints and a few discrete pixel flecks. Crisp stepped pixel edges for 64x64, no blur cloud. Keep the star sharp and sparse.
The first six are HD 256x256-ready components; last two are 64x64-ready pixel accents. No text, numbers, symbols resembling letters, watermarks, captions, opaque backgrounds or copied game UI. All eight independent and fully isolated.
Role direction: KEHENG, precision time-difference energy, distinct from electrical lightning or a wizard magic circle. Palette luminous teal #42D9BD, restrained amber #F1C76B, silver-white #F2F6EA, very sparse displaced purple-gray #8C7CA6. Build the rich textures from staggered translucent planar shear layers, short fine amber timing cuts, narrow silver needle highlights, displaced teal refraction strands and ghosted purple-gray echoes. RING uses broken precision timing incisions rather than complete clock ticks; ARC is a staggered shear crescent; STREAM is a fine silver/teal needle wake with offset echoes; IMPACT is a needle-cut convergence and time-slice shatter; VEIL is displaced teal time-sheet strata; SHIELD is three subtly offset refractive strata in one curved plate, with open gaps and amber stitch highlights. No clock face, readable clock numerals, runic letters, giant gears, magic sigil text, lightning bolts, sword array, frozen ice chunks, floral petals or generic sci-fi hexagon grid. Keep amber to fine structural accents, not gold opaque metal. Rich delicate authored luminous surfaces, transparent through their gaps."""
PANELS = (
    ("ring", (16, 13, 424, 418)),
    ("arc", (456, 18, 850, 416)),
    ("stream", (902, 131, 1319, 320)),
    ("impact", (1352, 20, 1754, 413)),
    ("veil", (84, 452, 370, 865)),
    ("shield", (504, 451, 825, 874)),
    ("motes", (956, 542, 1224, 789)),
    ("spark", (1411, 526, 1687, 804)),
)
PIVOTS = {"impact":[1595,225],"spark":[1532,670]}
ROLE = "keheng"


def alpha_floor(image):
    image = image.convert("RGBA")
    image.putalpha(image.getchannel("A").point(lambda value: 0 if value <= 8 else value))
    # Transparent RGB contributes nothing and does not leak stale matte colors when packed.
    image.putdata([(r, g, b, a) if a else (0, 0, 0, 0) for r, g, b, a in image.get_flattened_data()])
    return image


def main():
    source_dir = Path(__file__).resolve().parent
    project = source_dir.parents[3]
    target = project / "src/main/resources/assets/myriad/textures/particle/vfx"
    target.mkdir(parents=True, exist_ok=True)
    source = Image.open(source_dir / "hd_source.png").convert("RGBA")
    assert source.size == (1774, 887)
    source = alpha_floor(source)
    previews = []
    for name, box in PANELS:
        size = 64 if name in ("motes", "spark") else 256
        cx, cy = PIVOTS.get(name, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2))
        half_x, half_y = max(cx - box[0], box[2] - cx), max(cy - box[1], box[3] - cy)
        scale = (size - 16) / (2 * max(half_x, half_y))
        sprite = source.crop(box)
        dimensions = (max(1, round(sprite.width * scale)), max(1, round(sprite.height * scale)))
        sprite = sprite.resize(dimensions, Image.Resampling.NEAREST if size == 64 else Image.Resampling.LANCZOS)
        sprite = alpha_floor(sprite)
        result = Image.new("RGBA", (size, size))
        x = round(size / 2 - (cx - box[0]) * scale)
        y = round(size / 2 - (cy - box[1]) * scale)
        result.paste(sprite, (x, y))
        result.save(target / f"{ROLE}_hd_{name}.png", optimize=True)
        alpha = result.getchannel("A")
        assert alpha.getextrema()[0] == 0
        assert alpha.crop((0, 0, size, 8)).getbbox() is None
        assert alpha.crop((0, size - 8, size, size)).getbbox() is None
        assert alpha.crop((0, 0, 8, size)).getbbox() is None
        assert alpha.crop((size - 8, 0, size, size)).getbbox() is None
        hist = alpha.histogram()
        assert sum(hist[9:255]) > 0
        radius = max(hypot(abs(px + .5 - size / 2) + .5, abs(py + .5 - size / 2) + .5)
                     for py in range(size) for px in range(size) if alpha.getpixel((px, py)))
        print(f"{ROLE} {name}: {size}px alpha bbox={alpha.getbbox()} max occupied texel-corner radius/halfCanvas={radius/(size/2):.9f}")
        previews.append((name, result))
    for label, color in (("black", (0, 0, 0, 255)), ("white", (255, 255, 255, 255)), ("grass", (107, 134, 85, 255))):
        board = Image.new("RGBA", (4 * 288, 2 * 288), color)
        draw = ImageDraw.Draw(board)
        for index, (name, sprite) in enumerate(previews):
            x, y = index % 4 * 288, index // 4 * 288
            visual = sprite if sprite.width == 256 else sprite.resize((256, 256), Image.Resampling.NEAREST)
            board.alpha_composite(visual, (x + 16, y + 28))
            draw.text((x + 12, y + 8), name, fill=(170, 170, 170, 255))
        board.convert("RGB").save(source_dir / f"hd_contact_{label}.png", optimize=True)


if __name__ == "__main__":
    main()
