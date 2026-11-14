"""Generate the +Y chronometric needle from editable specs and compile its real faces."""
from pathlib import Path
import importlib.util,itertools,json,sys,uuid
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/vfx'))
from compile_roster_projectiles import compile_models


def main():
    helper=ROOT/'.agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py'
    module=importlib.util.spec_from_file_location('bbmodel_generator',helper);g=importlib.util.module_from_spec(module);module.loader.exec_module(g)
    # Thin embedded inlays intentionally retain sub-half-pixel volume.
    g._snap=lambda value,grid=None:round(value,6)
    art=ROOT/'art_source/keheng/projectiles';models={};atlas=None
    for name,key in [('delay_needle','needle'),('delay_needle_lod','needle_lod')]:
        spec=json.loads((art/f'{name}.spec.json').read_text());sequence=itertools.count()
        g._uuid=lambda:str(uuid.uuid5(uuid.NAMESPACE_URL,f'myriad:keheng/{name}/{next(sequence)}'))
        model,png,findings=g.generate(spec)
        if findings:raise ValueError(findings)
        if atlas is not None and atlas!=png:raise ValueError('LOD atlas differs')
        atlas=png;model['textures'][0]['name']='keheng_projectiles_atlas.png'
        path=art/f'{name}.bbmodel';path.write_text(json.dumps(model,indent=2)+'\n');models[key]=path
    (art/'keheng_projectiles_atlas.png').write_bytes(atlas)
    (ROOT/'src/main/resources/assets/myriad/textures/particle/vfx/keheng_projectiles_atlas.png').write_bytes(atlas)
    data=compile_models(models,ROOT/'src/main/resources/assets/myriad/vfx/keheng_projectiles_mesh.json')
    for key,value in data['meshes'].items():print(key,value['parts'],'parts',len(value['faces']),'real textured faces')

if __name__=='__main__':main()
