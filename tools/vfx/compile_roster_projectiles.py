"""Compile real BBModel cuboid faces and pixel UVs to the two roster projectile caches.

Schema matches the existing Thunder face row: 4 xyz vertices, normalized u0/v0/u1/v1,
RGB face shade. Local +Y is forward; tip_y binds the foremost vertex to each real endpoint.
"""
from __future__ import annotations
import base64
import hashlib
import io
import json
import math
from pathlib import Path
from PIL import Image


def face_corners(lo, hi, name):
    x,y,z=lo;X,Y,Z=hi
    return {"north":[[X,y,z],[x,y,z],[x,Y,z],[X,Y,z]],"south":[[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],
            "east":[[X,y,Z],[X,y,z],[X,Y,z],[X,Y,Z]],"west":[[x,y,z],[x,y,Z],[x,Y,Z],[x,Y,z]],
            "up":[[x,Y,Z],[X,Y,Z],[X,Y,z],[x,Y,z]],"down":[[x,y,z],[X,y,z],[X,y,Z],[x,y,Z]]}[name]


def rotate(point, origin, degrees):
    x,y,z=[point[i]-origin[i] for i in range(3)]
    for i,a in enumerate(degrees):
        c,s=math.cos(math.radians(a)),math.sin(math.radians(a))
        if i==0:y,z=y*c-z*s,y*s+z*c
        elif i==1:x,z=x*c+z*s,-x*s+z*c
        else:x,y=x*c-y*s,x*s+y*c
    return [x+origin[0],y+origin[1],z+origin[2]]


def compile_models(models:dict[str,Path], target:Path, source_length=64):
    result={"version":1,"forward_axis":"+Y","source_length":source_length,"meshes":{}}
    for key,path in models.items():
        model=json.loads(Path(path).read_text());width=model['resolution']['width'];height=model['resolution']['height']
        image=Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
        assert image.size==(width,height)
        faces=[];tips=[]
        for part in model['elements']:
            assert all(part['to'][i]>part['from'][i] for i in range(3)),part['name']
            for name,face in part['faces'].items():
                assert face['texture']==0
                corners=[rotate(v,part.get('origin',part['from']),part.get('rotation',[0,0,0])) for v in face_corners(part['from'],part['to'],name)]
                uv=face['uv'];assert 0<=min(uv[0::2])<max(uv[0::2])<=width and 0<=min(uv[1::2])<max(uv[1::2])<=height
                assert not face.get('rotation',0),'Rotated UV requires explicit source layout'
                tips.extend(v[1] for v in corners)
                # BBModel faces map lower vertices to v1 and upper vertices to v0, as the particle path does.
                sample=image.crop((int(min(uv[0::2])),int(min(uv[1::2])),math.ceil(max(uv[0::2])),math.ceil(max(uv[1::2]))))
                assert sample.getchannel('A').getextrema()[1]>0,(part['name'],name,'unbound blank texture')
                a=[corners[1][i]-corners[0][i] for i in range(3)];b=[corners[2][i]-corners[0][i] for i in range(3)]
                normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
                magnitude=math.sqrt(sum(v*v for v in normal));assert magnitude>1e-10
                # Gentle neutral source-face lighting preserves each atlas pixel's hue.
                level=max(.72,min(1,.86+.14*sum(normal[i]/magnitude*[-.35,.81,-.47][i] for i in range(3))))
                channel=round(level*255);shade=(channel<<16)|(channel<<8)|channel
                faces.append([round(n,7) for v in corners for n in v]+[uv[0]/width,uv[1]/height,uv[2]/width,uv[3]/height,shade])
        result['meshes'][key]={"parts":len(model['elements']),"tip_y":max(tips),"source_sha256":hashlib.sha256(Path(path).read_bytes()).hexdigest(),"faces":faces}
    target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(result,separators=(',',':'))+'\n')
    return result
