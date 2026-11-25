"""Rebuild the 26.3 training court structure. Requires nbtlib."""
from io import BytesIO
from pathlib import Path
import gzip

from nbtlib import Byte, Compound, File, Int, List, String


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'src/main/resources/data/myriad/structure/training_ground.nbt'
ORIGIN = (-36, 64, -8)
SIZE = (73, 13, 60)
blocks = {}


def block(x, y, z, material, properties=(), nbt=None):
    pos = tuple(value - low for value, low in zip((x, y, z), ORIGIN))
    assert all(0 <= value < length for value, length in zip(pos, SIZE)), (x, y, z)
    blocks[pos] = ((material, tuple(properties)), nbt)


def floor(x1, x2, z1, z2, material, y=64):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            block(x, y, z, material)


def post(x, z, top, material, bottom=65):
    for y in range(bottom, top + 1):
        block(x, y, z, material)


def line(key, *args):
    text = Compound({'translate': String('training.myriad.map.' + key)})
    if args:
        text['with'] = List[Compound]([Compound({'text': String(str(arg))}) for arg in args])
    return text


def sign(x, z, rotation, *lines):
    empty = Compound({'text': String('')})
    rows = [empty, *lines]
    rows.extend([empty] * (4 - len(rows)))
    face = Compound({'messages': List[Compound](rows), 'color': String('white'), 'has_glowing_text': Byte(1)})
    data = Compound({'id': String('minecraft:sign'), 'front_text': face, 'back_text': face, 'is_waxed': Byte(1)})
    block(x, 65, z, 'warped_sign', [('rotation', str(rotation)), ('waterlogged', 'false')], data)


def beacon(x, z):
    floor(x - 1, x + 1, z - 1, z + 1, 'polished_deepslate', 65)
    post(x, z, 68, 'chiseled_tuff', 66)
    block(x, 69, z, 'sea_lantern')
    block(x, 70, z, 'waxed_cut_copper_slab')


def arcade(x, z, width):
    for px in (x - width, x + width):
        floor(px - 1, px + 1, z - 1, z + 1, 'chiseled_tuff', 65)
        post(px, z, 72, 'polished_deepslate', 66)
        block(px, 69, z, 'sea_lantern')
        floor(px - 1, px + 1, z - 1, z + 1, 'waxed_exposed_cut_copper', 72)
    floor(x - width - 2, x + width + 2, z - 1, z + 1, 'deepslate_tiles', 73)
    floor(x - width - 2, x + width + 2, z, z, 'waxed_exposed_cut_copper', 74)
    floor(x - width + 1, x + width - 1, z, z, 'dark_prismarine', 75)
    for px in (x - width - 2, x + width + 2):
        block(px, 74, z - 1, 'dark_prismarine')
        block(px, 74, z + 1, 'dark_prismarine')


def build_court():
    floor(-36, 36, -8, 51, 'deepslate_tiles')
    floor(-33, 33, -5, 48, 'polished_tuff')
    for x in (-35, 35):
        floor(x, x, -7, 50, 'deepslate_bricks', 65)
        floor(x, x, -7, 50, 'polished_deepslate_wall', 66)
        for z in (-6, 7, 21, 35, 49):
            beacon(x, z)
    for z in (-7, 50):
        floor(-34, 34, z, z, 'deepslate_bricks', 65)
        floor(-34, 34, z, z, 'polished_deepslate_wall', 66)
    for x in (-10, 10):
        floor(x - 1, x + 1, 1, 46, 'deepslate_tiles')
        floor(x, x, 1, 46, 'waxed_oxidized_cut_copper')
    floor(-31, 31, -3, 4, 'polished_deepslate')
    floor(-31, 31, 1, 1, 'waxed_exposed_cut_copper')
    floor(-7, 7, -4, 4, 'dark_prismarine')
    for x in (-6, 6):
        for z in (-3, 3):
            block(x, 64, z, 'sea_lantern')
    arcade(0, -4, 6)
    arcade(-21, 5, 6)
    arcade(0, 5, 6)
    arcade(21, 5, 6)
    sign(2, -1, 0, line('ground'), line('menu'))
    for x, title in ((-21, 'melee'), (0, 'range'), (21, 'mobility')):
        sign(x + 4, 4, 8, line(title))
        arcade(x, 46, 6)


