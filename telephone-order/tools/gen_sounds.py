"""Synthesises every TELEPHONE ORDER sound with numpy and encodes it to Ogg Vorbis with ffmpeg."""
import numpy as np, subprocess, os, wave, math

SR = 32000
RNG = np.random.default_rng(1337)

def t_(d): return np.arange(int(SR * d)) / SR

def noise(d, seed=None):
    r = np.random.default_rng(seed) if seed is not None else RNG
    return r.uniform(-1, 1, int(SR * d))

def fft_filter(x, lo=None, hi=None):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    m = np.ones_like(f)
    if lo: m *= 1 / (1 + (lo / np.maximum(f, 1e-3)) ** 4)
    if hi: m *= 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * m, len(x))

def lp(x, fc): return fft_filter(x, None, fc)
def hp(x, fc): return fft_filter(x, fc, None)
def bp(x, lo, hi): return fft_filter(x, lo, hi)

def env(n, a=0.002, d=0.1, curve=1.0):
    t = np.arange(n) / SR
    e = np.exp(-t / max(d, 1e-4)) ** curve
    att = np.minimum(1, t / max(a, 1e-4))
    return e * att

def sweep(d, f0, f1, shape="exp"):
    n = int(SR * d); t = np.arange(n) / SR
    if shape == "exp":
        f = f0 * (f1 / f0) ** (t / d)
    else:
        f = f0 + (f1 - f0) * t / d
    return np.sin(2 * np.pi * np.cumsum(f) / SR)

def tone(d, f, harm=(1.0,)):
    t = t_(d); y = np.zeros_like(t)
    for i, h in enumerate(harm): y += h * np.sin(2 * np.pi * f * (i + 1) * t)
    return y

def mix(*parts, dur=None):
    n = max(len(p[1]) + int(SR * p[0]) for p in parts) if dur is None else int(SR * dur)
    y = np.zeros(n)
    for off, s in parts:
        o = int(SR * off); m = min(len(s), n - o)
        if m > 0: y[o:o + m] += s[:m]
    return y

