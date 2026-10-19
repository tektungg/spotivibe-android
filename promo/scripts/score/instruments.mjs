// Instruments of the score. Each one adds a single event into a stereo bus:
// play(bus, event, rand). Event times are in seconds; `rand` is the seeded PRNG.
import { Biquad, addVoice, midiToHz, noteEnvelope, saw, SAMPLE_RATE } from "./dsp.mjs";

const TWO_PI = Math.PI * 2;

function kick(bus, ev) {
  const TAIL = 0.5;
  let phase = 0;
  addVoice(bus, { start: ev.time, tail: TAIL, gain: ev.gain }, (t) => {
    const freq = 46 + 110 * Math.exp(-t / 0.035);
    phase += freq / SAMPLE_RATE;
    const body = Math.sin(TWO_PI * phase) * Math.exp(-t / 0.28);
    const click = t < 0.004 ? (1 - t / 0.004) * 0.6 : 0;
    return Math.tanh((body + click) * 1.6);
  });
}

function noiseVoice(bus, ev, rand, { filter, tail, decay, bursts = [0] }) {
  addVoice(bus, { start: ev.time, tail, gain: ev.gain, pan: ev.pan ?? 0 }, (t) => {
    let env = 0;
    for (const b of bursts) if (t >= b) env = Math.max(env, Math.exp(-(t - b) / decay));
    return filter.process(rand() * 2 - 1) * env;
  });
}

function clap(bus, ev, rand) {
  const filter = new Biquad("bandpass", 1400, 1.1);
  noiseVoice(bus, ev, rand, { filter, tail: 0.35, decay: 0.07, bursts: [0, 0.011, 0.022] });
}

function hat(bus, ev, rand) {
  const filter = new Biquad("highpass", 7800, 0.9);
  noiseVoice(bus, ev, rand, { filter, tail: ev.open ? 0.3 : 0.08, decay: ev.open ? 0.12 : 0.025 });
}

function tick(bus, ev, rand) {
  const filter = new Biquad("bandpass", 3600, 4);
  noiseVoice(bus, ev, rand, { filter, tail: 0.03, decay: 0.006 });
}

/** Sawtooth voice through a lowpass whose cutoff decays from `cutoff`. */
function filteredSaw(bus, ev, { voices, detune, cutoff, cutoffFloor, env }) {
  const filter = new Biquad("lowpass", cutoff, 0.9);
  const hz = midiToHz(ev.note);
  const phases = new Array(voices).fill(0).map((_, i) => i / voices);
  let n = 0;
  addVoice(bus, { start: ev.time, tail: ev.dur + env.release * 5, gain: ev.gain, pan: ev.pan ?? 0 }, (t) => {
    if (n++ % 32 === 0) filter.set(cutoffFloor + (cutoff - cutoffFloor) * Math.exp(-t / env.sweep));
    let s = 0;
    for (let v = 0; v < voices; v++) {
      const spread = voices > 1 ? (v / (voices - 1) - 0.5) * detune : 0;
      phases[v] += (hz * 2 ** (spread / 1200)) / SAMPLE_RATE;
      s += saw(phases[v]);
    }
    return filter.process(s / voices) * noteEnvelope(t, { attack: env.attack, release: env.release, dur: ev.dur });
  });
}

function bass(bus, ev) {
  filteredSaw(bus, ev, {
    voices: 2, detune: 8, cutoff: 900, cutoffFloor: 220,
    env: { attack: 0.004, release: 0.06, sweep: 0.12 },
  });
}

function pad(bus, ev) {
  filteredSaw(bus, ev, {
    voices: 4, detune: 18, cutoff: ev.bright ? 2600 : 1300, cutoffFloor: ev.bright ? 1600 : 700,
    env: { attack: 0.35, release: 0.6, sweep: 2.5 },
  });
}

function pluck(bus, ev) {
  filteredSaw(bus, ev, {
    voices: 2, detune: 10, cutoff: 4200, cutoffFloor: 500,
    env: { attack: 0.002, release: 0.12, sweep: 0.09 },
  });
}

/** Two-operator FM bell. */
function bell(bus, ev) {
  const hz = midiToHz(ev.note);
  addVoice(bus, { start: ev.time, tail: 2.6, gain: ev.gain, pan: ev.pan ?? 0 }, (t) => {
    const mod = Math.sin(TWO_PI * hz * 3.5 * t) * 2.2 * Math.exp(-t / 0.4);
    return Math.sin(TWO_PI * hz * t + mod) * Math.exp(-t / 0.75) * Math.min(1, t / 0.002);
  });
}

/** Noise swept up through a bandpass plus a rising sine, `dur` seconds long. */
function riser(bus, ev, rand) {
  const filter = new Biquad("bandpass", 400, 1.4);
  let n = 0;
  let phase = 0;
  addVoice(bus, { start: ev.time, tail: ev.dur, gain: ev.gain }, (t) => {
    const p = t / ev.dur;
    if (n++ % 32 === 0) filter.set(300 + 7000 * p * p, 1.4);
    phase += (180 + 900 * p * p) / SAMPLE_RATE;
    const tone = Math.sin(TWO_PI * phase) * 0.25;
    return (filter.process(rand() * 2 - 1) + tone) * p * p;
  });
}

/** Reverse-swell whoosh that peaks exactly at ev.time + ev.dur. */
function whoosh(bus, ev, rand) {
  const filter = new Biquad("bandpass", 900, 0.8);
  let n = 0;
  addVoice(bus, { start: ev.time, tail: ev.dur + 0.08, gain: ev.gain, pan: ev.pan ?? 0 }, (t) => {
    const p = Math.min(1, t / ev.dur);
    if (n++ % 32 === 0) filter.set(500 + 3500 * p, 0.8);
    const env = t <= ev.dur ? p ** 3 : Math.exp(-(t - ev.dur) / 0.02);
    return filter.process(rand() * 2 - 1) * env;
  });
}

/** Cinematic hit: sub boom + noise crack. */
function impact(bus, ev, rand) {
  const filter = new Biquad("lowpass", 5000, 0.7);
  let phase = 0;
  addVoice(bus, { start: ev.time, tail: 2.4, gain: ev.gain }, (t) => {
    phase += (38 + 70 * Math.exp(-t / 0.08)) / SAMPLE_RATE;
    const boom = Math.sin(TWO_PI * phase) * Math.exp(-t / 0.9);
    const crack = filter.process(rand() * 2 - 1) * Math.exp(-t / 0.18) * 0.7;
    return Math.tanh((boom + crack) * 1.4);
  });
}

export const INSTRUMENTS = { kick, clap, hat, tick, bass, pad, pluck, bell, riser, whoosh, impact };

/** Instruments mixed on the drum bus (never ducked by the sidechain). */
export const DRUM_INSTRUMENTS = new Set(["kick", "clap", "hat", "tick", "impact", "riser", "whoosh"]);
/** Instruments that also feed the reverb send. */
export const REVERB_SENDS = { clap: 0.5, pad: 0.6, pluck: 0.7, bell: 1, impact: 0.5, whoosh: 0.4, riser: 0.3 };
