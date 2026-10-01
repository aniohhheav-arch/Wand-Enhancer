"""Synthesised sound effects and music.

Every sound is generated from scratch (oscillators, filtered noise, FM bells, Karplus-Strong plucks, convolution
reverb) and written as mono Ogg Vorbis so Minecraft can position it in 3D. Generation is seeded and deterministic.
"""
import numpy as np
import soundfile as sf
from scipy import signal

from .common import ASSETS

SR = 44100
MUSIC_SR = 32000

# event -> options. "stream" marks long files Minecraft should stream instead of loading whole.
SOUND_EVENTS = {
    "rift.ambient": {}, "rift.open": {}, "rift.enter": {}, "portal.open": {}, "portal.enter": {}, "portal_gun.fire": {},
    "black_hole.ambient": {}, "black_hole.pull": {}, "black_hole.collapse": {}, "wormhole.travel": {"stream": True},
    "wormhole.emerge": {}, "universe.arrive": {}, "gravity.pulse": {}, "gravity.beam": {}, "singularity.implode": {},
    "blade.slash": {}, "blade.dash": {}, "energy.fire": {}, "energy.hit": {}, "glitch.noise": {}, "jelly.chime": {},
    "whale.call": {}, "drone.hum": {}, "stalker.hiss": {}, "wraith.scream": {}, "sentinel.step": {}, "warden.roar": {},
    "warden.charge": {}, "leviathan.roar": {}, "creature.hurt": {}, "creature.death": {}, "armor.equip": {},
    "ability.activate": {}, "ui.select": {}, "ui.manifest": {},
    "music.cosmic": {"stream": True}, "music.neon": {"stream": True}, "music.dream": {"stream": True},
    "music.void": {"stream": True}, "music.nexus": {"stream": True}, "music.ocean": {"stream": True},
    "music.ancient": {"stream": True},
}


# ------------------------------------------------------------------------------------------------------- DSP toolkit

def n_of(dur, sr=SR):
    return int(round(dur * sr))


def taxis(dur, sr=SR):
    return np.arange(n_of(dur, sr)) / sr


def phase_of(freq, n, sr=SR):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return 2 * np.pi * np.cumsum(f) / sr


def sine(freq, dur, sr=SR, phase=0.0):
    n = n_of(dur, sr)
    return np.sin(phase_of(freq, n, sr) + phase)


def saw(freq, dur, sr=SR):
    n = n_of(dur, sr)
    ph = phase_of(freq, n, sr) / (2 * np.pi)
    return 2 * (ph % 1.0) - 1


def square(freq, dur, sr=SR, duty=0.5):
    n = n_of(dur, sr)
    ph = phase_of(freq, n, sr) / (2 * np.pi)
    return np.where(ph % 1.0 < duty, 1.0, -1.0)


