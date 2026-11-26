"""Render Night Shadow's independent Foley cues from noise, friction and damped modes."""
import array
import math
import os
import random
import subprocess
from pathlib import Path
from zlib import crc32

FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
RATE = 48000
OUTPUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/myriad/sounds/skill/yingxiao"
TAU = math.tau


def friction(start, length, low, high, width, gain, crest=.35, grain=0):
    """Swept, band-limited air. Grain modulates the pressure of fabric on metal."""
    return ("air", start, length, low, high, width, gain, crest, grain)


def strike(start, length, root, decay, gain, modes, bend=0):
    """Excite independently damped inharmonic resonances, once per contact."""
    return ("strike", start, length, root, decay, gain, modes, bend)


BLADE = ((1, .62), (1.74, .27), (2.81, .17), (4.38, .08))
CATCH = ((1, .62), (2.37, .31), (3.13, .14))
MEMBRANE = ((1, .74), (1.56, .27), (2.19, .14), (3.45, .06))

# Times are seconds. Each entry is a separate performance and export.
SCORE = {
    "throw": (.218, [strike(0, .095, 1620, .020, .78, BLADE),
                     friction(.012, .192, 1800, 6100, 1.15, .65, .23)]),
    "hit": (.166, [strike(0, .080, 1080, .012, 1, BLADE, -.10),
                   friction(.008, .146, 2600, 540, .85, .54, .14)]),
    "block": (.152, [friction(0, .101, 3500, 1280, .7, .85, .3),
                     strike(.019, .119, 2170, .022, .48, CATCH, -.035)]),
    "enter": (.328, [friction(0, .186, 2850, 650, .9, .71, .18, 77),
                     friction(.055, .256, 1400, 360, .92, .68, .65),
                     strike(.008, .048, 1150, .009, .12, BLADE)]),
    "exit": (.191, [friction(0, .147, 590, 2000, 1.1, .67, .52, 51),
                    friction(.057, .118, 1400, 4000, .9, .30, .38)]),
    "dash": (.286, [strike(.001, .065, 960, .012, .31, BLADE),
                    friction(0, .176, 510, 2550, .84, .96, .34),
                    friction(.043, .227, 3200, 510, 1.35, .66, .23)]),
    "contact": (.129, [friction(0, .111, 3950, 910, 1.27, .91, .18, 108),
                       strike(.005, .061, 835, .009, .28, BLADE)]),
    "out": (.246, [friction(0, .194, 790, 3300, .98, .8, .30),
                   strike(.012, .08, 1270, .015, .41, BLADE),
                   strike(.158, .070, 580, .012, .32, CATCH)]),
    "return_vanish": (.169, [friction(0, .151, 3300, 790, .98, .88, .66),
                             strike(.002, .052, 580, .009, .18, CATCH)]),
    "return_arrive": (.158, [friction(0, .082, 840, 2000, .86, .34, .42),
                             strike(.029, .112, 1160, .019, .62, CATCH, .025)]),
    "windup": (.804, [strike(.002, .1, 1255, .019, .53, BLADE),
                      friction(.025, .205, 770, 1020, .94, .39, .26, 64),
                      friction(.115, .664, 510, 2250, 1.18, .76, .89, 14),
                      friction(.336, .436, 1250, 3310, 1.6, .31, .84)]),
    "blink": (.154, [friction(0, .115, 5900, 810, 1.35, .79, .14),
                     friction(.014, .123, 3000, 540, .86, .46, .29)]),
    "stab": (.202, [strike(.002, .103, 940, .016, .83, BLADE, -.07),
                    friction(0, .069, 2100, 900, .85, .83, .14),
                    strike(.009, .176, 174, .043, .54, MEMBRANE, -.22)]),
    "cancel": (.161, [friction(0, .117, 910, 390, .76, .61, .17, 55),
                      strike(.008, .118, 210, .019, .36, MEMBRANE, -.23)]),
    "shield_open": (.377, [strike(.002, .087, 1340, .017, .46, CATCH),
                           friction(.018, .278, 1220, 340, .89, .52, .54),
                           strike(.049, .31, 293, .077, .67, MEMBRANE, -.10)]),
    "absorb": (.141, [strike(.002, .125, 376, .026, .83, MEMBRANE, -.27),
                      friction(.001, .074, 850, 520, .78, .25, .17)]),
    "shield_break": (.210, [strike(0, .172, 286, .035, .55, MEMBRANE, -.29),
                            friction(.003, .142, 1650, 516, .85, .78, .19, 83),
                            friction(.047, .140, 970, 330, 1.13, .36, .20, 47)]),
}


def render(name, duration, layers):
    rng = random.Random(crc32(name.encode()))
    mix = [0.] * round(RATE * duration)
    for kind, start, length, *params in layers:
        offset, count = round(start * RATE), round(length * RATE)
        if kind == "air":
            low, high, q, gain, crest, grain = params
            last = prev = last_white = prev_white = 0.
        else:
            root, decay, gain, modes, bend = params
            phase = 0.
        for i in range(count):
            u = i / count
            t = i / RATE
            if kind == "air":
                omega = TAU / RATE * low * (high / low) ** u
                alpha = math.sin(omega) / (2 * q)
                white = rng.uniform(-1., 1.)
                band = (alpha * (white - prev_white) + 2 * math.cos(omega) * last
                        - (1 - alpha) * prev) / (1 + alpha)
                prev, last = last, band
                prev_white, last_white = last_white, white
                envelope = (u / crest) ** .72 if u < crest else ((1 - u) / (1 - crest)) ** 1.55
                texture = 1 if not grain else .64 + .36 * math.sin(TAU * (grain * t + .22 * math.sin(53 * t)))
                sample = gain * band * envelope * texture
            else:
                phase += TAU * root * (1 + bend * (1 - math.exp(-35 * t))) / RATE
                sample = gain * sum(weight * math.sin(phase * ratio) * math.exp(-t * ratio ** .4 / decay)
                                    for ratio, weight in modes)
            # A sub-millisecond attack and short release at every edit prevent clicks.
            mix[offset+i] += sample * min(1, i / 48) * min(1, (count-i) / 240)
    return mix


def encode(name, mix):
    path = OUTPUT / (name + ".ogg")
    peak = max(map(abs, mix))
    gain = 10 ** (-3 / 20) / peak
    for _ in range(3):
        samples = array.array("f", (sample * gain for sample in mix))
        subprocess.run([FFMPEG, "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE),
                        "-ac", "1", "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "7",
                        "-fflags", "+bitexact", str(path)], input=samples.tobytes(), check=True)
        decoded = array.array("f", subprocess.check_output(
            [FFMPEG, "-v", "error", "-i", str(path), "-f", "f32le", "-"]))
        db = 20 * math.log10(max(map(abs, decoded)))
        if abs(db + 3) < .25:
            break
        gain *= 10 ** ((-3 - db) / 20)
    print(f"{name:15}  {len(decoded)/RATE:.3f} s  {db:.2f} dBFS")


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for cue, (duration, layers) in SCORE.items():
        encode(cue, render(cue, duration, layers))
