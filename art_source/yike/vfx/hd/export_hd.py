"""Export authored yike HD / pixel VFX components.

Artwork: OpenAI built-in image_gen, 2026-11-13, exec-b42cdde1-0fdb-4e98-a1cd-900b5939f090.png.
A native alpha-cleanup edit (exec-fbcd4e81-5578-45d4-93a0-1a388ccc8a35.png) was inspected; the original
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
Role direction: YIKE / HONGLIE, futuristic precision photon technology, distinctly cold and electronic. Palette ice blue #5DDEFF, cold white #F1FCFF, cobalt blue #18456B for low-light luminous strata, with only tiny violet #A88AFF and magenta #FF6AAE accents. Build the rich textures from multiple parallel photon filaments, angular segmented collimator traces, prismatic interference fringes, broken electronic lattice and occasional pixel-light junctions. RING uses angular segmented coaxial collimator rings with interrupted fine circuitry; ARC is an angular diffraction crescent with repeated narrow light rails; STREAM has a pointed focused white photon core on RIGHT and long blue/violet parallel tails to LEFT; IMPACT is a white-focused electronic overload with layered angular blue fragments; VEIL is a vertical layered collimated photon sheet with narrow flowing band-pass traces; SHIELD is an angular curved hard-light panel with fragmented electronic lattice and open transparent holes. Absolutely no gold/brass, warm amber, fantasy magical flower petals, gemstones, leather, decorative engraving, wizard glyphs, circular spell runes, organic flame, lightning column or sword array. No physical gun or emitter device, only the translucent effect component sprites."""
PANELS = (
    ("ring", (23, 17, 417, 425)),
    ("arc", (458, 18, 860, 428)),
    ("stream", (904, 117, 1313, 319)),
    ("impact", (1353, 26, 1749, 418)),
    ("veil", (73, 459, 355, 867)),
    ("shield", (506, 459, 824, 868)),
    ("motes", (975, 521, 1232, 790)),
    ("spark", (1416, 513, 1683, 795)),
)
PIVOTS = {"impact":[1559,226],"spark":[1555,659]}
ROLE = "yike"


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