def norm(x, peak=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak

def fade(x, ms=4):
    n = int(SR * ms / 1000); x = x.copy()
    if len(x) > 2 * n:
        x[-n:] *= np.linspace(1, 0, n); x[:n] *= np.linspace(0, 1, n)
    return x

def echo(x, delays=((0.09, 0.4), (0.19, 0.22), (0.31, 0.12))):
    y = np.concatenate([x, np.zeros(int(SR * 0.4))])
    out = y.copy()
    for d, g in delays:
        o = int(SR * d); out[o:] += y[:len(y) - o] * g
    return lp(out, 3500)

# ---------------- gunshots ----------------
def shot(dur, th0, th1, nlp, crack, tail, tg, thump=1.0, echoed=False, hpf=None, seed=None):
    n = int(SR * dur)
    th = sweep(dur, th0, th1) * env(n, 0.001, dur * 0.25) * thump
    nz = lp(noise(dur, seed), nlp) * env(n, 0.0005, dur * 0.18)
    cr = hp(noise(dur, None if seed is None else seed + 1), 2500) * env(n, 0.0003, 0.012) * crack
    tl = lp(noise(dur, None if seed is None else seed + 2), 900) * env(n, 0.01, tail) * tg
    y = th * 0.9 + nz * 0.9 + cr + tl
    if hpf: y = hp(y, hpf)
    if echoed: y = echo(y)
    return norm(np.tanh(y * 1.6))

SHOTS = dict(
    pistol=lambda: shot(0.28, 220, 70, 7000, 0.9, 0.08, 0.3, seed=1),
    revolver=lambda: shot(0.5, 150, 45, 4500, 0.8, 0.16, 0.5, thump=1.3, seed=2),
    smg=lambda: shot(0.16, 260, 90, 8000, 0.8, 0.04, 0.2, seed=3),
    rifle=lambda: shot(0.34, 180, 55, 6500, 0.9, 0.12, 0.45, seed=4),
    rifle2=lambda: shot(0.32, 200, 60, 7500, 1.0, 0.1, 0.4, seed=40),
    battle=lambda: shot(0.45, 140, 42, 5000, 0.8, 0.16, 0.6, thump=1.3, seed=5),
    dmr=lambda: shot(0.42, 170, 48, 5500, 1.0, 0.14, 0.5, thump=1.1, echoed=True, seed=6),
    sniper=lambda: shot(0.7, 120, 36, 4000, 1.1, 0.25, 0.7, thump=1.5, echoed=True, seed=7),
    anti_material=lambda: shot(1.1, 90, 28, 3200, 1.1, 0.4, 0.9, thump=1.9, echoed=True, seed=8),
    shotgun=lambda: shot(0.55, 130, 38, 3800, 0.7, 0.2, 0.7, thump=1.5, seed=9),
    shotgun2=lambda: shot(0.42, 150, 45, 4500, 0.8, 0.14, 0.55, thump=1.3, seed=10),
    lmg=lambda: shot(0.2, 160, 60, 6000, 0.8, 0.05, 0.3, thump=1.2, seed=11),
    saw=lambda: shot(0.22, 130, 52, 5500, 0.8, 0.06, 0.35, thump=1.4, seed=12),
    pdw=lambda: shot(0.13, 330, 120, 9000, 0.9, 0.03, 0.15, seed=13),
    machinepistol=lambda: shot(0.1, 300, 110, 9500, 0.9, 0.025, 0.1, seed=14),
    burst=lambda: shot(0.22, 210, 70, 7000, 0.9, 0.06, 0.25, seed=15),
)

def suppressed():
    n = int(SR * 0.2)
    pf = lp(noise(0.2, 21), 1800) * env(n, 0.002, 0.04)
    th = sweep(0.2, 160, 80) * env(n, 0.002, 0.04) * 0.6
    cl = hp(noise(0.05, 22), 3500) * env(int(SR * 0.05), 0.0005, 0.006) * 0.4
    return norm(mix((0, pf + th), (0.012, cl)))

def paradox():
    base = shot(0.4, 160, 50, 5000, 0.8, 0.12, 0.4, thump=1.2, seed=31)
    rev = base[::-1] * np.linspace(0.2, 0.7, len(base))
    ring = tone(0.7, 880, (1, 0.4, 0.2)) * env(int(SR * 0.7), 0.002, 0.25) * 0.25
    return norm(mix((0, base), (0.12, rev * 0.5), (0.0, ring)))

def void():
    s = shot(0.4, 150, 50, 4200, 0.6, 0.16, 0.5, thump=1.2, seed=32)
    sh_ = sweep(0.9, 2400, 300) * env(int(SR * 0.9), 0.01, 0.3) * 0.25
    sub = sweep(0.9, 60, 30) * env(int(SR * 0.9), 0.02, 0.4) * 0.7
    return norm(mix((0, s), (0.05, sh_), (0, sub)))

def ember():
    s = shot(0.55, 120, 36, 3500, 0.6, 0.22, 0.8, thump=1.5, seed=33)
    cr = hp(noise(0.8, 34), 1800) * (np.random.default_rng(9).uniform(0, 1, int(SR * 0.8)) > 0.97) * env(int(SR * 0.8), 0.01, 0.3)
    return norm(mix((0, s), (0.1, lp(cr, 6000) * 0.6)))

def ledger():
    s = shot(1.0, 90, 26, 3000, 1.0, 0.35, 0.9, thump=1.9, echoed=True, seed=35)
    bell_ = (tone(1.2, 523, (1, 0.5, 0.3, 0.2)) * env(int(SR * 1.2), 0.002, 0.5)) * 0.35
    return norm(mix((0, s), (0.04, bell_)))

# ---------------- energy ----------------
def zap(dur, f0, f1, nz=0.2, harm=(1, 0.3), d=0.2):
    n = int(SR * dur)
    y = sweep(dur, f0, f1) * env(n, 0.001, d) + 0.4 * sweep(dur, f0 * 2, f1 * 2) * env(n, 0.001, d * 0.7)
    y += hp(noise(dur), 3000) * env(n, 0.0005, d * 0.3) * nz
    return norm(np.tanh(y))

def rail():
    ch = sweep(0.55, 200, 2600) * np.linspace(0.1, 0.8, int(SR * 0.55))
    boom = shot(0.55, 100, 30, 3500, 1.0, 0.2, 0.7, thump=1.6, seed=51)
    zp = sweep(0.4, 4000, 300) * env(int(SR * 0.4), 0.001, 0.12) * 0.5
    return norm(mix((0, ch * 0.5), (0.55, boom), (0.55, zp)))

def arc():
    n = int(SR * 0.45)
    imp = (np.random.default_rng(5).uniform(0, 1, n) > 0.985).astype(float) * np.random.default_rng(6).uniform(-1, 1, n)
    y = bp(imp * 8, 600, 7000) * env(n, 0.001, 0.2)
    buzz = np.sign(np.sin(2 * np.pi * 120 * t_(0.45))) * 0.2 * env(n, 0.001, 0.25)
    return norm(np.tanh(y * 2 + buzz))

def cryo():
    n = int(SR * 0.4)
    hs = bp(noise(0.4, 61), 3000, 9000) * env(n, 0.03, 0.3) * 0.8
    ice = tone(0.4, 2400, (1, 0.5)) * (np.sin(2 * np.pi * 38 * t_(0.4)) > 0.8) * env(n, 0.005, 0.25) * 0.2
    return norm(hs + ice)

def gravity():
    t = t_(0.7)
    f = 70 + 25 * np.sin(2 * np.pi * 5 * t)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.8 + 0.3 * np.sin(2 * np.pi * np.cumsum(f * 3.01) / SR)
    y *= env(len(t), 0.05, 0.5)
    return norm(y)

def smart():
    b1 = tone(0.07, 1800) * env(int(SR * 0.07), 0.002, 0.04); b2 = tone(0.07, 2400) * env(int(SR * 0.07), 0.002, 0.04)
    launch = lp(noise(0.5, 71), 2500) * env(int(SR * 0.5), 0.02, 0.2) + sweep(0.5, 500, 150) * env(int(SR * 0.5), 0.01, 0.2) * 0.5
    return norm(mix((0, b1), (0.09, b2), (0.2, launch)))

def charge():
    return norm(sweep(0.9, 120, 1800) * np.linspace(0.1, 1, int(SR * 0.9)) + hp(noise(0.9, 81), 4000) * np.linspace(0, 0.3, int(SR * 0.9)))

def charge_release():
    return norm(mix((0, shot(0.7, 110, 30, 3000, 0.7, 0.25, 0.8, thump=1.8, seed=82)), (0, sweep(0.6, 3000, 200) * env(int(SR * 0.6), 0.001, 0.2) * 0.6)))

def shield():
    t = t_(0.8)
    y = (np.sin(2 * np.pi * 220 * t) + 0.5 * np.sin(2 * np.pi * 330 * t) + 0.3 * np.sin(2 * np.pi * 441 * t)) * env(len(t), 0.05, 0.6)
    y += sweep(0.8, 400, 2400) * env(len(t), 0.01, 0.3) * 0.4
    return norm(y)

def rift():
    y = sweep(1.0, 3000, 60) * env(int(SR * 1.0), 0.01, 0.5) * 0.7 + lp(noise(1.0, 91), 800) * env(int(SR * 1.0), 0.1, 0.5) * 0.5
    return norm(y[::-1] * 0.4 + y)

def cannon():
    return norm(mix((0, shot(0.9, 80, 24, 2500, 0.5, 0.35, 0.9, thump=2.0, seed=92)), (0, zap(0.5, 1200, 100, 0.3, d=0.3) * 0.6)))

def mortar():
    thump = shot(0.5, 90, 30, 2800, 0.4, 0.2, 0.6, thump=1.8, seed=93)
    whistle = sweep(0.7, 1800, 600) * env(int(SR * 0.7), 0.02, 0.4) * 0.2
    return norm(mix((0, thump), (0.2, whistle)))

def beam():
    t = t_(0.5)
    y = (np.sin(2 * np.pi * 440 * t) + np.sin(2 * np.pi * 443 * t) + 0.5 * np.sin(2 * np.pi * 880 * t)) * 0.4
    y += lp(noise(0.5, 94), 3000) * 0.2
    return norm(y * np.minimum(1, t / 0.02) * np.minimum(1, (0.5 - t) / 0.05))

def turret():
    return norm(mix((0, zap(0.1, 1800, 500, 0.5, d=0.04)), (0.0, shot(0.1, 300, 120, 7000, 0.8, 0.03, 0.1, seed=95) * 0.6)))

def unstable():
    t = t_(0.5)
    f = 300 + 250 * np.sin(2 * np.pi * 17 * t) + 200 * np.sin(2 * np.pi * 7 * t + 1)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(t), 0.002, 0.25) + hp(noise(0.5, 96), 3500) * env(len(t), 0.001, 0.1) * 0.4
    return norm(np.tanh(y * 1.5))

