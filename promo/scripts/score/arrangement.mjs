// The score's arrangement: a pure list of note events in beats, derived from
// the same scene table the video uses (src/timeline.ts), so every hit lands
// on a cut. Key: F# minor, progression i-VI-III-VII (F#m D A E), one chord
// per bar.
import {
  BEATS_PER_BAR,
  BUILD_DROP_BEAT,
  END_SCROLL_BEAT,
  SECONDS_PER_BEAT,
  TOTAL_BEATS,
  sceneStartBeat,
  timedScenes,
} from "../../src/timeline.ts";

const CHORDS = [
  { root: 42, pad: [54, 57, 61, 66] }, // F#m
  { root: 38, pad: [50, 54, 57, 62] }, // D
  { root: 45, pad: [52, 57, 61, 64] }, // A
  { root: 40, pad: [52, 56, 59, 64] }, // E
];
/** Bell motif C#5 B4 A4 F#4, the recurring hook under the logo and end card. */
const MOTIF = [73, 71, 69, 66];
const MOTIF_STEP = 0.5;
const OCTAVE = 12;

const chordAt = (beat) => CHORDS[Math.floor(beat / BEATS_PER_BAR) % CHORDS.length];
const range = (from, to, step) => {
  const out = [];
  for (let b = from; b < to - 1e-9; b += step) out.push(b);
  return out;
};

/** Four-on-the-floor groove: kick, clap on 2 and 4, offbeat hats, bass, arp. */
function groove(from, to, { arp = true, sixteenthHats = false } = {}) {
  const ev = [];
  for (const b of range(from, to, 1)) {
    ev.push({ inst: "kick", beat: b, gain: 0.95 });
    if (Math.round(b) % 2 === 1) ev.push({ inst: "clap", beat: b, gain: 0.5 });
    ev.push({ inst: "hat", beat: b + 0.5, gain: 0.32, pan: 0.2 });
    ev.push({ inst: "bass", beat: b + 0.5, durBeats: 0.4, note: chordAt(b).root, gain: 0.42 });
  }
  if (sixteenthHats) {
    for (const b of range(from + 0.25, to, 0.5)) ev.push({ inst: "hat", beat: b, gain: 0.14, pan: -0.25 });
  }
  if (arp) {
    for (const [i, b] of range(from, to, 0.25).entries()) {
      const chord = chordAt(b).pad;
      ev.push({ inst: "pluck", beat: b, durBeats: 0.15, note: chord[i % chord.length] + OCTAVE, gain: 0.07, pan: i % 2 ? 0.45 : -0.45 });
    }
  }
  return ev;
}

/** One sustained pad chord per bar between two beats. */
function pads(from, to, { gain = 0.16, bright = false } = {}) {
  return range(from, to, BEATS_PER_BAR).flatMap((b) =>
    chordAt(b).pad.map((note, i) => ({
      inst: "pad", beat: b, durBeats: Math.min(BEATS_PER_BAR, to - b) - 0.1,
      note, gain, bright, pan: (i / 3 - 0.5) * 0.8,
    })),
  );
}

const motif = (at, gain) =>
  MOTIF.map((note, i) => ({ inst: "bell", beat: at + i * MOTIF_STEP, note, gain, pan: (i - 1.5) * 0.2 }));

function coldOpen(at) {
  const ev = [{ inst: "impact", beat: at, gain: 0.9 }];
  for (const b of range(at + 1, at + 9, 1)) {
    ev.push({ inst: "kick", beat: b, gain: 0.8 });
    ev.push({ inst: "whoosh", beat: b - 0.25, durBeats: 0.25, gain: 0.25, pan: b % 2 ? 0.5 : -0.5 });
    chordAt(b - at - 1).pad.forEach((note) => ev.push({ inst: "pluck", beat: b, durBeats: 0.3, note, gain: 0.07 }));
  }
  for (const b of range(at + 4, at + 9, 0.25)) ev.push({ inst: "hat", beat: b, gain: 0.1 + (b - at) * 0.015 });
  for (const b of range(at + 9, at + 10.5, 0.125)) ev.push({ inst: "tick", beat: b, gain: 0.35 });
  ev.push({ inst: "riser", beat: at + 9, durBeats: 2.5, gain: 0.35 });
  return ev;
}

function found(at) {
  const ev = [{ inst: "impact", beat: at, gain: 0.5 }, ...pads(at, at + 8)];
  for (const b of range(at, at + 3.5, 0.5)) ev.push({ inst: "pluck", beat: b, durBeats: 0.3, note: 66 + OCTAVE, gain: 0.12 });
  for (const b of range(at + 4, at + 8, 2)) ev.push({ inst: "kick", beat: b, gain: 0.7 });
  for (const b of range(at + 5, at + 8, 2)) ev.push({ inst: "clap", beat: b, gain: 0.35 });
  return ev;
}

