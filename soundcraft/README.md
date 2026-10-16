# Soundcraft

A cozy pixel-art music studio with a real DAW inside. It runs as a Windows desktop app (Electron) and in a browser, from the same code in `app/`.

Version 0.3.0. Status below is checked against `tests/e2e.cjs` (acceptance tests that render and measure real audio) and `tests/electron-smoke.cjs` (desktop shell).

## Get the Windows app

- **From CI.** Every push that touches `soundcraft/` runs the GitHub Actions workflow *Soundcraft desktop build*. Open the run, then download the `soundcraft-windows` artifact. It contains:
  - `Soundcraft-Setup-0.3.0.exe`, a per-user installer that lets you choose the folder
  - `Soundcraft-0.3.0-portable.exe`, which runs without installing
- **Build it yourself on Windows:** `cd soundcraft && npm ci && npm run dist:win`.
- **Unsigned.** The builds are not code-signed, so Windows SmartScreen will warn the first time ("More info → Run anyway"). Signing needs a certificate that you buy and keep out of the repository.
- **Run from source** on any OS: `npm ci && npm start`.

Your projects, presets and imported samples live in the app's user-data folder (`%APPDATA%\Soundcraft`). They survive updates and uninstalls. Timestamped project backups go to `%APPDATA%\Soundcraft\Backups`, logs to `…\Logs`, and exports default to `Documents\Soundcraft`.

### Release checklist (not done yet)

1. Test both executables on a clean Windows 10/11 machine: install, first launch, MIDI keyboard, export, update over an older version, uninstall.
2. Code-sign with your certificate (`CSC_LINK` / `CSC_KEY_PASSWORD` secrets in CI). Never commit keys.
3. Steam: create the app in Steamworks, upload `dist/win-unpacked` as the depot, and add Steamworks integration if you want achievements or DLC. None of that exists yet.
4. Write the privacy note. Soundcraft makes no network requests at runtime; fonts are bundled.

## What works (verified)

