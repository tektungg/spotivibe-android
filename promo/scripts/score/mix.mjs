// Renders arrangement events into a mastered stereo buffer.
import { SAMPLE_RATE, reverb, seededRandom, stereoBuffer } from "./dsp.mjs";
import { DRUM_INSTRUMENTS, INSTRUMENTS, REVERB_SENDS } from "./instruments.mjs";

export const SCORE_SEED = 20261019;
// -1 dBFS sample peak leaves headroom for AAC, whose decoded true peak
// runs above the PCM peak.
export const TARGET_PEAK = 0.89;
const SIDECHAIN_DEPTH = 0.55;
const SIDECHAIN_RELEASE_S = 0.14;
const SOFT_CLIP_DRIVE = 1.25;
const FADE_OUT_S = 0.4;
const REVERB_WET = 0.9;

/** Gain curve that ducks the music bus after every kick (pumping). */
function sidechainCurve(events, length) {
  const curve = new Float32Array(length).fill(1);
  for (const e of events) {
    if (e.inst !== "kick") continue;
    const first = Math.round(e.time * SAMPLE_RATE);
    const last = Math.min(length, first + Math.round(SIDECHAIN_RELEASE_S * 5 * SAMPLE_RATE));
    for (let i = Math.max(0, first); i < last; i++) {
      const duck = 1 - SIDECHAIN_DEPTH * Math.exp(-(i - first) / SAMPLE_RATE / SIDECHAIN_RELEASE_S);
      curve[i] = Math.min(curve[i], duck);
    }
  }
  return curve;
}

function renderBuses(events, length) {
  const rand = seededRandom(SCORE_SEED);
  const drums = stereoBuffer(length);
  const music = stereoBuffer(length);
  const send = stereoBuffer(length);
  for (const e of events) {
    const play = INSTRUMENTS[e.inst];
    if (!play) throw new Error(`mix: unknown instrument "${e.inst}"`);
    const target = DRUM_INSTRUMENTS.has(e.inst) ? drums : music;
    play(target, e, rand);
    const sendLevel = REVERB_SENDS[e.inst];
    if (sendLevel) play(send, { ...e, gain: e.gain * sendLevel }, rand);
  }
  return { drums, music, send };
}

/**
 * Renders events to a mastered buffer of `durationSec` seconds.
 * Peak is normalized to TARGET_PEAK; the last FADE_OUT_S seconds fade out.
 */
export function renderScore(events, { durationSec }) {
  const length = Math.round(durationSec * SAMPLE_RATE);
  const inWindow = events.filter((e) => e.time < durationSec);
  const { drums, music, send } = renderBuses(inWindow, length);
  const duck = sidechainCurve(inWindow, length);
  const wet = reverb(send, { wet: REVERB_WET });
  const out = stereoBuffer(length);
  const norm = Math.tanh(SOFT_CLIP_DRIVE);
  let peak = 0;
  for (const ch of ["L", "R"]) {
    for (let i = 0; i < length; i++) {
      const x = drums[ch][i] + music[ch][i] * duck[i] + wet[ch][i];
      out[ch][i] = Math.tanh(x * SOFT_CLIP_DRIVE) / norm;
      peak = Math.max(peak, Math.abs(out[ch][i]));
    }
  }
  const gain = peak > 0 ? TARGET_PEAK / peak : 1;
  const fadeStart = length - Math.round(FADE_OUT_S * SAMPLE_RATE);
  for (const ch of ["L", "R"]) {
    for (let i = 0; i < length; i++) {
      const fade = i > fadeStart ? (length - i) / (length - fadeStart) : 1;
      out[ch][i] *= gain * fade;
    }
  }
  return out;
}

/** Peak and RMS of a stereo buffer, for logging and tests. */
export function levels({ L, R }, from = 0, to = L.length) {
  let peak = 0;
  let sum = 0;
  for (let i = from; i < to; i++) {
    peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
    sum += L[i] * L[i] + R[i] * R[i];
  }
  return { peak, rms: Math.sqrt(sum / Math.max(1, 2 * (to - from))) };
}
