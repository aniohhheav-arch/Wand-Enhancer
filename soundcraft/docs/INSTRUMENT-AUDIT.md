# Instrument audit and overhaul (Soundcraft 0.4)

Every number below was measured in the real audio engine: the acceptance tests render notes offline and measure them, and `tools/build-samples.py` measures every recording it ships. The tests are in `tests/e2e.cjs` under "instrument quality".

## What was wrong (root causes)

| Problem | Cause in the code |
| --- | --- |
| Acoustic instruments sounded synthetic and alike | Brass, strings and woodwinds were one shared source-filter model each (`brass`, `strings`, `wind` engines) with different filter settings. Trumpet, horn, trombone and tuba differed only in formant frequency and brightness. |
| Brass Section was not an ensemble | It was the same brass voice stacked 5× with random detune. |
| String family too similar | One sawtooth-plus-body-filter model for violin, viola, cello and bass, separated only by EQ. |
| Organs nearly identical | One engine. The presets differed only in a few drawbars. Drawbars were linear, not 3 dB per step. There was no single-trigger percussion, no vibrato/chorus scanner, no foldback and no expression pedal, and one rotor ramp served both horn and drum. |
| Celesta and Music Box the same | Both were the same two-operator FM patch with a different ratio. |
| Prism Bells out of tune | Strong inharmonic partials at 2.76× and 4.07× (55% and 22% of the fundamental) pulled the perceived pitch. "Shimmer" detuned only upward (+2 to +11 cents), and the sparkles used the 7th and 11th harmonics (31–49 cents off the scale). |
| Repetition | Synth voices were identical on every hit, and there were no round robins anywhere. |
| AudioWorklets missing in browser tests | They cannot load from `file://` pages in a plain browser, so the tests now run over http. The desktop app was not affected; this is verified in the smoke test. |

## What changed

**Recorded instruments (new).** These come from VSCO 2 Community Edition, which is CC0 1.0: public domain, so it is free for commercial use and needs no attribution. They are prepared by `tools/build-samples.py`, and the samples total 31 MB of Ogg Opus.

- The build tool measures each recording's real pitch. It found that most folders name middle C "C3", so it corrects the octave. It then tunes every zone to A4 = 440 Hz, with a median correction of 0.5–8 cents.
- It keeps the real loudness contrast between dynamic layers.
- It bakes seamless crossfade loops into sustains.
- **Brass:** trumpet (sustain, staccato, straight mute), French horn, tenor trombone and tuba, each with its own recordings.
- **Strings:** solo violin (sustain, spiccato, pizzicato), violin section, viola section, cello section, solo double bass (bowed and pizzicato) and concert harp.
- **Woodwinds:** flute, oboe, clarinet and bassoon (sustain and staccato).
- **Mallets:** glockenspiel, xylophone and marimba.

**Recorded instrument engine (`orch`).**
- Velocity layers are equal-power crossfaded, or can be driven by the mod wheel for sustains.
- Round robins cycle per note, so repeated notes alternate between real takes.
- Legato mode starts the new note past its attack with a crossfade.
- The trombone has a slide mode that glides between overlapping notes.
- Pitch bend, release, tone and attack controls are included.

**Recorded sections (`section`).** This engine is used by Brass Section, String Ensemble, Woodwind Section and the other ensemble presets.
- Each note goes to the player whose range, sweet spot and current load suit it, so a chord is voiced across trumpet, horn, trombone and tuba by register.
- A lone note is doubled in unison by two different instruments. When more notes arrive within 40 ms (a chord), those doubles are withdrawn.
- Every player has its own seat, entry timing and round-robin cycle. Nothing is detuned.

**Organ.** The engine was rebuilt:
- 3 dB-per-step drawbars, with slightly impure tonewheels and foldback above about 5.9 kHz.
- Single-trigger percussion that disables the 1' bar, with normal/soft volume.
- Key click on press and release, tonewheel leakage, and the V1–C3 scanner.
- A swell pedal (a live parameter), a tube preamp with drive and tone, and a horn/drum balance.
- The horn and drum ramp at different speeds.

The three presets are now separate registrations:

- **Tonewheel Organ:** 888000000, chorus C3, slow rotor.
- **Jazz Organ:** 888000000 with 3rd percussion, rotor stopped, no scanner, clean amp, more click and leakage.
- **Full Gospel Organ:** 888880008, hot preamp, fast rotor. Expression on a Swell macro, aftertouch and CC11.

**Celesta.** A new modal model: felt hammer, free-bar partials, a wooden resonator that reinforces the fundamental, register-dependent decay, velocity-dependent hardness, and dampers.

**Music Box.** A new modal model: cantilever tine partials (1, 6.27, 17.55), a pin pluck, wooden case resonance and mechanism noise. Each tine has a fixed per-note tuning offset (±1.6 cents by default, adjustable). Striking a tine that is still ringing produces a damper buzz.

**Prism Bells.** The pitch is now carried by the fundamental, the octave and the 3rd harmonic, with the inharmonic partials kept quieter. Shimmer detunes in symmetric pairs, and sparkles use octaves and fifths only. Measured from C3 to C7, it is 0 to −1 cents.

## Measurements (from the acceptance tests)

- **Tuning, in cents from A4 = 440 Hz:**
  - Prism Bells: 0 to −1, from C3 to C7.
  - Celesta: 0.
  - Recorded trumpet, horn, trombone, violin, cello, flute and oboe: within ±5.
  - Tuba at its lowest test note: +15. This is a real player's intonation at the edge of the recorded range.
- **Brightness (RMS frequency):** trumpet about 1310 Hz, trombone about 770 Hz, horn about 520 Hz, tuba about 440 Hz. The muted trumpet differs from the open one. Celesta and Music Box differ by more than 2×.
- **Dynamics:** a soft trumpet note is about 6× quieter than a loud one, and audibly darker, because it is a different recording.

## Remaining limitations (honest)

- **No solo viola or solo cello recordings.** Those presets are now the viola and cello sections, and their names say so.
- **Not replaced with recordings:**
  - piano
  - electric pianos
  - guitars
  - piccolo
  - synth brass lead
  - choir
  - drums

  These are still the synthesized or physical models. They now carry "(modeled)" where a name could mislead. A sampled piano would need a licensed piano library; the VSCO upright piano was not included in this pass.
- **Mallet decays are limited by the length of the recordings** (glockenspiel up to 4 s).
- **Legato is attack-skipping with a crossfade, not recorded transitions.** The CC0 library has none.
- **Not built:** instrument artwork, reactive visual "worlds" per instrument, the mutation system, and the new signature engines (Crystal, Photon Harp, Liquid Metal, Clockwork, Organism). These are design proposals and are not implemented.
- **Automated measurements check tuning, distinctness and levels, but not authenticity.** A listening pass on good monitors is still needed before release.
