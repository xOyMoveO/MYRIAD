"""Lossless pack of native HD frames: no resize, quantization, crop or temporal recentering."""
from pathlib import Path
from PIL import Image,ImageDraw
import hashlib,json
from draw_thunder_pixel_frames import SIZE,PADDING,PALETTE,ART

OUT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/myriad/textures/particle/vfx'
NAMES=('slash','bolt','impact','seal','corona','pillar')

def main():
    src=Image.open(ART/'source_sheet.png').convert('RGBA')
    assert src.size==(SIZE*8,SIZE*6),src.size
    tile=SIZE+24;board=Image.new('RGB',(8*tile,6*tile),(46,72,47));metrics={}
    OUT.mkdir(parents=True,exist_ok=True)
    for row,name in enumerate(NAMES):
        atlas=Image.new('RGBA',(SIZE*4,SIZE*2));frames=[];checks=[]
        directory=ART/'frames'/name;directory.mkdir(parents=True,exist_ok=True)
        for i in range(8):
            # Source pixels are already final-size and palette-constrained. Preserve them exactly.
            frame=src.crop((i*SIZE,row*SIZE,(i+1)*SIZE,(row+1)*SIZE))
            bounds=frame.getchannel('A').getbbox();assert bounds,(name,i,'empty frame')
            assert min(bounds[0],bounds[1],SIZE-bounds[2],SIZE-bounds[3])>=PADDING,(name,i,bounds)
            pixels=list(frame.getdata());count=sum(p[3]>0 for p in pixels)
            assert 0<count<(SIZE-2*PADDING)**2,(name,i,'opaque cell background')
            colors={p[:3] for p in pixels if p[3]>0};assert colors<=set(PALETTE),(name,colors)
            checks.append(dict(frame=i,bounds=bounds,visible=count,sha256=hashlib.sha256(frame.tobytes()).hexdigest()))
            frame.save(directory/f'frame_{i:02d}.png');frames.append(frame);atlas.paste(frame,(i%4*SIZE,i//4*SIZE))
            bg=Image.new('RGBA',(tile,tile),(46,72,47,255));bg.alpha_composite(frame,(12,18))
            ImageDraw.Draw(bg).text((4,3),f'{name} {i+1} / native {SIZE}px',fill=(230,240,255));board.paste(bg.convert('RGB'),(i*tile,row*tile))
        assert len({c['sha256'] for c in checks})==8,(name,'duplicate frames')
        atlas.save(OUT/f'thunder_pixel_{name}.png');metrics[name]=checks
        play=[]
        for frame in frames:
            panel=Image.new('RGB',(SIZE*4,SIZE))
            for x,color in enumerate(((46,72,47),(14,23,40),(205,210,211),(110,30,85))):
                bg=Image.new('RGBA',(SIZE,SIZE),(*color,255));bg.alpha_composite(frame);panel.paste(bg.convert('RGB'),(x*SIZE,0))
            play.append(panel)
        play[0].save(ART/f'{name}_preview.gif',save_all=True,append_images=play[1:],duration=100 if name in ('seal','corona') else 70,loop=0,disposal=2)
    board.save(ART/'contact_sheet.png')
    (ART/'frame_checks.json').write_text(json.dumps(metrics,indent=2),encoding='utf-8')
    print(f'PASS: 48 unique native {SIZE}px frames, six {SIZE*4}x{SIZE*2} RGBA atlases, {PADDING}px gutters; lossless source-to-atlas pixels')

if __name__=='__main__':main()
