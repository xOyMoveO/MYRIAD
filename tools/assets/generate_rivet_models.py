"""Compile original Rivet cuboids through the project's validated BBModel pipeline."""
from pathlib import Path
import importlib.util,itertools,json,sys,uuid
from PIL import Image
from render_bbmodel import render
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'art_source/rivet'
ASSET=ROOT/'src/main/resources/assets/myriad'
sys.path.insert(0,str(ROOT/'tools/vfx'))
from compile_roster_projectiles import compile_models

def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')

def main():
 helper=ROOT/'.agents/skills/minecraft-ai-generate-bbmodel/scripts/generate_bbmodel.py'
 mod=importlib.util.spec_from_file_location('rivet_model_generator',helper);compiler=importlib.util.module_from_spec(mod);mod.loader.exec_module(compiler)
 paths={};atlas=None
 for key in ('hammer','anchor'):
  spec=json.loads((SOURCE/(key+'.spec.json')).read_text());serial=itertools.count()
  compiler._uuid=lambda:str(uuid.uuid5(uuid.NAMESPACE_URL,'myriad:rivet/'+key+'/'+str(next(serial))))
  model,png,findings=compiler.generate(spec)
  errors=[f for f in findings if f[0]=='error']
  if errors:raise ValueError(errors)
  if atlas is None:atlas=png
  assert png==atlas,'Rivet solids must share one UV atlas'
  path=SOURCE/(key+'.bbmodel');write(path,model);paths[key]=path
  render(model,SOURCE/(key+'_preview.png'),view='isometric',size=(640,640),scale=20,background='#17232c')
  if key=='hammer':
   display={
    'thirdperson_righthand':{'rotation':[0,90,-12],'translation':[0,0,0],'scale':[.65,.65,.65]},
    'thirdperson_lefthand':{'rotation':[0,-90,12],'translation':[0,0,0],'scale':[.65,.65,.65]},
    'firstperson_righthand':{'rotation':[0,75,-25],'translation':[1,-1,-2],'scale':[.55,.55,.55]},
    'firstperson_lefthand':{'rotation':[0,-75,25],'translation':[1,-1,-2],'scale':[.55,.55,.55]},
    'gui':{'rotation':[15,0,-35],'translation':[0,-1,0],'scale':[.55,.55,.55]},
    'ground':{'rotation':[0,0,90],'translation':[0,2,0],'scale':[.4,.4,.4]},
    'fixed':{'rotation':[0,0,0],'translation':[0,-1,0],'scale':[.5,.5,.5]}}
   runtime={'credit':'Original MYRIAD Rivet / generate_rivet_models.py','texture_size':[128,128],
    'textures':{'0':'myriad:item/rivet_metal','particle':'myriad:item/rivet_metal'},'elements':[],'display':display}
   for element in model['elements']:
    part={k:element[k] for k in ('name','from','to')};part['faces']={name:{'uv':[v/8 for v in f['uv']],'texture':'#0'} for name,f in element['faces'].items()};runtime['elements'].append(part)
   write(ASSET/'models/item/rivet_hammer.json',runtime)
   write(ASSET/'items/rivet_hammer.json',{'model':{'type':'minecraft:model','model':'myriad:item/rivet_hammer'}})
 (ASSET/'textures/item/rivet_metal.png').write_bytes(atlas)
 (ASSET/'textures/particle/vfx/rivet_metal.png').write_bytes(atlas)
 (SOURCE/'rivet_metal.png').write_bytes(atlas)
 result=compile_models(paths,ASSET/'vfx/rivet_models_mesh.json',source_length=28)
 print('Rivet validated models: '+', '.join(k+': '+str(v['parts'])+' cuboids' for k,v in result['meshes'].items()))
if __name__=='__main__':main()
