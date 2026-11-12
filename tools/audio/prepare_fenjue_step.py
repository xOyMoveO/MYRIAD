"""Edit existing recorded air movement and the user's selected single sword swish; no synthesis."""
from pathlib import Path
import argparse,hashlib,io,json,subprocess,zipfile
import numpy as np
from prepare_fenjue_slash_layers import RATE,DEFAULT_FFMPEG,decode,inspect,process,encode

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--qa',required=True);ap.add_argument('--ffmpeg',default=DEFAULT_FFMPEG)
    args=ap.parse_args();qa=Path(args.qa);qa.mkdir(parents=True,exist_ok=True)
    archive=Path('E:/mod/role_assets/audio/lingyuan-heaven-sweep-20261105/source/swishes.zip')
    assert hashlib.sha256(archive.read_bytes()).hexdigest()=='7980215241b739a787dcf26f660ce510bb237900968789395a86911619795693'
    with zipfile.ZipFile(archive) as z:recording=z.read('swishes/swish-7.wav')
    result=subprocess.run([args.ffmpeg,'-v','error','-i','pipe:0','-af','highpass=f=160,lowpass=f=7500','-ac','1','-ar',str(RATE),'-f','f32le','pipe:1'],input=recording,capture_output=True,check=True)
    wind=np.frombuffer(result.stdout,dtype='<f4').copy()
    # Keep the real swish's peak near the first guided movement step, with a short natural tail.
    hop=480;energy=np.mean(wind[:len(wind)//hop*hop].reshape(-1,hop)**2,axis=1)
    begin=max(0,int(np.argmax(energy))*hop-round(.065*RATE))
    dash=np.zeros(round(.30*RATE),dtype=np.float32);part=wind[begin:begin+len(dash)];dash[:len(part)]=part
    dash-=dash.mean();dash[:240]*=np.linspace(0,1,240);dash[-2400:]*=np.linspace(1,0,2400)
    slash=process(args.ffmpeg,Path('E:/mod/斩击.mp3'),.10,.64,1.8,'highpass=f=95,lowpass=f=14000')
    output=Path(__file__).resolve().parents[2]/'src/main/resources/assets/myriad/sounds/skill/fenjue'
    report={}
    for name,samples,peak in [('step_wind',dash,-9),('step_slash',slash,-4)]:
        samples*=10**(peak/20)/max(1E-12,float(np.max(np.abs(samples))))
        path=output/(name+'.ogg');encode(args.ffmpeg,samples,path)
        decoded=decode(args.ffmpeg,path);stats=inspect(decoded)
        assert .20<stats['seconds']<.40 and stats['clipped_samples']==0 and stats['peak_db']<-1,stats
        assert np.max(np.abs(decoded[:96]))<.04 and np.max(np.abs(decoded[-96:]))<.04,'Short click-free fades'
        report[name]=stats|dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    preview=np.zeros(round(.85*RATE),dtype=np.float32)
    preview[:len(dash)]+=dash*.50;at=round(.30*RATE);preview[at:at+len(slash)]+=slash*.70
    assert np.max(np.abs(preview))<.9
    encode(args.ffmpeg,preview,qa/'flame_step_audio_preview.mp3',True)
    report['source']=dict(wind_archive=str(archive),entry='swishes/swish-7.wav',entry_sha256=hashlib.sha256(recording).hexdigest(),
        slash='E:/mod/斩击.mp3',slash_sha256=hashlib.sha256(Path('E:/mod/斩击.mp3').read_bytes()).hexdigest())
    (qa/'audio_edits.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
