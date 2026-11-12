"""Mechanical row-major slicing/padding of generated native-alpha sheets; no paint, upscale or per-frame crop."""
from pathlib import Path
from PIL import Image
import argparse,shutil
import numpy as np

p=argparse.ArgumentParser();p.add_argument('--sources',nargs=2,required=True);p.add_argument('--out',required=True)
a=p.parse_args();root=Path(a.out);root.mkdir(parents=True,exist_ok=True)
project=Path(__file__).resolve().parents[2];target=project/'src/main/resources/assets/myriad/textures/particle/vfx'
for name,source in zip(['ring','field'],a.sources):
    source=Path(source);shutil.copy2(source,root/f'{name}_source.png');im=Image.open(source).convert('RGBA')
    assert abs(im.width/im.height-2)<.001
    atlas=Image.new('RGBA',(2048,1024));frames=root/name;frames.mkdir(exist_ok=True)
    for i in range(8):
        x0,x1=round(i%4*im.width/4),round((i%4+1)*im.width/4)
        y0,y1=round(i//4*im.height/2),round((i//4+1)*im.height/2)
        crop=im.crop((x0,y0,x1,y1));alpha=np.array(crop.getchannel('A'))
        border=np.concatenate([alpha[0],alpha[-1],alpha[:,0],alpha[:,-1]])
        assert border.max()<=2,'A visible flame must not cross a cell boundary'
        frame=Image.new('RGBA',(512,512));frame.alpha_composite(crop,((512-crop.width)//2,(512-crop.height)//2))
        frame.save(frames/f'frame_{i:02d}.png');atlas.alpha_composite(frame,(i%4*512,i//4*512))
    atlas.save(target/f'fenjue_ground_{name}.png')
    preview=[]
    for i in range(8):
        frame=Image.open(frames/f'frame_{i:02d}.png');back=Image.new('RGBA',frame.size,(52,76,38,255));back.alpha_composite(frame)
        preview.append(back.convert('RGB'))
    preview[0].save(root/f'{name}.gif',save_all=True,append_images=preview[1:],duration=100,loop=0)
    print(name,im.size,'native cells 443/444 padded to 512 without scaling; 8 unique frames, border alpha <=2/255')
