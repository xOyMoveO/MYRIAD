"""Mechanical replacement of legacy synthetic spell cues with material families."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]/'src/main/java/com/myriad/character/skills'
families={'qingluo':'wood','sulan':'wind','tianxing':'thunder','fenjue':'fire',
 'hanze':'ice','xuanchen':'wood','lingyuan':'blade','yingxiao':'blade','chixiao':'stone','yunsheng':'wind'}
count=0
for path in root.rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    # Preserve musical instruments for YunSheng; remove only electronic beacon/anchor cues.
    pattern=r'SoundEvents\.(?:BEACON_\w+|ENCHANTMENT_TABLE_USE|EXPERIENCE_ORB_PICKUP|RESPAWN_ANCHOR_\w+)(?:\.value\(\))?'
    text,n=re.subn(pattern,f'com.myriad.registry.ModSounds.foley("{families.get(path.parent.name, "blade")}")',text)
    if n:path.write_text(text,encoding='utf-8');count+=n
print(f'Replaced {count} electronic legacy cues.')