def plasma(): return zap(0.35, 1600, 280, 0.25, d=0.18)
def laser(): return zap(0.22, 3600, 700, 0.15, d=0.1)
def pulse(): return zap(0.12, 2200, 900, 0.1, d=0.05)
def eshotgun(): return norm(mix((0, zap(0.4, 900, 160, 0.4, d=0.2)), (0, shot(0.4, 160, 50, 3500, 0.4, 0.1, 0.4, thump=1.4, seed=97) * 0.6)))

# ---------------- mechanical ----------------
def tick(f=2200, d=0.02, g=1.0):
    n = int(SR * d)
    return (hp(noise(d), f * 0.5) * env(n, 0.0003, d * 0.3) + tone(d, f) * env(n, 0.0003, d * 0.4) * 0.5) * g

def mag_reload():
    return norm(mix((0.00, tick(1600, 0.04) * 0.9), (0.10, lp(noise(0.12, 101), 2500) * env(int(SR * 0.12), 0.01, 0.06) * 0.6),
                    (0.30, tick(2400, 0.03)), (0.34, tick(1500, 0.05) * 1.2), (0.52, tick(2000, 0.03) * 0.8)))

def bolt():
    return norm(mix((0, tick(1400, 0.05)), (0.08, lp(noise(0.2, 102), 1800) * env(int(SR * 0.2), 0.01, 0.1) * 0.5), (0.3, tick(1000, 0.05)), (0.36, tick(2200, 0.04))))

