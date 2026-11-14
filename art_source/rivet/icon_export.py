"""Export individually measured complete borders from the original opaque 4x2 icon sheet."""
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path(__file__).resolve().parent
TARGET=ROOT/'src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/rivet'
# Each bounds tuple was measured independently against its own outer steel frame.
PANELS=[
 ('rivet_station_armor',(6,5,438,438)),
 ('rivet_breach_rhythm',(449,5,881,438)),
 ('rivet_impact_hammer',(894,5,1327,438)),
 ('rivet_tactical_anchor',(1337,5,1770,438)),
 ('rivet_emergency_strut',(6,450,438,883)),
 ('rivet_tow_cable',(449,450,881,883)),
 ('rivet_hold_protocol',(894,450,1327,883)),
 ('rivet_breach_charge',(1337,450,1770,883))]
def main():
 source=Image.open(SOURCE/'icon_source.png').convert('RGB');assert source.size==(1774,887)
 TARGET.mkdir(parents=True,exist_ok=True);contact=Image.new('RGB',(256,128),'#15212a')
 for i,(name,box) in enumerate(PANELS):
  icon=source.crop(box).resize((64,64),Image.Resampling.NEAREST)
  icon.save(TARGET/(name+'.png'));contact.paste(icon,((i%4)*64,(i//4)*64))
  assert Image.open(TARGET/(name+'.png')).size==(64,64)
 release=Image.open(SOURCE/'tow_release_source.png').convert('RGB').resize((64,64),Image.Resampling.NEAREST)
 release.save(TARGET/'rivet_tow_cable_2.png')
 release.resize((256,256),Image.Resampling.NEAREST).save(SOURCE/'tow_release_preview.png')
 contact.save(SOURCE/'icon_contact_native.png')
 contact.resize((1024,512),Image.Resampling.NEAREST).save(SOURCE/'icon_contact_preview.png')
 print('Eight candidate icons and one independent release variant exported as opaque 64x64 PNGs.')
if __name__=='__main__':main()
