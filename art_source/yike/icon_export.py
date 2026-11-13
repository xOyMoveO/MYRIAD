"""Export yike's complete eight-candidate icon set from the accepted image source.

Source: OpenAI built-in image_gen, 2026-11-13; original exec-ba1ef958-7dcf-41c2-8e91-2335fde09d62.png.
Every crop below was measured against its own visible outer metal border.
Coordinates are Pillow (left, top, right-exclusive, bottom-exclusive), not fixed pitch.
The generator's neutral gutters and cast shadows are excluded from the skill slots.
"""
from pathlib import Path

from PIL import Image

GENERATION_PROMPT = r"""Use case: stylized-concept. Asset type: production pixel-art skill icon atlas for a Minecraft fantasy combat mod. Create ONE landscape image with exactly EIGHT square independent icons in an evenly spaced FOUR COLUMN, TWO ROW grid. Every cell is a full self-contained finished square icon with its own compact 2-pixel-weight dark metal outer frame, thin colored inner line and tiny matching corner ornaments. Consistent top-left illumination and professional high-contrast hand-painted pixel-art shading throughout. Each icon fills its own panel, dark opaque textured background, no transparent area. Four columns and two rows only; visible neutral dark gutters separate every complete frame, margins outside the grid. Design for crisp readability after nearest-neighbor downsampling to 64x64 per icon: bold distinct silhouettes, sharp chunky pixel clusters, restrained shading, minimal micro-detail. This is detailed finished game skill art, not flat vector glyphs or geometric placeholders. No letters, no numbers, no captions, no hotkeys, no text, no watermarks, no portraits, no characters, no whole-sheet frame. Keep effects entirely within their own compact panel frame; never cross panel borders. Compose all cells at similar scale and detail.
Character: Yike, a precision fantasy mage with a manually aimed deployable three-petal arcane artillery staff, prism rails and condensed magical shells. Palette: deep purple #282137 and almost-black violet background, luminous coral #FF6C78, pale brass gold #F3C879, warm ivory-white #FFF3DC, secondary violet #9B83F4. Match dark purple metal frames with thin brass inner line and tiny coral/pale-gold corner facets across all 8 icons. The weapon is a long delicate magical focusing device, coral petal armor around a hollow brass focus and warm-white crystal, not a gun. No triggers, modern guns, firearm sights, bullets, brass casings, ammo magazines, ordinary orange fireballs or large opaque purple fog. No generic rainbow stripes despite the skill names.
Grid order, left to right then second row:
TOP ROW CELL 1, Rainbow Mark Calibration: three separate short pale-gold targeting arcs around a bright warm-white diamond star, small coral prismatic petals bursting outward, dark purple negative space. Clear three-part open reticle and diamond silhouette, not a complete large target disc.
TOP ROW CELL 2, Steady Barrier: three overlapping thin violet and pale-gold shield facets, warm-white short support struts and coral glints; three plane-like plates forming a protective front, no person inside. Clear angular shield silhouette.
TOP ROW CELL 3, Rainbow Rail Shell: ONE streamlined pointed warm-white crystal projectile with curved coral shell petals, speeding diagonally from lower left to upper right, three layered violet/pale-gold trailing ribbons. Clear pointed magical shell, not a round fireball or metal bullet.
TOP ROW CELL 4, Refraction Denial: several sharp coral crystal petals rising outward from an open sparse pale-gold ground range arc, translucent violet outward wave, dark gaps remain between the crystalline petal spikes. A low outward flower-like burst, no solid wall.
BOTTOM ROW CELL 1, Fixed Artillery stance: fully unfolded arcane artillery focusing head in three-quarter diagonal view, three large coral armored petals spread symmetrically around a hollow brass focusing ring and long warm ivory crystal core, strong paired pale-gold rails and small grounded support glints. Show precise metal joints and directionality, no wielder. Broad stable tripod-flower silhouette distinct from next cell.
BOTTOM ROW CELL 2, Mobile Array stance: slender long half-open arcane staff focusing head, two narrow pale-gold guide rails and tucked coral curved petals wrapped close around violet inner chassis, a warm-white crystal at tip; short lateral motion ribbons imply movement. Lighter, narrow diagonal silhouette with clear rail gaps, not the same full-open artillery head scaled down.
BOTTOM ROW CELL 3, Distant Heavy Bombardment: long pointed three-petal coral heavy magical shell diving diagonally down from upper left to lower right along a vivid curved violet/gold trajectory, warm-white concentrated core, a small open gold ground targeting arc below and coral prismatic impact facets. Clear heavy descending projectile action, no orange flames, no smoke cloud.
BOTTOM ROW CELL 4, Prismatic Array Beam: small three-petal brass/coral focusing muzzle at lower left projects a long sharply defined warm-white beam diagonally to upper right, coral outer stream and thin violet interleaved edge ribbons, parallel gold guide rails aligned with beam and bright concentrated endpoint. Long beam silhouette distinct from free-flying shell, no huge blank white panel.
Target canvas roughly 2048x1024 landscape. All eight panels square with complete borders. No text anywhere."""

PANELS = (
    ("yike_rainbow_mark", (9, 12, 435, 424)),
    ("yike_steady_barrier", (453, 12, 878, 424)),
    ("yike_rainbow_shell", (896, 12, 1322, 424)),
    ("yike_refraction_denial", (1340, 12, 1765, 424)),
    ("yike_fixed_artillery", (9, 438, 435, 860)),
    ("yike_mobile_array", (453, 438, 878, 860)),
    ("yike_distant_bombardment", (896, 438, 1322, 860)),
    ("yike_prismatic_beam", (1340, 438, 1765, 860)),
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
        native = Image.new("RGB", (4 * 64 + 3 * 8, 2 * 64 + 8), (14, 16, 23))
        for index, (name, box) in enumerate(PANELS):
            icon = source.crop(box).resize((64, 64), Image.Resampling.NEAREST)
            assert icon.size == (64, 64) and icon.mode == "RGB"
            icon.save(target_dir / (name + ".png"), optimize=True)
            native.paste(icon, ((index % 4) * 72, (index // 4) * 72))
        native.save(source_dir / "icon_contact_sheet_native.png", optimize=True)
        native.resize((native.width * 4, native.height * 4), Image.Resampling.NEAREST).save(
            source_dir / "icon_contact_sheet.png", optimize=True
        )
    print(f"yike: {len(PANELS)} opaque 64x64 PNGs; nearest-neighbor export")


if __name__ == "__main__":
    main()

