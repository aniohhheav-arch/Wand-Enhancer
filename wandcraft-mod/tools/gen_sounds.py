"""Synthesizes the Dark Clone sound set (numpy -> wav -> ogg via ffmpeg). Run from wandcraft-mod/."""
import json, os, subprocess, wave
import numpy as np

SR = 44100
OUT = 'src/main/resources/assets/wandcraft/sounds/dark/'
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1234)

def t_(d): return np.linspace(0, d, int(SR * d), endpoint=False)
def env(n, a=0.05, r=0.3):
    e = np.ones(n); na, nr = int(n * a), int(n * r)
    if na: e[:na] = np.linspace(0, 1, na) ** 2
    if nr: e[-nr:] = np.linspace(1, 0, nr) ** 1.5
    return e
def lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)):
        s = (1 - a) * x[i] + a * s; y[i] = s
    return y
def lp_fast(x, fc):  # FFT brickwall-ish for speed
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / (1 + (f / fc) ** 4); return np.fft.irfft(X, len(x))
def bp(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= (1 / (1 + (f / hi) ** 4)) * (1 - 1 / (1 + (f / lo) ** 4)); return np.fft.irfft(X, len(x))
def noise(n): return rng.standard_normal(n)
def norm(x, peak=0.9): return x / (np.max(np.abs(x)) + 1e-9) * peak
def reverb(x, tail=0.9, wet=0.35):
    ir = noise(int(SR * tail)) * np.exp(-np.linspace(0, 7, int(SR * tail)))
    ir = lp_fast(ir, 3500)
    y = np.fft.irfft(np.fft.rfft(x, len(x) + len(ir)) * np.fft.rfft(ir, len(x) + len(ir)))
    y = y[:len(x) + len(ir)]; y /= np.max(np.abs(y)) + 1e-9
    out = np.zeros(len(y)); out[:len(x)] += x / (np.max(np.abs(x)) + 1e-9)
    return out + wet * y
def save(name, x, peak=0.9):
    x = np.tanh(norm(x, 1.0) * 1.1) * peak
    x = x[:int(len(x))]
    fade = int(SR * 0.02); x[-fade:] *= np.linspace(1, 0, fade)
    path = OUT + name
    w = wave.open(path + '.wav', 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((x * 32767).astype('<i2').tobytes()); w.close()
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', path + '.wav', '-c:a', 'libvorbis', '-q:a', '4', path + '.ogg'], check=True)
    os.remove(path + '.wav')

def vowel(t, f0, formants, wob=0.0):
    ph = 2 * np.pi * np.cumsum(f0 * (1 + wob * np.sin(2 * np.pi * 5.3 * t))) / SR
    src = np.sign(np.sin(ph)) * 0.3 + np.sin(ph) * 0.5 + noise(len(t)) * 0.25
    out = sum(bp(src, fc * 0.82, fc * 1.18) * g for fc, g in formants)
    return out

def whisper(d, seed):
    r = np.random.default_rng(seed); n = int(SR * d); t = t_(d)
    x = r.standard_normal(n)
    sig = np.zeros(n)
    for k in range(4):
        c = r.uniform(0.15, 0.85) * d
        w = r.uniform(0.12, 0.3)
        gate = np.exp(-((t - c) / w) ** 2)
        fc = r.uniform(1800, 3600)
        sig += bp(x, fc * 0.6, fc * 1.4) * gate
    sig += 0.35 * bp(r.standard_normal(n), 400, 900) * env(n, 0.3, 0.5)
    return sig * env(n, 0.1, 0.4)

# ---- summon: deep drone swell, sub boom, reversed whisper layer, low rattle
d = 3.6; t = t_(d); n = len(t)
drone = (np.sin(2 * np.pi * 41 * t) + 0.6 * np.sin(2 * np.pi * 61.5 * t + 1) + 0.4 * np.sin(2 * np.pi * 82.3 * t)) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * t))
drone *= np.linspace(0.1, 1, n) ** 1.5 * env(n, 0.0, 0.35)
rumble = lp_fast(noise(n), 160) * 2.2 * env(n, 0.3, 0.3)
rev = whisper(d, 3)[::-1] * 0.45
boom = np.sin(2 * np.pi * (70 - 38 * t / d) * t) * np.exp(-np.maximum(t - 1.7, 0) * 2.6) * (t > 1.7) * 1.1
save('summon', reverb(drone * 0.9 + rumble * 0.6 + rev + boom, 1.3, 0.3))

# ---- whispers (3 variants)
for i, dd in enumerate((2.2, 2.8, 2.4)):
    w = whisper(dd, 10 + i)
    save('whisper%d' % (i + 1), reverb(w, 1.2, 0.5), 0.8)

