#!/usr/bin/env python3
"""Synthesizes every Final Frame sound effect (pure stdlib + ffmpeg for Ogg Vorbis).

Run from the final-frame directory: python3 tools/gen_sounds.py
Each effect is built from damped modes, filtered noise and envelopes so the
set stays reproducible without shipping third-party audio.
"""
import math
import os
import random
import struct
import subprocess
import tempfile
import wave

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "finalframe", "sounds")


def buf(seconds):
    return [0.0] * int(SR * seconds)


def add(dst, src, at=0.0, gain=1.0):
    o = int(at * SR)
    for i, v in enumerate(src):
        if 0 <= o + i < len(dst):
            dst[o + i] += v * gain
    return dst


def noise(seconds, seed):
    r = random.Random(seed)
    return [r.uniform(-1, 1) for _ in range(int(SR * seconds))]


def lowpass(x, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / SR)
    y, s = [], 0.0
    for v in x:
        s += a * (v - s)
        y.append(s)
    return y


def highpass(x, cutoff):
    lp = lowpass(x, cutoff)
    return [a - b for a, b in zip(x, lp)]


def bandpass_sweep(x, f0, f1, q=4.0):
    """State-variable band-pass whose centre glides from f0 to f1."""
    low = band = 0.0
    out = []
    n = len(x)
    for i, v in enumerate(x):
        f = f0 * (f1 / f0) ** (i / max(1, n - 1))
        k = 2 * math.sin(math.pi * min(f, SR / 6) / SR)
        low += k * band
        high = v - low - band / q
        band += k * high
        out.append(band)
    return out


def env_exp(x, tau, attack=0.0015):
    out = []
    for i, v in enumerate(x):
        t = i / SR
        a = min(1.0, t / attack) if attack > 0 else 1.0
        out.append(v * a * math.exp(-t / tau))
    return out


def env_swell(x, peak_at):
    n = len(x)
    out = []
    for i, v in enumerate(x):
        t = i / n
        e = (t / peak_at) ** 2 if t < peak_at else ((1 - t) / (1 - peak_at)) ** 1.5
        out.append(v * e)
    return out


def modes(seconds, freqs, tau, amps=None, detune_seed=0):
    r = random.Random(detune_seed)
    amps = amps or [1.0] * len(freqs)
    out = buf(seconds)
    for f, a in zip(freqs, amps):
        ph = r.random() * 6.28
        t_scale = tau * (1.0 - 0.3 * r.random())
        for i in range(len(out)):
            t = i / SR
            out[i] += a * math.sin(2 * math.pi * f * t + ph) * math.exp(-t / t_scale)
    return out


def sine_sweep(seconds, f0, f1, tau):
    out, ph = [], 0.0
    n = int(seconds * SR)
    for i in range(n):
        f = f0 * (f1 / f0) ** (i / max(1, n - 1))
        ph += 2 * math.pi * f / SR
        out.append(math.sin(ph) * math.exp(-(i / SR) / tau))
    return out


def click(seed, bright=1.0):
    c = env_exp(highpass(noise(0.03, seed), 1800), 0.0025, 0.0002)
    m = modes(0.06, [2900 * bright, 4700 * bright, 6900 * bright], 0.012, [0.6, 0.4, 0.25], seed)
    return add(c, m, 0, 0.8)


def thud(seed, freq=70, tau=0.09):
    body = sine_sweep(0.45, freq * 1.6, freq, tau)
    n = env_exp(lowpass(noise(0.45, seed), 500), tau * 0.6, 0.002)
    return add(body, n, 0, 0.6)


def normalize(x, peak=0.89):
    m = max(1e-9, max(abs(v) for v in x))
    return [v * peak / m for v in x]


def fade_out(x, seconds=0.03):
    n = int(seconds * SR)
    for i in range(min(n, len(x))):
        x[-1 - i] *= i / n
    return x


def save(name, x, peak=0.89):
    x = fade_out(normalize(x, peak))
    os.makedirs(os.path.dirname(os.path.join(OUT, name)), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in x))
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.unlink(path)