def noise(dur, seed, sr=SR, color="white"):
    n = n_of(dur, sr)
    x = np.random.default_rng(seed).standard_normal(n)
    if color == "white":
        return x / 3
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / sr)
    f[0] = f[1] if n > 1 else 1
    spec *= (1 / np.sqrt(f)) if color == "pink" else (1 / f)
    y = np.fft.irfft(spec, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def sos(kind, cutoff, sr=SR, order=2):
    nyq = sr / 2
    if kind == "bandpass":
        lo, hi = cutoff
        return signal.butter(order, [max(10, lo) / nyq, min(hi, nyq * 0.98) / nyq], btype="bandpass", output="sos")
    return signal.butter(order, min(cutoff, nyq * 0.98) / nyq, btype=kind, output="sos")


def lowpass(x, cutoff, sr=SR, order=2):
    return signal.sosfilt(sos("lowpass", cutoff, sr, order), x)


def highpass(x, cutoff, sr=SR, order=2):
    return signal.sosfilt(sos("highpass", cutoff, sr, order), x)


def bandpass(x, lo, hi, sr=SR, order=2):
    return signal.sosfilt(sos("bandpass", (lo, hi), sr, order), x)


def sweep(x, cutoffs, kind="lowpass", sr=SR, block=256, width=0.6, order=2):
    """Time-varying filter: cutoffs is an array (Hz) per sample; bandpass uses +-width octaves around it."""
    out = np.zeros_like(x)
    zi = None
    for i in range(0, len(x), block):
        c = float(np.mean(cutoffs[i:i + block]))
        s = sos("bandpass", (c * 2 ** -width, c * 2 ** width), sr, order) if kind == "bandpass" else sos(kind, c, sr, order)
        if zi is None:
            zi = np.zeros((s.shape[0], 2))
        out[i:i + block], zi = signal.sosfilt(s, x[i:i + block], zi=zi)
    return out


def env_exp(dur, decay, sr=SR, attack=0.002):
    t = taxis(dur, sr)
    e = np.exp(-t / max(decay, 1e-4))
    if attack > 0:
        e *= np.clip(t / attack, 0, 1)
    return e


def env_adsr(dur, a, d, s, r, sr=SR):
    n = n_of(dur, sr)
    t = np.arange(n) / sr
    e = np.where(t < a, t / max(a, 1e-4), s + (1 - s) * np.exp(-(t - a) / max(d, 1e-4)))
    rel_start = max(0.0, dur - r)
    e *= np.where(t > rel_start, np.clip(1 - (t - rel_start) / max(r, 1e-4), 0, 1), 1)
    return e


def line(dur, a, b, sr=SR, curve=1.0):
    t = np.linspace(0, 1, n_of(dur, sr))
    return a + (b - a) * t ** curve


def expline(dur, a, b, sr=SR):
    return a * (b / a) ** np.linspace(0, 1, n_of(dur, sr))


def fm(carrier, ratio, index, dur, sr=SR, index_env=None):
    n = n_of(dur, sr)
    mod = np.sin(phase_of(np.asarray(carrier) * ratio, n, sr))
    idx = index if index_env is None else index * index_env
    return np.sin(phase_of(carrier, n, sr) + idx * mod)


def bell(freq, dur, sr=SR, bright=1.0, decay=1.2):
    e = env_exp(dur, decay, sr, 0.001)
    tone = fm(freq, 3.5, 2.2 * bright, dur, sr, index_env=env_exp(dur, decay * 0.4, sr)) * 0.7
    tone += fm(freq * 2.01, 1.41, 0.6, dur, sr) * 0.2 * env_exp(dur, decay * 0.5, sr)
    return tone * e


def pluck(freq, dur, seed, sr=SR, damp=0.996, bright=0.5):
    """Karplus-Strong string, vectorised one period at a time."""
    n = n_of(dur, sr)
    p = max(2, int(round(sr / freq)))
    rng = np.random.default_rng(seed)
    y = np.zeros(n + p + 1)
    burst = rng.uniform(-1, 1, p)
    burst = lowpass(burst, 1000 + 7000 * bright, sr)
    y[1:p + 1] = burst
    k = p + 1
    while k < n + 1:
        end = min(k + p, n + 1)
        a = y[k - p:end - p]
        b = y[k - p - 1:end - p - 1]
        y[k:end] = damp * 0.5 * (a + b)
        k = end
    return y[1:n + 1]


def pad(freqs, dur, sr=SR, seed=0, cutoff=1800, attack=1.5, release=2.0, detune=0.006, voices=3):
    out = np.zeros(n_of(dur, sr))
    rng = np.random.default_rng(seed)
    for f in freqs:
        for v in range(voices):
            d = 1 + detune * (v - (voices - 1) / 2) + rng.uniform(-0.001, 0.001)
            out += saw(f * d, dur, sr) * 0.5 + sine(f * d * 0.5, dur, sr) * 0.3
    out = lowpass(out, cutoff, sr, 2)
    return out * env_adsr(dur, attack, 1.0, 0.85, release, sr) / (len(freqs) * voices)


def reverb(x, sr=SR, seconds=2.5, mix=0.35, seed=7, damp=5000, predelay=0.02):
    n = n_of(seconds, sr)
    t = np.arange(n) / sr
    ir = np.random.default_rng(seed).standard_normal(n) * np.exp(-t * 6.9 / seconds)
    ir = lowpass(ir, damp, sr)
    ir = np.concatenate([np.zeros(n_of(predelay, sr)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(x, ir)[:len(x) + len(ir) - 1]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1 - mix) + wet * mix * 0.9


def trim_tail(x, sr=SR, threshold=1e-4, pad_s=0.05):
    idx = np.nonzero(np.abs(x) > threshold * np.max(np.abs(x)))[0]
    if len(idx) == 0:
        return x
    return x[:min(len(x), idx[-1] + n_of(pad_s, sr))]


def fade(x, sr=SR, fin=0.005, fout=0.05):
    y = x.copy()
    a, b = n_of(fin, sr), n_of(fout, sr)
    if a > 0:
        y[:a] *= np.linspace(0, 1, a)
    if b > 0:
        y[-b:] *= np.linspace(1, 0, b)
    return y


def loopify(x, sr=SR, cross=0.5):
    """Crossfades the tail into the head so the sound loops seamlessly."""
    c = n_of(cross, sr)
    head, body, tail = x[:c], x[c:-c], x[-c:]
    w = np.linspace(0, 1, c)
    return np.concatenate([tail * (1 - w) + head * w, body])


def soft_clip(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def at(x, start, total, sr=SR):
    out = np.zeros(n_of(total, sr))
    s = n_of(start, sr)
    e = min(len(out), s + len(x))
    if s < len(out):
        out[s:e] += x[:e - s]
    return out


def normalize(x, peak=0.89):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ------------------------------------------------------------------------------------------------------ sound design

def snd_rift_ambient():
    d = 6.5
    t = taxis(d)
    drone = sine(55, d) * 0.5 + sine(82.5 * 1.003, d) * 0.3 + sine(110.2, d) * 0.15
    drone *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.31 * t)
    shimmer = sum(sine(f, d) * (0.5 + 0.5 * np.sin(2 * np.pi * r * t + f)) for f, r in [(880, 0.7), (1320, 0.43), (1975, 0.9)]) * 0.05
    crackle = bandpass(noise(d, 11), 1500, 5000) * (np.random.default_rng(12).random(n_of(d)) > 0.9985) * 6
    air = sweep(noise(d, 13, color="pink"), 600 + 400 * np.sin(2 * np.pi * 0.17 * t), "bandpass") * 0.25
    return loopify(reverb(drone + shimmer + crackle + air, seconds=2.0, mix=0.3)[:n_of(d)], cross=0.6)


def snd_rift_open():
    d = 2.4
    t = taxis(d)
    whoosh = sweep(noise(d, 21), expline(d, 200, 5000), "bandpass", width=0.8) * env_adsr(d, 0.6, 0.4, 0.5, 1.2) * 1.4
    boom = sine(expline(d, 90, 38), d) * env_exp(d, 0.5, attack=0.01) * 0.9
    chime = sum(bell(f, d, decay=0.9) * 0.18 for f in (1046, 1568, 2093))
    chime = at(chime, 0.25, d)
    return reverb(whoosh + boom + chime, seconds=2.2, mix=0.35)


def snd_rift_enter():
    d = 2.6
    swell = sweep(noise(d, 31, color="pink"), expline(d, 3000, 120), "lowpass") * env_adsr(d, 1.6, 0.2, 0.8, 0.5) * 1.5
    rev = reverb(bell(660, 1.0) * 0.4, seconds=1.6, mix=0.8)[::-1]
    thump = at(sine(expline(0.8, 70, 30), 0.8) * env_exp(0.8, 0.3), 1.7, d)
    return mix(swell, at(rev, 0.0, d), thump)


def snd_portal_open():
    d = 1.3
    f = expline(d, 180, 900)
    body = fm(f, 1.5, 3.0, d, index_env=env_exp(d, 0.4)) * env_adsr(d, 0.05, 0.3, 0.6, 0.6) * 0.6
    sh = sweep(noise(d, 41), expline(d, 800, 6000), "bandpass") * env_adsr(d, 0.1, 0.3, 0.4, 0.7) * 0.6
    return reverb(body + sh, seconds=1.2, mix=0.3)


def snd_portal_enter():
    d = 1.0
    wh = sweep(noise(d, 51), expline(d, 4000, 300), "bandpass") * env_adsr(d, 0.15, 0.2, 0.5, 0.5)
    bloop = sine(expline(d, 600, 180), d) * env_exp(d, 0.25, attack=0.02) * 0.6
    return reverb(wh + bloop, seconds=0.9, mix=0.3)


def snd_portal_gun_fire():
    d = 0.55
    f = expline(d, 2400, 280)
    zap = fm(f, 2.0, 4.0, d, index_env=env_exp(d, 0.1)) * env_exp(d, 0.18, attack=0.002)
    click = highpass(noise(0.03, 61), 3000) * env_exp(0.03, 0.01) * 2
    return reverb(mix(zap * 0.8, click), seconds=0.6, mix=0.2)


def snd_black_hole_ambient():
    d = 8.0
    t = taxis(d)
    sub = sine(32, d) * 0.6 + sine(41 * (1 + 0.003 * np.sin(2 * np.pi * 0.2 * t)), d) * 0.4
    roar = lowpass(noise(d, 71, color="brown"), 220) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.13 * t)) * 1.4
    whine = sine(220 + 15 * np.sin(2 * np.pi * 0.09 * t), d) * 0.04
    return loopify(soft_clip((sub + roar + whine) * 0.8, 1.5), cross=1.0)


def snd_black_hole_pull():
    d = 4.0
    t = taxis(d)
    rise = t / d
    rumble = sweep(noise(d, 81, color="brown"), 120 + 300 * rise, "lowpass") * (0.4 + rise) * 1.4
    whine = sine(expline(d, 160, 1400), d) * rise ** 2 * 0.25
    whine += sine(expline(d, 163, 1430), d) * rise ** 2 * 0.2
    air = sweep(noise(d, 82), expline(d, 300, 4000), "bandpass") * rise * 0.5
    return reverb(fade(soft_clip(rumble + whine + air, 1.3), fout=0.25), seconds=1.5, mix=0.25)


def snd_black_hole_collapse():
    d = 3.2
    suck = sweep(noise(1.0, 91), expline(1.0, 6000, 200), "bandpass") * np.linspace(0.2, 1.2, n_of(1.0)) ** 2
    boom = sine(expline(2.2, 80, 24), 2.2) * env_exp(2.2, 0.8, attack=0.005) * 1.2
    crack = lowpass(noise(0.5, 92), 2500) * env_exp(0.5, 0.08) * 1.5
    tail = lowpass(noise(2.2, 93, color="brown"), 150) * env_exp(2.2, 0.9)
    impact = fade(boom + at(crack, 0, 2.2) + tail, fin=0.0, fout=0.6)
    return reverb(soft_clip(mix(at(suck, 0, d), at(impact, 1.0, d)), 1.6), seconds=3.0, mix=0.35)


def snd_wormhole_travel():
    """Twelve seconds of hyperspace: rushing wind, an endless Shepard-Risset glissando and a swelling choir."""
    d = 12.0
    sr = SR
    t = taxis(d)
    shepard = np.zeros(len(t))
    cycles = 7
    for k in range(cycles):
        pos = ((t / d) * 1.4 + k / cycles) % 1.0
        f = 55 * 2 ** (pos * cycles)
        amp = np.exp(-((pos - 0.5) ** 2) / 0.045)
        shepard += np.sin(phase_of(f, len(t), sr)) * amp
    shepard = lowpass(shepard, 3500) * 0.22
    wind = sweep(noise(d, 101, color="pink"), 700 + 2200 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.21 * t)), "bandpass", width=0.9) * 0.9
    choir = pad([midi(50), midi(57), midi(62), midi(66), midi(69)], d, cutoff=1400, attack=3.0, release=2.5, seed=102) * 2.2
    choir = sweep(choir, 600 + 900 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.11 * t)), "bandpass", width=1.2)
    sub = sine(36, d) * 0.3 * env_adsr(d, 2.0, 1.0, 1.0, 2.0)
    out = (shepard + wind + choir + sub) * env_adsr(d, 1.2, 1.0, 1.0, 1.5)
    return reverb(out, seconds=3.0, mix=0.3)[:n_of(d)]


