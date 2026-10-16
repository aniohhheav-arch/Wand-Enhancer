# Soundcraft

A cozy pixel-art music studio that is also a working DAW. Everything ships as one self-contained file, `index.html`. It runs in any modern desktop browser and needs no build step or server.

This is the **first vertical slice** of the Soundcraft brief. It covers the core of phases 1–4: a real audio engine, a reliable editing workflow, the cozy visual identity, and music-reactive visuals. Later phases are listed under "Not built yet". They are deliberately not faked in the UI.

## Run it

Open `soundcraft/index.html` in Chrome or Edge. Click once to wake the audio engine, then press **Space** to play the demo cassette.

Web MIDI needs a real browser tab (Chrome, Edge, Opera or Firefox). Embedded previews usually block it.

## What works (verified)

Each item below is covered by `tests/e2e.cjs`, which drives the real page in headless Chromium.

| Area | Status |
| --- | --- |
| **Audio engine** | Web Audio graph per track: instrument → pan → fader → master bus, plus post-fader reverb and delay sends, a glue compressor and a limiter. A look-ahead scheduler (25 ms tick, 120 ms horizon) runs on the audio clock, separate from rendering. |
| **Instruments (8, all synthesized)** | Nebula Pad (supersaw), Glass Keys (FM electric piano), Acid Line (mono bass with glide), Growl (tempo-synced wobble bass), Vox Choir (formant voices), Circuit Kit (12-pad analog-style drums), 8-Bit (pulse chiptune), Prism Bells (additive). Each has three macro knobs plus pan, reverb and delay. |
| **Transport** | Play, stop (a second press silences every note), record, BPM 50–220, loop length 1/2/4/8 bars, metronome, swing, quantize. |
| **Piano roll / drum grid** | Draw by dragging, move (time and pitch), resize, delete (click or right-click), velocity (scroll on a note). Key preview, octave labels, named drum rows. |
| **Recording** | Overdub from MIDI, the computer keyboard or the on-screen keys into the selected track, with optional quantize. |
| **MIDI hardware** | Auto-detects devices, including hot-plug. Handles note on/off with velocity, sustain (CC64), mod wheel (CC1), pitch bend, all-notes-off, channel filter and MIDI learn for every knob. |
| **Mixer** | Per-track volume, mute, solo, pan and sends. Real per-track level meters, master volume, and a clip indicator measured before the limiter. |
| **Export** | Offline render of one loop pass plus a 2.5 s tail to a 16-bit stereo 44.1 kHz WAV. Inside the claude.ai viewer it is zipped, because that viewer does not allow `.wav` downloads. |
| **Projects ("cassettes")** | Create, open, rename, duplicate, recolor, delete (with confirmation), search, and import/export as `.soundcraft.json`. Autosave (debounced, plus on page hide), a schema version with a migration hook, and a clear error for bad files. A failed load never overwrites a stored project. |
| **Undo / redo** | Note edits, clears and recordings: Ctrl+Z, Ctrl+Shift+Z / Ctrl+Y. |
| **Studio (pixel bedroom)** | Walk with WASD or the arrow keys, or click anything to walk to it. Press E to use the nearest object. Computer opens the DAW. Keyboard opens the instrument. Groovebox opens the drum track. Record player plays or stops. Lamp toggles. Window cycles the weather. The cassette shelf opens the library and shows your real tapes' colors. |
| **Music-reactive room** | All driven by the live analyser, never random. The lamp pulses with bass energy. Speaker cones move with bass. A desk figurine hops on detected kicks. Note sprites float up with mid-range energy. Sparkles follow the highs. A haze follows the real reverb return level. The monitor shows the real output waveform. |
| **Cat** | Wanders between favorite spots (couch, window sill, desk, speakers, rug, shelf). Sleeps when it's quiet, prefers the speakers during playback, and bobs its head on detected kicks with adjustable sensitivity. Pet it for hearts and a purr. Five coats. |
| **Settings** | Six weathers, lamp, ambience sound and volume, reduced motion (also follows the OS setting), cat coat and reaction sensitivity. All persisted. |
| **Ambience routing** | Rain, wind or room-tone ambience and the cat's purr play on their own output. They never reach the master bus, the meters, the visuals or exported audio. |

## Not built yet

These parts of the brief do not exist in this slice. Nothing in the UI pretends they do.

- **Arrangement timeline with clips.** Each track is currently one looping pattern of up to 8 bars. There are no clips, markers, sections, automation lanes, track groups or time signatures other than 4/4.
- **Audio tracks.** No microphone or audio-interface recording, sampler, WAV import, sample library or Sound Playground.
- **MIDI file import/export.**
- **Step sequencer view.** Drums are programmed in the drum grid. Per-step probability, pattern chaining and duplication are not built.
- **Insert effects, effect toys and Sound Fusion.**
- **Other workspaces:** Sounds, Visuals (full-screen visualizers and video export), Journal, World Map and every world beyond the bedroom, Songworld Generator, Listening Café, venues and live gigs.
- **Progression:** quests, Notes currency, skill tree, rhythm arcade, loot, garden. A `quest()` hook is called from real events (export) and does nothing yet.
- **Happy Accident Generator, Song Variants, Focus Mode.**
- **Desktop and Steam:** no native app, installer, Steamworks, DLC or content shop. There is also no developer console. It must not be added to a browser build behind a key combination; it needs a developer-enabled build or an authenticated developer session.
- **Plugins:** browser audio cannot host VST3/AU plugins. All instruments are built in.

## Suggested next steps

1. Replace the single loop with a clip-based arrangement model: clips reference patterns, plus markers. Migrate schema v1 projects by wrapping each track's notes in one clip.
2. Move project storage from `localStorage` to IndexedDB before adding audio. Store audio blobs separately from project JSON.
3. Add MIDI file import/export and audio tracks, with recording through `getUserMedia` and an AudioWorklet recorder.
4. Then build the Visuals workspace and the Songworld Generator on the existing analysis data (`A.bass`, `A.mid`, `A.high`, `A.kick`, `A.rev`) and the project's MIDI notes.
5. For Steam, wrap the same engine in Electron or Tauri. Only then add Steamworks, the content manager and a developer build flag.

## Tests

```sh
npm i -D playwright      # or point PLAYWRIGHT_MODULE at an existing install
node soundcraft/tests/e2e.cjs [screenshot-dir]
```

The suite checks:

- creating a project
- drawing, moving, undoing and redoing notes
- that live keys and the sequencer produce real audio output
- that mute zeroes track signal
- that WAV export contains real audio
- that a project survives a reload
- that the studio visuals read real analysis data
- that the step clock stays exact while the studio animates
- studio click-to-walk, petting the cat, lamp persistence, and rejection of invalid projects
- no horizontal scroll at phone width, and no page errors