def karplus(freq, seconds, seed, decay=0.996):
    r = random.Random(seed)
    period = int(SR / freq)
    ring = [r.uniform(-1, 1) for _ in range(period)]
    out = []
    for i in range(int(seconds * SR)):
        v = ring[i % period]
        nxt = ring[(i + 1) % period]
        ring[i % period] = decay * 0.5 * (v + nxt)
        out.append(v)
    return out


def main():
    # Revolver shot: supersonic crack, chamber blast, low boom and a canyon tail.
    shot = buf(2.4)
    add(shot, env_exp(highpass(noise(0.05, 1), 3000), 0.004, 0.0001), 0, 0.9)
    add(shot, env_exp(lowpass(noise(0.5, 2), 2400), 0.045, 0.0004), 0, 1.0)
    add(shot, sine_sweep(0.8, 120, 38, 0.22), 0, 0.9)
    add(shot, env_exp(lowpass(noise(2.4, 3), 900), 0.55, 0.06), 0.02, 0.22)
    add(shot, env_exp(lowpass(noise(1.0, 4), 700), 0.25, 0.02), 0.38, 0.12)  # slap-back echo
    save("revolver/shot", shot, 0.95)

    # The Last Word shot: same core with a longer, heavier, reverberant tail.
    last = buf(3.6)
    add(last, shot, 0, 1.0)
    add(last, sine_sweep(1.6, 70, 28, 0.6), 0, 0.6)
    add(last, env_exp(lowpass(noise(3.6, 5), 600), 1.0, 0.1), 0.05, 0.25)
    add(last, env_exp(lowpass(noise(1.6, 6), 500), 0.4, 0.03), 0.62, 0.15)
    save("finisher/last_word_shot", last, 0.97)

    dry = buf(0.25)
    add(dry, click(10, 1.1), 0, 1)
    save("revolver/dry_fire", dry)

    cock = buf(0.4)
    add(cock, click(11, 0.9), 0, 0.7)
    add(cock, click(12, 1.15), 0.085, 1.0)
    add(cock, modes(0.3, [1850, 3120], 0.05, [0.3, 0.2], 13), 0.085, 0.5)
    save("revolver/hammer_cock", cock)

    cyl = buf(0.75)
    add(cyl, env_swell(bandpass_sweep(noise(0.75, 14), 2400, 1600, 3), 0.3), 0, 0.25)
    for k in range(7):
        add(cyl, click(20 + k, 1.0 + 0.04 * (k % 3)), 0.04 + k * 0.085, 0.55 + 0.05 * k)
    save("revolver/cylinder_spin", cyl)

    reload_ = buf(1.4)
    add(reload_, click(30, 0.7), 0, 0.9)  # gate opens
    for k in range(6):
        add(reload_, modes(0.12, [2100 + 90 * k, 3400], 0.03, [0.6, 0.3], 31 + k), 0.18 + k * 0.11, 0.7)
    add(reload_, cyl, 0.85, 0.6)
    add(reload_, click(40, 0.8), 1.3, 1.0)
    save("revolver/reload", reload_)

    spin = buf(1.0)
    whirr = bandpass_sweep(noise(1.0, 50), 900, 1300, 5)
    for i in range(len(whirr)):
        t = i / SR
        whirr[i] *= 0.55 + 0.45 * math.sin(2 * math.pi * 17 * t) ** 2
    add(spin, env_swell(whirr, 0.35), 0, 1)
    add(spin, click(51, 1.2), 0.93, 0.5)
    save("finisher/revolver_spin", spin)

    draw = buf(0.9)
    add(draw, env_swell(lowpass(noise(0.35, 60), 1800), 0.6), 0, 0.6)
    add(draw, modes(0.6, [2150, 3420, 5610, 7300], 0.16, [0.5, 0.45, 0.3, 0.15], 61), 0.3, 0.7)
    add(draw, click(62, 0.9), 0.31, 0.6)
    save("finisher/draw", draw)

    holster = buf(0.8)
    add(holster, env_swell(lowpass(noise(0.4, 70), 1400), 0.5), 0, 0.7)
    add(holster, thud(71, 110, 0.05), 0.38, 0.6)
    add(holster, click(72, 0.85), 0.42, 0.5)
    save("finisher/holster", holster)

    catch = buf(0.6)
    add(catch, env_exp(lowpass(noise(0.1, 80), 3500), 0.012, 0.0003), 0, 1.0)
    add(catch, modes(0.5, [1720, 2950, 4400], 0.09, [0.5, 0.4, 0.25], 81), 0, 0.6)
    add(catch, thud(82, 140, 0.04), 0, 0.5)
    save("finisher/catch", catch)

    whoosh = env_swell(bandpass_sweep(noise(1.0, 90), 260, 2200, 2.5), 0.7)
    save("finisher/whoosh", whoosh)

    shove = buf(0.7)
    add(shove, env_swell(bandpass_sweep(noise(0.3, 100), 400, 1200, 2), 0.7), 0, 0.6)  # cloth
    add(shove, thud(101, 75, 0.11), 0.24, 1.0)
    add(shove, env_exp(lowpass(noise(0.3, 102), 1500), 0.05), 0.24, 0.4)
    save("finisher/shove", shove)

    cloth = env_swell(bandpass_sweep(noise(0.45, 110), 700, 1500, 1.6), 0.4)
    save("finisher/cloth", cloth)

    stumble = buf(1.0)
    for k, at in enumerate((0.0, 0.22, 0.48)):
        add(stumble, thud(120 + k, 60 + 8 * k, 0.07), at, 0.9 - 0.15 * k)
        add(stumble, env_exp(highpass(lowpass(noise(0.25, 130 + k), 3000), 400), 0.06), at, 0.35)  # gravel scuff
    save("finisher/stumble", stumble)

    impact = buf(1.6)
    add(impact, thud(140, 55, 0.25), 0, 1.0)
    add(impact, env_exp(lowpass(noise(1.6, 141), 400), 0.5, 0.05), 0, 0.4)
    add(impact, sine_sweep(1.4, 240, 50, 0.5), 0.02, 0.35)  # shockwave
    save("finisher/impact", impact)

    slow = buf(1.6)
    add(slow, sine_sweep(1.6, 220, 46, 1.4), 0, 0.6)
    add(slow, env_swell(bandpass_sweep(noise(1.6, 150), 1800, 180, 2), 0.25), 0, 0.5)
    add(slow, thud(151, 50, 0.12), 0.55, 0.7)
    add(slow, thud(152, 50, 0.12), 0.8, 0.5)  # heartbeat
    save("finisher/slowmo_in", slow)

    resume = buf(1.0)
    add(resume, env_swell(bandpass_sweep(noise(1.0, 160), 180, 2400, 2), 0.85), 0, 0.6)
    add(resume, [v * min(1, i / (0.6 * SR)) for i, v in enumerate(sine_sweep(1.0, 50, 220, 10))], 0, 0.4)
    save("finisher/slowmo_out", resume)

    ring = buf(3.0)
    for i in range(len(ring)):
        t = i / SR
        ring[i] = math.sin(2 * math.pi * 3870 * t) * min(1, t / 0.05) * math.exp(-t / 1.1) * 0.6
    save("finisher/ear_ring", ring, 0.5)

    smoke = env_swell(lowpass(noise(2.0, 170), 700), 0.2)
    save("finisher/smoke_hiss", smoke, 0.5)

    # Western stinger: a low E-minor arpeggio plucked with Karplus-Strong.
    sting = buf(4.0)
    for k, (f, at) in enumerate(((82.41, 0.0), (123.47, 0.14), (164.81, 0.28), (196.0, 0.42), (246.94, 0.56))):
        add(sting, karplus(f, 3.4, 180 + k, 0.9975), at, 0.7)
    add(sting, karplus(329.63, 3.2, 190, 0.998), 0.95, 0.5)
    save("finisher/stinger", sting, 0.8)


if __name__ == "__main__":
    main()
