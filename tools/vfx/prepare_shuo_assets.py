"""Mechanical native imagegen import only: whole atlas resize, exact panel crops, no repainting."""
from pathlib import Path
from PIL import Image
import json,shutil,hashlib

ROOT=Path(__file__).resolve().parents[2]
SRC=Path('C:/Users/Administrator/.codex/generated_images/01a02d4a-659e-76a0-ae97-dd121a6cf00e')
ART=Path('E:/mod/role_assets/iaito/20261111/art');ART.mkdir(parents=True,exist_ok=True)
RES=ROOT/'src/main/resources/assets/myriad'
ICON=RES/'textures/gui/sprites/skill_icons/generated/shuo';ICON.mkdir(parents=True,exist_ok=True)
sources={'slash':'exec-05dcb7ac-dd03-4104-a72f-b90627db2c81.png','primary':'exec-b6393e83-c278-4188-95d3-9851b3f04b5c.png','alternate':'exec-7d3cd558-1318-4f50-ab09-528bf043e8ab.png'}
for name,file in sources.items():shutil.copy2(SRC/file,ART/(name+'_source.png'))
im=Image.open(ART/'slash_source.png');assert im.mode=='RGBA' and im.size==(1774,887)
# Exact 2:1 whole-sheet normalization. Do not shift/recenter/crop individual cells.
im=im.resize((1024,512),Image.Resampling.NEAREST)
# Retired art stays outside runtime resources; do not restore old skill VFX.
im.save(ART/'retired_slash_atlas.png')
frames=[]
for i in range(8):
    f=im.crop((i%4*256,i//4*256,i%4*256+256,i//4*256+256));lo,hi=f.getchannel('A').getextrema();assert lo==0 and hi>32
    visible=f.getchannel('A').point(lambda a:255 if a>=16 else 0).getbbox()
    assert visible and visible[0]>5 and visible[2]<251 and visible[1]>5 and visible[3]<251
    frames.append(f);f.save(ART/f'slash_frame_{i+1}.png')
assert len({hashlib.sha256(f.tobytes()).hexdigest() for f in frames})==8
# Individually measured full metal border bounds in native 2172x724 images, not fixed-pitch assumptions.
bounds={
 'primary':[(14,130,424,537),(448,130,856,537),(881,130,1291,537),(1316,130,1724,537),(1749,130,2158,537)],
 'alternate':[(12,141,425,551),(446,141,860,551),(878,141,1294,551),(1310,141,1729,551),(1746,141,2160,551)]}
panels={}
for sheet,boxes in bounds.items():
    source=Image.open(ART/(sheet+'_source.png')).convert('RGB')
    panels[sheet]=[source.crop(b).resize((64,64),Image.Resampling.NEAREST) for b in boxes]
mapping={'passive':('primary',0),'shuo_nadao':('primary',1),'shuo_qiaofan':('primary',2),'shuo_yishan':('primary',3),'shuo_wusheng':('primary',4),
 'shuo_jianqie':('alternate',0),'shuo_duanliu':('alternate',3),'shuo_canyue':('alternate',4),
 'shuo_qiaofan_2':('alternate',0),'shuo_canyue_2':('alternate',3)}
for name,(sheet,i) in mapping.items():panels[sheet][i].save(ICON/(name+'.png'))
contact=Image.new('RGB',(5*128,2*128),(15,20,30))
for i,(name,_) in enumerate(mapping.items()):contact.paste(Image.open(ICON/(name+'.png')).resize((128,128),Image.Resampling.NEAREST),(i%5*128,i//5*128))
contact.save(ART/'icons_contact.png')
preview=[]
for f in frames:
    bg=Image.new('RGBA',f.size,(24,38,47,255));bg.alpha_composite(f);preview.append(bg.convert('RGB'))
preview[0].save(ART/'slash_preview.gif',save_all=True,append_images=preview[1:],duration=50,loop=0)
(ART/'import_report.json').write_text(json.dumps({'generator':'built-in image_gen','sources':sources,'bounds':bounds,'atlas':{'native':[1774,887],'output':[1024,512],'grid':[4,2],'frames':8,'alpha':'preserved','transform':'whole-sheet nearest-neighbor resize only'},'icon_mapping':mapping},ensure_ascii=False,indent=2),encoding='utf-8')
print('Imported eight alpha frames and ten 64px full-frame skill panels:',ART)
