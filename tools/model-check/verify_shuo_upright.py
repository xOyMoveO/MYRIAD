from pathlib import Path
import zipfile,json,hashlib
old=Path('E:/mod/outputs/万象_MYRIAD_朔取消手肘_20261111.jar')
new=Path('E:/mod/MDK-26.2-ModDevGradle-main/build/libs/myriad-0.1.0.jar')
qa=Path('E:/mod/outputs/shuo-upright-long-20261111');qa.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
    aa=set(a.namelist());bb=set(b.namelist());added=sorted(bb-aa);removed=sorted(aa-bb)
    changed=sorted(n for n in aa&bb if a.read(n)!=b.read(n))
    assert not added and not removed
    assert set(changed)<= {'com/myriad/client/firstperson/ShuoRig.class','com/myriad/client/firstperson/ShuoRig$Frame.class',
        'com/myriad/game/ShuoChecks.class','com/myriad/game/ShuoChecks$Fixture.class'},changed
    assert 'com/myriad/client/firstperson/ShuoRig.class' in changed
    report=dict(added=added,removed=removed,changed=changed,sha256=hashlib.sha256(new.read_bytes()).hexdigest(),bytes=new.stat().st_size,
                old_sha256=hashlib.sha256(old.read_bytes()).hexdigest(),all_resources_and_gameplay_preserved=True)
(qa/'package_report.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))
