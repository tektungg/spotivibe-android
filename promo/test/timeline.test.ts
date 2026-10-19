import assert from "node:assert/strict";
import { test } from "node:test";
import {
  BUILD_DROP_BEAT,
  DURATION_FRAMES,
  END_SCROLL_BEAT,
  FPS,
  FRAMES_PER_BEAT,
  SCENES,
  TOTAL_BEATS,
  beatToFrame,
  sceneAt,
  sceneStartBeat,
  timedScenes,
} from "../src/timeline.ts";

const PROMO_SECONDS = 60;

test("the promo is exactly 60 seconds", () => {
  assert.equal(DURATION_FRAMES, PROMO_SECONDS * FPS);
  assert.equal(SCENES.reduce((sum, s) => sum + s.beats, 0), TOTAL_BEATS);
});

test("a beat is a whole number of frames, so cuts never round", () => {
  assert.ok(Number.isInteger(FRAMES_PER_BEAT));
  for (const s of timedScenes()) {
    assert.ok(Number.isInteger(s.startFrame), `${s.id} starts mid-frame`);
    assert.equal(s.startFrame % FRAMES_PER_BEAT, 0, `${s.id} does not start on a beat`);
  }
});

test("scenes are contiguous and cover every frame", () => {
  const scenes = timedScenes();
  scenes.forEach((s, i) => {
    const expectedStart = i === 0 ? 0 : scenes[i - 1].startFrame + scenes[i - 1].durationFrames;
    assert.equal(s.startFrame, expectedStart, `${s.id} leaves a gap or overlaps`);
  });
  const last = scenes[scenes.length - 1];
  assert.equal(last.startFrame + last.durationFrames, DURATION_FRAMES);
});

test("scene ids are unique", () => {
  const ids = SCENES.map((s) => s.id);
  assert.equal(new Set(ids).size, ids.length);
});

test("sceneAt resolves boundaries and past-the-end frames", () => {
  assert.equal(sceneAt(0)?.id, "coldOpen");
  assert.equal(sceneAt(beatToFrame(12) - 1)?.id, "coldOpen");
  assert.equal(sceneAt(beatToFrame(12))?.id, "found");
  assert.equal(sceneAt(DURATION_FRAMES - 1)?.id, "endCard");
  assert.equal(sceneAt(DURATION_FRAMES), undefined);
});

test("in-scene beat markers fall inside their scenes", () => {
  const beatsOf = (id: string) => SCENES.find((s) => s.id === id)?.beats ?? 0;
  assert.ok(BUILD_DROP_BEAT > 0 && BUILD_DROP_BEAT < beatsOf("build"));
  assert.ok(END_SCROLL_BEAT > 0 && END_SCROLL_BEAT < beatsOf("endCard"));
});

test("sceneStartBeat throws a named error on an unknown id", () => {
  assert.throws(() => sceneStartBeat("nope" as never), /sceneStartBeat: unknown scene "nope"/);
});
