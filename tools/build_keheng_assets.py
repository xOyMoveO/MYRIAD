"""Original Keheng assets, editable source. Run with python; Pillow and ffmpeg required for textures and audio."""
from pathlib import Path
import json, math, uuid, wave, struct, subprocess, tempfile, random
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/'src/main/resources/assets/myriad'
SOURCE=ROOT/'art_source/keheng'
SOURCE.mkdir(parents=True,exist_ok=True)
def save(p,obj):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
# Curated pixel-painted metal clusters, no external source artwork.
im=Image.new('RGBA',(64,64));draw=ImageDraw.Draw(im)
colors=['#F2F6EA','#A8B9B9','#1C2C35','#42D9BD','#F1C76B','#79623B','#23494D','#8C7CA6']
for i,color in enumerate(colors):
 draw.rectangle((i*8,0,i*8+7,63),fill=color)
 for y in range(2,64,8):draw.line((i*8,y,i*8+6,y),fill=tuple(min(255,c+12) for c in im.getpixel((i*8,y))[:3])+(255,))
(ASSET/'textures/item').mkdir(parents=True,exist_ok=True);im.save(ASSET/'textures/item/hengke.png');im.save(SOURCE/'hengke_texture.png')
elements=[]
def cube(name,a,b,material,rot=None):
 uv=[material*8,0,material*8+7,16]
 x={'name':name,'from':a,'to':b,'faces':{f:{'uv':uv,'texture':'#0'} for f in ['north','south','east','west','up','down']}}
 if rot:x['rotation']=rot
 elements.append(x)
cube('silver_needle',[7.66,11,7.84],[8.34,28.5,8.16],0)
cube('dark_teal_spine',[7.82,10.4,7.72],[8.13,27.8,7.85],2)
cube('turquoise_filament',[7.94,12,7.69],[8.03,25,7.73],3)
cube('tapered_tip',[7.88,28.5,7.91],[8.12,30,8.09],0)
cube('leather_grip',[7.4,3,7.45],[8.6,8.4,8.55],6)
for y in [3.5,4.5,5.5,6.5,7.5]:cube('grip_binding',[7.35,y,7.4],[8.65,y+.16,8.6],2)
cube('pommel',[7.28,2.2,7.3],[8.72,3.15,8.7],4)
cube('grip_collar',[7.2,8,7.18],[8.8,8.55,8.82],4)
cube('crossguard_connection',[6.4,8.6,7.5],[10.7,9.1,8.5],2)
for i,a in enumerate(range(-70,171,20)):
 rad=math.radians(a);x=9.2+2.2*math.cos(rad);y=9.45+2.2*math.sin(rad)
 cube('open_guard_'+str(i),[x-.38,y-.2,7.58],[x+.38,y+.2,8.42],4,{'origin':[x,y,8],'axis':'z','angle':[-45,-22.5,0,22.5,45][i%5]})
cube('escapement_hub',[9.2,9.25,7.7],[9.95,10,8.3],5)
for i in range(8):
 a=i*math.pi/4;x=9.58+.7*math.cos(a);y=9.62+.7*math.sin(a)
 cube('escapement_tooth_'+str(i),[x-.19,y-.19,7.8],[x+.19,y+.19,8.2],4)
cube('time_crystal',[6.1,9,7.55],[6.8,10.15,8.45],3,{'origin':[6.45,9.58,8],'axis':'z','angle':22.5})
model={'credit':'Original MYRIAD Keheng / build_keheng_assets.py','texture_size':[64,64],'textures':{'0':'myriad:item/hengke','particle':'myriad:item/hengke'},'elements':elements,'display':{
 'thirdperson_righthand':{'rotation':[0,90,-12],'translation':[0,0,0],'scale':[.62,.62,.62]},
 'thirdperson_lefthand':{'rotation':[0,-90,12],'translation':[0,0,0],'scale':[.62,.62,.62]},
 'firstperson_righthand':{'rotation':[0,80,-32],'translation':[1.8,-1.8,-2.5],'scale':[.54,.54,.54]},
 'firstperson_lefthand':{'rotation':[0,-80,32],'translation':[1.8,-1.8,-2.5],'scale':[.54,.54,.54]},
 'gui':{'rotation':[0,0,-36],'translation':[-1,-3,0],'scale':[.48,.48,.48]},
 'ground':{'rotation':[0,0,90],'translation':[0,2,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,0,0],'translation':[0,-3,0],'scale':[.5,.5,.5]}}}
