"""Regenerate the current Hengke weapon and legacy audio; requires Pillow and ffmpeg."""
from pathlib import Path
import json, wave, struct, subprocess, tempfile, random, sys
ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/'src/main/resources/assets/myriad'
SOURCE=ROOT/'art_source/keheng'
SOURCE.mkdir(parents=True,exist_ok=True)
def save(p,obj):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
# The dedicated spec pipeline owns the editable weapon, UV atlas and true model previews.
subprocess.run([sys.executable, str(ROOT/'tools/assets/generate_keheng_weapon.py')], check=True)
# Current dedicated skill icons are delivered by artwork generation/slicing in art_source/keheng.
# Restrained original mono synthesized Foley. No endless looping sound; subsequent field ticks stop with state.
sounds=json.loads((ASSET/'sounds.json').read_text());rng=random.Random(11)
for idx,cue in enumerate(['cast','hit','field','shield','echo','pulse','pierce','end']):
 duration=[.16,.12,.38,.26,.3,.12,.35,.3][idx];freq=[950,1600,180,640,740,1100,330,260][idx];rate=48000
 frames=[]
 for n in range(int(duration*rate)):
  t=n/rate;env=min(1,t/.004)*math.exp(-t/(duration*.21));tone=math.sin(2*math.pi*freq*t)+.23*math.sin(2*math.pi*freq*2.71*t)
  tick=(rng.random()*2-1)*math.exp(-t/.012)*.23;frames.append(struct.pack('<h',int(max(-1,min(1,(tone*.35+tick)*env))*17000)))
 with tempfile.TemporaryDirectory() as tmp:
  wav=Path(tmp)/'cue.wav'
  with wave.open(str(wav),'wb') as out:out.setnchannels(1);out.setsampwidth(2);out.setframerate(rate);out.writeframes(b''.join(frames))
  dst=ASSET/f'sounds/keheng/{cue}.ogg';dst.parent.mkdir(parents=True,exist_ok=True)
  subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(wav),'-c:a','vorbis','-strict','experimental','-ac','2','-q:a','4',str(dst)],check=True)
 sounds['keheng_'+cue]={'subtitle':'subtitle.myriad.keheng.'+cue,'sounds':[{'name':'myriad:keheng/'+cue,'attenuation_distance':16}]}
save(ASSET/'sounds.json',sounds)
