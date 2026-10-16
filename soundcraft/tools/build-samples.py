#!/usr/bin/env python3
"""Build Soundcraft's recorded instruments from the VSCO 2 Community Edition library (CC0 1.0, public domain).

Usage:  python3 tools/build-samples.py <path to a VSCO-2-CE checkout> [instrument ids...]

For every sample it:
  * trims leading silence (keeps a 2 ms pre-roll so attacks stay intact),
  * measures the real pitch of the steady part and stores a cents correction, so every zone plays at
    A4 = 440 Hz even when the player was slightly sharp or flat,
  * keeps the true loudness difference between dynamic layers (one gain per articulation, not per file),
  * for sustains, shortens long notes and bakes an equal-power crossfade into the end of the file so the
    engine can loop [ls, le] seamlessly while a key is held,
  * encodes Ogg Opus (48 kHz) and writes app/js/sample-lib.js (the manifest the engine reads).
Fetch the needed folders with tools/fetch-samples.sh first.
"""
import json, os, re, subprocess, sys
import numpy as np

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app', 'samples')
NOTE = {'C': 0, 'C#': 1, 'D': 2, 'D#': 3, 'E': 4, 'F': 5, 'F#': 6, 'G': 7, 'G#': 8, 'A': 9, 'A#': 10, 'B': 11}
DYN = {'ppp': 0, 'pp': 1, 'p': 2, 'mp': 3, 'mf': 4, 'f': 5, 'ff': 6, 'fff': 7, 'soft': 2, 'medium': 4, 'loud': 6}

# id: (display name, {articulation: (folder, kind, max seconds, max round robins)}, channels, bitrate kbps)
INSTRUMENTS = {
    'trumpet': ('Trumpet', {'sus': ('Brass/Trumpet/sus', 'sus', 5.0, 1), 'stac': ('Brass/Trumpet/stac', 'short', 1.6, 2), 'mute': ('Brass/Trumpet/straightM-sus', 'sus', 5.0, 1)}, 1, 72),
    'horn': ('French horn', {'sus': ('Brass/F Horn/sus', 'sus', 5.0, 1), 'stac': ('Brass/F Horn/stac', 'short', 1.6, 2)}, 1, 72),
    'trombone': ('Tenor trombone', {'sus': ('Brass/Tenor Trombone/sus', 'sus', 5.0, 1), 'stac': ('Brass/Tenor Trombone/stac', 'short', 1.6, 2)}, 1, 72),
    'tuba': ('Tuba', {'sus': ('Brass/Tuba/sus', 'sus', 5.0, 1), 'stac': ('Brass/Tuba/stac', 'short', 1.6, 2)}, 1, 72),
    'violin': ('Solo violin', {'sus': ('Strings/Solo Violin/Arco Vib', 'sus', 5.0, 1), 'stac': ('Strings/Solo Violin/spic', 'short', 1.2, 2), 'pizz': ('Strings/Solo Violin/Pizz', 'short', 2.0, 2)}, 1, 72),
    'violins': ('Violin section', {'sus': ('Strings/Violin Section/susVib', 'sus', 5.0, 1), 'stac': ('Strings/Violin Section/Spic', 'short', 1.2, 2), 'pizz': ('Strings/Violin Section/Pizz', 'short', 2.0, 2)}, 2, 96),
    'violas': ('Viola section', {'sus': ('Strings/Viola Section/susvib', 'sus', 5.0, 1), 'stac': ('Strings/Viola Section/spic', 'short', 1.2, 2), 'pizz': ('Strings/Viola Section/pizz', 'short', 2.0, 2)}, 2, 96),
    'cellos': ('Cello section', {'sus': ('Strings/Cello Section/susvib', 'sus', 5.0, 1), 'stac': ('Strings/Cello Section/spic', 'short', 1.2, 2), 'pizz': ('Strings/Cello Section/pizzT', 'short', 2.0, 2)}, 2, 96),
    'bass': ('Contrabass', {'sus': ('Strings/Solo Contrabass/SusVib', 'sus', 5.0, 1), 'pizz': ('Strings/Solo Contrabass/Pizz', 'short', 2.5, 2)}, 1, 72),
    'harp': ('Concert harp', {'pluck': ('Strings/Harp', 'decay', 4.0, 1)}, 2, 96),
    'flute': ('Flute', {'sus': ('Woodwinds/Flute/susvib', 'sus', 5.0, 1), 'stac': ('Woodwinds/Flute/stac', 'short', 1.2, 2)}, 1, 64),
    'oboe': ('Oboe', {'sus': ('Woodwinds/Oboe/Vib', 'sus', 5.0, 1), 'stac': ('Woodwinds/Oboe/Stacc', 'short', 1.2, 2)}, 1, 64),
    'clarinet': ('Clarinet', {'sus': ('Woodwinds/Clarinet/susLong', 'sus', 5.0, 1), 'stac': ('Woodwinds/Clarinet/stac', 'short', 1.2, 2)}, 1, 64),
    'bassoon': ('Bassoon', {'sus': ('Woodwinds/Bassoon/vib', 'sus', 5.0, 1), 'stac': ('Woodwinds/Bassoon/stac', 'short', 1.2, 2)}, 1, 64),
    'glock': ('Glockenspiel', {'hit': ('Percussion/Glock', 'decay', 4.0, 1)}, 1, 80),
    'xylo': ('Xylophone', {'hit': ('Percussion/Xylo', 'decay', 2.0, 1)}, 1, 80),
    'marimba': ('Marimba', {'hit': ('Percussion/Marimba', 'decay', 3.0, 1)}, 1, 80),
}