save(ASSET/'models/item/hengke.json',model)
save(ASSET/'items/hengke.json',{'model':{'type':'minecraft:model','model':'myriad:item/hengke'}})
bb=[]
for el in elements:
 e=dict(el);e['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,'myriad:hengke/'+e['name']));e['type']='cube';e['autouv']=0;e['box_uv']=False
 e['faces']={f:{**v,'texture':0} for f,v in el['faces'].items()}
 if 'rotation' in e:
  rot=e.pop('rotation');e['origin']=rot['origin'];e['rotation']=[rot['angle'] if x==rot['axis'] else 0 for x in ['x','y','z']]
 bb.append(e)
save(SOURCE/'hengke.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':'恒刻 Hengke','resolution':{'width':64,'height':64},'elements':bb,'outliner':[e['uuid'] for e in bb],'textures':[{'path':'hengke_texture.png','name':'hengke_texture.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'myriad:hengke/texture'))}]})
# Consistent orthographics rendered directly from the same cube coordinates, hence no shape drift.
svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="760" viewBox="0 0 1200 760"><rect width="1200" height="760" fill="#162229"/>']
for ox,title,axes in [(210,'FRONT',(0,1)),(580,'SIDE',(2,1)),(920,'TOP',(0,2))]:
 svg.append(f'<text x="{ox-70}" y="50" fill="#F1C76B" font-family="sans-serif" font-size="22">{title}</text>')
 for e in elements:
  a,b=e['from'],e['to'];i,j=axes;x=ox+(a[i]-8)*17;y=650-(b[j]-2)*20 if j==1 else 380-(b[j]-8)*25
  w=(b[i]-a[i])*17;h=(b[j]-a[j])*(20 if j==1 else 25)
  mat=int(e['faces']['north']['uv'][0]//8)
  svg.append(f'<rect x="{x:.2f}" y="{y:.2f}" width="{w:.2f}" height="{h:.2f}" fill="{colors[mat]}" stroke="#739797" stroke-width=".3"/>')
svg.append('<text x="65" y="725" fill="#AAC5C8" font-family="sans-serif" font-size="17">HENGKE / silver needle · open eccentric guard · amber escapement · teal crystal</text></svg>')
(SOURCE/'hengke_orthographic.svg').write_text('\n'.join(svg))
# Individual original icon line drawings, all categories have different silhouettes.
ids=['kehen','hengbu','chizhen','cuopaibu','chijie','suixing','yimiao','zhongke']
for idx,name in enumerate(ids):
 icon=Image.new('RGBA',(128,128),(18,34,41,255));d=ImageDraw.Draw(icon)
 d.rounded_rectangle((3,3,124,124),radius=15,outline='#F1C76B',width=3)
 if idx==0:
  for i in range(3):d.line((40+i*19,40,40+i*19,86),fill='#F1C76B',width=5)
 elif idx==1:
  for x in range(3):d.arc((23+x*6,23+x*6,107-x*6,107-x*6),210,320,fill='#42D9BD',width=5)
 elif idx==2:d.polygon([(30,98),(75,30),(91,16),(84,40),(36,104)],fill='#F2F6EA');d.line((29,100,64,70),fill='#42D9BD',width=4)
 elif idx==3:
  for x in range(3):d.line((24+x*22,82,42+x*22,48),fill=['#8C7CA6','#42D9BD','#F2F6EA'][x],width=6)
 elif idx in [4,5]:
  d.ellipse((23,45,105,98),outline='#42D9BD',width=5)
  for i in range(4):d.line((32+i*22,45,32+i*22,33),fill='#F1C76B',width=4)
  if idx==5:d.line((47,71,78,71,70,62),fill='#F2F6EA',width=5)
 elif idx==6:
  d.arc((22,23,106,111),180,360,fill='#42D9BD',width=5);d.line((22,68,106,68),fill='#F1C76B',width=3)
  for x in [42,64,86]:d.line((x,82,x,94),fill='#F2F6EA',width=4)
 else:
  d.line((19,94,106,32),fill='#42D9BD',width=7);d.line((26,99,113,37),fill='#8C7CA6',width=3);d.line((19,94,106,32),fill='#F2F6EA',width=2)
 dest=ASSET/f'textures/gui/sprites/skill_icons/generated/keheng/{name}.png';dest.parent.mkdir(parents=True,exist_ok=True);icon.save(dest)
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
