"""Pack the built-in generated replacement sheet without upscaling its native pixel detail."""
from pathlib import Path
import hashlib,json
import numpy as np
from scipy.ndimage import label
from PIL import Image,ImageDraw

ROOT=Path('E:/mod/role_assets/fenjue/20261108/crisp_slash')
OUT=Path('src/main/resources/assets/myriad/textures/particle/vfx/fenjue_crisp_slash.png')
SIZE=512

def main():
    source=Image.open(ROOT/'generated_sheet.png').convert('RGBA')
    frames=[];metrics=[]
    for i in range(8):
        x,y=i%4,i//4
        box=(round(x*source.width/4),round(y*source.height/2),round((x+1)*source.width/4),round((y+1)*source.height/2))
        frame=source.crop(box);a=np.array(frame)
        # Hard pixel-art alpha is intentional. Remove detached generated micro-noise, not real flame fragments.
        labels,n=label(a[:,:,3]>=192);counts=np.bincount(labels.ravel());counts[0]=0
        keep=np.zeros(labels.shape,dtype=bool)
        for k in range(1,n+1):
            if counts[k]<32:continue
            yy,xx=np.where(labels==k)
            assert xx.min()>1 and yy.min()>1 and xx.max()<frame.width-2 and yy.max()<frame.height-2,(i,'substantial contour crosses cell')
            keep|=labels==k
        assert keep.sum()>1000,(i,'missing phase')
        a[:,:,3]=np.where(keep,255,0);a[~keep]=0
        canvas=Image.new('RGBA',(SIZE,SIZE))
        assert frame.width<=SIZE and frame.height<=SIZE
        canvas.paste(Image.fromarray(a),((SIZE-frame.width)//2,(SIZE-frame.height)//2))
        bounds=canvas.getchannel('A').getbbox()
        assert min(bounds[0],bounds[1],SIZE-bounds[2],SIZE-bounds[3])>=48,(i,bounds)
        frames.append(canvas)
        metrics.append(dict(frame=i,source_rectangle=box,source_cell=frame.size,bounds=bounds,opaque_pixels=int(keep.sum())))
    # One palette for all eight phases, no per-frame palette flicker and no spatial blur or sharpening convolution.
    board=Image.new('RGB',(SIZE*4,SIZE*2))
    for i,im in enumerate(frames):board.paste(im.convert('RGB'),(i%4*SIZE,i//4*SIZE))
    palette=board.quantize(colors=32,method=Image.Quantize.MEDIANCUT)
    atlas=Image.new('RGBA',board.size);preview=[]
    folder=ROOT/'frames';folder.mkdir(exist_ok=True)
    contact=Image.new('RGB',(SIZE*4,(SIZE+24)*2),(44,51,36));draw=ImageDraw.Draw(contact)
    for i,frame in enumerate(frames):
        result=frame.convert('RGB').quantize(palette=palette,dither=Image.Dither.NONE).convert('RGBA');result.putalpha(frame.getchannel('A'))
        result.save(folder/f'frame_{i:02}.png');atlas.paste(result,(i%4*SIZE,i//4*SIZE))
        metrics[i]['sha256']=hashlib.sha256(result.tobytes()).hexdigest()
        panel=Image.new('RGBA',(SIZE*3,SIZE))
        for j,bg in enumerate(((110,145,80),(15,21,28),(235,235,235))):
            base=Image.new('RGBA',(SIZE,SIZE),(*bg,255));base.alpha_composite(result);panel.paste(base,(j*SIZE,0))
        preview.append(panel.convert('RGB'))
        contact.paste(panel.crop((0,0,SIZE,SIZE)).convert('RGB'),(i%4*SIZE,i//4*(SIZE+24)+24))
        draw.text((i%4*SIZE+8,i//4*(SIZE+24)+5),f'phase {i}',fill='white')
    assert len({m['sha256'] for m in metrics})==8
    OUT.parent.mkdir(parents=True,exist_ok=True);atlas.save(OUT)
    contact.save(ROOT/'contact_sheet.png')
    preview[0].save(ROOT/'crisp_slash_preview.gif',save_all=True,append_images=preview[1:],duration=80,loop=0,disposal=2)
    report=dict(source_size=source.size,source_grid=[4,2],canvas=SIZE,atlas=atlas.size,upscaled=False,alpha_levels=[0,255],palette_colors=32,frames=metrics)
    (ROOT/'frame_checks.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
