"""Import the approved-theme built-in generated 8x6 sheet, clean disconnected alpha debris,
and pack stable, shared-canvas frames. Never reconstruct/crop a main effect across cell edges.
"""
from pathlib import Path
import json, hashlib
import numpy as np
from scipy.ndimage import label, binary_dilation
from PIL import Image,ImageDraw

ROOT=Path('E:/mod/role_assets/fenjue/20261107/layered_vfx')
OUT=Path('src/main/resources/assets/myriad/textures/particle/vfx')
NAMES=('slash','flame','burst','ring','stream','embers')
SIZE=256; PAD=24

def main():
    src=Image.open(ROOT/'generated_sheet_v2.png').convert('RGBA')
    assert src.width%8==0 and src.height%6==0,src.size
    cw,ch=src.width//8,src.height//6
    ratio=min((SIZE-2*PAD)/cw,(SIZE-2*PAD)/ch)
    target=(round(cw*ratio),round(ch*ratio))
    frames=[]
    for row,name in enumerate(NAMES):
        strip=[]
        for col in range(8):
            im=src.crop((col*cw,row*ch,(col+1)*cw,(row+1)*ch))
            a=np.array(im)
            labels,n=label(a[:,:,3]>16)
            counts=np.bincount(labels.ravel());counts[0]=0
            largest=int(np.argmax(counts));assert counts[largest]>100,(name,col)
            ys,xs=np.where(labels==largest)
            assert xs.min()>0 and ys.min()>0 and xs.max()<cw-1 and ys.max()<ch-1,(name,col,'main contour crosses source cell')
            # Remove detached generated scatter near cell boundaries, not parts of the main body.
            near=binary_dilation(labels==largest,iterations=7)
            keep=labels==largest
            for k in range(1,n+1):
                if k!=largest and counts[k]>=5 and np.any(near&(labels==k)):
                    ky,kx=np.where(labels==k)
                    if kx.min()>1 and ky.min()>1 and kx.max()<cw-2 and ky.max()<ch-2:keep|=labels==k
            a[~keep]=0
            strip.append(Image.fromarray(a))
        frames.append(strip)
    # One palette for the entire family, not an independently flickering palette per frame.
    palette_source=Image.new('RGB',(cw*8,ch*6),(130,30,12))
    for row in range(6):
        for col in range(8):palette_source.paste(frames[row][col].convert('RGB'),(col*cw,row*ch))
    palette=palette_source.quantize(colors=64,method=Image.Quantize.MEDIANCUT)
    OUT.mkdir(parents=True,exist_ok=True)
    board=Image.new('RGB',(8*SIZE,6*(SIZE+24)),(38,48,32));metrics={}
    for row,name in enumerate(NAMES):
        atlas=Image.new('RGBA',(SIZE*4,SIZE*2));play=[];metrics[name]=[]
        folder=ROOT/'frames'/name;folder.mkdir(parents=True,exist_ok=True)
        for col in range(8):
            frame=frames[row][col]
            rgb=frame.convert('RGB').quantize(palette=palette,dither=Image.Dither.NONE).convert('RGBA');rgb.putalpha(frame.getchannel('A'))
            rgb=rgb.resize(target,Image.Resampling.NEAREST)
            canvas=Image.new('RGBA',(SIZE,SIZE));canvas.paste(rgb,((SIZE-target[0])//2,(SIZE-target[1])//2))
            bounds=canvas.getchannel('A').getbbox();assert bounds and min(bounds[0],bounds[1],SIZE-bounds[2],SIZE-bounds[3])>=PAD
            atlas.paste(canvas,(col%4*SIZE,col//4*SIZE));canvas.save(folder/f'frame_{col:02}.png')
            metrics[name].append({'frame':col,'bounds':bounds,'sha256':hashlib.sha256(canvas.tobytes()).hexdigest()})
            panel=Image.new('RGBA',(SIZE*3,SIZE))
            for j,bg in enumerate(((111,145,88),(16,20,29),(205,210,215))):
                base=Image.new('RGBA',(SIZE,SIZE),(*bg,255));base.alpha_composite(canvas);panel.paste(base,(j*SIZE,0))
            play.append(panel.convert('RGB'))
            base=Image.new('RGBA',(SIZE,SIZE),(111,145,88,255));base.alpha_composite(canvas);board.paste(base.convert('RGB'),(col*SIZE,row*(SIZE+24)+24))
        assert len({m['sha256'] for m in metrics[name]})==8,name
        atlas.save(OUT/f'fenjue_pixel_{name}.png')
        play[0].save(ROOT/f'{name}_preview.gif',save_all=True,append_images=play[1:],duration=100,loop=0,disposal=2)
        ImageDraw.Draw(board).text((8,row*(SIZE+24)+5),name,fill='white')
    board.save(ROOT/'contact_sheet.png')
    (ROOT/'frame_checks.json').write_text(json.dumps({'sourceSize':src.size,'sourceCell':[cw,ch],'canvas':SIZE,'padding':PAD,'frames':metrics},indent=2),encoding='utf-8')
    print('PASS: 48 distinct frames; six 1024x512 RGBA atlases; stable source-cell placement; shared 64-color palette; 24px gutters. Source cell:',cw,ch)

if __name__=='__main__':main()
