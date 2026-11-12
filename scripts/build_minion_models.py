"""Regenerate validated Blockbench projects and export their cuboids to item assets."""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/models/minions'
ASSETS = ROOT / 'src/main/resources/assets/myriad'
GENERATOR = Path('C:/Users/Administrator/.codex/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py')
for spec in sorted(ART.glob('*.spec.json')):
    name = spec.name.removesuffix('.spec.json')
    texture = ASSETS / 'textures/item' / (name + '.png')
    subprocess.run([sys.executable, str(GENERATOR), str(spec), '-o', str(ART / (name + '.bbmodel')), '--png', str(texture)], check=True)
    model = json.loads((ART / (name + '.bbmodel')).read_text(encoding='utf-8'))
    w, h = model['resolution']['width'], model['resolution']['height']
    elements = []
    for cube in model['elements']:
        faces = {face: {'uv': [value * 16 / (w if i % 2 == 0 else h) for i, value in enumerate(data['uv'])], 'texture': '#0'} for face, data in cube['faces'].items()}
        elements.append({'from': [cube['from'][0]+8, cube['from'][1], cube['from'][2]+8], 'to': [cube['to'][0]+8,cube['to'][1],cube['to'][2]+8], 'faces': faces})
    output = {'textures': {'0': 'myriad:item/' + name, 'particle': 'myriad:item/' + name}, 'elements': elements}
    (ASSETS / 'models/item' / (name + '.json')).write_text(json.dumps(output, indent=2), encoding='utf-8')
    (ASSETS / 'items' / (name + '.json')).write_text(json.dumps({'model': {'type':'minecraft:model','model':'myriad:item/'+name}}, indent=2), encoding='utf-8')

for lang in ['zh_cn', 'en_us']:
    path = ASSETS / 'lang' / (lang+'.json')
    data = json.loads(path.read_text(encoding='utf-8'))
    data['entity.myriad.magic_minion'] = '%s 魔法傀儡 %s/%s' if lang == 'zh_cn' else '%s Magic Golem %s/%s'
    for kind, zh in [('melee','近战'),('ranged','远程'),('magic','魔法')]:
        for team, color in [('red','赤'),('blue','青')]:
            data[f'item.myriad.minion_{kind}_{team}'] = f'{color}纹{zh}傀儡' if lang == 'zh_cn' else f'{team.title()} {kind.title()} Golem'
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