def snd_wormhole_emerge():
    d = 4.0
    sh = sweep(noise(d, 111), expline(d, 9000, 800), "bandpass") * env_adsr(d, 0.4, 0.4, 0.3, 2.5) * 0.8
    hit = sine(expline(1.5, 110, 45), 1.5) * env_exp(1.5, 0.5) * 0.8
    chord = pad([midi(62), midi(66), midi(69), midi(73)], d, cutoff=3000, attack=0.6, release=2.4, seed=112) * 2
    return reverb(mix(sh, at(hit, 0.35, d), at(chord, 0.3, d)), seconds=3.5, mix=0.45)


def snd_universe_arrive():
    d = 5.5
    notes = [midi(n) for n in (60, 64, 67, 71, 74)]
    chord = pad(notes, d, cutoff=2500, attack=1.2, release=2.5, seed=121) * 2.2
    bells = mix(*[at(bell(midi(n), 3.0, decay=1.4) * 0.22, 0.4 + i * 0.22, d) for i, n in enumerate((84, 88, 91, 95))])
    return reverb(chord + bells, seconds=4.0, mix=0.5)


def snd_gravity_pulse():
    d = 1.2
    t = taxis(d)
    wub = sine(55 + 25 * np.sin(2 * np.pi * 6 * t), d) * env_exp(d, 0.45, attack=0.02)
    whoom = lowpass(noise(d, 131), expline(d, 2000, 150)[0]) * env_exp(d, 0.2)
    return reverb(soft_clip(wub + whoom * 0.5, 1.5), seconds=1.0, mix=0.25)


