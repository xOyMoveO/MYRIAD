"""Edit three CC0 recorded heavy swishes into a giant-sword cue; no synthesized waveform."""
from pathlib import Path
import io
import sys
import math
import json
import hashlib
import zipfile
sys.path.insert(0, 'E:/mod/.tmp/audio-runtime-sf')
import numpy as np
import soundfile as sf

root=Path('E:/mod/role_assets/audio/lingyuan-heaven-sweep-20261105')
out=Path('src/main/resources/assets/myriad/sounds/skill/lingyuan')
out.mkdir(parents=True,exist_ok=True)
(root/'preview').mkdir(parents=True,exist_ok=True)
rate=48000
report={}
preview=[]
with zipfile.ZipFile(root/'source/swishes.zip') as archive:
    for index,source in enumerate(('swishes/swish-7.wav','swishes/swish-8.wav','swishes/swish-9.wav'),1):
        # A real heavy air-cut body, pitched down in the file instead of mismatching the runtime timing.
        wave,sample_rate=sf.read(io.BytesIO(archive.read(source)),always_2d=True,dtype='float64')
        wave=wave.mean(axis=1)
        ratio=rate/sample_rate/.84
        wave=np.interp(np.arange(round(len(wave)*ratio))/ratio,np.arange(len(wave)),wave)
        # First-order high-pass on existing recording content (not synthesized wind/noise).
        alpha=1/(1+2*math.pi*70/rate)
        filtered=np.empty_like(wave);previous=0.;previous_input=0.
        for n,sample in enumerate(wave):
            previous=alpha*(previous+sample-previous_input);filtered[n]=previous;previous_input=sample
        wave=filtered
        audible=np.flatnonzero(np.abs(wave)>max(.001,np.max(np.abs(wave))*.02))
        assert len(audible)
        wave=wave[max(0,audible[0]-96):min(len(wave),audible[-1]+1200)]
        # Align the strongest air-cut at ~0.21s; impact is a separate cue at 0.30s.
        window=480
        envelope=np.convolve(wave*wave,np.ones(window)/window,mode='same')
        peak=int(np.argmax(envelope))
        start=max(0,peak-round(.21*rate))
        wave=wave[start:start+round(.52*rate)]
        signal=np.zeros(round(.52*rate));signal[:len(wave)]=wave
        signal-=signal.mean()
        signal[:96]*=np.linspace(0,1,96)
        signal[-1920:]*=np.linspace(1,0,1920)**2
        signal*=.65/max(.0001,float(np.max(np.abs(signal))))
        path=out/f'heaven_sweep_{index}.ogg'
        sf.write(path,signal,rate,format='OGG',subtype='VORBIS')
        decoded,decoded_rate=sf.read(path,always_2d=True,dtype='float32')
        peak=float(np.max(np.abs(decoded)))
        assert decoded_rate==48000 and decoded.shape[1]==1 and peak<.90
        assert np.any(np.abs(decoded[:round(.1*rate)])>.008)
        report[path.name]={'source':source,'author':'artisticdude','license':'CC0-1.0',
            'sourcePage':'https://opengameart.org/content/swishes-sound-pack','rate':decoded_rate,
            'channels':1,'seconds':len(decoded)/decoded_rate,'peakDbFS':round(20*math.log10(peak),2),
            'rmsDbFS':round(20*math.log10(float(np.sqrt(np.mean(decoded**2)))),2),
            'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
        preview.extend((decoded[:,0],np.zeros(round(.35*rate))))
sf.write(root/'preview/heaven_sweep_variants.wav',np.concatenate(preview),rate,subtype='PCM_16')
(root/'audio-qc.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
