"""Export Yike's future-tech eight-candidate icon set from the accepted image source.

Source: OpenAI built-in image_gen, 2026-11-13; original exec-46740bbd-f490-40ae-b0c7-d81227c83ef8.png.
Each full titanium-frame crop was measured independently from its visible border,
including its dark bevel. Coordinates are Pillow (left, top, right-exclusive,
bottom-exclusive); the sheet's gutters are excluded. No fixed-pitch crop assumption.
"""
from pathlib import Path

from PIL import Image

GENERATION_PROMPT = r"""Use case: stylized-concept.
Asset type: production high-contrast PIXEL ART skill-icon atlas for the futuristic photon-array character Yike / Honglie.
Primary request: Create ONE wide image containing EXACTLY EIGHT independent SQUARE finished game skill icons arranged FOUR COLUMNS by TWO ROWS, reading left to right then next row. No heading or captions. Each square has its own complete compact titanium-gray hard metal frame with a thin ice-blue / cool-white inner circuit line and tiny magenta status lights. The frames must remain fully visible, including all four corners, separated by neutral dark gutters. Do not wrap the sheet in a single outer frame.
Style: sophisticated sharp pixel-art painting, clear chunky pixel clusters, angular bevelled hard-surface 3D forms, deliberate highlights and deep shadows, top-left illumination. Match detail density and border construction across all eight. Must remain readable and distinct at 64x64 pixels. Finished richly shaded game art, not flat vector symbols, not simple geometric placeholders.
Shared palette: white ceramic armor #DCE8F0, graphite #15222E, deep cobalt blue #18456B, luminous ice blue #5DDEFF, cold white #F1FCFF, restrained violet #A88AFF and a few magenta status lights #FF6AAE. Opaque dark-blue graphite textured background in every cell. Cold high-tech precision equipment, visible electronic light paths, capacitor cells, angular cooling slots and segmented collimator rings.
Technology language: long slender photon-array device with a clear thin grip/shaft, THREE angular hard armor wings which unfold, and a hollow sliding segmented focusing ring on its firing axis. Ceramic plates have bevelled cut planes and tiny radiator grooves; cobalt/graphite structural chassis; small blue capacitor modules. The light source is an electronic emitter, never a gemstone.
ABSOLUTELY AVOID: gold/brass/bronze trims, ornate fantasy decoration, engravings, leather, gemstones, crystal jewelry, magical flower petals, organic wing/petal silhouettes, wizard staves, spell runes, clock faces, orange flame, shell casings, ammo magazines, modern gun sights, rainbow stripes, fog filling a panel. No character portraits or people. No text, letters, numbers, UI hotkeys, labels, icons of letters, logos, watermarks.
Exact grid content:
TOP ROW, CELL 1 — Rainbow Mark Calibration: THREE separated angular electronic targeting brackets forming an OPEN three-part reticle around a small cold-white focused photon diamond/pixel point. Cobalt metal bracket housings, cyan signal paths, tiny magenta calibration points. Open negative space, a precision sensor impression, not a circular fantasy seal.
TOP ROW, CELL 2 — Steady Barrier: THREE layered polygonal translucent ice-blue energy-shield facets, hard angular edges and white/cobalt miniature emitter anchors. Thin violet secondary diffraction at facet edges. Strong clear shield silhouette, no person and no fully opaque dome.
TOP ROW, CELL 3 — Rainbow Rail Shell: ONE sharply pointed slim photon projectile racing diagonally from lower left to upper right, cold-white linear emitter core enclosed by short faceted cobalt/white ceramic fins, layered cyan and thin violet trajectories extending continuously behind it. A precise narrow energy dart, no round fireball or gemstone.
TOP ROW, CELL 4 — Refraction Denial: angular segmented GROUND RANGE GATES and opposing ice-blue hard-light refraction wedges rising from a sparse open electronic ground range boundary. A low outward repulsion wave rendered as broken cyan planes with small magenta junctions, visible dark gaps, clear area-denial silhouette rather than another flying projectile.
BOTTOM ROW, CELL 1 — Fixed Artillery: fully unfolded THREE-WING photon-artillery array, three broad angular white-ceramic/cobalt armor wings spread around a hollow segmented cyan-lit collimator ring, internal capacitor modules visible, slim graphite shaft/grip continues down-left, stable small deployment supports below. Three-quarter view, fully open broad radial stance, clean mechanical joints, no organic petals. The long axial focusing structure must be obvious.
BOTTOM ROW, CELL 2 — Mobile Array: lighter long photon-array device in a half-open travelling configuration, side armor wings swept closer to two narrow blue guide rails, white/cobalt angular ceramic chassis, slim long graphite shaft/grip, small hollow segmented focusing ring at its forward tip, short motion trails. Narrow mobile diagonal silhouette clearly different from the broad fully open artillery icon; do not merely shrink the previous icon.
BOTTOM ROW, CELL 3 — Distant Heavy Bombardment: pointed heavy focused-photon payload descending along an ARC from upper left toward lower right, a larger white/cobalt angular multi-fin body with cold-white narrow tip, curved cyan/violet flight trail showing a downward final direction, small angular electronic ground-range bracket below. A controlled heavy ballistic energy payload, no explosion cloud, no flame, no magic jewel.
BOTTOM ROW, CELL 4 — Prismatic Array Beam: a compact angular white/cobalt segmented collimator emitter at lower left firing a LONG NARROW cold-white beam toward upper right, parallel cobalt/cyan rail lines framing the beam, small violet independently interleaved flow traces, bright focused contact point. Keep the beam core fine, not a giant blank white slab. Distinct long continuous laser silhouette versus the free-flying dart in top row.
Composition: each subject centered and fills its own panel without touching/crossing the inner border. Similar subject weight across all panels, complete individual frames. Roughly 2048x1024 landscape canvas, four square panels across and two down, modest neutral dark gutters. No text anywhere."""