def snd_gravity_beam():
    d = 3.0
    t = taxis(d)
    hum = (saw(110, d) * 0.3 + sine(220, d) * 0.4 + sine(331, d) * 0.15) * (0.75 + 0.25 * np.sin(2 * np.pi * 8 * t))
    hum = lowpass(hum, 1200)
    fizz = bandpass(noise(d, 141), 3000, 7000) * 0.08 * (0.5 + 0.5 * np.sin(2 * np.pi * 11 * t))
    return loopify(hum + fizz, cross=0.4)


def snd_singularity_implode():
    d = 2.0
    suck = sweep(noise(0.9, 151), expline(0.9, 5000, 150), "bandpass") * np.linspace(0.1, 1.0, n_of(0.9)) ** 2 * 1.2
    thump = sine(expline(1.1, 120, 35), 1.1) * env_exp(1.1, 0.35, attack=0.003)
    return reverb(mix(at(suck, 0, d), at(thump, 0.85, d)), seconds=1.6, mix=0.3)


def snd_blade_slash():
    d = 0.45
    swish = sweep(noise(d, 161), expline(d, 900, 5000), "bandpass", width=0.5) * env_adsr(d, 0.05, 0.1, 0.4, 0.25) * 1.5
    ring = fm(1840, 1.41, 1.5, d) * env_exp(d, 0.15) * 0.15
    return reverb(swish + ring, seconds=0.6, mix=0.2)


def snd_blade_dash():
    d = 0.7
    wh = sweep(noise(d, 171), expline(d, 300, 6000), "bandpass") * env_adsr(d, 0.08, 0.1, 0.6, 0.4) * 1.3
    zap = sine(expline(d, 1600, 400), d) * env_exp(d, 0.15) * 0.3
    return reverb(wh + zap, seconds=0.8, mix=0.25)


def snd_energy_fire():
    d = 0.5
    shot = fm(expline(d, 900, 160), 1.0, 5.0, d, index_env=env_exp(d, 0.06)) * env_exp(d, 0.14, attack=0.003)
    burst = bandpass(noise(0.1, 181), 1000, 6000) * env_exp(0.1, 0.03)
    return reverb(mix(shot * 0.8, burst), seconds=0.5, mix=0.2)


def snd_energy_hit():
    d = 0.6
    crack = lowpass(noise(d, 191), 4000) * env_exp(d, 0.06) * 1.3
    boom = sine(expline(d, 140, 50), d) * env_exp(d, 0.18) * 0.8
    fizz = bandpass(noise(d, 192), 4000, 9000) * env_exp(d, 0.2) * 0.3
    return reverb(crack + boom + fizz, seconds=0.7, mix=0.25)


def snd_glitch_noise():
    d = 0.8
    rng = np.random.default_rng(201)
    out = np.zeros(n_of(d))
    pos = 0
    while pos < len(out):
        seg = n_of(rng.uniform(0.015, 0.07))
        kind = rng.integers(0, 3)
        segd = seg / SR
        if kind == 0:
            s = square(rng.uniform(200, 2400), segd, duty=rng.uniform(0.1, 0.5))
        elif kind == 1:
            s = noise(segd, int(rng.integers(0, 1e6)))
        else:
            s = sine(rng.uniform(60, 900), segd)
        s = np.round(s * 4) / 4
        out[pos:pos + seg] = s[:len(out) - pos] * rng.uniform(0.3, 0.9)
        pos += seg + n_of(rng.uniform(0, 0.03))
    return fade(out * env_adsr(d, 0.01, 0.2, 0.8, 0.2) * 0.7)


def snd_jelly_chime():
    d = 2.0
    notes = [79, 83, 86, 91]
    rng = np.random.default_rng(211)
    rng.shuffle(notes)
    return reverb(mix(*[at(bell(midi(n), 1.6, decay=0.8, bright=0.6) * 0.3, i * 0.13, d) for i, n in enumerate(notes)]), seconds=2.5, mix=0.5)


