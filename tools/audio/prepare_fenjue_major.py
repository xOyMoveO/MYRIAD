"""Author the four major skills from the user's flame recording and licensed recorded Foley. No synthesized waveform."""
from pathlib import Path
import argparse,hashlib,io,json,subprocess,zipfile
import numpy as np
from prepare_fenjue_slash_layers import RATE,DEFAULT_FFMPEG,decode,inspect,process,encode

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--qa',required=True);ap.add_argument('--ffmpeg',default=DEFAULT_FFMPEG)
    a=ap.parse_args();qa=Path(a.qa);qa.mkdir(parents=True,exist_ok=True)
    fire=Path('E:/mod/火焰.mp3')
    assert hashlib.sha256(fire.read_bytes()).hexdigest()=='c14838525b180a13b0c0fc9f6113bc4a11ddc34e9f167b5cbcab744311ab7d6b'
    archive=Path('E:/mod/role_assets/audio/hit-feedback-20261105/source/kenney_impact-sounds.zip')
    def foley(name):
        with zipfile.ZipFile(archive) as z:raw=z.read('Audio/'+name+'.ogg')
        r=subprocess.run([a.ffmpeg,'-v','error','-i','pipe:0','-af','highpass=f=70,lowpass=f=5500','-ac','1','-ar',str(RATE),'-f','f32le','pipe:1'],input=raw,capture_output=True,check=True)
        x=np.frombuffer(r.stdout,dtype='<f4').copy();return x/max(.001,float(np.max(np.abs(x))))
    # asset, excerpt, pitch-preserving tempo, target duration, peak, optional recorded contact
    specs=[('cage_cast',3.08,3.78,1.30,.52,-9,None),
           ('cage_open',1.46,2.32,1.10,.78,-5,('impactMining_001',.24)),
           ('burst_charge',8.10,8.85,1.10,.65,-10,None),
           ('burst_release',4.40,5.12,1.05,.72,-4.5,('impactSoft_heavy_002',.23)),
           ('sea_jump',5.68,6.12,1.10,.40,-8,None),
           ('sea_impact',6.66,7.40,1.0,.82,-4,('impactMining_003',.34)),
           ('armor_ignite',9.29,9.90,.92,.67,-7,('impactMetal_light_001',.13))]
    output=Path(__file__).resolve().parents[2]/'src/main/resources/assets/myriad/sounds/skill/fenjue'
    report={};demo=[]
    for name,start,end,tempo,duration,peak,contact in specs:
        samples=process(a.ffmpeg,fire,start,end,tempo,'highpass=f=95,lowpass=f=10000')
        signal=np.zeros(round(duration*RATE),dtype=np.float32);n=min(len(signal),len(samples))
        samples/=max(.001,float(np.max(np.abs(samples))));signal[:n]=samples[:n]
        if name=='burst_charge':signal*=np.linspace(.20,1,len(signal)) # real flame swells with the charge
        if contact:
            x=foley(contact[0]);n=min(len(x),len(signal));signal[:n]+=x[:n]*contact[1]
        signal-=signal.mean();signal[:240]*=np.linspace(0,1,240);signal[-2880:]*=np.linspace(1,0,2880)**1.5
        signal*=10**(peak/20)/max(1E-12,float(np.max(np.abs(signal))))
        path=output/(name+'.ogg');encode(a.ffmpeg,signal,path)
        decoded=decode(a.ffmpeg,path);stats=inspect(decoded)
        assert stats['clipped_samples']==0 and stats['peak_db']<-1 and .3<stats['seconds']<.9,stats
        assert np.max(np.abs(decoded[:96]))<.04 and np.max(np.abs(decoded[-96:]))<.04,'Click-free fade'
        report[name]=stats|dict(path=str(path),excerpt=[start,end],tempo=tempo,contact=contact,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        demo.extend([decoded*.65,np.zeros(round(.35*RATE))])
    encode(a.ffmpeg,np.concatenate(demo),qa/'major_skill_audio_preview.mp3',True)
    report['sources']={'fire':str(fire),'fire_sha256':hashlib.sha256(fire.read_bytes()).hexdigest(),
                       'foley':str(archive),'foley_sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}
    (qa/'audio_edits.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