| Area | What you get |
| --- | --- |
| Sound engines (24) | Analog/subtractive, Wavetable (morphable 4-frame tables), FM, Tonewheel Organ (drawbars and a real rotary speaker model), Modeled Piano (additive, stretched partials), Brass Model, Bowed Strings, Woodwind Model, Plucked String (Karplus-Strong in an AudioWorklet), Drum Machine (8 kits × 12 editable pads), Granular Cloud, Tape & Vinyl, Reverse Swell, Cinematic Impact, Glitch Stutter, Organic Modal, Fusion Lab (4-layer rack), Sampler, plus Nebula Pad, Acid Line, Growl Bass, Vox Choir, 8-Bit and Prism Bells from earlier versions. Every one renders audio in the tests. |
| Library | 101 factory presets across keys, brass, strings, woodwinds, synths, bass & guitars, drums, experimental and Fusion. A preset is a saved configuration of one engine and is never counted as an instrument. |
| Fusion Lab | Up to 4 layers from any compatible preset. Each layer has level, pan, filter, transpose, fine tune, fade-in, drive, key range, velocity range with crossfade, solo, duplicate and reorder, plus per-layer sound overrides. Covers key splits, stacks and velocity switching. |
| Sampler | Import WAV/MP3/OGG/FLAC; files are stored in IndexedDB and deduplicated by content hash. Zones have key and velocity ranges, root, start/end, loop points, reverse, tune, level, pan and round-robin groups. Edit points on the waveform; replace missing samples. |
| Modulation | Sources: LFOs (sine, triangle, saw, square, random, custom, free or tempo-synced, optional restart per note), ADSR and multi-stage envelopes, step modulators, sample & hold, envelope followers on other tracks, velocity, key tracking, note random, mod wheel, pitch bend, channel and poly aftertouch, any MIDI CC, and macros. Routes have amount, polarity, bipolar option, curve, min/max range, smoothing, on/off, name and color. Destinations come from the parameter registry; each is marked **live** (audio-rate) or **note start**. Every instrument also gets per-voice level, pan, filter and pitch. Routing is drag and drop, with knob rings, an overview graph and refusal of feedback loops between tracks. |
| Macros | Any number per instrument, each mapping to many controls with its own min/max, reverse and curve. MIDI-learnable, usable as modulation sources, and shown as big knobs in the instrument view. |
| Patch Lab | All engine controls grouped into modules with real visualizations (envelope shapes, filter response from a real BiquadFilter, oscillator and wavetable shapes). Save as a new preset; overwrite only your own presets; revert; export; A/B compare; history with restore and "restore start"; randomize a single control, a module, the modulation, a layer, or a new patch in a category. Factory presets never change. |
| Arrangement | Clip timeline with patterns per track. Add, move and resize clips (bar or beat snap), duplicate, split, make unique, delete. Loop region, play cursor, section markers (rename, loop section, move, delete). Track rename, color, reorder, duplicate and delete with confirmation. Resizable timeline/editor split. |
| Piano roll | Draw, move, resize; marquee and Shift selection; duplicate, nudge, delete; chord drawing (triad, 7th, power, octave) that follows the scale; scale highlighting and snap; 1/32 to 1/4 grid; velocity and probability lanes; quantize; humanize; patterns (new, duplicate, rename, length); a break-pattern helper on drum tracks. |
| Playback and recording | Look-ahead scheduler on the audio clock (the tests check timing drift stays below 1 µs), swing, metronome, stateless arpeggiator (up, down, up/down, random, chord) that behaves the same live and in export, note probability. Overdub recording into the clip under the playhead or a new clip. |
| MIDI | Device hot-plug, channel filter, notes with velocity, sustain, mod wheel, pitch bend with per-track range, channel and poly aftertouch, CC as a modulation source, and MIDI learn for any control or macro. MIDI file import and export (SMF type 1). |
| Ensembles | Several instruments played from one keyboard, split by key range, velocity range, MIDI channel or transpose. 6 factory ensembles; save your own. |
| Effects (17) | Parametric EQ (with response curve), Filter (12/24 dB, synced LFO), Compressor (gain reduction readout), Limiter, Gate, Transient shaper, Saturation (soft, hard, fold), Bitcrusher, Chorus, Flanger, Phaser, Tremolo, Auto-pan, Stereo width, Delay (synced, ping-pong), Reverb (rebuilds its room), Dream machine. Reverb, delay, filter and Dream machine have XY "toys" bound to the real controls. All render in the tests. |
| Mixer | Strips for each track, the reverb and delay returns, and master. Inserts can be added, bypassed, reordered and removed; sends, pan, faders, peak meters with clip hold, mute/solo. Effect-chain presets (7 factory). Master meters: L/R peak, momentary, short-term and integrated loudness (approximate, ungated K-weighted). Reference-track A/B with optional loudness match, on its own output. |
| Presets everywhere | Instrument presets, effect chains, track presets, ensembles, project templates (5 factory) and modulation patches. All are searchable with favorites, recents, genre and mood filters, previews, duplicate detection, import/export, missing-content badges and compatibility checks. |
| Projects | Cassette library, autosave, the last 5 autosaves kept as backups (plus timestamped files on disk in the desktop app), named snapshots with compare and restore (restoring first takes a safety snapshot), whole-project undo/redo, crash detection on the next launch, project files that can embed your own samples, and migration of v1 projects. |
| Export | Faster-than-real-time render to WAV at 44.1, 48 or 96 kHz in 16-bit, 24-bit or 32-bit float. Whole song or loop region; stereo mix and/or per-track stems. Progress is shown, the saved path is reported, and silent files are flagged. |
| Desktop app | Native save dialogs (never overwrites existing files when exporting several), on-disk backups, logs, a CPU readout, a crash dialog with reload, a single instance, offline fonts, context isolation and sandbox, and only the MIDI permission granted. |
| Studio | The pixel bedroom from earlier versions. The lamp, speakers, figurine, cat, sparkles and haze react to the real audio analysis. Objects open the DAW, the Patch Lab, the drum pads and the cassette library. |
| Settings | Themes (match system, night, day), interface size, reduced motion, studio weather and ambience (kept off the master bus), cat coat and sensitivity, audio output device (where the system allows), latency mode, sample rate with engine restart, storage usage, and a copyable/saveable diagnostics report. |
| Help | Searchable guide and an optional five-step tour. |