def pump():
    return norm(mix((0, tick(900, 0.06)), (0.05, lp(noise(0.18, 103), 1500) * env(int(SR * 0.18), 0.01, 0.1) * 0.5), (0.22, tick(1300, 0.06)), (0.27, tick(2300, 0.03))))

def revolver_reload():
    ticks = [(0.05 * i, tick(2600 - i * 80, 0.012, 0.6)) for i in range(6)]
    return norm(mix((0, tick(900, 0.05)), *[(0.12 + a, b) for a, b in ticks], (0.55, tick(1100, 0.06))))

def belt():
    return norm(mix((0, tick(1000, 0.06)), (0.1, tick(1500, 0.04)), (0.2, lp(noise(0.25, 104), 1200) * env(int(SR * 0.25), 0.01, 0.1) * 0.6),
                    (0.5, tick(900, 0.07)), (0.58, tick(1700, 0.04))))

def cell():
    return norm(mix((0, tick(1200, 0.05) * 0.9), (0.12, tick(800, 0.06)), (0.2, sweep(0.18, 500, 1800) * env(int(SR * 0.18), 0.002, 0.08) * 0.5)))

def core():
    return norm(mix((0, tick(700, 0.07)), (0.14, tick(500, 0.09)), (0.28, sweep(0.35, 150, 900) * env(int(SR * 0.35), 0.005, 0.15) * 0.6)))

def rocket_reload():
    return norm(mix((0, tick(600, 0.08)), (0.1, lp(noise(0.3, 105), 1200) * env(int(SR * 0.3), 0.01, 0.15) * 0.5), (0.4, tick(800, 0.09))))

def empty_click(): return norm(mix((0, tick(1800, 0.02)), (0.03, tick(900, 0.03) * 0.7)))