def parse(name):
    base = os.path.splitext(name)[0]
    m = re.search(r'_([A-G]#?)(-?\d)(?=_|$)', base)
    if not m:
        return None
    midi = 12 * (int(m.group(2)) + 1) + NOTE[m.group(1)]
    v = re.search(r'_v(\d+)', base)
    d = re.search(r'_(ppp|pp|p|mp|mf|f|ff|fff|soft|medium|loud)(?=_|$)', base)
    vel = int(v.group(1)) if v else DYN[d.group(1)] if d else 0
    r = re.search(r'(?:rr|RR)(\d+)', base) or re.search(r'_(\d+)(?:_(?:Main|Mid|sum|Sum|far|close))?$', base)
    rr = int(r.group(1)) if r else 1
    return midi, vel, rr


def decode(path, ch):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', str(ch), '-ar', str(SR), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, ch).copy()


def pitch(x, expect):
    """f0 by normalised autocorrelation over the steady part, searched near the expected note."""
    m = x.mean(axis=1)
    n = len(m)
    a, b = int(min(n * 0.25, SR * 0.35)), int(min(n * 0.75, SR * 1.2))
    seg = m[a:b]
    if len(seg) < 4096:
        seg = m[:min(n, 8192)]
    seg = seg - seg.mean()
    f_exp = 440 * 2 ** ((expect - 69) / 12)
    lo, hi = int(SR / (f_exp * 2 ** (1.5 / 12))), int(SR / (f_exp / 2 ** (1.5 / 12))) + 1
    if hi >= len(seg) // 2 or lo < 2:
        return None
    spec = np.fft.rfft(seg, 2 * len(seg))
    ac = np.fft.irfft(spec * np.conj(spec))[:len(seg)]
    ac /= ac[0] + 1e-12
    k = lo + int(np.argmax(ac[lo:hi]))
    if ac[k] < 0.3:
        return None
    y0, y1, y2 = ac[k - 1], ac[k], ac[k + 1]
    den = y0 - 2 * y1 + y2
    p = k + (0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0)
    return 69 + 12 * np.log2(SR / p / 440)


def hps_midi(x):
    """Broad-range fundamental (harmonic product spectrum), used only to find each folder's octave naming."""
    m = x.mean(axis=1)
    seg = m[int(min(len(m) * 0.2, SR * 0.3)):int(min(len(m) * 0.6, SR * 0.8))]
    if len(seg) < 4096:
        return None
    S = np.abs(np.fft.rfft((seg - seg.mean()) * np.hanning(len(seg)), 1 << 16))
    f = np.fft.rfftfreq(1 << 16, 1 / SR)
    h = S.copy()
    for k in (2, 3, 4):
        h[:len(S) // k] *= S[::k][:len(S) // k]
    sel = (f > 25) & (f < 4200)
    f0 = f[sel][np.argmax(h[sel])]
    return 69 + 12 * np.log2(f0 / 440)


def encode(x, path, kbps):
    ch = x.shape[1]
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', str(ch), '-i', '-', '-c:a', 'libopus', '-b:a', '%dk' % kbps, '-vbr', 'on', '-application', 'audio', path],
                   input=np.ascontiguousarray(x, dtype=np.float32).tobytes(), check=True)


