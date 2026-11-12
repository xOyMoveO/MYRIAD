"""Local material-sample sound edits and deterministic slicing of inspected icon art."""
from pathlib import Path
import subprocess, array, math, sys
from PIL import Image
from mix_foley import source, ROOT

def sounds():
    out=ROOT/'src/main/resources/assets/myriad/sounds/mingzhu'
    out.mkdir(parents=True,exist_ok=True)
    recipes={
        'orb_cast':('block/amethyst/step{}.ogg','entity/player/attack/sweep{}.ogg',.28,.10),
        'orb_hit':('block/amethyst_cluster/break{}.ogg','random/glass{}.ogg',.22,.10),
        'mirror_place':('block/amethyst/place{}.ogg','block/amethyst/resonate{}.ogg',.45,.12),
        'mirror_turn':('block/amethyst/step{}.ogg','random/glass{}.ogg',.26,.10),
        'dash':('block/amethyst/step{}.ogg','item/trident/throw{}.ogg',.35,.17),
        'beam_start':('block/amethyst_cluster/break{}.ogg','block/amethyst/resonate{}.ogg',.48,.18),
        'beam_body':('block/amethyst/shimmer.ogg','entity/player/attack/sweep{}.ogg',1.0,.05),
        'beam_end':('block/amethyst/step{}.ogg','block/amethyst/resonate{}.ogg',.30,.08),
    }
    for name,(a,b,duration,gain) in recipes.items():
        for variant in (1,2):
            dest=out/f'{name}_{variant}.ogg'
            filters=(f'[0:a]aresample=48000,atrim=0:{duration},asetpts=PTS-STARTPTS,volume=.5[a];'
                     f'[1:a]aresample=48000,atrim=0:{duration},asetpts=PTS-STARTPTS,volume={gain},adelay=25:all=1[b];'
                     f'[a][b]amix=inputs=2:normalize=0,atrim=0:{duration},'
                     f'highpass=f=500,lowpass=f=9500,equalizer=f=3200:t=q:w=1.2:g=2,afade=t=in:d=0.004,'
                     f'afade=t=out:st={duration-.09}:d=0.09,volume=2.3,alimiter=limit=0.72:level=false[out]')
            subprocess.run(['ffmpeg','-nostdin','-filter_complex_threads','1','-threads','1','-v','error','-y','-i',str(source(a,variant)),'-i',str(source(b,variant)),
                            '-filter_complex',filters,'-map','[out]','-ac','1','-ar','48000','-c:a','libvorbis','-q:a','5',str(dest)],check=True,timeout=30)
            raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(dest),'-f','f32le','-ac','1','-ar','48000','-'])
            pcm=array.array('f');pcm.frombytes(raw)
            peak=max(map(abs,pcm));rms=math.sqrt(sum(x*x for x in pcm)/len(pcm))
            assert peak<.99 and rms>.001,(name,peak,rms)
            print(f'{dest.name}: mono 48kHz {len(pcm)/48000:.2f}s peak={peak:.3f} RMS={rms:.3f}')

def icons(path):
    im=Image.open(path).convert('RGB')
    assert im.size==(2172,724)
    # Each complete outer border measured independently on the inspected source.
    bounds=[(13,120,423,528),(447,120,858,528),(881,120,1291,528),(1315,120,1726,528),(1751,120,2160,528)]
    names=['passive','liuguangguanzhu','juyaozhejing','yaobuhuihuan','yixiantianming']
    out=ROOT/'src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/mingzhu'
    out.mkdir(parents=True,exist_ok=True)
    preview=Image.new('RGB',(320,64))
    for i,(name,box) in enumerate(zip(names,bounds)):
        icon=im.crop(box).resize((64,64),Image.Resampling.NEAREST)
        icon.save(out/f'{name}.png');preview.paste(icon,(i*64,0))
    preview.save(ROOT/'build/mingzhu_icons_preview.png')
    print('Five opaque 64x64 icons exported; source not altered.')

if __name__=='__main__':
    if sys.argv[1]=='sounds': sounds()
    elif sys.argv[1]=='icons': icons(sys.argv[2])
    elif sys.argv[1]=='recast':
        im=Image.open(sys.argv[2]).convert('RGB').resize((64,64),Image.Resampling.NEAREST)
        im.save(ROOT/'src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/mingzhu/juyaozhejing_2.png')
