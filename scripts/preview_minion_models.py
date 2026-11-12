"""Orthographic geometry/UV preview of the exported, generated Blockbench models."""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
siege = '--siege' in sys.argv
models = sorted((ROOT/'art/models/minions').glob('siege_*.bbmodel' if siege else 'minion_*.bbmodel'))
out = Image.new('RGB', (360*min(3,len(models)), ((len(models)+2)//3)*410+10), '#17222e')
d = ImageDraw.Draw(out)
for i, p in enumerate(models):
    model = json.loads(p.read_text(encoding='utf-8'))
    tex = Image.open(ROOT/'src/main/resources/assets/myriad/textures'/('entity' if siege else 'item')/f'{p.stem}.png').convert('RGB')
    ox, oy = (i%3)*360+180, (i//3)*410+330
    def project(v):
        x,y,z = v
        return (ox+10*(.8*x+.6*z),oy+10*(.24*x-.9*y-.32*z))
    polys=[]
    for c in model['elements']:
        x,y,z=c['from']; X,Y,Z=c['to']
        for face, vertices, shade in [
            ('north',[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z)],.9),
            ('east',[(X,y,z),(X,y,Z),(X,Y,Z),(X,Y,z)],.65),
            ('up',[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1.1)]:
            u,v,_,_=c['faces'][face]['uv']
            col=tuple(min(255,int(k*shade)) for k in tex.getpixel((int(u+1),int(v+1))))
            depth=sum(.6*a+.4*b-.7*cc for a,b,cc in vertices)/4
            polys.append((depth, vertices,col))
    for _, vertices, col in sorted(polys):
        d.polygon([project(v) for v in vertices], fill=col, outline='#202b33')
    d.text((ox-80,oy+42),p.stem,fill='#e8edf1')
out.save(ROOT/'art/models/minions'/('siege_preview.png' if siege else 'preview.png'))