def melee():
    # Three flush sparring rings leave a clear approach for melee dashes and player-created dummies.
    for x, z, radius in ((-21, 15, 6), (-25, 32, 4), (-16, 35, 4)):
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                r2 = dx * dx + dz * dz
                if r2 <= radius * radius:
                    inside = r2 <= (radius - 1) ** 2
                    block(x + dx, 64, z + dz, 'polished_andesite' if inside else 'waxed_exposed_cut_copper')
        block(x, 64, z, 'chiseled_copper')
        for dz in (-radius, radius):
            block(x, 64, z + dz, 'ochre_froglight')
    for x in (-31, -13):
        beacon(x, 24)


def firing_range():
    floor(-5, 5, 9, 39, 'polished_deepslate')
    for x in (-5, 5):
        floor(x, x, 9, 39, 'waxed_exposed_cut_copper')
    floor(-6, 6, 9, 9, 'calcite')
    for meters in range(5, 31, 5):
        z = 9 + meters
        for x in (-5, 5):
            floor(x - 1, x + 1, z, z, 'calcite')
            block(x, 64, z, 'sea_lantern')
        sign(7, z, 4, line('distance', meters))
    for y in range(65, 71):
        floor(-7, 7, 40, 41, 'deepslate_bricks', y)
    for x in (-4, 0, 4):
        for y in (66, 67, 68):
            floor(x - 1, x + 1, 39, 39, 'stripped_spruce_log', y)
        block(x, 67, 39, 'target')
    floor(-8, 8, 40, 41, 'waxed_exposed_cut_copper_slab', 71)


def mobility():
    # Beginners can walk alongside both courses and rejoin at any numbered tier.
    floor(13, 19, 9, 41, 'deepslate_bricks')
    floor(22, 29, 9, 41, 'deepslate_tiles')
    for index, (cx, z, height) in enumerate(((15, 12, 1), (17, 16, 2), (15, 20, 2),
                                            (17, 24, 3), (15, 28, 3), (17, 32, 2), (15, 36, 1))):
        for y in range(65, 65 + height):
            floor(cx - 1, cx + 1, z, z + 1, 'polished_tuff', y)
        block(cx, 64 + height, z, 'ochre_froglight' if index % 2 == 0 else 'waxed_exposed_cut_copper')
    for z, gap in ((14, 23), (22, 27), (30, 23), (38, 27)):
        for x in range(22, 30):
            if abs(x - gap) >= 2:
                post(x, z, 68, 'deepslate_bricks')
                block(x, 69, z, 'waxed_cut_copper_slab')
        block(gap, 64, z, 'verdant_froglight')
    for x in (13, 30):
        beacon(x, 44)


def save():
    palette = {}
    entries = []
    for (x, y, z), (state, nbt) in sorted(blocks.items(), key=lambda e: (e[0][1], e[0][2], e[0][0])):
        index = palette.setdefault(state, len(palette))
        entry = Compound({'pos': List[Int]([x, y, z]), 'state': Int(index)})
        if nbt is not None:
            entry['nbt'] = nbt
        entries.append(entry)
    states = []
    for name, props in palette:
        state = Compound({'id': String('minecraft:' + name)})
        if props:
            state['properties'] = Compound({k: String(v) for k, v in props})
        states.append(state)
    data = File({'DataVersion': Int(5023), 'size': List[Int](SIZE), 'palette': List[Compound](states),
                 'blocks': List[Compound](entries), 'entities': List[Compound]()})
    encoded = BytesIO()
    data.write(encoded)
    OUTPUT.write_bytes(gzip.compress(encoded.getvalue(), mtime=0))


if __name__ == '__main__':
    build_court()
    melee()
    firing_range()
    mobility()
    save()
