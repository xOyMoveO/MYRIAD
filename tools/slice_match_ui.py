"""Export the lobby and draft sprites from the transparent source sheets. Requires Pillow."""
import json
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art_source/match_ui'
SPRITES = ROOT / 'src/main/resources/assets/myriad/textures/gui/sprites'

# Each rectangle is an isolated cell in its source atlas, in source pixels.
COMPONENTS = [
    ('lobby/panel', (0, 0, 340, 410), (32, 32), 6),
    ('draft/panel', (340, 0, 683, 410), (32, 32), 6),
    ('draft/card', (683, 0, 1024, 410), (32, 32), 6),
    ('match/button', (0, 410, 340, 650), (40, 20), 5),
    ('match/button_hover', (340, 410, 683, 650), (40, 20), 5),
    ('match/button_disabled', (683, 410, 1024, 650), (40, 20), 5),
    ('match/guardian', (0, 650, 340, 1024), (24, 24), None),
    ('match/destruction', (340, 650, 683, 1024), (24, 24), None),
    ('match/lock', (683, 650, 1024, 1024), (16, 16), None),
]
BORDERS = [
    ('match/guardian_frame', (0, 0, 512, 500), (32, 32), 6),
    ('match/destruction_frame', (512, 0, 1024, 500), (32, 32), 6),
    ('match/guardian_light', (55, 604, 455, 634), (24, 2), 0),
    ('match/destruction_light', (571, 604, 968, 634), (24, 2), 0),
    ('match/guardian_corner', (0, 710, 512, 1024), (12, 12), None),
    ('match/destruction_corner', (512, 710, 1024, 1024), (12, 12), None),
]


def export(source, entries):
    with Image.open(SOURCE / source) as sheet:
        for name, cell, size, border in entries:
            image = sheet.crop(cell)
            # Ignore translucent antialiasing tails when fitting the painted silhouette to a cell.
            opaque = image.getchannel('A').point(lambda alpha: 255 if alpha >= 100 else 0)
            image = image.crop(opaque.getbbox())
            if border is None:
                image = ImageOps.pad(image, size, method=Image.Resampling.LANCZOS, color=(0, 0, 0, 0))
            else:
                image = image.resize(size, Image.Resampling.LANCZOS)
            output = SPRITES / (name + '.png')
            output.parent.mkdir(parents=True, exist_ok=True)
            image.save(output, optimize=True)
            if border:
                metadata = {'gui': {'scaling': {'type': 'nine_slice', 'width': size[0], 'height': size[1],
                                                'border': border, 'stretch_inner': True}}}
                output.with_suffix('.png.mcmeta').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    export('components.png', COMPONENTS)
    export('faction_borders.png', BORDERS)