PANELS = (
    ("yike_rainbow_mark", (6, 5, 437, 420)),
    ("yike_steady_barrier", (450, 5, 880, 420)),
    ("yike_rainbow_shell", (893, 5, 1324, 420)),
    ("yike_refraction_denial", (1337, 5, 1768, 420)),
    ("yike_fixed_artillery", (6, 428, 437, 853)),
    ("yike_mobile_array", (450, 428, 880, 853)),
    ("yike_distant_bombardment", (893, 429, 1324, 853)),
    ("yike_prismatic_beam", (1337, 429, 1768, 853)),
)


def main():
    source_dir = Path(__file__).resolve().parent
    project = source_dir.parents[1]
    target_dir = project / "src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/yike"
    target_dir.mkdir(parents=True, exist_ok=True)
    with Image.open(source_dir / "icon_source.png") as original:
        original.load()
        assert original.size == (1774, 887), original.size
        opaque = original.convert("RGBA")
        assert opaque.getchannel("A").getextrema() == (255, 255)
        source = opaque.convert("RGB")
        native = Image.new("RGB", (4 * 64 + 3 * 8, 2 * 64 + 8), (15, 23, 32))
        for index, (name, box) in enumerate(PANELS):
            icon = source.crop(box).resize((64, 64), Image.Resampling.NEAREST)
            assert icon.size == (64, 64) and icon.mode == "RGB"
            icon.save(target_dir / (name + ".png"), optimize=True)
            native.paste(icon, ((index % 4) * 72, (index // 4) * 72))
        native.save(source_dir / "icon_contact_sheet_native.png", optimize=True)
        native.resize((native.width * 4, native.height * 4), Image.Resampling.NEAREST).save(
            source_dir / "icon_contact_sheet.png", optimize=True
        )
    print(f"yike: {len(PANELS)} opaque 64x64 future-tech PNGs; nearest-neighbor export")


if __name__ == "__main__":
    main()