## Partly done

- **CPU display.** The desktop app shows process CPU. Browsers do not expose audio-thread load, so the browser shows JS memory instead. Voice count is shown everywhere.
- **Buffer size.** Only the latency mode (interactive, balanced, playback) can be chosen. Web Audio picks the actual buffer and does not report it. Latency has not been measured on real hardware.
- **Loudness metering** is approximate and ungated, and labelled as such.
- **Pitch bend.** Works on every engine except Plucked String, whose delay-line model has a fixed pitch.
- **Modulation scope.** Live (audio-rate) destinations are the parameters that map to real AudioParams. Others, such as unison count or waveform choice, change when a note starts. The UI marks which is which.
- **Arpeggiator.** MIDI file export writes the held notes, not the arpeggiated result.

## Not built yet

- Audio tracks, microphone/interface recording, comping, time-stretch, slicing, resampling, freezing and bouncing.
- Automation lanes.
- Tempo and time-signature changes (4/4 at one tempo only).
- Track groups and buses, and sidechain routing.
- MP3 export (no encoder is bundled).
- Plugin hosting (VST3/AU). An Electron/Web Audio app cannot host native plugins.
- Recorded multisample instruments. Every built-in sound is synthesized or physically modeled. Sampled pianos, brass and strings need properly licensed recordings, which this project does not have yet.
- Customizable keyboard shortcuts.
- Game layer beyond the studio room: world map and locations, quests and Notes currency, Songworld Generator, garden, rhythm arcade, venues and live performance mode, cat cosmetics shop, content shop, developer console.
- Steamworks integration and code signing (see the release checklist).

## Architecture

```
app/        web app (also packaged into the exe)
  js/core.js        audio context, master graph, worklets (Karplus-Strong, gate, crusher, transient), analysis
  js/params.js      parameter registry shared by knobs, modulation, macros, presets and randomize
  js/effects.js     effect rack
  js/engines*.js    sound engines and drum kits
  js/sampler.js     IndexedDB sample store and the Sampler engine
  js/mod.js         modulation sources, routes, macros, live AudioParam wiring
  js/library.js     factory content and the preset file format
  js/project.js     project model, persistence, snapshots, undo
  js/sequencer.js   scheduler, arpeggiator, recording, offline render, WAV/zip, MIDI files
  js/ui-*.js        workspaces and dialogs; studio.js pixel room; main.js boot and shortcuts
electron/   desktop shell (main process, preload bridge)
tests/      acceptance tests (browser) and desktop shell smoke test
tools/      artifact-page.py builds the hosted web version
```

Web Audio processes audio on its own rendering thread. The JavaScript main thread only schedules notes 120 ms ahead and draws the UI, and game animation never touches scheduling.

## Preset file format

Every exported preset, ensemble, track preset, effect chain, template or modulation patch is one JSON file:

```json
{ "format": "soundcraft", "formatVersion": 1, "type": "instrument | ensemble | track | fxchain | template | modpatch",
  "name": "…", "author": "…", "created": "ISO date", "app": "Soundcraft 0.3.0",
  "data": { … }, "samples": { "smp_<hash>": { "name": "…", "b64": "…" } } }
```

On import, Soundcraft checks:

- **Version:** a newer `formatVersion` is refused with an explanation.
- **Engines and effects:** unknown ones are refused or listed.
- **Values:** every parameter value is clamped to its registry range.
- **Modulation:** sources are rebuilt from known types only.
- **Dependencies:** missing samples or presets are reported, and their parts stay silent.

Nothing in a file is ever executed. Exports only embed samples you imported yourself.

Project files use the same envelope with `"type": "project"` and are imported from the Cassettes library.

## Tests

```sh
cd soundcraft && npm ci && npx playwright install chromium
node tests/e2e.cjs [screenshot-dir]                 # acceptance tests, renders and measures audio
xvfb-run -a node tests/electron-smoke.cjs [dir]     # desktop shell (Linux; use a display on other OSes)
```