def overheat():
    return norm(hp(noise(0.8, 106), 2500) * env(int(SR * 0.8), 0.02, 0.4) * 0.8 + np.concatenate([tone(0.1, 1200) * 0.4, np.zeros(int(SR * 0.7))]))

def lock_beep(): return norm(tone(0.06, 1700) * env(int(SR * 0.06), 0.002, 0.03))
def lock_done(): return norm(mix((0, tone(0.07, 1700)), (0.09, tone(0.2, 2600) * env(int(SR * 0.2), 0.002, 0.1))))

def impact_bullet(): return norm(mix((0, hp(noise(0.08, 111), 1500) * env(int(SR * 0.08), 0.0003, 0.012)), (0, tone(0.06, 1900) * env(int(SR * 0.06), 0.0003, 0.015) * 0.4)))
def impact_energy(): return norm(hp(noise(0.3, 112), 2500) * env(int(SR * 0.3), 0.001, 0.08) + sweep(0.3, 2000, 200) * env(int(SR * 0.3), 0.001, 0.1) * 0.6)
def explosion_small(): return norm(mix((0, shot(0.8, 100, 28, 2800, 0.4, 0.3, 0.8, thump=2.0, seed=113)), (0.02, lp(noise(0.7, 114), 1500) * env(int(SR * 0.7), 0.01, 0.3) * 0.6)))
def emp_burst(): return norm(mix((0, sweep(0.8, 120, 1600) * env(int(SR * 0.8), 0.01, 0.3) * 0.7), (0.0, bp(noise(0.8, 115), 1500, 8000) * env(int(SR * 0.8), 0.001, 0.2) * 0.6)))

# ---------------- phone ----------------
def bell(f, d):
    n = int(SR * d); t = np.arange(n) / SR
    y = (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * 2.76 * t) + 0.3 * np.sin(2 * np.pi * f * 5.4 * t)) * np.exp(-t / (d * 0.5))
    return y

def ring_classic():
    parts = []
    for burst in (0.0, 0.5):
        n = int(SR * 0.4); t = np.arange(n) / SR
        m = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 22 * t))
        y = (bell(1250, 0.4) * 0.5 + bell(1500, 0.4) * 0.5) * (0.35 + 0.65 * m)
        parts.append((burst, y * np.minimum(1, (0.4 - t) / 0.03)))
    return norm(mix(*parts, dur=1.0))

def ring_digital():
    b = tone(0.1, 2000, (1, 0.3)) * np.minimum(1, (0.1 - t_(0.1)) / 0.01)
    return norm(mix(*[(i * 0.15, b) for i in range(3)], dur=0.9))

def ring_satellite():
    parts = []
    for i in range(5):
        parts.append((i * 0.12, sweep(0.1, 2800 - i * 300, 1500 - i * 150) * env(int(SR * 0.1), 0.002, 0.06)))
    return norm(mix(*parts, dur=1.0))

def ring_retro():
    notes = [659, 587, 370, 415, 554, 494, 294, 330, 494, 440]
    parts = [(i * 0.1, tone(0.1, f, (1, 0.5)) * env(int(SR * 0.1), 0.005, 0.08)) for i, f in enumerate(notes)]
    return norm(mix(*parts, dur=1.1))

def phone_pickup():
    return norm(mix((0, tick(500, 0.1, 1.2)), (0.04, lp(noise(0.12, 121), 700) * env(int(SR * 0.12), 0.01, 0.05)), (0.18, tick(1300, 0.04))))

def phone_hangup():
    return norm(mix((0, tick(1200, 0.04)), (0.03, tick(450, 0.14, 1.3)), (0.05, bell(900, 0.1) * 0.15)))

def dial_tone():
    t = t_(1.0)
    return norm((np.sin(2 * np.pi * 350 * t) + np.sin(2 * np.pi * 440 * t)) * 0.5)

def key_beep(): return norm(mix((0, (tone(0.08, 697) + tone(0.08, 1209)) * env(int(SR * 0.08), 0.002, 0.05))))