def snd_whale_call():
    d = 4.0
    t = taxis(d)
    f = 110 + 70 * np.sin(np.pi * t / d) ** 2 - 25 * (t / d) + 4 * np.sin(2 * np.pi * 5 * t)
    voice = saw(f, d) * 0.5 + sine(f, d) * 0.5
    voice = sweep(voice, 500 + 500 * np.sin(np.pi * t / d), "bandpass", width=0.7) * env_adsr(d, 0.6, 0.5, 0.9, 1.2) * 1.4
    return reverb(voice, seconds=4.0, mix=0.55, damp=3000)


def snd_drone_hum():
    d = 2.5
    t = taxis(d)
    hum = saw(120, d) * 0.25 + square(240, d, duty=0.3) * 0.08 + sine(60, d) * 0.3
    rotor = bandpass(noise(d, 221), 400, 2500) * (0.6 + 0.4 * np.sin(2 * np.pi * 37 * t)) * 0.4
    return loopify(lowpass(hum, 1600) + rotor, cross=0.3)


def snd_stalker_hiss():
    d = 1.3
    breath = sweep(noise(d, 231), line(d, 2500, 4500), "bandpass", width=0.5) * env_adsr(d, 0.1, 0.3, 0.7, 0.6) * 1.2
    growl = lowpass(saw(48 + 6 * np.sin(2 * np.pi * 9 * taxis(d)), d), 400) * env_adsr(d, 0.2, 0.3, 0.5, 0.5) * 0.5
    return reverb(breath + growl, seconds=1.2, mix=0.3)


def snd_wraith_scream():
    d = 1.6
    t = taxis(d)
    f = expline(d, 1400, 520) * (1 + 0.03 * np.sin(2 * np.pi * 7 * t))
    voice = fm(f, 1.01, 2.5, d) * 0.5 + fm(f * 1.5, 0.5, 1.0, d) * 0.3
    voice = bandpass(voice, 500, 5000) * env_adsr(d, 0.04, 0.3, 0.7, 0.7)
    breath = bandpass(noise(d, 241), 2000, 7000) * env_adsr(d, 0.05, 0.2, 0.4, 0.8) * 0.3
    return reverb(voice + breath, seconds=2.4, mix=0.45)


def snd_sentinel_step():
    d = 0.6
    thump = sine(expline(d, 85, 40), d) * env_exp(d, 0.12, attack=0.002)
    clink = mix(bell(2637, d, decay=0.15, bright=0.4) * 0.2, bell(3520, d, decay=0.1, bright=0.4) * 0.12)
    grit = lowpass(noise(0.12, 251), 2500) * env_exp(0.12, 0.03) * 0.6
    return reverb(mix(thump, clink, grit), seconds=0.6, mix=0.2)


def snd_warden_roar():
    d = 3.2
    t = taxis(d)
    f = 70 + 25 * np.sin(np.pi * t / d) + 6 * np.sin(2 * np.pi * 13 * t)
    growl = saw(f, d) + saw(f * 1.01, d) * 0.7 + saw(f * 0.5, d) * 0.6
    growl = sweep(growl, 300 + 1500 * np.sin(np.pi * t / d), "lowpass") * env_adsr(d, 0.15, 0.5, 0.85, 0.9)
    breath = sweep(noise(d, 261, color="pink"), 600 + 1500 * np.sin(np.pi * t / d), "bandpass") * env_adsr(d, 0.2, 0.5, 0.8, 1.0) * 0.8
    sub = sine(35, d) * env_adsr(d, 0.3, 0.5, 0.8, 1.0) * 0.6
    return reverb(soft_clip((growl * 0.5 + breath + sub) * 1.3, 2.0), seconds=3.0, mix=0.4)


def snd_warden_charge():
    d = 2.0
    t = taxis(d)
    f = expline(d, 140, 1100)
    whine = (sine(f, d) + sine(f * 1.5, d) * 0.5 + sine(f * 2.02, d) * 0.3) * (0.6 + 0.4 * np.sin(2 * np.pi * expline(d, 6, 22) * t))
    crackle = bandpass(noise(d, 271), 3000, 9000) * (t / d) ** 2 * 0.4
    return reverb((whine * 0.4 + crackle) * np.clip(t / 0.2, 0, 1) * env_adsr(d, 0.01, 1, 1, 0.15), seconds=1.5, mix=0.3)


def snd_leviathan_roar():
    d = 4.5
    t = taxis(d)
    f = 48 + 30 * np.sin(np.pi * t / d) ** 1.5 + 3 * np.sin(2 * np.pi * 7 * t)
    body = saw(f, d) * 0.6 + saw(f * 2.003, d) * 0.3 + sine(f * 0.5, d) * 0.6
    body = sweep(body, 200 + 900 * np.sin(np.pi * t / d), "lowpass") * env_adsr(d, 0.4, 0.6, 0.85, 1.4)
    song = sweep(saw(f * 4, d), 900 + 400 * np.sin(np.pi * t / d), "bandpass", width=0.4) * env_adsr(d, 0.8, 0.5, 0.7, 1.4) * 0.3
    water = lowpass(noise(d, 281, color="brown"), 300) * env_adsr(d, 0.5, 1.0, 0.6, 1.5) * 0.6
    return reverb(soft_clip(body + song + water, 1.6), seconds=4.5, mix=0.5, damp=2500)


def snd_creature_hurt():
    d = 0.4
    f = expline(d, 900, 380)
    chirp = fm(f, 2.0, 2.0, d, index_env=env_exp(d, 0.1)) * env_exp(d, 0.12, attack=0.004)
    return reverb(chirp * 0.8, seconds=0.5, mix=0.2)


