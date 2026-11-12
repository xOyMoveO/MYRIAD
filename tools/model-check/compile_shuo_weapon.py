"""Compile the native Blockbench-exported source into two vanilla 26.2 items.

Preserves the approved source and all UVs. No generated .bbmodel is hand-written.
Hidden interior cube faces are removed without changing exposed faces or topology.
"""
from pathlib import Path
import base64,copy,hashlib,importlib.util,json,sys
ROOT=Path(__file__).resolve().parents[2]
ART=Path('E:/mod/role_assets/iaito/20261111')
SOURCE=ART/'crescent_iaito_pair_blockbench_v2.bbmodel'
ASSETS=ROOT/'src/main/resources/assets/myriad'
sys.path.insert(0,str(ROOT/'tools/vfx'))
from compile_lingyuan_model import subtract,face_corners
from held_display_math import blade_edge_forward

# Reproducible fit adjustment of the approved native source, not a replacement
# design. Keep UVs; extend the hilt and blade separately about their joint.
WIDTH=1.8
DEPTH=2.4
BLADE_LENGTH=1.15
BLADE_DEPTH=4.0
HILT_LENGTH=1.4
HILT_WIDTH=2.1
HILT_DEPTH=2.4
HILT_PARTS=('sword_grip_', 'sword_pommel', 'sword_fuchi', 'sword_menuki_')

def fitted_vertex(v,kind,offset,center,name):
    x,y,z=v
    if kind=='sword':
        y=8+(y-8)*HILT_LENGTH if y<=18 else 8+10*HILT_LENGTH+(y-18)*BLADE_LENGTH
    else:
        y=center+(y-center)*BLADE_LENGTH
    width=HILT_WIDTH if name.startswith(HILT_PARTS) else WIDTH
    depth=HILT_DEPTH if name.startswith(HILT_PARTS) else BLADE_DEPTH if name.startswith('sword_blade_') else DEPTH
    return [round(8+(x-offset)*.25*width,6),
            round(8+(y-center)*.25,6),round(8+z*.25*depth,6)]

def hidden(part,face,parts):
    axes={'north':(2,-1),'south':(2,1),'east':(0,1),'west':(0,-1),'up':(1,1),'down':(1,-1)}
    axis,sign=axes[face];others=[i for i in range(3) if i!=axis]
    lo,hi=part['from'],part['to'];plane=hi[axis] if sign>0 else lo[axis]
    remaining=[[lo[others[0]],lo[others[1]],hi[others[0]],hi[others[1]]]]
    for p in parts:
        if p is part:continue
        a,b=p['from'],p['to']
        blocks=a[axis]<=plane+1e-7 and b[axis]>plane+1e-7 if sign>0 else a[axis]<plane-1e-7 and b[axis]>=plane-1e-7
        if not blocks:continue
        r=[a[others[0]],a[others[1]],b[others[0]],b[others[1]]]
        remaining=[s for rect in remaining for s in subtract(rect,r)]
        if not remaining:return True
    return False

def main():
    data=json.loads(SOURCE.read_text(encoding='utf-8'));aw=data['resolution']['width'];ah=data['resolution']['height']
    assert aw==ah==1024
    image=base64.b64decode(data['textures'][0]['source'].split(',')[1])
    tex=ASSETS/'textures/item/shuo_crescent.png';tex.parent.mkdir(parents=True,exist_ok=True);tex.write_bytes(image)
    stats={}
    for kind,offset,center in [('sword',0,8),('scabbard',14,17)]:
        parts=[p for p in data['elements'] if p['name'].startswith('sword_' if kind=='sword' else 'sheath_')]
        parts=[dict(p,**{key:fitted_vertex(p[key],kind,offset,center,p['name']) for key in ('from','to')}) for p in parts]
        elements=[];culled=0
        for part in parts:
            lo,hi=part['from'],part['to']
            assert all(-16<=v<=32 for v in lo+hi)
            faces={}
            for f,face in part['faces'].items():
                if hidden(part,f,parts):culled+=1;continue
                faces[f]={'uv':[round(v/64,7) for v in face['uv']],'texture':'#weapon'}
            if faces:elements.append(dict(name=part['name'],**{'from':lo,'to':hi},faces=faces))
        display=blade_edge_forward({
            'thirdperson_righthand':{'rotation':[-10,0,0],'translation':[0,-1.327,1.439],'scale':[1,1,1]},
            'thirdperson_lefthand':{'rotation':[-10,0,0],'translation':[0,-1.327,1.439],'scale':[1,1,1]},
            'firstperson_righthand':{'rotation':[0,-25,20],'translation':[1,-.85,-.35],'scale':[.78,.78,.78]},
            'firstperson_lefthand':{'rotation':[0,25,-20],'translation':[1,-.85,-.35],'scale':[.78,.78,.78]},
            'gui':{'rotation':[0,0,-35],'translation':[-1,-4,0],'scale':[.72,.72,.72]},
            'ground':{'rotation':[0,0,85],'translation':[0,1,0],'scale':[.5,.5,.5]},
            'fixed':{'rotation':[0,180,-35],'translation':[0,-4,0],'scale':[.72,.72,.72]}})
        model={'credit':'MYRIAD original approved crescent iaito / Blockbench MCP refinement',
               'myriad_source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'ambientocclusion':False,'gui_light':'front',
               'myriad_fit':{'width':WIDTH,'depth':DEPTH,'blade_and_sheath_length':BLADE_LENGTH,
                          'blade_depth':BLADE_DEPTH if kind=='sword' else None,
                          'hilt_length':HILT_LENGTH if kind=='sword' else 1,
                          'hilt_width':HILT_WIDTH if kind=='sword' else None,'hilt_depth':HILT_DEPTH if kind=='sword' else None},
               'textures':{'weapon':'myriad:item/shuo_crescent','particle':'myriad:item/shuo_crescent'},'elements':elements,'display':display}
        target=ASSETS/f'models/item/shuo_{kind}.json';target.write_text(json.dumps(model,indent=2),encoding='utf-8')
        item=ASSETS/f'items/shuo_{kind}.json';item.write_text(json.dumps({'model':{'type':'minecraft:model','model':f'myriad:item/shuo_{kind}'}}),encoding='utf-8')
        stats[kind]={'source_parts':len(parts),'visible_elements':len(elements),'removed_hidden_faces':culled,'faces':sum(len(p['faces']) for p in elements)}
        if kind=='sword':
            grip=next(p for p in elements if p['name']=='sword_grip_core')
            size=[b-a for a,b in zip(grip['from'],grip['to'])]
            assert all(abs(a-b)<1E-5 for a,b in zip(size,[1.05,4.9,1.2])),size
            assert abs(fitted_vertex([0,18,0],kind,offset,center,'sword_blade')[1]-11.5)<1E-6
            assert abs(fitted_vertex([0,70,0],kind,offset,center,'sword_blade')[1]-26.45)<1E-6
            stats[kind]['blade_length_source_units']=52*BLADE_LENGTH
            stats[kind]['grip_dimensions']=size
    print(json.dumps(stats,indent=2))
if __name__=='__main__':main()