def phone_connect():
    return norm(mix((0, tone(0.15, 1300)), (0.2, tone(0.15, 2100)), (0.4, hp(noise(0.5, 122), 1200) * env(int(SR * 0.5), 0.02, 0.3) * 0.5 + sweep(0.5, 900, 1900, "lin") * 0.3 * env(int(SR * 0.5), 0.02, 0.4))))

def phone_disconnected():
    parts = [(0, tone(0.3, 913)), (0.35, tone(0.3, 1370)), (0.7, tone(0.3, 1777))]
    return norm(mix(*parts, (1.1, lp(noise(0.5, 123), 2500) * env(int(SR * 0.5), 0.01, 0.25) * 0.5), dur=1.7))

def static_burst():
    n = int(SR * 0.6)
    y = noise(0.6, 124) * (np.random.default_rng(7).uniform(0, 1, n) > 0.2) * env(n, 0.005, 0.35)
    return norm(bp(y, 400, 8000))

def whisper():
    n = int(SR * 1.6); t = np.arange(n) / SR
    y = noise(1.6, 125) * (0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t)) * np.minimum(1, t / 0.2) * np.minimum(1, (1.6 - t) / 0.4)
    y = bp(y, 900, 3800) + 0.4 * bp(noise(1.6, 126), 400, 800) * (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * t + 1))
    return norm(y * 0.9)

def order_confirm():
    ka = mix((0, tick(1500, 0.03)), (0.04, tick(900, 0.04)))
    chime = (bell(1568, 0.5) + bell(2093, 0.5) * 0.7)
    return norm(mix((0, ka), (0.12, chime), (0.30, bell(2637, 0.6) * 0.6)))

def order_denied(): return norm(np.sign(np.sin(2 * np.pi * 110 * t_(0.35))) * 0.4 * env(int(SR * 0.35), 0.002, 0.3))

def ui_click(): return norm(mix((0, tick(2400, 0.025)), (0, tone(0.03, 900) * env(int(SR * 0.03), 0.001, 0.01) * 0.4)))
def ui_tab(): return norm(mix((0, sweep(0.1, 600, 1500) * env(int(SR * 0.1), 0.002, 0.05) * 0.5), (0, hp(noise(0.1, 127), 3000) * env(int(SR * 0.1), 0.001, 0.03) * 0.4)))
def ui_error(): return norm(mix((0, tone(0.08, 220, (1, 0.5))), (0.1, tone(0.12, 180, (1, 0.5)))))

# ---------------- delivery / ambience ----------------
def incoming_whistle():
    n = int(SR * 2.2)
    y = sweep(2.2, 2600, 500) * np.linspace(0.05, 0.6, n) + hp(noise(2.2, 131), 2000) * np.linspace(0.0, 0.3, n)
    return norm(y)

def crate_impact():
    return norm(mix((0, shot(0.8, 110, 30, 2200, 0.2, 0.3, 0.9, thump=2.0, seed=132)), (0.0, bp(noise(0.5, 133), 300, 3500) * env(int(SR * 0.5), 0.002, 0.12) * 0.7),
                    (0.2, tick(500, 0.1) * 0.6), (0.33, tick(700, 0.08) * 0.5)))

def pod_impact():
    return norm(mix((0, shot(1.2, 80, 22, 1800, 0.3, 0.5, 1.0, thump=2.4, seed=134)), (0.05, bp(noise(1.0, 135), 200, 2500) * env(int(SR * 1.0), 0.002, 0.3) * 0.8),
                    (0.5, hp(noise(0.6, 136), 3000) * env(int(SR * 0.6), 0.005, 0.2) * 0.3)))

def pod_hiss(): return norm(hp(noise(1.2, 137), 2500) * env(int(SR * 1.2), 0.02, 0.6))

def drone_buzz():
    t = t_(0.5)
    y = np.sign(np.sin(2 * np.pi * 180 * t)) * 0.3 + np.sin(2 * np.pi * 360 * t) * 0.3 + lp(noise(0.5, 138), 1500) * 0.3
    y *= (0.75 + 0.25 * np.sin(2 * np.pi * 36 * t))
    return norm(y * np.minimum(1, t / 0.02) * np.minimum(1, (0.5 - t) / 0.03))

