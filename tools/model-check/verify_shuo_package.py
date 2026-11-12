"""Strict resource preservation / model / atlas / audio / branch packaging verification."""
from pathlib import Path
import hashlib,io,json,zipfile
from PIL import Image
QA=Path('E:/mod/outputs/shuo-20261111')
ROOT=Path(__file__).resolve().parents[2]
old=Path('E:/mod/outputs/万象_MYRIAD_更名与多题材角色定位_20261110.jar')
new=ROOT/'build/libs/myriad-0.1.0.jar'
report={}
def obj(z,name):return json.loads(z.read(name))
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
    aa=set(a.namelist());bb=set(b.namelist());added=sorted(bb-aa);removed=sorted(aa-bb)
    changed=sorted(n for n in aa&bb if a.read(n)!=b.read(n))
    assert not removed,removed
    for n in aa&bb:
        if n.startswith('com/myriad/character/skills/') or n=='com/myriad/combat/CombatAnimationPolicy.class':
            assert a.read(n)==b.read(n),'Existing hero skill or paused global animation policy changed: '+n
    resource_changes={'assets/myriad/lang/zh_cn.json','assets/myriad/lang/en_us.json','assets/myriad/sounds.json','assets/myriad/particles/ability_mesh.json'}
    for n in changed:
        if n.startswith(('assets/','data/','META-INF/')):assert n in resource_changes,'Unrelated resource changed: '+n
    for n in added:
        assert not n.endswith(('.bbmodel','.py','.ps1')) and '.bb_session' not in n,'Source/debug data leaked into JAR'
    for lang in ('zh_cn','en_us'):
        name='assets/myriad/lang/'+lang+'.json';before=obj(a,name);after=obj(b,name)
        assert all(after.get(k)==v for k,v in before.items()),'Existing translations changed'
    before=obj(a,'assets/myriad/sounds.json');after=obj(b,'assets/myriad/sounds.json')
    assert all(after.get(k)==v for k,v in before.items()),'Existing sounds changed'
    atlas=obj(b,'assets/myriad/particles/ability_mesh.json')['textures']
    assert atlas[:-1]==obj(a,'assets/myriad/particles/ability_mesh.json')['textures'] and atlas[-1]=='myriad:vfx/shuo_slash'
    skills=obj(b,'data/myriad/role_rework/shuo.json')['skills'];assert len(skills)==8
    assert [s['index'] for s in skills]==list(range(8)) and [s['maxRank'] for s in skills]==[5,5,5,5,7,7,3,3]
    models={}
    for name in ('shuo_sword','shuo_scabbard'):
        prefix='assets/myriad/';wrapper=obj(b,prefix+'items/'+name+'.json')
        assert wrapper['model']['model']=='myriad:item/'+name
        model=obj(b,prefix+'models/item/'+name+'.json');count=0
        assert model['textures']['weapon']=='myriad:item/shuo_crescent'
        for e in model['elements']:
            assert all(-16<=x<=32 for x in e['from']+e['to'])
            for face in e['faces'].values():
                assert all(0<=v<=16 for v in face['uv']);count+=1
        models[name]={'cuboids':len(model['elements']),'visible_faces':count}
    im=Image.open(io.BytesIO(b.read('assets/myriad/textures/particle/vfx/shuo_slash.png')))
    assert im.mode=='RGBA' and im.size==(1024,512)
    frames=[]
    for i in range(8):
        f=im.crop((i%4*256,i//4*256,i%4*256+256,i//4*256+256));frames.append(hashlib.sha256(f.tobytes()).hexdigest())
        bounds=f.getchannel('A').point(lambda a:255 if a>=16 else 0).getbbox()
        assert bounds and min(bounds[:2])>5 and max(bounds[2:])<251
    assert len(set(frames))==8
    audio={}
    for name in ('draw','sheathe','slash','parry','charge'):
        entry=after['shuo_'+name]['sounds'][0];assert entry['attenuation_distance']==20
        raw=b.read('assets/myriad/sounds/'+entry['name'].split(':')[1]+'.ogg')
        marker=raw.index(b'\x01vorbis');assert raw[:4]==b'OggS' and raw[marker+11]==1 and int.from_bytes(raw[marker+12:marker+16],'little')==48000
        audio[name]=hashlib.sha256(raw).hexdigest()
    report={'old':str(old),'new':str(new),'sha256':hashlib.sha256(new.read_bytes()).hexdigest(),'bytes':new.stat().st_size,'added':added,'changed':changed,'removed':removed,
            'preserved_old_resources':True,'models':models,'eight_independent_frames':True,'five_mono_48k_vorbis':audio,'branches':{s['id']:s['maxRank'] for s in skills}}
(QA/'package_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ('added','changed')},ensure_ascii=False,indent=2))
print('Added:',len(report['added']),'changed:',len(report['changed']),'removed:',len(report['removed']))
print('Changed entries:\n'+'\n'.join(report['changed']))
