import assert from "node:assert/strict";
import { test } from "node:test";
import { buildEvents, cutBeats } from "../scripts/score/arrangement.mjs";
import { SAMPLE_RATE, seededRandom } from "../scripts/score/dsp.mjs";
import { INSTRUMENTS } from "../scripts/score/instruments.mjs";
import { TARGET_PEAK, levels, renderScore } from "../scripts/score/mix.mjs";
import { SECONDS_PER_BEAT, TOTAL_BEATS, sceneStartBeat } from "../src/timeline.ts";

const HIT_INSTRUMENTS = new Set(["impact", "kick"]);
const WINDOW_SECONDS = 2;
const ONSET_SECONDS = 0.05;

/** Events of a window starting at `fromSec`, shifted so the window starts at 0. */
function windowEvents(fromSec, seconds) {
  return buildEvents()
    .filter((e) => e.time >= fromSec && e.time < fromSec + seconds)
    .map((e) => ({ ...e, time: e.time - fromSec }));
}

test("every event is inside the promo and uses a known instrument", () => {
  for (const e of buildEvents()) {
    assert.ok(e.beat >= 0 && e.beat < TOTAL_BEATS, `event at beat ${e.beat} is outside the promo`);
    assert.ok(INSTRUMENTS[e.inst], `unknown instrument ${e.inst}`);
    assert.ok(e.gain > 0 && e.gain <= 1, `${e.inst} gain ${e.gain} out of range`);
  }
});

test("every scene cut lands on a hit (impact or kick) on the same beat", () => {
  const events = buildEvents();
  for (const cut of cutBeats()) {
    const hit = events.some((e) => HIT_INSTRUMENTS.has(e.inst) && Math.abs(e.beat - cut) < 1e-9);
    assert.ok(hit, `no hit on the cut at beat ${cut}`);
  }
});

test("the hero groove kicks on every beat", () => {
  const from = sceneStartBeat("hero");
  const to = sceneStartBeat("accent");
  const kicks = new Set(buildEvents().filter((e) => e.inst === "kick").map((e) => e.beat));
  for (let b = from; b < to; b++) assert.ok(kicks.has(b), `missing kick on beat ${b}`);
});

test("event times are derived from beats at the timeline tempo", () => {
  for (const e of buildEvents()) assert.ok(Math.abs(e.time - e.beat * SECONDS_PER_BEAT) < 1e-9);
});

test("seeded PRNG is deterministic", () => {
  const a = seededRandom(7);
  const b = seededRandom(7);
  for (let i = 0; i < 100; i++) assert.equal(a(), b());
});

test("a rendered window has the right length, is normalized and deterministic", () => {
  const events = windowEvents(sceneStartBeat("hero") * SECONDS_PER_BEAT, WINDOW_SECONDS);
  const first = renderScore(events, { durationSec: WINDOW_SECONDS });
  const second = renderScore(events, { durationSec: WINDOW_SECONDS });
  assert.equal(first.L.length, WINDOW_SECONDS * SAMPLE_RATE);
  const { peak } = levels(first);
  assert.ok(Math.abs(peak - TARGET_PEAK) < 1e-3, `peak ${peak} is not normalized to ${TARGET_PEAK}`);
  assert.deepEqual(first.L, second.L);
});

test("a kick onset is louder than the decayed tail before the next beat", () => {
  const events = windowEvents(sceneStartBeat("hero") * SECONDS_PER_BEAT, WINDOW_SECONDS);
  const score = renderScore(events, { durationSec: WINDOW_SECONDS });
  const at = (sec) => Math.round(sec * SAMPLE_RATE);
  const onset = levels(score, 0, at(ONSET_SECONDS)).rms;
  const tail = levels(score, at(SECONDS_PER_BEAT - 0.1), at(SECONDS_PER_BEAT - 0.05)).rms;
  assert.ok(onset > tail * 1.5, `onset rms ${onset} vs tail rms ${tail}`);
});
