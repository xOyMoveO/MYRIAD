"""Edit/layer licensed Foley recordings; no oscillators or synthetic impact generators.
Requires NumPy/SciPy and workspace-local SoundFile (libsndfile Vorbis encoder).
"""
from pathlib import Path
import sys
import io
import json
import hashlib
import zipfile
import math
sys.path.insert(0, 'E:/mod/.tmp/audio-runtime-sf')
import numpy as np
from scipy.signal import resample_poly, butter, sosfilt
import soundfile as sf

ROOT = Path('E:/mod/role_assets/audio/hit-feedback-20261105')
OUT = Path('src/main/resources/assets/myriad/sounds/combat_impact')
RATE = 48000
PACKS = {p: zipfile.ZipFile(ROOT / 'source' / f'kenney_{p}.zip') for p in ('impact-sounds', 'rpg-audio')}


def recording(pack, name):
    data, rate = sf.read(io.BytesIO(PACKS[pack].read('Audio/' + name)), dtype='float32', always_2d=True)
    data = data.mean(axis=1)
    if rate != RATE:
        gcd = math.gcd(rate, RATE)
        data = resample_poly(data, RATE // gcd, rate // gcd)
    data = sosfilt(butter(2, 38, btype='highpass', fs=RATE, output='sos'), data)
    peak = float(np.max(np.abs(data)))
    audible = np.flatnonzero(np.abs(data) > max(.001, peak * .018))
    assert len(audible), name
    data = data[max(0, int(audible[0]) - 96):]
    # Normalize components before authored relative gains; only existing waveform content.
    return data / max(.01, peak)


def impact(name, index):
    n = f'{index:03d}.ogg'
    p = lambda stem, gain, delay=0: ('impact-sounds', stem + '_' + n, gain, delay)
    r = lambda file, gain, delay=0: ('rpg-audio', file + '.ogg', gain, delay)
    knife = 'knifeSlice' if index % 2 == 0 else 'knifeSlice2'
    table = {
        'blade': (.34, [p('impactPunch_medium', .76), r(knife, .43, .003), p('impactMetal_light', .10, .008)]),
        'blunt': (.33, [p('impactPunch_heavy', .85), p('impactWood_heavy', .20, .005)]),
        'projectile': (.27, [p('impactSoft_heavy', .85), p('impactWood_light', .32, .004), r(knife, .12)]),
        'arcane': (.36, [p('impactPunch_medium', .7), p('impactGlass_light', .42, .004), r(knife, .12)]),
        'critical': (.40, [p('impactPunch_heavy', .86), r(knife, .42, .002), p('impactMetal_medium', .15, .006)]),
        'structure': (.37, [p('impactMining', .8), p('impactWood_heavy', .25, .004), p('impactMetal_heavy', .08)]),
        'shield': (.39, [p('impactGlass_medium', .6), p('impactMetal_light', .36, .002), p('impactSoft_medium', .36)]),
        'swing': (.31, [r(knife, .85), r('cloth' + str(index + 1), .20)]),
    }
    return table[name]


OUT.mkdir(parents=True, exist_ok=True)
(ROOT / 'preview').mkdir(parents=True, exist_ok=True)
report = {}
demo = []
for name in ('blade', 'blunt', 'projectile', 'arcane', 'critical', 'structure', 'shield', 'swing'):
    for i in range(3):
        duration, parts = impact(name, i)
        signal = np.zeros(round(duration * RATE), dtype='float64')
        for pack, file, gain, delay in parts:
            wave = recording(pack, file) * gain
            start = round(delay * RATE)
            length = min(len(wave), len(signal) - start)
            signal[start:start+length] += wave[:length]
        signal -= signal.mean()
        fade_in, fade_out = 48, 1200
        signal[:fade_in] *= np.linspace(0, 1, fade_in)
        signal[-fade_out:] *= np.linspace(1, 0, fade_out) ** 2
        signal *= .70 / max(.0001, np.max(np.abs(signal)))
        path = OUT / f'{name}_{i+1}.ogg'
        sf.write(path, signal, RATE, format='OGG', subtype='VORBIS')
        decoded, rate = sf.read(path, dtype='float32', always_2d=True)
        peak = float(np.max(np.abs(decoded)))
        assert decoded.shape[1] == 1 and rate == RATE and peak < .95, (path, peak)
        assert np.any(np.abs(decoded[:480]) > .02), f'Missing immediate contact: {path}'
        report[path.name] = {'sampleRate': rate, 'channels': 1, 'seconds': len(decoded)/rate,
                            'peakDbFS': round(20*math.log10(peak), 2),
                            'rmsDbFS': round(20*math.log10(float(np.sqrt(np.mean(decoded**2)))), 2),
                            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                            'components': parts}
        print(path.name, report[path.name]['peakDbFS'], 'dBFS', flush=True)
        if i == 0:
            demo.extend([decoded[:,0], np.zeros(round(RATE*.35))])
    one, _ = sf.read(OUT/f'{name}_1.ogg')
    sf.write(ROOT/'preview'/f'{name}.wav', one, RATE, subtype='PCM_16')
sf.write(ROOT/'preview'/'all_families.wav', np.concatenate(demo), RATE, subtype='PCM_16')
(ROOT/'audio-qc.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
for archive in PACKS.values():
    archive.close()
