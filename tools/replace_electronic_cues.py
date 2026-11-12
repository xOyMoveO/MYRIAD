"""Mechanical replacement of legacy synthetic spell cues with material families."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]/'src/main/java/com/myriad/character/skills'
families={'guanlan':'water','qingluo':'wood','sulan':'wind','tianxing':'thunder','fenjue':'fire',
 'wuxiang':'ice','luoheng':'stone','wanyue':'blade','feiyu':'wind','zhechuan':'blade','shenli':'shot',
 'hanze':'ice','xuanchen':'wood','lingyuan':'blade','yingxiao':'blade','xuanjia':'stone','chixiao':'stone','yunsheng':'wind','lige':'wind'}
count=0
for path in root.rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    # Preserve musical instruments for LiGe/YunSheng; remove only electronic beacon/anchor cues.
    pattern=r'SoundEvents\.(?:BEACON_\w+|ENCHANTMENT_TABLE_USE|EXPERIENCE_ORB_PICKUP|RESPAWN_ANCHOR_\w+)(?:\.value\(\))?'
    text,n=re.subn(pattern,f'com.myriad.registry.ModSounds.foley("{families[path.parent.name]}")',text)
    if n:path.write_text(text,encoding='utf-8');count+=n
path=root/'shenli/ShenLiSkills.java';text=path.read_text(encoding='utf-8')
text=text.replace('private static void applySlow(ServerPlayer p,LivingEntity target,State s,double amount,int ticks,long now){',
    'private static void applySlow(ServerPlayer p,LivingEntity target,State s,double amount,int ticks,long now){if(target instanceof com.myriad.game.DefenseTowerEntity)return;')
path.write_text(text,encoding='utf-8')
print(f'Replaced {count} electronic legacy cues.')
