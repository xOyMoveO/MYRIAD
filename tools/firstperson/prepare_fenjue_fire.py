"""Mechanical atlas import: exact cells, no per-frame crop, no upscaling or repainting."""
from pathlib import Path
from PIL import Image
import argparse

p=argparse.ArgumentParser();p.add_argument('source');p.add_argument('output');p.add_argument('frames')
a=p.parse_args();source=Image.open(a.source).convert('RGBA')
assert source.width==source.height and source.width%2==0
n=source.width//2;cell=n+29
atlas=Image.new('RGBA',(cell*2,cell*2))
frames=Path(a.frames);frames.mkdir(parents=True,exist_ok=True)
for i in range(4):
    # Keep identical cell coordinates/pivot; add safe atlas gutters, including faint stray embers.
    frame=Image.new('RGBA',(cell,cell));crop=source.crop((i%2*n,i//2*n,(i%2+1)*n,(i//2+1)*n))
    frame.alpha_composite(crop,(14,14));frame.save(frames/f'fire_{i}.png')
    atlas.alpha_composite(frame,(i%2*cell,i//2*cell))
atlas.save(a.output)
print(f'Native {n}px cells, padded {cell}px, 2x2 RGBA atlas {atlas.size}; no upscale')
