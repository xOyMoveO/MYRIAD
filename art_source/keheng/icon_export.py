"""Export keheng's complete eight-candidate icon set from the accepted image source.

Source: OpenAI built-in image_gen, 2026-11-13; original exec-fd9d45dc-90a9-4e2e-829a-0e7bfc3c06c4.png.
Every crop below was measured against its own visible outer metal border.
Coordinates are Pillow (left, top, right-exclusive, bottom-exclusive), not fixed pitch.
The generator's neutral gutters and cast shadows are excluded from the skill slots.
"""
from pathlib import Path

from PIL import Image

GENERATION_PROMPT = r"""Use case: stylized-concept. Asset type: production pixel-art skill icon atlas for a Minecraft fantasy combat mod. Create ONE landscape image with exactly EIGHT square independent icons in an evenly spaced FOUR COLUMN, TWO ROW grid. Every cell is a full self-contained finished square icon with its own compact 2-pixel-weight dark metal outer frame, thin colored inner line and tiny matching corner ornaments. Consistent top-left illumination and professional high-contrast hand-painted pixel-art shading throughout. Each icon fills its own panel, dark opaque textured background, no transparent area. Four columns and two rows only; visible neutral dark gutters separate every complete frame, margins outside the grid. Design for crisp readability after nearest-neighbor downsampling to 64x64 per icon: bold distinct silhouettes, sharp chunky pixel clusters, restrained shading, minimal micro-detail. This is detailed finished game skill art, not flat vector glyphs or geometric placeholders. No letters, no numbers, no captions, no hotkeys, no text, no watermarks, no portraits, no characters, no whole-sheet frame. Keep effects entirely within their own compact panel frame; never cross panel borders. Compose all cells at similar scale and detail.
Character: Keheng, a precision-fantasy time-difference duelist with a needle rapier and displaced timing layers. Palette: deep ink teal #1C2C35 and almost-black blue background, luminous turquoise #42D9BD, amber gold #F1C76B and fine silver-white #F2F6EA; tiny desaturated purple-gray #8C7CA6 echoes. Match silver/ink-teal metal frames and fine amber corner tick accents across all 8 icons. Do NOT use large clocks, clockfaces, readable numerals, steampunk gears everywhere, generic blue ice, explosions or swords in every cell.
Grid order, left to right then second row:
TOP ROW CELL 1, Echo of Scars: three amber needle-like incisions converge upon a small turquoise split diamond, fine outward echo fragments, bright central triple-mark impact. The three amber marks are the clear main symbol.
TOP ROW CELL 2, Constant Step shield: three offset curved turquoise shield plates wrapped around a small open dark center, their endpoints tipped in gold timing ticks, silver highlights. Clear protective arch silhouette, no human.
TOP ROW CELL 3, Delaying Needle: ONE long silver-white needle projectile diagonally ascending from lower left to upper right, sharp amber star at its pointed tip, two narrow turquoise trailing streams and a small violet displaced echo. Clear slim needle shape, not a broad sword.
TOP ROW CELL 4, Offbeat Step: bright teal diagonal movement slash sweeping from lower left toward upper right, two offset purple-gray afterimages and a small broken gold residual ground arc at the starting point, several short amber step ticks; clear fast movement ribbon, not another needle.
BOTTOM ROW CELL 1, Delayed Realm: grounded circular zone seen in angled top-down perspective, separated gold boundary nodes and three horizontally offset translucent teal planar arcs within, dark visible interior. Clear wide flat territory silhouette.
BOTTOM ROW CELL 2, Traveling Seconds field: two tilted OPEN turquoise crescent panels orbiting a silver needle-shaped center, offset purple contours and gold cadence ticks, asymmetric hovering orbit rather than a closed ground disc. Clear diagonal two-crescent silhouette distinct from adjacent ground zone.
BOTTOM ROW CELL 3, Beyond One Second: an immense elegant teal dome made of separated thin vertical curved time ribbons, gold broken ground perimeter, several silver horizontal ticks suspended inside, dark negative space through the dome. Clear arched canopy silhouette, jewel-like and powerful, not a solid glowing bubble.
BOTTOM ROW CELL 4, Final Moment Pierce: a dramatic forward needle-rapier thrust from lower left toward upper right, concentrated silver-white razor core and two teal angled shearing planes forming a narrow corridor, amber measuring ticks drawn toward its point and subtle narrow violet afterimage. Longer and much stronger piercing energy than the small needle in top row; contained inside border.
Target canvas roughly 2048x1024 landscape. All eight panels square with complete borders. No text anywhere."""

PANELS = (
    ("kehen", (14, 12, 430, 422)),
    ("hengbu", (457, 12, 874, 422)),
    ("chizhen", (901, 12, 1317, 422)),
    ("cuopaibu", (1344, 12, 1761, 422)),
    ("chijie", (14, 449, 430, 859)),
    ("suixing", (457, 449, 874, 859)),
    ("yimiao", (901, 449, 1317, 859)),
    ("zhongke", (1344, 449, 1761, 859)),
)


def main():
    source_dir = Path(__file__).resolve().parent
    project = source_dir.parents[1]
    target_dir = project / "src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/keheng"
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
    print(f"keheng: {len(PANELS)} opaque 64x64 PNGs; nearest-neighbor export")


if __name__ == "__main__":
    main()

