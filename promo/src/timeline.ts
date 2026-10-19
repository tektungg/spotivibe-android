/**
 * Single source of truth for the promo's timing.
 *
 * Everything is counted in beats at a fixed tempo, so the picture and the
 * synthesized score (scripts/score) cut on exactly the same grid. At 120 BPM
 * and 60 fps one beat is 30 frames, so beat math never rounds.
 *
 * This file must stay free of imports: Node imports it directly (native type
 * stripping) from the score generator and the tests.
 */

export const FPS = 60;
export const BPM = 120;
export const BEATS_PER_BAR = 4;
export const WIDTH = 1920;
export const HEIGHT = 1080;

const SECONDS_PER_MINUTE = 60;
export const FRAMES_PER_BEAT = (FPS * SECONDS_PER_MINUTE) / BPM;
export const SECONDS_PER_BEAT = SECONDS_PER_MINUTE / BPM;

export type SceneId =
  | "coldOpen"
  | "found"
  | "logoMorph"
  | "hero"
  | "accent"
  | "everywhere"
  | "board"
  | "build"
  | "endCard";

export interface SceneSpec {
  id: SceneId;
  beats: number;
  title: string;
}

/** Scenes in playback order. Their beats must sum to TOTAL_BEATS (60 s). */
export const SCENES: readonly SceneSpec[] = [
  { id: "coldOpen", beats: 12, title: "Cold open: every player, no lyrics" },
  { id: "found", beats: 8, title: "Lyrics found, tagline" },
  { id: "logoMorph", beats: 8, title: "Lyric lines become the mark" },
  { id: "hero", beats: 20, title: "Now Playing hero" },
  { id: "accent", beats: 16, title: "Accent follows the cover" },
  { id: "everywhere", beats: 12, title: "Overlay and notification" },
  { id: "board", beats: 20, title: "Feature board and source race" },
  { id: "build", beats: 14, title: "Build, drop, tagline" },
  { id: "endCard", beats: 10, title: "End card" },
];

export const TOTAL_BEATS = 120;
export const DURATION_FRAMES = TOTAL_BEATS * FRAMES_PER_BEAT;

/** Beat offsets inside the "build" scene, shared with the score. */
export const BUILD_DROP_BEAT = 8;
/** Beat inside the end card where the mark "scrolls" one line. */
export const END_SCROLL_BEAT = 6;

export interface TimedScene extends SceneSpec {
  startBeat: number;
  startFrame: number;
  durationFrames: number;
}

export function beatToFrame(beat: number): number {
  return Math.round(beat * FRAMES_PER_BEAT);
}

export function frameToBeat(frame: number): number {
  return frame / FRAMES_PER_BEAT;
}

/** Scenes with absolute start beats and frame ranges, in order. */
export function timedScenes(): TimedScene[] {
  let startBeat = 0;
  return SCENES.map((scene) => {
    const timed: TimedScene = {
      ...scene,
      startBeat,
      startFrame: beatToFrame(startBeat),
      durationFrames: beatToFrame(scene.beats),
    };
    startBeat += scene.beats;
    return timed;
  });
}

/** The scene playing at an absolute frame, or undefined past the end. */
export function sceneAt(frame: number): TimedScene | undefined {
  return timedScenes().find(
    (s) => frame >= s.startFrame && frame < s.startFrame + s.durationFrames,
  );
}

/** Absolute start beat of a scene. Throws on an unknown id. */
export function sceneStartBeat(id: SceneId): number {
  const scene = timedScenes().find((s) => s.id === id);
  if (!scene) throw new Error(`sceneStartBeat: unknown scene "${id}"`);
  return scene.startBeat;
}