def build(src, iid, spec):
    name, arts, ch, kbps = spec
    os.makedirs(os.path.join(OUT, iid), exist_ok=True)
    files = []
    for art, (folder, kind, maxlen, maxrr) in arts.items():
        d = os.path.join(src, folder)
        if not os.path.isdir(d):
            print('  missing folder', d)
            continue
        for f in sorted(os.listdir(d)):
            if not f.lower().endswith('.wav'):
                continue
            p = parse(f)
            if not p:
                continue
            midi, vel, rr = p
            if rr > maxrr:
                continue
            files.append((art, kind, maxlen, os.path.join(d, f), midi, vel, rr))
    data, peak = [], 0.0
    for art, kind, maxlen, path, midi, vel, rr in files:
        x = decode(path, ch)
        env = np.abs(x).max(axis=1)
        if env.max() < 1e-4:
            continue
        on = int(np.argmax(env > env.max() * 0.03))
        x = x[max(0, on - int(SR * 0.002)):]
        data.append([art, kind, maxlen, midi, vel, rr, x, os.path.basename(path)])
        peak = max(peak, float(np.abs(x).max()))
    # level each articulation by the loudness of its loudest dynamic layer (RMS of the first second), so a
    # trombone and a flute sit at similar levels; one gain per articulation keeps the layers' real contrast
    gains = {}
    for art in arts:
        labs, pk = {}, 1e-6
        for e in data:
            if e[0] == art:
                labs.setdefault(e[4], []).append(float(np.sqrt(np.mean(e[6][:SR] ** 2))))
                pk = max(pk, float(np.abs(e[6]).max()))
        if labs:
            target = 0.12 if arts[art][1] == 'sus' else 0.09
            gains[art] = min(target / max(1e-6, max(np.mean(v) for v in labs.values())), 0.97 / pk)
    # octave naming differs between folders of the library (C3 or C4 as middle C): measure it per articulation
    offs = {}
    for art in arts:
        votes = {}
        for e in data:
            if e[0] == art:
                hm = hps_midi(e[6])
                if hm is not None and abs((hm - e[3]) - 12 * round((hm - e[3]) / 12)) < 0.6:
                    o = 12 * round((hm - e[3]) / 12)
                    votes[o] = votes.get(o, 0) + 1
        offs[art] = max(votes, key=votes.get) if votes else None
    fallback = offs.get('sus')
    for art in arts:
        if offs[art] is None:
            offs[art] = fallback or 0
        print('    %s: names are %+d semitones from the sound' % (art, -offs[art]) if offs[art] else '    %s: names match the sound' % art)
    for e in data:
        e[3] += offs[e[0]]
    # dynamic layers: order each articulation's labels by measured loudness
    layers = {}
    for art in arts:
        labs = {}
        for e in data:
            if e[0] == art:
                labs.setdefault(e[4], []).append(float(np.sqrt(np.mean(e[6][:SR] ** 2))))
        order = sorted(labs, key=lambda k: np.mean(labs[k]))
        layers[art] = {lab: i for i, lab in enumerate(order)}
    out = {'name': name, 'source': 'VSCO 2 Community Edition (CC0 1.0)', 'arts': {}}
    tunes = []
    for art, kind, maxlen, midi, vel, rr, x, fname in data:
        x = x * gains[art]
        loop = None
        L = int(maxlen * SR)
        if kind == 'sus' and len(x) > L + SR // 2:
            F = int(0.35 * SR)
            ls, le = max(int(1.2 * SR), int(L * 0.4)), L
            w = np.sin(np.linspace(0, np.pi / 2, F))[:, None]
            tail = x[le - F:le] * np.cos(np.linspace(0, np.pi / 2, F))[:, None] + x[ls - F:ls] * w
            x = np.concatenate([x[:le - F], tail])
            loop = (ls / SR, le / SR)
        else:
            x = x[:L].copy()
            fade = min(len(x) // 4, int((0.12 if kind == 'short' else 0.4) * SR))
            x[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 2
        det = pitch(x, midi) if kind != 'decay' or midi > 40 else None
        cents = 0.0
        if det is not None and abs(det - midi) < (0.25 if kind == 'short' else 0.45):
            cents = round(float(-(det - midi) * 100), 1)
            tunes.append(abs(cents))
            if abs(cents) > 25:
                print('    large tuning correction %+.1f c on %s' % (cents, fname))
        lay = layers[art][vel]
        fn = '%s_%d_v%d_r%d.ogg' % (art, midi, lay, rr)
        encode(x, os.path.join(OUT, iid, fn), kbps)
        z = {'f': 'samples/%s/%s' % (iid, fn), 'root': midi, 'tune': cents, 'v': lay, 'rr': rr - 1, 'len': round(len(x) / SR, 3)}
        if loop:
            z['ls'], z['le'] = round(loop[0], 4), round(loop[1], 4)
        a = out['arts'].setdefault(art, {'kind': kind, 'layers': len(layers[art]), 'zones': []})
        a['zones'].append(z)
    for a in out['arts'].values():
        a['zones'].sort(key=lambda z: (z['root'], z['v'], z['rr']))
        a['lo'], a['hi'] = a['zones'][0]['root'], a['zones'][-1]['root']
    size = sum(os.path.getsize(os.path.join(OUT, iid, f)) for f in os.listdir(os.path.join(OUT, iid)))
    print('  %-9s %3d zones  %5.1f MB  tuning corrections: median %.1f c, max %.1f c' % (iid, sum(len(a['zones']) for a in out['arts'].values()), size / 1e6, float(np.median(tunes)) if tunes else 0, max(tunes) if tunes else 0))
    return out


def main():
    src = sys.argv[1]
    only = sys.argv[2:] or list(INSTRUMENTS)
    man_path = os.path.join(ROOT, 'app', 'js', 'sample-lib.js')
    lib = {}
    if os.path.exists(man_path):
        txt = open(man_path).read()
        lib = json.loads(txt[txt.index('const SAMPLE_LIB=') + 17:txt.rindex(';')])
    for iid in only:
        print('building', iid)
        lib[iid] = build(src, iid, INSTRUMENTS[iid])
    with open(man_path, 'w') as f:
        f.write("'use strict';\n/* Generated by tools/build-samples.py from VSCO 2 Community Edition (CC0 1.0). Do not edit by hand. */\nconst SAMPLE_LIB=")
        f.write(json.dumps(lib, separators=(',', ':')))
        f.write(';\n')


if __name__ == '__main__':
    main()
