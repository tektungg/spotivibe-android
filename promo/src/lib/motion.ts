import { Easing, interpolate, spring, type SpringConfig } from "remotion";
import { FPS, FRAMES_PER_BEAT } from "../timeline";
import { EASE } from "../theme/tokens";

const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

type EaseFn = (t: number) => number;

/** Clamped, eased tween of `frame` from [start, end] onto [from, to]. */
export function tween(
  frame: number,
  [start, end]: [number, number],
  [from, to]: [number, number],
  easing: EaseFn = EASE.out,
): number {
  return interpolate(frame, [start, end], [from, to], { ...CLAMP, easing });
}

/** 0 -> 1 progress between two frames. */
export function progress(frame: number, start: number, end: number, easing: EaseFn = EASE.out): number {
  return tween(frame, [start, end], [0, 1], easing);
}

export const SPRING_SNAPPY: Partial<SpringConfig> = { damping: 14, stiffness: 180, mass: 0.7 };
export const SPRING_SOFT: Partial<SpringConfig> = { damping: 20, stiffness: 90, mass: 1 };
export const SPRING_BOUNCY: Partial<SpringConfig> = { damping: 9, stiffness: 160, mass: 0.6 };

/** Spring that starts at `delay` frames; 0 before it. */
export function springAt(frame: number, delay: number, config: Partial<SpringConfig> = SPRING_SNAPPY): number {
  return spring({ frame: frame - delay, fps: FPS, config });
}

/** Frame of beat `b` (fractional beats allowed), relative to the scene start. */
export const beat = (b: number): number => Math.round(b * FRAMES_PER_BEAT);

/**
 * Decaying pulse that fires on every beat: 1 on the beat, ~0 by `decayFrames`.
 * Used to make static elements breathe with the kick.
 */
export function beatPulse(frame: number, decayFrames = 10): number {
  const sinceBeat = frame % FRAMES_PER_BEAT;
  return Math.exp(-sinceBeat / decayFrames);
}

/** Characters a fixed-rate typewriter has revealed by `frame`, 0..length. */
export function typedChars(frame: number, start: number, framesPerChar: number, length: number): number {
  return Math.max(0, Math.min(length, Math.floor((frame - start) / framesPerChar)));
}

const LYRIC_SCROLL_FRAMES = 20;
const SWEEP_MARGIN_FRAMES = 4;

/**
 * Lyric playhead: from `origin`, one line every `lineFrames`. `active` eases
 * from base - 1 to base on the first line, then +1 per line; `sweep` is the
 * karaoke wipe across the current line.
 */
export function lyricClock(frame: number, { origin, base, lineFrames }: { origin: number; base: number; lineFrames: number }) {
  const elapsed = Math.max(0, frame - origin);
  const local = elapsed % lineFrames;
  return {
    active: base + Math.floor(elapsed / lineFrames) - 1 + progress(local, 0, LYRIC_SCROLL_FRAMES),
    sweep: progress(local, SWEEP_MARGIN_FRAMES, lineFrames - SWEEP_MARGIN_FRAMES, Easing.linear),
  };
}
