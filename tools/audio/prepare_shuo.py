"""Trim/layer existing CC0 recorded metal/cloth Foley. No synthesis."""
from pathlib import Path
import io,json,zipfile,hashlib,sys,math
sys.path.insert(0,'E:/mod/.tmp/audio-runtime-sf')
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly
ROOT=Path(__file__).resolve().parents[2]
QA=Path('E:/mod/outputs/shuo-20261111');QA.mkdir(parents=True,exist_ok=True)
SOURCE=Path('E:/mod/role_assets/audio/hit-feedback-20261105/source')
OUT=ROOT/'src/main/resources/assets/myriad/sounds/skill/shuo';OUT.mkdir(parents=True,exist_ok=True)
RATE=48000
archives={name:zipfile.ZipFile(SOURCE/('kenney_'+name+'.zip')) for name in ('rpg-audio','impact-sounds')}
def clip(pack,name):
    data,rate=sf.read(io.BytesIO(archives[pack].read('Audio/'+name+'.ogg')),dtype='float32',always_2d=True);data=data.mean(axis=1)
    if rate!=RATE:
        g=math.gcd(rate,RATE);data=resample_poly(data,RATE//g,rate//g)
    ids=np.flatnonzero(np.abs(data)>max(.001,float(np.max(np.abs(data)))*.015));assert len(ids)
    data=data[max(0,int(ids[0])-96):];return data/max(.001,float(np.max(np.abs(data))))
spec={
 'draw':(.26,-6,[('rpg-audio','drawKnife1',1,0)]),
 'sheathe':(.24,-5,[('rpg-audio','metalLatch',1,0),('rpg-audio','drawKnife3',.18,0)]),
 'slash':(.30,-5,[('rpg-audio','knifeSlice',1,0),('rpg-audio','cloth1',.18,.015)]),
 'parry':(.34,-5,[('impact-sounds','impactMetal_light_001',1,0),('rpg-audio','metalClick',.30,.012)]),
 'charge':(.38,-10,[('rpg-audio','drawKnife2',.75,0),('rpg-audio','cloth2',.2,.04)])}
report={}
for name,(duration,db,parts) in spec.items():
    wave=np.zeros(round(duration*RATE),dtype='float32')
    for pack,file,gain,delay in parts:
        source=clip(pack,file);at=round(delay*RATE);n=min(len(source),len(wave)-at);wave[at:at+n]+=source[:n]*gain
    wave-=wave.mean();wave[:144]*=np.linspace(0,1,144);wave[-1680:]*=np.linspace(1,0,1680)**2
    wave*=10**(db/20)/max(.001,float(np.max(np.abs(wave))))
    path=OUT/(name+'.ogg');sf.write(path,wave,RATE,format='OGG',subtype='VORBIS')
    decoded,rate=sf.read(path,always_2d=True);peak=float(np.max(np.abs(decoded)))
    assert decoded.shape[1]==1 and rate==RATE and peak<.9 and abs(decoded[-1,0])<.01
    report[name]={'seconds':len(decoded)/rate,'channels':1,'rate':rate,'peak_db':20*math.log10(peak),'sources':parts,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
preview=np.zeros(RATE*3)
for at,name in [(0,'charge'),(.6,'draw'),(1.0,'slash'),(1.6,'parry'),(2.2,'sheathe')]:
    a,_=sf.read(OUT/(name+'.ogg'));begin=round(at*RATE);preview[begin:begin+len(a)]+=a
sf.write(QA/'shuo_audio_preview.ogg',preview,RATE,format='OGG',subtype='VORBIS')
report['archives']={name:hashlib.sha256((SOURCE/('kenney_'+name+'.zip')).read_bytes()).hexdigest() for name in archives}
(QA/'audio_report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
