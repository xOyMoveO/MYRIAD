"""Deterministic grid normalization/packing only; never synthesizes animation frames.

Each generated sheet is one eight-frame sequence (row-major, not two animations).
One common centred scale is used for all eight frames, preserving growth and pivot.
"""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image, ImageDraw

NAMES = ('slash', 'stream', 'seal', 'impact', 'filament', 'motes')
STEPS = (100, 75, 150, 75, 125, 175)


def prepare(source, output, art):
    output.mkdir(parents=True, exist_ok=True)
    art.mkdir(parents=True, exist_ok=True)
    summary = {}
    sequences = {}
    for name, duration in zip(NAMES, STEPS):
        src = Image.open(source / f'{name}_selected.png').convert('RGBA')
        # Generated PNGs contain invisible alpha 1..7 dust far from every motif.
        # Remove only that numerical fringe uniformly, not a binary alpha mask:
        # every partial alpha value 8..254 and all opaque artwork is retained.
        src.putalpha(src.getchannel('A').point(lambda a: 0 if a < 8 else a))
        # The generator returns 1774x887; normalize the whole sheet, never its
        # individual silhouettes, before slicing the known fixed grid.
        assert abs(src.width / src.height - 2) < .001
        cell = (src.width + 3) // 4
        grid = src.resize((cell * 4, cell * 2), Image.Resampling.NEAREST)
        grid.save(art / f'{name}_grid.png')
        frames = [grid.crop((i % 4 * cell, i // 4 * cell,
                            (i % 4 + 1) * cell, (i // 4 + 1) * cell)) for i in range(8)]
        bounds = [f.getchannel('A').getbbox() for f in frames]
        assert all(bounds), f'Empty source frame: {name}'
        assert all(b[0] > 0 and b[1] > 0 and b[2] < cell and b[3] < cell for b in bounds), \
            f'Source sheet crosses/clips a cell: {name} {bounds}'
        extent = max(max(abs(v - cell / 2) for v in b) for b in bounds)
        # Common pivot (centre of cell), maximum extent 56 native pixels.
        size = int(cell * 56 / extent)
        native = []
        for f in frames:
            result = Image.new('RGBA', (128, 128))
            result.paste(f.resize((size, size), Image.Resampling.NEAREST),
                         ((128 - size) // 2, (128 - size) // 2))
            native.append(result.resize((256, 256), Image.Resampling.NEAREST))
        atlas = Image.new('RGBA', (1024, 512))
        directory = art / 'frames' / name
        directory.mkdir(parents=True, exist_ok=True)
        metrics = []
        for i, frame in enumerate(native):
            atlas.paste(frame, (i % 4 * 256, i // 4 * 256))
            frame.save(directory / f'frame_{i:02d}.png')
            b = frame.getchannel('A').getbbox()
            assert min(b[0], b[1], 256-b[2], 256-b[3]) >= 12, (name, i, b)
            metrics.append({'frame': i, 'bounds': b, 'sha256': hashlib.sha256(frame.tobytes()).hexdigest()})
        assert len({m['sha256'] for m in metrics}) == 8, f'Duplicated painted frames: {name}'
        atlas.save(output / f'lingyuan_anim_{name}.png')
        preview = []
        for f in native:
            bg = Image.new('RGBA', (256, 256), '#172b31')
            bg.alpha_composite(f)
            preview.append(bg.convert('RGB'))
        preview[0].save(art / f'{name}.gif', save_all=True, append_images=preview[1:],
                        duration=duration, loop=0 if name != 'impact' else 1, disposal=2)
        summary[name] = {'sourceSize': list(src.size), 'grid': [4, 2], 'cell': [256, 256],
                         'pivot': [.5, .5], 'nativeSize': [128, 128], 'durationMs': duration,
                         'playback': 'one-shot; mapped to effect life' if name == 'impact' else 'loop',
                         'commonScaleSize': size, 'frames': metrics}
        sequences[name] = native
        print(name, '8 unique frames; 12px+ alpha-zero gutters; common pivot/scale', flush=True)
    # Review alpha against four backgrounds and see frame-to-frame silhouette variation.
    board = Image.new('RGB', (8 * 160, 6 * 180), '#172b31')
    draw = ImageDraw.Draw(board)
    backgrounds = ('#172b31', '#ffffff', '#9ca68b', '#6b305a')
    for row, name in enumerate(NAMES):
        draw.text((5, row * 180 + 4), name, fill='white')
        for i, f in enumerate(sequences[name]):
            bg = Image.new('RGBA', (160, 160), backgrounds[i % 4])
            bg.alpha_composite(f.resize((160, 160), Image.Resampling.NEAREST))
            board.paste(bg.convert('RGB'), (i * 160, row * 180 + 20))
    board.save(art / 'contact-sheet.png')
    (art / 'frames.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path)
    p.add_argument('output', type=Path)
    p.add_argument('art', type=Path)
    a = p.parse_args()
    prepare(a.source, a.output, a.art)
