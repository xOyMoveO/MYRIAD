"""Mechanical whole-cell normalization to the 4x2 motion-reference contract. No repaint or alpha edits."""
from pathlib import Path
from PIL import Image
import argparse,shutil
import numpy as np

p=argparse.ArgumentParser();p.add_argument('source');p.add_argument('destination');p.add_argument('production')
a=p.parse_args();root=Path(a.production);root.mkdir(parents=True,exist_ok=True)
im=Image.open(a.source).convert('RGBA');shutil.copy2(a.source,root/'generated_hit_source.png')
assert abs(im.width/im.height-2)<.001
frames=root/'frames';frames.mkdir(exist_ok=True);atlas=Image.new('RGBA',(1024,512));play=[]
for i in range(8):
    crop=im.crop((round(i%4*im.width/4),round(i//4*im.height/2),round((i%4+1)*im.width/4),round((i//4+1)*im.height/2)))
    alpha=np.array(crop.getchannel('A'));assert np.max(np.concatenate([alpha[0],alpha[-1],alpha[:,0],alpha[:,-1]]))<=2
    # One identical scale/canvas for every phase, no independent crop or centroid alignment.
    crop=crop.resize((208,208),Image.Resampling.NEAREST)
    frame=Image.new('RGBA',(256,256));frame.alpha_composite(crop,(24,24));frame.save(frames/f'frame_{i:02d}.png')
    atlas.alpha_composite(frame,(i%4*256,i//4*256))
    panel=Image.new('RGBA',(768,256))
    for j,bg in enumerate([(72,100,44,255),(15,22,31,255),(215,219,222,255)]):
        back=Image.new('RGBA',(256,256),bg);back.alpha_composite(frame);panel.alpha_composite(back,(j*256,0))
    play.append(panel.convert('RGB'))
atlas.save(a.destination)
play[0].save(root/'hit_alpha_preview.gif',save_all=True,append_images=play[1:],duration=50,loop=0,disposal=2)
print('PASS: eight complete reference phases, fixed 4x2/256px metadata, native alpha, 24px gutters, nearest-neighbour downsample only, no invented phases/paint')