def snd_creature_death():
    d = 1.3
    t = taxis(d)
    f = expline(d, 700, 90) * (1 + 0.05 * np.sin(2 * np.pi * 9 * t))
    warble = fm(f, 1.5, 2.0, d) * env_adsr(d, 0.01, 0.3, 0.6, 0.8) * 0.6
    dissolve = sweep(noise(d, 291), expline(d, 4000, 300), "bandpass") * env_adsr(d, 0.3, 0.3, 0.5, 0.6) * 0.5
    return reverb(warble + dissolve, seconds=1.4, mix=0.35)


def snd_armor_equip():
    d = 0.7
    clink = mix(bell(1318, d, decay=0.12, bright=0.5) * 0.3, bell(1760, d, decay=0.1, bright=0.5) * 0.2)
    hum = sine(expline(d, 200, 420), d) * env_adsr(d, 0.1, 0.2, 0.5, 0.3) * 0.3
    return reverb(clink + hum, seconds=0.8, mix=0.25)


def snd_ability_activate():
    d = 1.2
    swell = sine(expline(d, 220, 880), d) * env_adsr(d, 0.4, 0.2, 0.8, 0.4) * 0.35
    swell += sine(expline(d, 330, 1320), d) * env_adsr(d, 0.4, 0.2, 0.8, 0.4) * 0.2
    chime = at(bell(1760, 0.8, decay=0.4) * 0.3, 0.38, d)
    sh = sweep(noise(d, 301), expline(d, 500, 8000), "bandpass") * env_adsr(d, 0.4, 0.1, 0.3, 0.5) * 0.4
    return reverb(swell + chime + sh, seconds=1.2, mix=0.3)


def snd_ui_select():
    d = 0.16
    return fade(sine(1320, d) * env_exp(d, 0.04, attack=0.001) * 0.5 + sine(2640, d) * env_exp(d, 0.02) * 0.15)


def snd_ui_manifest():
    d = 3.5
    arp = mix(*[at(bell(midi(n), 1.5, decay=0.6) * 0.22, i * 0.09, d) for i, n in enumerate((72, 76, 79, 83, 84, 88, 91, 95, 96))])
    wh = sweep(noise(d, 311), expline(d, 400, 9000), "bandpass") * env_adsr(d, 0.9, 0.3, 0.3, 1.5) * 0.4
    chord = at(pad([midi(n) for n in (60, 64, 67, 71)], 2.6, cutoff=3000, attack=0.2, release=1.8, seed=312) * 2, 0.8, d)
    return reverb(arp + wh + chord, seconds=3.0, mix=0.45)


# --------------------------------------------------------------------------------------------------------------- music

def track(length, bpm):
    return np.zeros(n_of(length, MUSIC_SR)), 60.0 / bpm


def place(buf, x, start):
    s = n_of(start, MUSIC_SR)
    if s >= len(buf):
        return
    e = min(len(buf), s + len(x))
    buf[s:e] += x[:e - s]


def finish(buf, seconds=4.0, mix_amt=0.4, damp=6000):
    out = reverb(buf, MUSIC_SR, seconds=seconds, mix=mix_amt, damp=damp)[:len(buf)]
    out = fade(out, MUSIC_SR, 1.5, 4.0)
    return normalize(soft_clip(normalize(out, 0.95), 1.2), 0.85)


def music_cosmic():
    sr = MUSIC_SR
    buf, beat = track(64, 60)
    chords = [[50, 57, 61, 64, 68], [52, 59, 64, 68, 71], [47, 54, 61, 62, 66], [43, 50, 57, 61, 66]]
    for i in range(8):
        ch = chords[i % 4]
        place(buf, pad([midi(n) for n in ch], 8.6, sr, seed=400 + i, cutoff=1500, attack=2.5, release=3.0) * 3.0, i * 8)
    place(buf, sine(midi(26), 64, sr) * 0.12 * env_adsr(64, 4, 1, 1, 4, sr), 0)
    rng = np.random.default_rng(401)
    scale = [62, 64, 66, 68, 69, 71, 73, 74, 76, 78, 81]
    for k in range(48):
        t0 = 4 + k * 1.25 + rng.uniform(-0.1, 0.1)
        if t0 > 60:
            break
        place(buf, bell(midi(int(rng.choice(scale)) + 12), 3.0, sr, bright=0.5, decay=1.5) * 0.12, t0)
    return finish(buf, 5.0, 0.5)


