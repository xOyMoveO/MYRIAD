"""Normalize and pack the accepted generated 8x6 sheet; no synthesized animation poses."""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw

ART=Path('E:/mod/role_assets/yingxiao/20261106/gray_vfx')
OUT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/myriad/textures/particle/vfx'
NAMES=('blade','slash','wake','smoke','seal','impact')
PALETTE=(24,32,40,48,60,74,90,110,134,160,194,230)

def main():
    source=Image.open(ART/'source_sheet.png').convert('RGBA')
    assert source.width%8==0 and source.height%6==0 and source.width//8==source.height//6
    cell=source.width//8
    # Uniform palette/near-zero-alpha normalization; keep all meaningful partial opacity.
    pixels=[]
    for r,g,b,a in source.getdata():
        if a<8:pixels.append((0,0,0,0));continue
        value=round(.2126*r+.7152*g+.0722*b)
        level=min(PALETTE,key=lambda v:abs(v-value))
        pixels.append((level,level,level,a))
    source.putdata(pixels)
    source.save(ART/'normalized_sheet.png')
    OUT.mkdir(parents=True,exist_ok=True)
    metrics={}
    board=Image.new('RGB',(8*160,6*160),(54,76,49))
    for row,name in enumerate(NAMES):
        atlas=Image.new('RGBA',(512,256))
        directory=ART/'frames'/name;directory.mkdir(parents=True,exist_ok=True)
        frames=[];records=[]
        for i in range(8):
            raw=source.crop((i*cell,row*cell,(i+1)*cell,(row+1)*cell))
            # One common canvas/scale for all frames; no independent crop or recentering.
            small=raw.resize((104,104),Image.Resampling.NEAREST)
            frame=Image.new('RGBA',(128,128));frame.paste(small,(12,12))
            bounds=frame.getchannel('A').getbbox();assert bounds,(name,i)
            assert min(bounds[0],bounds[1],128-bounds[2],128-bounds[3])>=12
            records.append(dict(frame=i,bounds=bounds,sha256=hashlib.sha256(frame.tobytes()).hexdigest()))
            frame.save(directory/f'frame_{i:02d}.png');frames.append(frame)
            atlas.paste(frame,(i%4*128,i//4*128))
            bg=Image.new('RGBA',(160,160),(54,76,49,255));bg.alpha_composite(frame,(16,16))
            ImageDraw.Draw(bg).text((5,3),f'{name} {i+1}',fill=(230,230,230))
            board.paste(bg.convert('RGB'),(i*160,row*160))
        assert len({r['sha256'] for r in records})==8,(name,'Duplicate generated frames')
        atlas.save(OUT/f'night_gray_{name}.png')
        # Honest frame playback on three backgrounds, not a capture of the game.
        play=[]
        for frame in frames:
            panel=Image.new('RGB',(384,128))
            for x,color in enumerate(((54,76,49),(18,23,28),(182,188,178))):
                bg=Image.new('RGBA',(128,128),(*color,255));bg.alpha_composite(frame)
                panel.paste(bg.convert('RGB'),(x*128,0))
            play.append(panel.resize((768,256),Image.Resampling.NEAREST))
        play[0].save(ART/f'{name}_preview.gif',save_all=True,append_images=play[1:],duration=90 if name!='seal' else 150,loop=0,disposal=2)
        metrics[name]=records
    board.save(ART/'contact_sheet.png')
    (ART/'frame_checks.json').write_text(json.dumps(metrics,indent=2),encoding='utf-8')
    print('PASS: six RGBA atlases, 48 unique frames, 128px cells, 12px safe gutters, 12-level neutral palette; generated frames only')

if __name__=='__main__':main()
