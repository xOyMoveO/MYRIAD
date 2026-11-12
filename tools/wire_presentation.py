"""One-time mechanical wiring of validated cast sites and sound resource variants."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/com/myriad'
def change(file, old, new, count=1):
    p=JAVA/file; text=p.read_text(encoding='utf-8')
    assert old in text,(file,old)
    p.write_text(text.replace(old,new,count),encoding='utf-8')
for role, cls, actor in [('wuxiang','WuxiangSkills','player'),('feiyu','FeiYuSkills','p'),('wanyue','WanyueSkills','p'),('shenli','ShenLiSkills','p')]:
    file=f'character/skills/{role}/{cls}.java'
    needle=f'PlayerDataProvider.syncToClient({actor});'
    change(file,needle,f'com.myriad.character.SkillPresentation.cast({actor}, skill);\n        '+needle)
for role,cls in [('luoheng','LuoHengSkills'),('zhechuan','ZheChuanSkills')]:
    file=f'character/skills/{role}/{cls}.java'
    needle='if (!s.qNoMana) data.spendMana(q.getManaCost());'
    change(file,needle,needle+'\n        com.myriad.character.SkillPresentation.cast(p, q);')
    needle='if (!noMana) data.spendMana(skill.getManaCost());'
    change(file,needle,'com.myriad.character.SkillPresentation.cast(p, skill); '+needle,100)
change('character/skills/wanyue/WanyueSkills.java','if (!noMana) data.spendMana(skill.getManaCost());','com.myriad.character.SkillPresentation.cast(p, skill);\n        if (!noMana) data.spendMana(skill.getManaCost());')
change('character/skills/shenli/ShenLiSkills.java','if(!s.wNoMana)data.spendMana(skill.getManaCost());','com.myriad.character.SkillPresentation.cast(p, skill); if(!s.wNoMana)data.spendMana(skill.getManaCost());')
change('character/skills/chixiao/ChiXiaoSkills.java','double moved = player.position().distanceTo(dash.lastPos); dash.lastPos = player.position();','Vec3 previous = dash.lastPos;\n            double moved = player.position().distanceTo(dash.lastPos); dash.lastPos = player.position();')
change('character/skills/chixiao/ChiXiaoSkills.java','level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.6),\n                    e -> e != player && e.isAlive() && !ally(player, e))','com.myriad.combat.DashHitbox.targets(player, previous, player.position(), 1.6, dash.hit)')
change('character/skills/lingyuan/LingYuanSkills.java','dash.last = player.position();\n        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.35),\n                e -> e != player && e.isAlive() && !ally(player, e)))','Vec3 previous = dash.last;\n        dash.last = player.position();\n        for (LivingEntity target : com.myriad.combat.DashHitbox.targets(player, previous, player.position(), 1.35, dash.hit))')
# The ability line was measured from feet to target center, producing a vertical miss even on flat ground.
change('character/skills/zhechuan/ZheChuanSkills.java','p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ally(p, e))','com.myriad.combat.DashHitbox.targets(p, a, b, 1.5, m.hit)')
change('character/skills/zhechuan/ZheChuanSkills.java','if (m.hit.contains(target.getUUID()) || distanceSegment(target.position().add(0, target.getBbHeight() * .5, 0), a, b) > 1.5) continue;','if (m.hit.contains(target.getUUID())) continue;')
p=ROOT/'src/main/resources/assets/myriad/sounds.json'
data=json.loads(p.read_text(encoding='utf-8'))
families=['blade','wind','fire','ice','water','wood','stone','shot','thunder']
for family in families:
    data['foley_'+family]={'sounds':[{'name':f'myriad:foley/{family}_{i}'} for i in (1,2)]}
# Replace the previously synthesized Hanze and Zhechuan events too.
for key in list(data):
    if key.startswith('hanze_'):
        data[key]={'sounds':[{'name':f'myriad:foley/ice_{i}'} for i in (1,2)]}
    elif key.startswith('zhechuan_'):
        family='wind' if 'outbound' in key or 'return' in key else 'blade'
        data[key]={'sounds':[{'name':f'myriad:foley/{family}_{i}'} for i in (1,2)]}
p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Validated cast sites and 18 inspected Foley variants wired.')
