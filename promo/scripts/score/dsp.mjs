// DSP building blocks for the synthesized score. Deterministic: every random
// value comes from a seeded PRNG, so the same arrangement renders the same
// samples on every machine.
import { writeFile } from "node:fs/promises";

export const SAMPLE_RATE = 44100;
const TWO_PI = Math.PI * 2;
const A4_HZ = 440;
const A4_MIDI = 69;
const SEMITONES = 12;

/** mulberry32: small, fast, seedable PRNG returning floats in [0, 1). */
export function seededRandom(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function midiToHz(midi) {
  return A4_HZ * 2 ** ((midi - A4_MIDI) / SEMITONES);
}

/** RBJ cookbook biquad. type: "lowpass" | "highpass" | "bandpass". */
export class Biquad {
  constructor(type, freq, q = Math.SQRT1_2) {
    this.type = type;
    this.x1 = this.x2 = this.y1 = this.y2 = 0;
    this.set(freq, q);
  }

  set(freq, q = this.q) {
    this.q = q;
    const nyquistSafe = Math.min(freq, SAMPLE_RATE * 0.45);
    const w0 = (TWO_PI * nyquistSafe) / SAMPLE_RATE;
    const cos = Math.cos(w0);
    const alpha = Math.sin(w0) / (2 * q);
    let b0, b1, b2;
    if (this.type === "lowpass") {
      b0 = (1 - cos) / 2; b1 = 1 - cos; b2 = b0;
    } else if (this.type === "highpass") {
      b0 = (1 + cos) / 2; b1 = -(1 + cos); b2 = b0;
    } else {
      b0 = alpha; b1 = 0; b2 = -alpha;
    }
    const a0 = 1 + alpha;
    this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0;
    this.a1 = (-2 * cos) / a0; this.a2 = (1 - alpha) / a0;
  }

  process(x) {
    const y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
    this.x2 = this.x1; this.x1 = x;
    this.y2 = this.y1; this.y1 = y;
    return y;
  }
}

/** Naive sawtooth in [-1, 1] for a phase in cycles. Filtered downstream. */
export function saw(phase) {
  return 2 * (phase - Math.floor(phase + 0.5));
}

/** Linear attack, exponential release envelope for a note of `dur` seconds. */
export function noteEnvelope(t, { attack, release, dur }) {
  if (t < 0) return 0;
  const rise = attack > 0 ? Math.min(1, t / attack) : 1;
  if (t <= dur) return rise;
  return rise * Math.exp(-(t - dur) / release);
}

/** Equal-power pan; pan in [-1, 1]. */
export function panGains(pan) {
  const angle = ((pan + 1) * Math.PI) / 4;
  return [Math.cos(angle), Math.sin(angle)];
}

/** A stereo buffer of float samples. */
export function stereoBuffer(lengthSamples) {
  return { L: new Float32Array(lengthSamples), R: new Float32Array(lengthSamples) };
}

/**
 * Adds `render(t)` (mono, t in seconds from note start) into a stereo bus.
 * Stops at `tail` seconds or the buffer end.
 */
export function addVoice(bus, { start, tail, gain = 1, pan = 0 }, render) {
  const [gl, gr] = panGains(pan);
  const first = Math.max(0, Math.round(start * SAMPLE_RATE));
  const last = Math.min(bus.L.length, Math.round((start + tail) * SAMPLE_RATE));
  for (let i = first; i < last; i++) {
    const v = render(i / SAMPLE_RATE - start) * gain;
    bus.L[i] += v * gl;
    bus.R[i] += v * gr;
  }
}

class Comb {
  constructor(size, feedback, damp) {
    this.buf = new Float32Array(size);
    this.i = 0; this.feedback = feedback; this.damp = damp; this.store = 0;
  }
  process(x) {
    const out = this.buf[this.i];
    this.store = out * (1 - this.damp) + this.store * this.damp;
    this.buf[this.i] = x + this.store * this.feedback;
    this.i = (this.i + 1) % this.buf.length;
    return out;
  }
}

class Allpass {
  constructor(size) { this.buf = new Float32Array(size); this.i = 0; }
  process(x) {
    const b = this.buf[this.i];
    this.buf[this.i] = x + b * 0.5;
    this.i = (this.i + 1) % this.buf.length;
    return b - x;
  }
}

// Freeverb tunings at 44.1 kHz; the right channel is offset for width.
const COMB_SIZES = [1116, 1188, 1277, 1356, 1422, 1491];
const ALLPASS_SIZES = [556, 441, 341];
const STEREO_SPREAD = 23;

/** Freeverb-style stereo reverb, processed in place into a new buffer. */
export function reverb(bus, { room = 0.84, damp = 0.3, wet = 1 } = {}) {
  const make = (offset) => ({
    combs: COMB_SIZES.map((s) => new Comb(s + offset, room, damp)),
    allpasses: ALLPASS_SIZES.map((s) => new Allpass(s + offset)),
  });
  const chans = [make(0), make(STEREO_SPREAD)];
  const out = stereoBuffer(bus.L.length);
  for (let i = 0; i < bus.L.length; i++) {
    const input = (bus.L[i] + bus.R[i]) * 0.015;
    [out.L, out.R].forEach((dst, c) => {
      let acc = 0;
      for (const comb of chans[c].combs) acc += comb.process(input);
      for (const ap of chans[c].allpasses) acc = ap.process(acc);
      dst[i] = acc * wet;
    });
  }
  return out;
}

const PCM_MAX = 32767;
const WAV_HEADER_BYTES = 44;

/** Writes 16-bit PCM stereo WAV. Samples are clamped to [-1, 1]. */
export async function writeWav(path, { L, R }) {
  const dataBytes = L.length * 4;
  const buf = Buffer.alloc(WAV_HEADER_BYTES + dataBytes);
  buf.write("RIFF", 0); buf.writeUInt32LE(36 + dataBytes, 4); buf.write("WAVE", 8);
  buf.write("fmt ", 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SAMPLE_RATE, 24);
  buf.writeUInt32LE(SAMPLE_RATE * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
  buf.write("data", 36); buf.writeUInt32LE(dataBytes, 40);
  for (let i = 0; i < L.length; i++) {
    const o = WAV_HEADER_BYTES + i * 4;
    buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, L[i])) * PCM_MAX), o);
    buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, R[i])) * PCM_MAX), o + 2);
  }
  await writeFile(path, buf);
}