def teleport_zap():
    n = int(SR * 1.1)
    y = sweep(1.1, 200, 5000) * np.linspace(0.2, 1.0, n) * 0.5 + hp(noise(1.1, 139), 4000) * np.linspace(0, 0.8, n) * 0.5
    sp = (np.random.default_rng(8).uniform(0, 1, n) > 0.995) * np.random.default_rng(9).uniform(-1, 1, n)
    return norm(np.tanh(y + bp(sp * 8, 1000, 9000)))

def rift_open():
    n = int(SR * 2.4); t = np.arange(n) / SR
    sub = sweep(2.4, 40, 70) * np.minimum(1, t / 0.6) * 0.8
    cr = bp((np.random.default_rng(10).uniform(0, 1, n) > 0.99) * np.random.default_rng(11).uniform(-1, 1, n) * 8, 500, 6000) * np.linspace(0.1, 0.8, n)
    wail = sweep(2.4, 900, 200) * 0.2 * np.sin(2 * np.pi * 3 * t) ** 2
    return norm(np.tanh(sub + cr + wail))

def case_latch(): return norm(mix((0, tick(1800, 0.04)), (0.1, tick(1200, 0.05)), (0.35, tick(700, 0.08)), (0.4, lp(noise(0.3, 140), 3000) * env(int(SR * 0.3), 0.005, 0.1) * 0.4)))
def flash_whoosh(): return norm(sweep(0.4, 6000, 400) * env(int(SR * 0.4), 0.001, 0.15) * 0.5 + hp(noise(0.4, 141), 2500) * env(int(SR * 0.4), 0.001, 0.12) * 0.5)
def item_ready(): return norm(mix((0, bell(1319, 0.5)), (0.12, bell(1760, 0.6))))

def eerie_drone():
    t = t_(3.5)
    y = (np.sin(2 * np.pi * 55 * t) + np.sin(2 * np.pi * 58.3 * t) + 0.6 * np.sin(2 * np.pi * 82.5 * t * (1 + 0.01 * np.sin(2 * np.pi * 0.7 * t)))) * 0.5
    y += bp(noise(3.5, 142), 1500, 4000) * 0.1 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.9 * t))
    return norm(y * np.minimum(1, t / 0.8) * np.minimum(1, (3.5 - t) / 1.0))

def impact_flesh(): return norm(lp(noise(0.12, 143), 800) * env(int(SR * 0.12), 0.001, 0.03))

def turret_deploy(): return norm(mix((0, tick(500, 0.1)), (0.15, sweep(0.4, 200, 900) * env(int(SR * 0.4), 0.01, 0.2) * 0.5), (0.5, tick(1500, 0.05))))
def rocket(): return norm(mix((0, lp(noise(0.8, 144), 2200) * env(int(SR * 0.8), 0.01, 0.35) * 0.9), (0, shot(0.35, 120, 40, 3500, 0.3, 0.1, 0.5, thump=1.5, seed=145))))

def siren():
    n = int(SR * 6.0); t = np.arange(n) / SR
    f = 600 + 380 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.33 * t - 1.57))
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.35 * np.sin(2 * np.pi * np.cumsum(f * 2) / SR)
    y = np.tanh(y * 1.3)
    return norm(y * np.minimum(1, t / 0.3) * np.minimum(1, (6.0 - t) / 0.6))

def orbital_charge():
    n = int(SR * 4.0); t = np.arange(n) / SR
    y = sweep(4.0, 60, 900) * np.linspace(0.1, 1, n) + 0.5 * sweep(4.0, 120, 1800) * np.linspace(0, 1, n) ** 2
    y += hp(noise(4.0, 201), 3000) * np.linspace(0, 0.4, n)
    return norm(np.tanh(y))

