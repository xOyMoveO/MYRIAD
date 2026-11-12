"""Edit CC0 Tesla-generator recordings into short positional cues; no synthesized tones.
Run from the mod project with Python 3.13 + workspace NumPy/SciPy/SoundFile.
"""
from pathlib import Path
import sys, json, math, hashlib
sys.path.insert(0, 'E:/mod/.tmp/audio-runtime-sf')
import numpy as np
from scipy.signal import resample_poly, butter, sosfilt
from scipy.ndimage import maximum_filter1d
import soundfile as sf

ROOT = Path('E:/mod/role_assets/tianxing/20261107/audio')
OUT = Path('src/main/resources/assets/myriad/sounds/skill/tianxing')
RATE = 48000
SOURCE = 'https://opengameart.org/content/electricity-sound-effects-0'

def recording(name, speed=1):
    data, rate = sf.read(ROOT/'sources'/name, always_2d=True)
    data = data.mean(axis=1)
    divisor = math.gcd(rate, RATE)
    data = resample_poly(data, RATE//divisor, rate//divisor)
    if speed != 1:
        data = np.interp(np.arange(round(len(data)/speed))*speed, np.arange(len(data)), data)
    # Retain the crackle but reduce low-frequency DC/rumble and very sharp top-end hiss.
    data = sosfilt(butter(2, [150, 9500], btype='bandpass', fs=RATE, output='sos'), data)
    audible = np.flatnonzero(np.abs(data) > max(.0001, np.max(np.abs(data))*.012))
    data = data[max(0, audible[0]-48):min(len(data), audible[-1]+240)]
    # Short-window feed-forward compression: tame isolated spikes without making the
    # crackling body inaudible after positional falloff. Changes gain, not the waveform source.
    envelope = maximum_filter1d(np.abs(data), size=round(.008*RATE), mode='nearest')
    data *= np.minimum(3, (.12/(envelope+.001))**.65)
    return data/max(.00001, float(np.max(np.abs(data))))

def layer(buffer, wave, start, gain):
    at=round(start*RATE)
    n=min(len(wave), len(buffer)-at)
    if n>0: buffer[at:at+n] += wave[:n]*gain

OUT.mkdir(parents=True, exist_ok=True)
(ROOT/'preview').mkdir(parents=True, exist_ok=True)
report={}
preview=[]
for cue, duration, peak in [('charge',.60,.45),('cast',.32,.65),('hit',.23,.56),
                             ('guard',.46,.55),('pulse',.26,.48),('judgment',.64,.70)]:
    for variant in (1,2):
        spark=recording('spark.wav', .96 if variant==1 else 1.04)
        bed=recording('continuousspark.wav', .92 if variant==1 else 1.03)
        signal=np.zeros(round(duration*RATE))
        if cue=='charge':
            # Irregular overlapping fragments, not a fixed-frequency oscillator or infinite loop.
            for at,gain in [(0,.24),(.14,.38),(.31,.55),(.43,.72)]: layer(signal,bed,at,gain)
            signal *= np.linspace(.28,1,len(signal))
        elif cue=='cast':
            layer(signal,spark,0,1);layer(signal,bed,.025,.22)
        elif cue=='hit':
            layer(signal,spark,0,1);layer(signal,spark[::-1],.045,.16)
        elif cue=='guard':
            layer(signal,spark,0,.7);layer(signal,bed,.08,.42);layer(signal,bed,.23,.23)
        elif cue=='pulse':
            layer(signal,bed,0,.8);layer(signal,spark,.025,.3)
        else:
            layer(signal,recording('spark.wav',.78 if variant==1 else .83),0,1)
            layer(signal,bed,.04,.5);layer(signal,spark,.12,.35);layer(signal,bed,.26,.25)
        signal -= signal.mean()
        signal[:96] *= np.linspace(0,1,96)
        fade=min(round(.04*RATE),len(signal)//4)
        signal[-fade:] *= np.linspace(1,0,fade)**2
        signal *= peak/max(.00001,float(np.max(np.abs(signal))))
        path=OUT/f'{cue}_{variant}.ogg'
        sf.write(path,signal,RATE,format='OGG',subtype='VORBIS')
        decoded,rate=sf.read(path,always_2d=True)
        p=float(np.max(np.abs(decoded)))
        assert rate==RATE and decoded.shape[1]==1 and p<.90 and np.all(np.isfinite(decoded))
        assert float(np.sqrt(np.mean(decoded**2)))>.012, 'Inaudible body: '+str(path)
        assert np.max(np.abs(decoded[:round(.08*RATE)]))>.025, path
        assert np.max(np.abs(decoded[-48:]))<.04, path
        report[path.name]={'seconds':len(decoded)/rate,'channels':1,'sampleRate':rate,
            'peakDbFS':round(20*math.log10(p),2),'rmsDbFS':round(20*math.log10(float(np.sqrt(np.mean(decoded**2)))),2),
            'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'sourcePage':SOURCE,'author':'Brian MacIntosh (BMacZero)','license':'CC0-1.0'}
        preview.extend([decoded[:,0],np.zeros(round(.35*RATE))])
sf.write(ROOT/'preview/electric_cues.wav',np.concatenate(preview),RATE,subtype='PCM_16')
(ROOT/'audio-qc.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'files':len(report),'totalBytes':sum(r['bytes'] for r in report.values()),'qc':str(ROOT/'audio-qc.json')}))
