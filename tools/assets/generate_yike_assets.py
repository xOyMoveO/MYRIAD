"""Original MYRIAD Yike cuboid meshes, clean painted atlas, icons and synthesized Foley.
Run with python; ffmpeg encodes the authored PCM into Minecraft's Ogg Vorbis.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json, math, random, wave, struct, subprocess, uuid
import soundfile as sf
ROOT=Path(__file__).resolve().parents[2]
ASSET=ROOT/'src/main/resources/assets/myriad'
SOURCE=ROOT/'art_source/yike'
SOURCE.mkdir(parents=True,exist_ok=True)
COLORS={'violet':'#282137','purple':'#644781','coral':'#E46576','redshade':'#A4425E','gold':'#D4A662','lightgold':'#F3C879','ivory':'#FFF3DC','dark':'#171521'}
# Large per-material islands: deterministic hand-painted bevel/engraving, no noisy random surface.
texture=Image.new('RGBA',(256,256));d=ImageDraw.Draw(texture)
for i,(name,color) in enumerate(COLORS.items()):
 x=(i%4)*64;y=(i//4)*96
 d.rectangle((x,y,x+63,y+95),fill=color)
 d.line((x+2,y+2,x+61,y+2,x+61,y+93),fill=COLORS['lightgold'] if name in ('gold','lightgold') else COLORS['purple'],width=2)
 if name in ('violet','purple','coral'):
  for q in (22,66):
   d.line((x+28,y+q-8,x+34,y+q,x+28,y+q+8,x+22,y+q,x+28,y+q-8),fill=COLORS['lightgold'],width=2)
  d.line((x+28,y+30,x+28,y+55),fill=COLORS['gold'],width=1)
 if name=='violet':
  for q in range(8,92,7):d.line((x+1,y+q,x+62,y+q),fill='#382C48',width=2)
(ASSET/'textures/item').mkdir(parents=True,exist_ok=True)
texture.save(ASSET/'textures/item/hong_lie.png');texture.save(SOURCE/'hong_lie_atlas.png')
keys=list(COLORS)
def uv(material):
 i=keys.index(material);x=i%4*4;y=i//4*6;return [x+.08,y+.08,x+3.92,y+5.92]
def cube(name,a,b,mat):
 return {'name':name,'from':a,'to':b,'faces':{side:{'uv':uv(mat),'texture':'#weapon'} for side in ('north','east','south','west','up','down')}}
core=[]
def C(name,a,b,m):core.append(cube(name,a,b,m))
C('exposed_wrapped_grip',[7.58,5.3,7.58],[8.42,10.7,8.42],'violet')
C('lower_shaft',[7.66,2.2,7.66],[8.34,5.3,8.34],'purple')
C('upper_shaft',[7.66,10.7,7.66],[8.34,13.8,8.34],'purple')
C('counterweight',[7.35,2,7.35],[8.65,2.9,8.65],'gold')
C('counterweight_heart',[7.63,1.9,7.63],[8.37,2,8.37],'coral')
for y in (3.1,5.1,10.7,12.3):C('shaft_collar_'+str(y),[7.47,y,7.47],[8.53,y+.28,8.53],'lightgold')
C('focus_crystal_base',[7.74,13.1,7.74],[8.26,20.2,8.26],'ivory')
C('focus_crystal_tip',[7.87,20.2,7.87],[8.13,21.1,8.13],'ivory')
for x,z in ((6.95,8),(8.53,7.07),(8.53,8.93)):
 C('connected_brass_rail',[x-.07,13,z-.07],[x+.07,21.2,z+.07],'gold')
# Hollow rings: open to the viewer, with a true crystal behind the muzzle.
def ring(y,radius=1.35):
 out=[]
 for n in range(8):
  a=n*math.pi/4;rad=radius
  x,z=8+math.sin(a)*rad,8+math.cos(a)*rad
  out.append(cube('octagonal_ring_'+str(n),[x-.30,y-.13,z-.20],[x+.30,y+.13,z+.20],'lightgold'))
 return out
core+=ring(13.2,1.2)
petals=[]
petals.append(cube('hinge',[7.55,7.85,8.76],[8.45,8.55,9.55],'gold'))
petals.append(cube('hinge_axle',[7.42,8.02,9.03],[8.58,8.32,9.32],'lightgold'))
petals.append(cube('coral_petal_root',[7.62,8.35,9.15],[8.38,10.1,9.52],'redshade'))
petals.append(cube('coral_petal_shoulder',[7.33,10.1,9.15],[8.67,13.7,9.55],'coral'))
petals.append(cube('coral_petal_taper',[7.52,13.7,9.15],[8.48,15.5,9.55],'coral'))
petals.append(cube('petal_tip',[7.68,15.5,9.15],[8.32,16.1,9.55],'lightgold'))
petals.append(cube('violet_inner',[7.49,10.25,8.98],[8.51,13.55,9.15],'purple'))
petals.append(cube('outer_gilded_spine',[7.94,9.2,9.55],[8.06,15.45,9.61],'lightgold'))
petals.append(cube('fine_core_line',[7.95,10.9,8.94],[8.05,12.85,8.98],'ivory'))
ringmesh=ring(8,1.4)
DISPLAY={
 'gui':{'rotation':[25,-38,0],'translation':[0,-1,0],'scale':[.66,.66,.66]},
 'ground':{'rotation':[0,0,40],'translation':[0,3,0],'scale':[.45,.45,.45]},
 'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
 'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
 'firstperson_righthand':{'rotation':[0,0,-18],'translation':[5,-2,0],'scale':[.8,.8,.8]},
 'firstperson_lefthand':{'rotation':[0,0,18],'translation':[-5,-2,0],'scale':[.8,.8,.8]}}
def rotated(e,a,hinge=(8,13.5,8),openangle=0):
 # For inventory preview only, 3 radial copies of the same closed petal, exact runtime hinge source.
 import copy
 o=copy.deepcopy(e);o['rotation']={'origin':list(hinge),'axis':'y','angle':a,'rescale':False};return o
# JSON model rotation only supports multiples of22.5/45, so three-petal radial arrangement is authored
# as baked cubes for the inventory mesh. Runtime pieces can use arbitrary 120degree rotations.
full=list(core)+ring(21.2,1.4)
for n in range(3):
 a=n*2*math.pi/3
 for part in petals:
  coords=[]
  for axis in range(3):
   lo,hi=part['from'][axis],part['to'][axis]
   coords.append((lo,hi))
  pts=[]
  for x in coords[0]:
   for y in coords[1]:
    for z in coords[2]:
     dx,dz=x-8,z-8;pts.append((8+dx*math.cos(a)+dz*math.sin(a),y+5.5,8-dx*math.sin(a)+dz*math.cos(a)))
  full.append(cube(part['name']+'_'+str(n),[min(p[i] for p in pts) for i in range(3)],[max(p[i] for p in pts) for i in range(3)],'coral' if 'coral' in part['name'] else 'purple' if 'violet' in part['name'] else 'lightgold'))
for name,elements in [('hong_lie',full),('hong_lie_frame',core),('hong_lie_petal',petals),('hong_lie_ring',ringmesh)]:
 model={'credit':'MYRIAD original Yike mesh; authoring source tools/assets/generate_yike_assets.py','ambientocclusion':False,'gui_light':'front','textures':{'weapon':'myriad:item/hong_lie','particle':'myriad:item/hong_lie'},'elements':elements,'display':DISPLAY}
 (ASSET/'models/item'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n')
 (ASSET/'items'/f'{name}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'myriad:item/'+name}})+'\n')
 # Native Blockbench project mirrors the runtime part and its exact texture/material islands.
 bb={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'resolution':{'width':256,'height':256},'elements':[],'outliner':[],'textures':[{'path':'hong_lie_atlas.png','name':'hong_lie','id':'0','width':256,'height':256,'uv_width':16,'uv_height':16}]}
 for el in elements:
  ident=str(uuid.uuid5(uuid.NAMESPACE_DNS,name+el['name']));be={**el,'uuid':ident,'type':'cube','origin':[8,8,8],'autouv':0}
  be['faces']={side:{'uv':[v*16 for v in face['uv']],'texture':0} for side,face in el['faces'].items()};bb['elements'].append(be);bb['outliner'].append(ident)
 (SOURCE/f'{name}.bbmodel').write_text(json.dumps(bb,indent=2)+'\n')
# Eight geometric, readable skill icons; no unrelated character art is used.
(ASSET/'textures/gui/sprites/skill_icons/generated/yike').mkdir(parents=True,exist_ok=True)
for i,skill in enumerate(json.loads((ROOT/'src/main/resources/data/myriad/role_rework/yike.json').read_text())['skills']):
 im=Image.new('RGBA',(128,128),'#201B2E');dr=ImageDraw.Draw(im)
 dr.rounded_rectangle((4,4,123,123),radius=14,outline=COLORS['gold'],width=3)
 if i in (0,1):
  for j in range(3):
   a=j*math.pi*2/3-math.pi/2;x=64+math.cos(a)*29;y=64+math.sin(a)*29
   dr.polygon([(x,y-17),(x+12,y),(x,y+17),(x-12,y)],fill=COLORS['gold'] if i==0 else COLORS['purple'],outline=COLORS['ivory'])
 elif i in (2,6):
  dr.polygon([(26,95),(56,32),(102,20),(82,64)],fill=COLORS['coral'],outline=COLORS['lightgold'])
  dr.line((35,86,83,35),fill=COLORS['ivory'],width=7 if i==6 else 4)
 elif i==3:
  for j in range(6):
   a=j*math.pi/3;x=64+math.cos(a)*38;y=64+math.sin(a)*38
   dr.line((x,y,x*.85+64*.15,y*.85+64*.15),fill=COLORS['gold'],width=5)
  dr.polygon([(64,39),(84,75),(64,64),(44,75)],fill=COLORS['coral'])
 elif i in (4,5):
  for j in range(3):
   a=j*2*math.pi/3;cx=64+math.cos(a)*23;cy=64+math.sin(a)*23
   dr.polygon([(64,64),(cx+9,cy-21),(cx-9,cy-28)],fill=COLORS['coral'],outline=COLORS['gold'])
  dr.ellipse((54,54,74,74),fill=COLORS['ivory'])
  for j in range(5 if i==4 else 4):dr.rectangle((26+j*17,103,33+j*17,110),fill=COLORS['gold'])
 else:
  dr.line((22,102,102,22),fill=COLORS['purple'],width=22);dr.line((22,102,102,22),fill=COLORS['coral'],width=12);dr.line((22,102,102,22),fill=COLORS['ivory'],width=4)
 im.resize((64,64),Image.Resampling.LANCZOS).save(ASSET/'textures/gui/sprites/skill_icons/generated/yike'/f"{skill['id']}.png")
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
