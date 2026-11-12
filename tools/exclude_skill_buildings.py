"""Mechanical target filtering: role abilities ignore structures before applying effects."""
from pathlib import Path
import re
base=Path(__file__).resolve().parents[1]/'src/main/java/com/myriad/character/skills'
for path in base.rglob('*.java'):
    if path.parent.name in ('shenli','wanyue'):continue # These share projectile targeting with ordinary attacks.
    text=path.read_text(encoding='utf-8')
    updated=re.sub(r'\((e|enemy|target|other|living)\.isAlive\(\) && !\(\1 instanceof com.myriad.game.DefenseTowerEntity\)\)',
        lambda m:f'com.myriad.combat.SkillTargets.alive({m[1]})',text)
    if text!=updated:path.write_text(updated,encoding='utf-8')
print('Ability target scans exclude buildings; shared auto-attack selectors preserved.')
