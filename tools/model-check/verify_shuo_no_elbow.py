from pathlib import Path
import zipfile,json,hashlib
old=Path('E:/mod/outputs/万象_MYRIAD_居合朔_拔刀与刀鞘_20261111.jar')
new=Path('E:/mod/MDK-26.2-ModDevGradle-main/build/libs/myriad-0.1.0.jar')
qa=Path('E:/mod/outputs/shuo-no-elbow-20261111');qa.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
    aa=set(a.namelist());bb=set(b.namelist());added=sorted(bb-aa);removed=sorted(aa-bb)
    changed=sorted(n for n in aa&bb if a.read(n)!=b.read(n))
    assert added==['com/myriad/client/firstperson/ShuoArmModel.class'] and not removed
    assert set(changed)=={'com/myriad/client/firstperson/ShuoView.class','com/myriad/game/ShuoChecks.class','com/myriad/game/ShuoChecks$Fixture.class'},changed
    report=dict(added=added,removed=removed,changed=changed,sha256=hashlib.sha256(new.read_bytes()).hexdigest(),bytes=new.stat().st_size,
                old_sha256=hashlib.sha256(old.read_bytes()).hexdigest(),all_resources_and_gameplay_preserved=True)
(qa/'package_report.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))