def music_neon():
    sr = MUSIC_SR
    buf, beat = track(64, 100)
    bars = int(64 / (4 * beat))
    prog = [(45, [57, 60, 64]), (41, [53, 57, 60]), (48, [55, 60, 64]), (43, [55, 59, 62])]
    for b in range(bars):
        root, triad = prog[(b // 2) % 4]
        start = b * 4 * beat
        place(buf, pad([midi(n) for n in triad], 4 * beat + 0.4, sr, seed=500 + b, cutoff=1300, attack=0.05, release=0.4) * 2.2, start)
        for s in range(8):
            note = root - 12 if s % 4 != 3 else root - 5
            tone = (saw(midi(note), beat * 0.45, sr) * 0.6 + square(midi(note), beat * 0.45, sr) * 0.3)
            tone = lowpass(tone, 700, sr) * env_exp(beat * 0.45, 0.16, sr)
            place(buf, tone * 0.5, start + s * beat / 2)
        if b >= 4:
            arp_notes = triad + [triad[0] + 12]
            for s in range(16):
                n = arp_notes[s % 4] + 12
                tone = lowpass(saw(midi(n), beat * 0.22, sr), 2600, sr) * env_exp(beat * 0.22, 0.07, sr)
                place(buf, tone * 0.16, start + s * beat / 4)
        for s in range(8):
            hat = highpass(noise(0.05, 510 + b * 8 + s, sr), 7000, sr) * env_exp(0.05, 0.012, sr)
            place(buf, hat * (0.25 if s % 2 else 0.12), start + s * beat / 2)
        for s in (0, 2):
            kick = sine(expline(0.3, 120, 45, sr), 0.3, sr) * env_exp(0.3, 0.09, sr)
            place(buf, kick * 0.7, start + s * beat * 2)
    return finish(buf, 2.5, 0.3)


def music_dream():
    sr = MUSIC_SR
    buf, beat = track(64, 72)
    prog = [[53, 57, 60, 64], [50, 53, 57, 60], [46, 50, 53, 57], [48, 52, 55, 58]]
    bar = 4 * beat
    bars = int(64 / bar)
    for b in range(bars):
        place(buf, pad([midi(n) for n in prog[b % 4]], bar + 1.0, sr, seed=600 + b, cutoff=1100, attack=1.0, release=1.5) * 2.2, b * bar)
    melody = [77, 76, 72, 74, 72, 69, 70, 72, 77, 79, 81, 79, 77, 76, 74, 72]
    for i in range(bars * 4):
        n = melody[i % len(melody)]
        if i % 8 == 7:
            continue
        box = fm(midi(n), 4.0, 1.2, 1.8, sr, index_env=env_exp(1.8, 0.2, sr)) * env_exp(1.8, 0.7, sr)
        place(buf, box * 0.14, i * beat + 0.02 * np.sin(i))
    return finish(buf, 5.0, 0.55, damp=4500)


def music_void():
    sr = MUSIC_SR
    buf, _ = track(64, 60)
    t = taxis(64, sr)
    drone = sine(midi(25), 64, sr) * 0.35 + sine(midi(25) * 1.005, 64, sr) * 0.3 + sine(midi(32) * 0.997, 64, sr) * 0.12
    buf += drone * env_adsr(64, 6, 1, 1, 6, sr)
    for i, cl in enumerate([[49, 50, 56], [48, 49, 55, 60], [50, 51, 57], [49, 52, 53]]):
        place(buf, pad([midi(n) for n in cl], 15, sr, seed=700 + i, cutoff=700, attack=5, release=5) * 2.0, 2 + i * 15)
    rng = np.random.default_rng(701)
    for k in range(9):
        hit = mix(bell(midi(int(rng.integers(30, 42))), 4.0, sr, bright=1.6, decay=1.8) * 0.2,
                  lowpass(noise(2.0, 702 + k, sr, "brown"), 200, sr) * env_exp(2.0, 0.6, sr) * 0.3)
        place(buf, hit, 3 + k * 6.7 + rng.uniform(0, 2))
    whisper = sweep(noise(64, 703, sr), 1500 + 800 * np.sin(2 * np.pi * 0.05 * t), "bandpass", sr, width=0.3) * 0.05
    buf += whisper * (0.5 + 0.5 * np.sin(2 * np.pi * 0.031 * t))
    return finish(buf, 6.0, 0.55, damp=3000)


def music_nexus():
    sr = MUSIC_SR
    buf, beat = track(64, 66)
    prog = [[48, 55, 59, 64, 67], [45, 52, 55, 60, 64], [41, 48, 52, 57, 64], [43, 50, 55, 59, 64]]
    bar = 4 * beat
    bars = int(64 / bar)
    for b in range(bars):
        ch = prog[b % 4]
        place(buf, pad([midi(n) for n in ch], bar + 1.2, sr, seed=800 + b, cutoff=1900, attack=1.2, release=1.8, voices=4) * 2.6, b * bar)
        timp = sine(expline(1.5, midi(ch[0] - 12) * 1.5, midi(ch[0] - 12), sr), 1.5, sr) * env_exp(1.5, 0.5, sr)
        place(buf, timp * 0.45, b * bar)
    melody = [76, 79, 81, 79, 76, 74, 72, 74, 76, 77, 79, 84, 83, 79, 76, 74]
    for i in range(bars * 2):
        if i < 2:
            continue
        place(buf, bell(midi(melody[i % len(melody)]), 2.4, sr, bright=0.7, decay=1.1) * 0.16, i * 2 * beat)
    return finish(buf, 5.0, 0.5)


def music_ocean():
    sr = MUSIC_SR
    buf, beat = track(64, 56)
    t = taxis(64, sr)
    waves = lowpass(noise(64, 900, sr, "pink"), 900, sr) * (0.35 + 0.35 * np.sin(2 * np.pi * t / 7.5) ** 2) * 0.5
    buf += waves
    prog = [[52, 59, 63, 66], [49, 56, 61, 64], [45, 52, 57, 61], [47, 54, 59, 63]]
    bar = 4 * beat
    for b in range(int(64 / bar)):
        place(buf, pad([midi(n) for n in prog[b % 4]], bar + 1.5, sr, seed=901 + b, cutoff=1200, attack=2.0, release=2.0) * 2.2, b * bar)
    for k in range(5):
        d = 4.0
        tt = taxis(d, sr)
        f = midi(64 + k % 3 * 3) * (1 + 0.12 * np.sin(np.pi * tt / d))
        glide = sine(f, d, sr) * env_adsr(d, 1.0, 0.5, 0.8, 1.5, sr) * 0.12
        place(buf, glide, 6 + k * 11.5)
    rng = np.random.default_rng(902)
    for k in range(40):
        n = int(rng.choice([64, 66, 68, 71, 73, 76, 78]))
        place(buf, pluck(midi(n), 2.0, 903 + k, sr, damp=0.995, bright=0.3) * 0.18, 3 + k * 1.45)
    return finish(buf, 5.0, 0.5, damp=4000)


def music_ancient():
    sr = MUSIC_SR
    buf, beat = track(64, 80)
    buf += (sine(midi(38), 64, sr) * 0.25 + sine(midi(45), 64, sr) * 0.15) * env_adsr(64, 4, 1, 1, 4, sr)
    dorian = [62, 64, 65, 67, 69, 71, 72, 74]
    patterns = [[0, 2, 4, 7, 4, 2], [1, 3, 5, 7, 5, 3], [0, 4, 6, 4, 2, 4], [2, 4, 7, 5, 4, 1]]
    bar = 4 * beat
    bars = int(64 / bar)
    for b in range(bars):
        pat = patterns[b % 4]
        for i, deg in enumerate(pat):
            place(buf, pluck(midi(dorian[deg]), 2.5, 1000 + b * 10 + i, sr, damp=0.997, bright=0.6) * 0.3, b * bar + i * (bar / len(pat)))
        for s in (0, 1.5, 2, 3):
            drum = mix(sine(expline(0.5, 110, 55, sr), 0.5, sr) * env_exp(0.5, 0.12, sr),
                       lowpass(noise(0.2, 1100 + b * 4 + int(s * 2), sr), 1500, sr) * env_exp(0.2, 0.03, sr) * 0.4)
            place(buf, drum * (0.55 if s == 0 else 0.3), b * bar + s * beat)
    flute = [74, 72, 69, 67, 69, 65, 64, 62]
    for i in range(10):
        if 16 + i * 4 * beat * 1.5 > 60:
            break
        n = flute[i % len(flute)]
        d = 1.8
        tt = taxis(d, sr)
        f = midi(n) * (1 + 0.006 * np.sin(2 * np.pi * 5.5 * tt))
        tone = (sine(f, d, sr) + sine(f * 2, d, sr) * 0.15) * env_adsr(d, 0.15, 0.3, 0.8, 0.5, sr)
        breath = bandpass(noise(d, 1200 + i, sr), midi(n) * 2, midi(n) * 6, sr) * env_adsr(d, 0.1, 0.2, 0.4, 0.5, sr) * 0.15
        place(buf, (tone * 0.18 + breath), 16 + i * 4 * beat * 1.5)
    return finish(buf, 3.5, 0.45, damp=5000)


# ------------------------------------------------------------------------------------------------------------ driver

GENERATORS = {
    "rift.ambient": snd_rift_ambient, "rift.open": snd_rift_open, "rift.enter": snd_rift_enter, "portal.open": snd_portal_open,
    "portal.enter": snd_portal_enter, "portal_gun.fire": snd_portal_gun_fire, "black_hole.ambient": snd_black_hole_ambient,
    "black_hole.pull": snd_black_hole_pull, "black_hole.collapse": snd_black_hole_collapse, "wormhole.travel": snd_wormhole_travel,
    "wormhole.emerge": snd_wormhole_emerge, "universe.arrive": snd_universe_arrive, "gravity.pulse": snd_gravity_pulse,
    "gravity.beam": snd_gravity_beam, "singularity.implode": snd_singularity_implode, "blade.slash": snd_blade_slash,
    "blade.dash": snd_blade_dash, "energy.fire": snd_energy_fire, "energy.hit": snd_energy_hit, "glitch.noise": snd_glitch_noise,
    "jelly.chime": snd_jelly_chime, "whale.call": snd_whale_call, "drone.hum": snd_drone_hum, "stalker.hiss": snd_stalker_hiss,
    "wraith.scream": snd_wraith_scream, "sentinel.step": snd_sentinel_step, "warden.roar": snd_warden_roar,
    "warden.charge": snd_warden_charge, "leviathan.roar": snd_leviathan_roar, "creature.hurt": snd_creature_hurt,
    "creature.death": snd_creature_death, "armor.equip": snd_armor_equip, "ability.activate": snd_ability_activate,
    "ui.select": snd_ui_select, "ui.manifest": snd_ui_manifest,
    "music.cosmic": music_cosmic, "music.neon": music_neon, "music.dream": music_dream, "music.void": music_void,
    "music.nexus": music_nexus, "music.ocean": music_ocean, "music.ancient": music_ancient,
}
LOOPS = {"rift.ambient", "black_hole.ambient", "gravity.beam", "drone.hum"}


def generate(only=None, log=print):
    assert set(GENERATORS) == set(SOUND_EVENTS), set(GENERATORS) ^ set(SOUND_EVENTS)
    for event, fn in GENERATORS.items():
        if only and event not in only:
            continue
        x = fn()
        is_music = event.startswith("music.")
        sr = MUSIC_SR if is_music else SR
        if not is_music:
            if event not in LOOPS:
                x = trim_tail(x, sr)
                x = fade(x, sr, 0.002, 0.04)
            # leave headroom for Vorbis overshoot on sharp transients; continuous loops sit lower in the mix
            x = normalize(lowpass(x, 15000, sr), 0.55 if event in LOOPS else 0.8)
        path = ASSETS / "sounds" / (event.replace(".", "/") + ".ogg")
        path.parent.mkdir(parents=True, exist_ok=True)
        sf.write(str(path), x.astype(np.float32), sr, format="OGG", subtype="VORBIS")
        log(f"  {event:22s} {len(x) / sr:6.2f}s  {path.stat().st_size / 1024:7.1f} KiB")