# ---- hunt rumble: low unsettling bed with slow tremolo
d = 3.0; t = t_(d); n = len(t)
x = lp_fast(noise(n), 110) * 3 + np.sin(2 * np.pi * 47 * t) * 0.6 + 0.2 * np.sin(2 * np.pi * 93 * t)
x *= 0.6 + 0.4 * np.sin(2 * np.pi * 1.3 * t)
save('hunt_rumble', x * env(n, 0.25, 0.4), 0.7)

# ---- phase step: crackling whoosh
d = 0.55; t = t_(d); n = len(t)
x = bp(noise(n), 500, 5500) * np.exp(-t * 7) * (1 + 0.8 * np.sign(np.sin(2 * np.pi * 70 * t)))
x += np.sin(2 * np.pi * (900 * np.exp(-t * 9)) * t) * 0.3 * np.exp(-t * 6)
save('phase', reverb(x, 0.5, 0.25), 0.8)

# ---- reach: tension riser with dissonant pair and scrape
d = 1.4; t = t_(d); n = len(t)
f = 180 + 260 * (t / d) ** 2
x = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5 + np.sin(2 * np.pi * np.cumsum(f * 1.06) / SR) * 0.5
x += bp(noise(n), 1500, 4000) * (t / d) * 0.7
save('reach', reverb(x * env(n, 0.6, 0.15), 0.8, 0.3), 0.8)

# ---- possession: sub thud, crackling surge and a choir-like dissonant swell
d = 2.6; t = t_(d); n = len(t)
thud = np.sin(2 * np.pi * (95 * np.exp(-t * 3.2)) * t) * np.exp(-t * 3.5) * 2.0
crack = bp(noise(n), 1200, 7000) * (rng.random(n) > 0.985) * 6
crack = lp_fast(crack, 6000) * np.exp(-t * 1.4)
choir = sum(np.sin(2 * np.pi * f0 * t + k) for k, f0 in enumerate((220, 233.1, 329.6, 349.2))) * 0.25 * env(n, 0.4, 0.5)
save('possess', reverb(thud + crack + choir + whisper(d, 31) * 0.6, 1.2, 0.35), 0.9)

# ---- disintegrate: dry crackling ash crumble with descending hiss
d = 3.2; t = t_(d); n = len(t)
spikes = (rng.random(n) > 0.9975).astype(float)
crumble = lp_fast(bp(spikes * rng.standard_normal(n) * 8, 400, 6500), 7000)
hiss = bp(noise(n), 2500, 8000) * np.exp(-t * 0.9) * 0.35
save('disintegrate', reverb((crumble * (1 - 0.4 * t / d) + hiss) * env(n, 0.05, 0.4), 0.9, 0.25), 0.85)

# ---- implosion: reverse swell, bass drop, shockwave hiss
d = 2.4; t = t_(d); n = len(t); tp = 1.25
pre = (bp(noise(n), 200, 3000) * (t / tp) ** 3 * (t < tp)) + np.sin(2 * np.pi * np.cumsum(60 + 400 * (t / tp) ** 3) / SR) * 0.4 * (t < tp)
tt = np.maximum(t - tp, 0)
drop = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-tt * 1.6) + 30) / SR) * np.exp(-tt * 1.5) * 2.4 * (t >= tp)
post = lp_fast(noise(n), 900) * np.exp(-tt * 3) * 1.4 * (t >= tp)
save('implode', reverb(pre + drop + post, 1.6, 0.4), 0.95)

# ---- ash scatter: soft dusty puff
d = 0.9; t = t_(d); n = len(t)
x = lp_fast(bp(noise(n), 300, 3000), 3500) * np.exp(-t * 4.5) * env(n, 0.02, 0.1)
save('ash_scatter', x, 0.55)

events = {
    "dark_clone.summon": ["dark/summon"], "dark_clone.whisper": ["dark/whisper1", "dark/whisper2", "dark/whisper3"],
    "dark_clone.hunt": ["dark/hunt_rumble"], "dark_clone.phase": ["dark/phase"], "dark_clone.reach": ["dark/reach"],
    "dark_clone.possess": ["dark/possess"], "dark_clone.disintegrate": ["dark/disintegrate"], "dark_clone.implode": ["dark/implode"],
    "ash.scatter": ["dark/ash_scatter"],
}
sj = {k: {"category": "hostile" if "ash" not in k else "ambient", "sounds": [{"name": "wandcraft:" + s, "stream": False} for s in v]} for k, v in events.items()}
json.dump(sj, open('src/main/resources/assets/wandcraft/sounds.json', 'w'), indent=1)
print("sounds ok")
