"""Edit local Minecraft material sounds into short, non-synthetic combat layers.
Sources remain Mojang game assets; outputs are local remixes, not new recordings.
"""
import json, subprocess, pathlib, math, array
ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = pathlib.Path('D:/Minecraft/.minecraft/assets')
INDEX = json.loads((ASSETS/'indexes/32.json').read_text())['objects']
OUT = ROOT/'src/main/resources/assets/myriad/sounds/foley'
RECIPES = {
 'blade': ('entity/player/attack/sweep{}.ogg', 'block/chain/step{}.ogg', .48, .25),
 'wind': ('entity/player/attack/sweep{}.ogg', 'item/trident/throw{}.ogg', .60, .3),
 'fire': ('fire/ignite.ogg', 'fire/fire.ogg', .72, .45),
 'ice': ('random/glass{}.ogg', 'dig/stone{}.ogg', .55, .20),
 'water': ('liquid/splash{}.ogg', 'entity/player/attack/sweep{}.ogg', .70, .22),
 'wood': ('dig/wood{}.ogg', 'block/bamboo/place{}.ogg', .45, .30),
 'stone': ('dig/stone{}.ogg', 'block/chain/break{}.ogg', .55, .25),
 'shot': ('item/crossbow/shoot{}.ogg', 'random/explode{}.ogg', .48, .32),
 'thunder': ('ambient/weather/thunder{}.ogg', 'dig/stone{}.ogg', .90, .15),
}
def source(template, i):
    name='minecraft/sounds/'+template.format(i)
    if name not in INDEX and i==1: name=name.replace('1.ogg','.ogg')
    if name not in INDEX: name='minecraft/sounds/'+template.format(1)
    entry=INDEX[name]; path=ASSETS/'objects'/entry['hash'][:2]/entry['hash']
    assert path.is_file(), path
    return path

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for kind,(a,b,duration,gain) in RECIPES.items():
        for i in (1,2):
            target=OUT/f'{kind}_{i}.ogg'
            filters=(f'[0:a]atrim=0:{duration},asetpts=PTS-STARTPTS,volume=0.75[a];'
                f'[1:a]atrim=0:{duration},asetpts=PTS-STARTPTS,volume={gain},adelay=35:all=1[b];'
                f'[a][b]amix=inputs=2:normalize=0,highpass=f=65,lowpass=f=10000,'
                f'afade=t=in:d=0.008,afade=t=out:st={duration-.12}:d=0.12,'
                f'alimiter=limit=0.84:level=false,atrim=0:{duration}[out]')
            subprocess.run(['ffmpeg','-v','error','-y','-i',str(source(a,i)),'-i',str(source(b,i)),
                '-filter_complex',filters,'-map','[out]','-ac','1','-ar','48000','-c:a','libvorbis','-q:a','5',str(target)],check=True)
            decoded=subprocess.check_output(['ffmpeg','-v','error','-i',str(target),'-f','f32le','-ac','1','-ar','48000','-'])
            samples=array.array('f');samples.frombytes(decoded)
            peak=max(map(abs,samples));rms=math.sqrt(sum(v*v for v in samples)/len(samples))
            assert .002<rms and peak<.999,(target,rms,peak)
            print(f'{target.name}: mono 48000 Hz {len(samples)/48000:.2f}s peak={peak:.3f} rms={rms:.3f}')
if __name__=='__main__': main()
