"""Inspect/edit user-selected recordings. No synthesized replacement; originals are never changed."""
from pathlib import Path
import argparse, hashlib, json, subprocess, shutil
import numpy as np

RATE=48000
DEFAULT_FFMPEG='C:/Users/Administrator/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg.Essentials_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.1.1-essentials_build/bin/ffmpeg.exe'

def decode(ffmpeg,path):
    result=subprocess.run([ffmpeg,'-v','error','-i',str(path),'-ac','1','-ar',str(RATE),'-f','f32le','pipe:1'],capture_output=True,check=True)
    return np.frombuffer(result.stdout,dtype='<f4').copy()

def inspect(samples):
    hop=480
    rms=np.sqrt(np.mean(samples[:len(samples)//hop*hop].reshape(-1,hop)**2,axis=1))
    active=rms>max(.001,float(rms.max())*.035)
    indices=np.flatnonzero(active);groups=[]
    if len(indices):
        start=last=int(indices[0])
        for index in indices[1:]:
            if index-last>8:groups.append([start*.01,(last+1)*.01]);start=int(index)
            last=int(index)
        groups.append([start*.01,(last+1)*.01])
    return dict(seconds=len(samples)/RATE,peak_db=20*np.log10(max(1E-12,float(np.max(np.abs(samples))))),rms_db=20*np.log10(max(1E-12,float(np.sqrt(np.mean(samples*samples))))),active_intervals=groups,peak_10ms_time=float(np.argmax(rms))*.01,clipped_samples=int(np.sum(np.abs(samples)>=1)))

def process(ffmpeg,source,start,end,tempo,filters):
    af=f'atrim=start={start}:end={end},asetpts=PTS-STARTPTS,atempo={tempo},'+filters
    data=subprocess.run([ffmpeg,'-v','error','-i',str(source),'-af',af,'-ac','1','-ar',str(RATE),'-f','f32le','pipe:1'],capture_output=True,check=True)
    samples=np.frombuffer(data.stdout,dtype='<f4').copy()
    fade_in=min(len(samples),int(.004*RATE));fade_out=min(len(samples),int(.060*RATE))
    samples[:fade_in]*=np.linspace(0,1,fade_in);samples[-fade_out:]*=np.linspace(1,0,fade_out)
    return samples

def double(samples,peak_db):
    delay=round(.180*RATE) # second authored cut at 3.6 ticks; no client timer or repeated server task
    result=np.zeros(len(samples)+delay+round(.025*RATE),dtype=np.float32)
    result[:len(samples)]+=samples;result[delay:delay+len(samples)]+=samples*.92
    result*=10**(peak_db/20)/max(1E-12,float(np.max(np.abs(result))))
    return result

def encode(ffmpeg,samples,path,mp3=False):
    path.parent.mkdir(parents=True,exist_ok=True)
    codec=['-c:a','libmp3lame','-q:a','3'] if mp3 else ['-c:a','libvorbis','-q:a','5']
    subprocess.run([ffmpeg,'-v','error','-y','-f','f32le','-ar',str(RATE),'-ac','1','-i','pipe:0','-map_metadata','-1']+codec+[str(path)],input=samples.astype('<f4').tobytes(),capture_output=True,check=True)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--ffmpeg',default=DEFAULT_FFMPEG);ap.add_argument('--qa',required=True);ap.add_argument('--produce',action='store_true')
    args=ap.parse_args();qa=Path(args.qa);qa.mkdir(parents=True,exist_ok=True)
    result={}
    for name in ['斩击','火焰']:
        source=Path('E:/mod')/(name+'.mp3');samples=decode(args.ffmpeg,source)
        result[name]=inspect(samples)|dict(source=str(source),sha256=hashlib.sha256(source.read_bytes()).hexdigest())
    (qa/'source_analysis.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    if not args.produce:return
    project=Path(__file__).resolve().parents[2]
    source_dir=Path('E:/mod/role_assets/fenjue/20261109/audio')
    source_dir.mkdir(parents=True,exist_ok=True)
    for source,name in [('斩击.mp3','selected_slash.mp3'),('火焰.mp3','selected_flame.mp3')]:
        destination=source_dir/name;original=Path('E:/mod')/source
        if destination.exists():assert destination.read_bytes()==original.read_bytes(),'Do not overwrite another source recording'
        else:shutil.copy2(original,destination)
    blade=double(process(args.ffmpeg,Path('E:/mod/斩击.mp3'),.10,.74,1.6,'highpass=f=95,lowpass=f=14000'),-4)
    fire=double(process(args.ffmpeg,Path('E:/mod/火焰.mp3'),.19,.72,1.35,'highpass=f=200,lowpass=f=8500'),-8)
    report={}
    output=project/'src/main/resources/assets/myriad/sounds/skill/fenjue'
    for name,samples in [('slash_double',blade),('slash_flame_layer',fire)]:
        path=output/(name+'.ogg');encode(args.ffmpeg,samples,path)
        decoded=decode(args.ffmpeg,path);stats=inspect(decoded)
        assert .25<stats['seconds']<.8 and stats['clipped_samples']==0 and stats['peak_db']<-1,stats
        assert np.max(np.abs(decoded[:96]))<.03 and np.max(np.abs(decoded[-96:]))<.03,'Click-free short fades'
        report[name]=stats|dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    preview=np.zeros(max(len(blade),len(fire)),dtype=np.float32)
    preview[:len(blade)]+=blade*.70;preview[:len(fire)]+=fire*.32
    assert float(np.max(np.abs(preview)))<.9
    preview_path=source_dir/'flame_slash_preview.mp3';encode(args.ffmpeg,preview,preview_path,True)
    report['preview']=dict(path=str(preview_path),second_cut_seconds=.180,blade_volume=.70,fire_volume=.32)
    (qa/'audio_edits.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
