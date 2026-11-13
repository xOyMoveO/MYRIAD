"""Original MYRIAD Yike asset entry: reviewed weapon specs and existing Foley.
Run with python; requires Pillow and soundfile. For weapon-only regeneration use
python tools/assets/generate_yike_weapon.py. This entry retains the historical
sound synthesis, and therefore rewrites audio when run.
"""
from pathlib import Path
import json, math, random, wave, struct
import soundfile as sf
from generate_yike_weapon import generate

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'src/main/resources/assets/myriad'
SOURCE = ROOT / 'art_source/yike'
SOURCE.mkdir(parents=True, exist_ok=True)
generate()
# Skill icon sources now live in art_source/yike; this legacy entry no longer
# paints placeholder icons or overwrites the separately reviewed icon assets.
# Authored synthesis, not sampled game/library audio. Quiet tails and three short shot variations.
(ASSET/'sounds/yike').mkdir(parents=True,exist_ok=True)
cues={'basic':(.13,480),'shot_0':(.24,135),'shot_1':(.235,144),'shot_2':(.25,126),'impact':(.27,680),'warning':(.25,480),'mark':(.25,920),'barrier':(.36,350),'deploy':(.45,190),'close':(.20,170),'heavy_charge':(.60,90),'heavy_flight':(.50,240),'heavy_impact':(.70,70),'beam_charge':(.60,150),'beam_loop':(.40,100),'beam_tick':(.10,510),'beam_end':(.18,180)}
sounds=json.loads((ASSET/'sounds.json').read_text())
for idx,(cue,(duration,base)) in enumerate(cues.items()):
 rate=44100;n=int(rate*duration);rng=random.Random(1200+idx);pcm=[];prev=0
 for k in range(n):
  t=k/rate;u=t/duration;noise=rng.uniform(-1,1);prev=.84*prev+.16*noise
  if cue=='beam_loop':
   env=.55;v=math.sin(2*math.pi*100*t)*.18+math.sin(2*math.pi*300*t)*.055+math.sin(2*math.pi*700*t)*.025
  else:
   env=(1-math.exp(-t*160))*math.exp(-u*(5 if 'shot' in cue or cue=='basic' else 3.5))*(1-u)**.6
   sweep=base*(1+.35*math.exp(-t*25));v=math.sin(2*math.pi*sweep*t)*.35+math.sin(2*math.pi*base*2.51*t)*.11+prev*(.9 if 'shot' in cue or 'impact' in cue else .35)
   if cue in ('deploy','close'):v+=math.exp(-((u-.62)/.055)**2)*noise*.55
  pcm.append(struct.pack('<h',int(max(-.95,min(.95,v*env*.65))*32767)))
 wav=SOURCE/f'{cue}.wav'
 with wave.open(str(wav),'wb') as f:f.setparams((1,2,rate,n,'NONE','not compressed'));f.writeframes(b''.join(pcm))
 data, sample_rate = sf.read(wav)
 sf.write(ASSET/'sounds/yike'/f'{cue}.ogg', data, sample_rate, format='OGG', subtype='VORBIS')
 # Keep authored script and final Vorbis, no redundant PCM in repository.
 wav.unlink()
 sounds['yike_'+cue]={'sounds':[{'name':'myriad:yike/'+cue,'stream':False}],'subtitle':'subtitle.myriad.yike_'+cue}
(ASSET/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n')