function logoMorph(at) {
  const ev = [{ inst: "impact", beat: at, gain: 0.8 }, ...motif(at, 0.22), ...pads(at, at + 8, { bright: true })];
  for (const [i, b] of range(at + 2, at + 3.25, 0.25).entries()) ev.push({ inst: "tick", beat: b, gain: 0.3 + i * 0.05 });
  ev.push({ inst: "riser", beat: at + 6, durBeats: 2, gain: 0.3 });
  return ev;
}

function build(at) {
  const drop = at + BUILD_DROP_BEAT;
  const ev = [{ inst: "impact", beat: at, gain: 0.55 }, ...pads(at, drop, { bright: true }), { inst: "riser", beat: at, durBeats: BUILD_DROP_BEAT, gain: 0.5 }];
  const rolls = [[at, at + 4, 0.5], [at + 4, at + 7, 0.25], [at + 7, drop, 0.125]];
  for (const [from, to, step] of rolls) {
    for (const b of range(from, to, step)) ev.push({ inst: "clap", beat: b, gain: 0.18 + ((b - at) / BUILD_DROP_BEAT) * 0.3 });
  }
  ev.push({ inst: "impact", beat: drop, gain: 1 }, ...groove(drop, drop + 4, { sixteenthHats: true }));
  for (const b of range(drop, drop + 4, 1)) {
    chordAt(b).pad.forEach((note) => ev.push({ inst: "pluck", beat: b, durBeats: 0.5, note: note + OCTAVE, gain: 0.08 }));
  }
  ev.push({ inst: "whoosh", beat: drop + 4, durBeats: 2, gain: 0.5 });
  return ev;
}

function endCard(at) {
  const ev = [{ inst: "impact", beat: at, gain: 1 }, { inst: "kick", beat: at, gain: 1 }];
  CHORDS[0].pad.forEach((note, i) => ev.push({ inst: "pad", beat: at, durBeats: 9, note, gain: 0.17, bright: true, pan: (i / 3 - 0.5) * 0.8 }));
  ev.push({ inst: "bass", beat: at, durBeats: 6, note: CHORDS[0].root, gain: 0.3 });
  ev.push(...motif(at + 1, 0.2));
  ev.push({ inst: "bell", beat: at + END_SCROLL_BEAT, note: 78, gain: 0.28 }, { inst: "bell", beat: at + END_SCROLL_BEAT, note: 66, gain: 0.16 });
  return ev;
}

function middleSections() {
  const hero = sceneStartBeat("hero");
  const accent = sceneStartBeat("accent");
  const everywhere = sceneStartBeat("everywhere");
  const board = sceneStartBeat("board");
  const buildAt = sceneStartBeat("build");
  const ev = [{ inst: "impact", beat: hero, gain: 0.8 }, ...groove(hero, accent), ...pads(hero, buildAt, { gain: 0.11 })];
  ev.push(...groove(accent, everywhere));
  for (const b of range(accent, accent + 8, 2)) ev.push({ inst: "whoosh", beat: b - 0.5, durBeats: 0.5, gain: 0.22 });
  ev.push({ inst: "impact", beat: accent + 8, gain: 0.5 });
  ev.push({ inst: "impact", beat: everywhere, gain: 0.45 }, ...groove(everywhere + 2, board, { arp: false }), ...motif(everywhere, 0.12));
  ev.push({ inst: "bell", beat: everywhere + 6, note: 78, gain: 0.18 });
  ev.push({ inst: "impact", beat: board, gain: 0.6 }, ...groove(board, buildAt, { sixteenthHats: true }));
  for (const row of range(board, board + 12, 2)) {
    for (const b of range(row, row + 0.75, 1 / 16)) ev.push({ inst: "tick", beat: b, gain: 0.16 });
  }
  return ev;
}

/** All note events, each with absolute seconds, sorted by start time. */
export function buildEvents() {
  const ev = [
    ...coldOpen(sceneStartBeat("coldOpen")),
    ...found(sceneStartBeat("found")),
    ...logoMorph(sceneStartBeat("logoMorph")),
    ...middleSections(),
    ...build(sceneStartBeat("build")),
    ...endCard(sceneStartBeat("endCard")),
  ];
  return ev
    .filter((e) => e.beat >= 0 && e.beat < TOTAL_BEATS)
    .map((e) => ({ ...e, time: e.beat * SECONDS_PER_BEAT, dur: (e.durBeats ?? 0) * SECONDS_PER_BEAT }))
    .sort((a, b) => a.time - b.time);
}

/** Scene start beats, exported so tests can check every cut gets a hit. */
export const cutBeats = () => timedScenes().map((s) => s.startBeat);
