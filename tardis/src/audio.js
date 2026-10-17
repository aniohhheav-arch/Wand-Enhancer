// Fully procedural WebAudio sound design (no sample assets => nothing to license).
import { G } from './state.js';
import { clamp, damp } from './util.js';

export const audio = {
  ctx: null, ready: false,
  start() {
    if (this.ctx) { if (this.ctx.state === 'suspended') this.ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try { this.init(new AC()); } catch (e) { console.warn('audio init failed', e); }
  },

  init(ctx) {
    this.ctx = ctx;
    const out = (this.master = ctx.createGain());
    out.gain.value = G.settings.volume;
    const comp = ctx.createDynamicsCompressor();
    out.connect(comp); comp.connect(ctx.destination);
    // Two buses so doors / being outside can muffle or open up the sound.
    this.inBus = ctx.createGain(); this.exBus = ctx.createGain();
    this.exFilter = ctx.createBiquadFilter(); this.exFilter.type = 'lowpass'; this.exFilter.frequency.value = 400;
    this.inFilter = ctx.createBiquadFilter(); this.inFilter.type = 'lowpass'; this.inFilter.frequency.value = 12000;
    this.inBus.connect(this.inFilter); this.inFilter.connect(out);
    this.exBus.connect(this.exFilter); this.exFilter.connect(out);
    this.sfx = ctx.createGain(); this.sfx.connect(out);

    // noise buffers
    const mk = (kind) => {
      const len = ctx.sampleRate * 3, b = ctx.createBuffer(1, len, ctx.sampleRate), d = b.getChannelData(0);
      let last = 0;
      for (let i = 0; i < len; i++) {
        const w = Math.random() * 2 - 1;
        if (kind === 'brown') { last = (last + 0.02 * w) / 1.02; d[i] = last * 3.5; } else d[i] = w;
      }
      return b;
    };
    this.white = mk('white'); this.brown = mk('brown');
    const loopNoise = (buf, dest, type, f, q = 1) => {
      const s = ctx.createBufferSource(); s.buffer = buf; s.loop = true;
      const fl = ctx.createBiquadFilter(); fl.type = type; fl.frequency.value = f; fl.Q.value = q;
      const g = ctx.createGain(); g.gain.value = 0;
      s.connect(fl); fl.connect(g); g.connect(dest); s.start();
      return { s, fl, g };
    };

    // --- interior hum: slow detuned stack + filtered noise "air circulation"
    const hum = ctx.createGain(); hum.gain.value = 0.16; hum.connect(this.inBus);
    this.humOscs = [55, 55.35, 82.4, 110.2].map((f, i) => {
      const o = ctx.createOscillator(); o.type = i < 2 ? 'sawtooth' : 'sine'; o.frequency.value = f;
      const fl = ctx.createBiquadFilter(); fl.type = 'lowpass'; fl.frequency.value = 220;
      const g = ctx.createGain(); g.gain.value = i < 2 ? 0.25 : 0.4;
      o.connect(fl); fl.connect(g); g.connect(hum); o.start(); return o;
    });
    const lfo = ctx.createOscillator(); lfo.frequency.value = 0.13;
    const lg = ctx.createGain(); lg.gain.value = 0.04; lfo.connect(lg); lg.connect(hum.gain); lfo.start();
    this.hum = hum;
    this.air = loopNoise(this.brown, this.inBus, 'lowpass', 500); this.air.g.gain.value = 0.05;

    // --- room pad (changes per room) and environment pad (changes per world)
    const mkPad = (bus) => {
      const g = ctx.createGain(); g.gain.value = 0; g.connect(bus);
      const oscs = [1, 1.5, 2.01].map((r) => { const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = 110 * r; const og = ctx.createGain(); og.gain.value = 0.3; o.connect(og); og.connect(g); o.start(); return o; });
      const l = ctx.createOscillator(); l.frequency.value = 0.2; const lgg = ctx.createGain(); lgg.gain.value = 0.015; l.connect(lgg); lgg.connect(g.gain); l.start();
      return { g, oscs };
    };
    this.roomPad = mkPad(this.inBus); this.envPad = mkPad(this.exBus);
    this.envNoise = loopNoise(this.white, this.exBus, 'bandpass', 900, 0.4);
    this.rain = loopNoise(this.white, this.exBus, 'highpass', 3500);
    this.wind = loopNoise(this.brown, this.exBus, 'bandpass', 300, 0.6);
    // --- vortex roar
    this.vortex = loopNoise(this.brown, this.exBus, 'bandpass', 120, 0.5);
    this.vortexHi = loopNoise(this.white, this.exBus, 'bandpass', 2400, 2);

    // --- engine "wheeze": sawtooth with sweeping tremolo
    const eg = this.engGain = ctx.createGain(); eg.gain.value = 0; eg.connect(this.inBus);
    const eo = this.engOsc = ctx.createOscillator(); eo.type = 'sawtooth'; eo.frequency.value = 70;
    const eo2 = this.engOsc2 = ctx.createOscillator(); eo2.type = 'square'; eo2.frequency.value = 35;
    const ef = this.engFilter = ctx.createBiquadFilter(); ef.type = 'lowpass'; ef.frequency.value = 300; ef.Q.value = 6;
    const tl = this.engLfo = ctx.createOscillator(); tl.frequency.value = 5;
    const tg = ctx.createGain(); tg.gain.value = 18; tl.connect(tg); tg.connect(eo.frequency);
    const tg2 = ctx.createGain(); tg2.gain.value = 120; tl.connect(tg2); tg2.connect(ef.frequency);
    const og2 = ctx.createGain(); og2.gain.value = 0.5; eo2.connect(og2); og2.connect(ef);
    eo.connect(ef); ef.connect(eg); eo.start(); eo2.start(); tl.start();

    // alarm
    const al = this.alarmGain = ctx.createGain(); al.gain.value = 0; al.connect(this.inBus);
    this.alarmOsc = ctx.createOscillator(); this.alarmOsc.type = 'square'; this.alarmOsc.frequency.value = 440;
    const af = ctx.createBiquadFilter(); af.type = 'lowpass'; af.frequency.value = 1400;
    this.alarmOsc.connect(af); af.connect(al); this.alarmOsc.start();
    this.alarmKind = null; this.alarmT = 0;

    // state mirrors for smoothing
    this.s = { flight: 0, vortex: 0, door: 0, inside: 1, rain: 0, rotor: 0, roomHz: 0 };
    this.rotorPhase = 0; this.ready = true;
  },

  // ---------- one-shot helpers
  tone(f, dur = 0.1, type = 'sine', vol = 0.2, slideTo = null, bus = null, delay = 0) {
    if (!this.ready) return;
    const c = this.ctx, t = c.currentTime + delay, o = c.createOscillator(), g = c.createGain();
    o.type = type; o.frequency.setValueAtTime(f, t);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(Math.max(20, slideTo), t + dur);
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(bus || this.sfx); o.start(t); o.stop(t + dur + 0.05);
  },
  noise(dur = 0.2, f = 1000, q = 1, vol = 0.2, type = 'bandpass', bus = null, delay = 0, fEnd = null) {
    if (!this.ready) return;
    const c = this.ctx, t = c.currentTime + delay, s = c.createBufferSource();
    s.buffer = this.white; s.loopStart = Math.random() * 2; s.loop = true;
    const fl = c.createBiquadFilter(); fl.type = type; fl.frequency.setValueAtTime(f, t); fl.Q.value = q;
    if (fEnd) fl.frequency.exponentialRampToValueAtTime(fEnd, t + dur);
    const g = c.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(fl); fl.connect(g); g.connect(bus || this.sfx); s.start(t, s.loopStart); s.stop(t + dur + 0.05);
  },

  click(kind = 'button') {
    if (!this.ready) return;
    const j = 1 + (Math.random() - 0.5) * 0.12;
    switch (kind) {
      case 'lever': this.noise(0.12, 900 * j, 3, 0.3); this.tone(70 * j, 0.25, 'sine', 0.5, 40); this.tone(1800, 0.03, 'square', 0.08, null, null, 0.02); break;
      case 'switch': this.tone(2600 * j, 0.02, 'square', 0.12); this.tone(900 * j, 0.05, 'square', 0.1, 400, null, 0.015); break;
      case 'knob': this.tone(1400 * j, 0.02, 'triangle', 0.14); break;
      case 'big': this.tone(110, 0.35, 'sine', 0.6, 50); this.noise(0.18, 400, 1, 0.3, 'lowpass'); this.tone(660 * j, 0.15, 'triangle', 0.1, null, null, 0.05); break;
      case 'deny': this.tone(180, 0.18, 'sawtooth', 0.18, 120); this.tone(150, 0.2, 'sawtooth', 0.18, 90, null, 0.15); break;
      case 'ok': this.tone(880, 0.1, 'sine', 0.15); this.tone(1320, 0.18, 'sine', 0.12, null, null, 0.09); break;
      default: this.tone(1100 * j, 0.04, 'square', 0.1); this.tone(520 * j, 0.06, 'triangle', 0.12, 300, null, 0.01);
    }
  },
  door(open) {
    if (!this.ready) return;
    this.noise(0.9, open ? 500 : 800, 4, 0.18, 'bandpass', this.sfx, 0, open ? 1100 : 300);
    this.tone(open ? 140 : 100, 0.5, 'triangle', 0.18, open ? 200 : 70);
    if (!open) this.tone(60, 0.25, 'sine', 0.5, 35, null, 0.75);
  },
  knock() { for (let i = 0; i < 3; i++) { this.tone(160, 0.12, 'triangle', 0.5, 90, this.exBusBright(), i * 0.2); this.noise(0.06, 700, 2, 0.25, 'bandpass', null, i * 0.2); } },
  exBusBright() { return this.sfx; },
  lock(on) { this.tone(on ? 500 : 800, 0.07, 'square', 0.12); this.tone(on ? 300 : 1200, 0.1, 'square', 0.1, null, null, 0.08); this.noise(0.05, 3000, 3, 0.1); },
  chime(n = 0) { [0, 4, 7, 12].forEach((s, i) => this.tone(523 * Math.pow(2, (s + n) / 12), 1.4, 'sine', 0.08, null, null, i * 0.12)); },
  zap() { this.noise(0.5, 4000, 0.7, 0.35, 'highpass', null, 0, 800); this.tone(90, 0.7, 'sawtooth', 0.15, 30); },
  thunder() { this.noise(2.2, 200, 0.6, 0.5, 'lowpass', this.exBus, 0, 60); },
  scan() { this.tone(300, 1.2, 'sine', 0.15, 1800); this.tone(600, 1.2, 'triangle', 0.08, 3600, null, 0.1); },
  whisper() { this.noise(2.5, 1800, 6, 0.06, 'bandpass', this.inBus, 0, 900); this.tone(220, 2.5, 'sine', 0.03, 196, this.inBus); },
  beep(f = 900) { this.tone(f, 0.08, 'sine', 0.12); },
  materialize() {
    this.tone(300, 3.5, 'sawtooth', 0.0001); // keep ctx warm
    this.noise(5.5, 300, 2, 0.45, 'bandpass', this.sfx, 0, 2400);
    this.tone(60, 5, 'sawtooth', 0.35, 220, this.sfx); this.tone(90, 5, 'sine', 0.3, 330);
    this.tone(1200, 1.2, 'sine', 0.1, null, null, 5);
  },
  dematerialize() {
    this.noise(5.5, 2400, 2, 0.45, 'bandpass', this.sfx, 0, 250);
    this.tone(220, 5, 'sawtooth', 0.35, 55, this.sfx); this.tone(330, 5, 'sine', 0.3, 80);
  },
  engineStart() { this.tone(40, 1.8, 'sawtooth', 0.4, 120); this.noise(1.2, 200, 1, 0.3, 'lowpass', null, 0, 800); },
  rotorThump(v = 1) { this.tone(52, 0.35, 'sine', 0.3 * v, 38, this.inBus); this.noise(0.1, 220, 1, 0.08 * v, 'lowpass', this.inBus); },
  slam() { this.tone(50, 0.5, 'sine', 0.7, 28); this.noise(0.4, 300, 1, 0.5, 'lowpass'); },
  bell(f = 440) { this.tone(f, 2.2, 'sine', 0.1); this.tone(f * 2.76, 1.4, 'sine', 0.04); this.tone(f * 5.4, 0.9, 'sine', 0.02); },

  setAlarm(kind) { this.alarmKind = kind; },
  setMusic(on) { this.music = on; this.musicT = 0; this.musicStep = 0; },
  musicTick(dt) {
    if (!this.ready || !this.music) return;
    this.musicT = (this.musicT || 0) + dt; if (this.musicT < 0.42) return; this.musicT = 0;
    const scale = [0, 3, 7, 10, 12, 15, 19], root = [57, 53, 55, 50][Math.floor((this.musicStep || 0) / 8) % 4], n = scale[(this.musicStep * 3 + (this.musicStep >> 2)) % scale.length];
    this.tone(440 * Math.pow(2, (root + n - 69) / 12), 1.6, 'triangle', 0.05, null, this.inBus); this.musicStep = (this.musicStep || 0) + 1;
  },
  setVolume(v) { if (this.ready) this.master.gain.setTargetAtTime(v, this.ctx.currentTime, 0.1); },

  setRoom(hz, tension = 0) {
    if (!this.ready) return;
    const t = this.ctx.currentTime;
    this.roomPad.oscs.forEach((o, i) => o.frequency.setTargetAtTime(hz * [1, 1.5, 2.01][i], t, 1.5));
    this.roomPad.g.gain.setTargetAtTime(hz ? 0.06 + tension * 0.03 : 0, t, 1.5);
  },
  setEnv(p) {
    if (!this.ready) return;
    const t = this.ctx.currentTime;
    this.envPad.oscs.forEach((o, i) => o.frequency.setTargetAtTime(p.base * [1, p.ratio || 1.5, 2.01][i], t, 2));
    this.envPad.g.gain.setTargetAtTime(p.pad ?? 0.08, t, 2);
    this.envNoise.fl.frequency.setTargetAtTime(p.nf || 900, t, 1.5);
    this.envNoise.g.gain.setTargetAtTime(p.ng ?? 0.2, t, 1.5);
    this.wind.g.gain.setTargetAtTime(p.wind ?? 0.15, t, 1.5);
    this.rainTarget = p.rain || 0;
  },

  update(dt, ctxInfo) {
    if (!this.ready) return;
    const t = this.ctx.currentTime, s = this.s;
    const { inside, doorOpen, flying, flightLevel, rotorOn, vortexVisible, emergency, power } = ctxInfo;
    s.flight = damp(s.flight, flightLevel, 1.5, dt);
    s.door = damp(s.door, doorOpen, 4, dt);
    s.inside = damp(s.inside, inside ? 1 : 0, 3, dt);
    s.rotor = damp(s.rotor, rotorOn ? 1 : 0, 1.2, dt);
    s.rain = damp(s.rain, this.rainTarget || 0, 0.8, dt);

    // muffling: outside sound filtered by doors; inside sound muffled when player is outside
    const exOpen = inside ? s.door : 1;
    this.exFilter.frequency.setTargetAtTime(inside ? 280 + exOpen * 9000 : 12000, t, 0.12);
    this.exBus.gain.setTargetAtTime(inside ? 0.25 + exOpen * 0.9 : 1, t, 0.2);
    this.inFilter.frequency.setTargetAtTime(inside ? 12000 : 350 + s.door * 700, t, 0.15);
    this.inBus.gain.setTargetAtTime(inside ? 1 : 0.18 + s.door * 0.25, t, 0.2);

    const eng = [[55, 70, 90, 120], [40, 50, 66, 80], [110, 138, 165, 220]][G.settings.engine] || [55, 70, 90, 120];
    this.humOscs.forEach((o, i) => o.frequency.setTargetAtTime(eng[i] * (1 + s.flight * 0.35), t, 0.3));
    this.hum.gain.setTargetAtTime(0.14 + s.flight * 0.18, t, 0.3);
    this.engGain.gain.setTargetAtTime(s.flight * 0.34 * (G.settings.engine === 2 ? 0.7 : 1), t, 0.2);
    this.engOsc.frequency.setTargetAtTime(60 + s.flight * 130, t, 0.3);
    this.engOsc2.frequency.setTargetAtTime(30 + s.flight * 65, t, 0.3);
    this.engLfo.frequency.setTargetAtTime(3.5 + s.flight * 7, t, 0.4);
    this.engFilter.frequency.setTargetAtTime(220 + s.flight * 900, t, 0.3);
    this.air.g.gain.setTargetAtTime(0.04 + s.flight * 0.05, t, 0.4);

    const vq = vortexVisible ? 1 : 0;
    this.vortex.g.gain.setTargetAtTime(vq * (0.35 + s.flight * 0.55), t, 0.4);
    this.vortex.fl.frequency.setTargetAtTime(90 + s.flight * 160, t, 0.4);
    this.vortexHi.g.gain.setTargetAtTime(vq * s.flight * 0.12, t, 0.3);
    this.rain.g.gain.setTargetAtTime(s.rain * 0.25, t, 0.4);

    // rotor rhythm
    this.rotorPhase += dt * (0.55 + s.flight * 1.7) * (rotorOn ? 1 : 0);
    if (this.rotorPhase >= 1) { this.rotorPhase -= 1; if (rotorOn && inside) this.rotorThump(0.7 + s.flight * 0.6); }

    // alarms (distinct per kind)
    this.alarmT += dt;
    let ag = 0;
    if (this.alarmKind === 'red') { ag = (Math.floor(this.alarmT * 2) % 2) ? 0.0 : 0.16; this.alarmOsc.frequency.setTargetAtTime(Math.floor(this.alarmT * 2) % 2 ? 330 : 620, t, 0.01); }
    else if (this.alarmKind === 'nav') { ag = (this.alarmT % 1.2 < 0.15) ? 0.14 : 0; this.alarmOsc.frequency.setTargetAtTime(880, t, 0.01); }
    else if (this.alarmKind === 'power') { ag = 0.1 * (0.5 + 0.5 * Math.sin(this.alarmT * 3)); this.alarmOsc.frequency.setTargetAtTime(110 + 60 * Math.sin(this.alarmT * 1.5), t, 0.05); }
    else if (this.alarmKind === 'temporal') { ag = (this.alarmT % 0.5 < 0.08) ? 0.12 : 0; this.alarmOsc.frequency.setTargetAtTime(1200 - (this.alarmT % 0.5) * 2000, t, 0.01); }
    this.alarmGain.gain.setTargetAtTime(ag, t, 0.01);
  },
};