def orbital_beam():
    n = int(SR * 3.5); t = np.arange(n) / SR
    rumble = lp(noise(3.5, 202), 300) * 1.4 + sweep(3.5, 45, 28) * 1.2
    zap = (np.sin(2 * np.pi * 220 * t) + np.sin(2 * np.pi * 222 * t) + 0.5 * np.sin(2 * np.pi * 880 * t)) * 0.35
    y = (rumble + zap + hp(noise(3.5, 203), 2500) * 0.25) * np.minimum(1, t / 0.05) * np.minimum(1, (3.5 - t) / 1.2)
    return norm(np.tanh(y * 1.3))

def radio_beep():
    return norm(mix((0, tone(0.08, 1100) * env(int(SR * 0.08), 0.002, 0.05)), (0.12, tone(0.08, 1100) * env(int(SR * 0.08), 0.002, 0.05)),
                    (0.3, bp(noise(0.5, 204), 500, 3000) * env(int(SR * 0.5), 0.01, 0.2) * 0.5)))

def mushroom_boom():
    return norm(mix((0, shot(2.5, 70, 18, 1500, 0.2, 1.2, 1.0, thump=2.6, seed=205)), (0.1, lp(noise(2.5, 206), 400) * env(int(SR * 2.5), 0.05, 1.0))))

SOUNDS = {}
for k, f in SHOTS.items(): SOUNDS["fire_" + k] = f
SOUNDS.update({
    "fire_suppressed": suppressed, "fire_paradox": paradox, "fire_void": void, "fire_ember": ember, "fire_ledger": ledger,
    "fire_plasma": plasma, "fire_laser": laser, "fire_rail": rail, "fire_pulse": pulse, "fire_arc": arc, "fire_cryo": cryo,
    "fire_gravity": gravity, "fire_smart": smart, "fire_eshotgun": eshotgun, "fire_charge": charge_release, "charge_up": charge,
    "fire_shield": shield, "fire_rift": rift, "fire_cannon": cannon, "fire_mortar": mortar, "fire_beam": beam, "fire_turret": turret,
    "fire_unstable": unstable, "fire_rocket": rocket,
    "reload_mag": mag_reload, "reload_bolt": bolt, "reload_pump": pump, "reload_revolver": revolver_reload, "reload_belt": belt,
    "reload_cell": cell, "reload_core": core, "reload_rocket_reload": rocket_reload, "empty_click": empty_click, "overheat": overheat,
    "lock_beep": lock_beep, "lock_done": lock_done, "impact_bullet": impact_bullet, "impact_energy": impact_energy,
    "explosion_small": explosion_small, "emp_burst": emp_burst, "impact_flesh": impact_flesh,
    "ring_classic": ring_classic, "ring_digital": ring_digital, "ring_satellite": ring_satellite, "ring_retro": ring_retro,
    "phone_pickup": phone_pickup, "phone_hangup": phone_hangup, "dial_tone": dial_tone, "key_beep": key_beep,
    "phone_connect": phone_connect, "phone_disconnected": phone_disconnected, "static_burst": static_burst, "whisper": whisper,
    "order_confirm": order_confirm, "order_denied": order_denied, "ui_click": ui_click, "ui_tab": ui_tab, "ui_error": ui_error,
    "incoming_whistle": incoming_whistle, "crate_impact": crate_impact, "pod_impact": pod_impact, "pod_hiss": pod_hiss,
    "drone_buzz": drone_buzz, "teleport_zap": teleport_zap, "rift_open": rift_open, "case_latch": case_latch,
    "siren": siren, "orbital_charge": orbital_charge, "orbital_beam": orbital_beam, "radio_beep": radio_beep, "mushroom_boom": mushroom_boom,
    "flash_whoosh": flash_whoosh, "item_ready": item_ready, "eerie_drone": eerie_drone, "turret_deploy": turret_deploy,
})

def write_all(outdir):
    os.makedirs(outdir, exist_ok=True)
    for name, fn in SOUNDS.items():
        y = fade(np.clip(fn(), -1, 1) * 0.9)
        wav = f"/tmp/_snd_{name}.wav"
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes((y * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", f"{outdir}/{name}.ogg"], check=True)
        os.remove(wav)
    return list(SOUNDS)

if __name__ == "__main__":
    import sys
    print(len(write_all(sys.argv[1] if len(sys.argv) > 1 else "/tmp/snd_test")))
