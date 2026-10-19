import assert from "node:assert/strict";
import { test } from "node:test";
import { isRenderFinished, latestProgress, rateBetween } from "../scripts/render-progress.mjs";

test("latestProgress reads the last Rendered N/TOTAL pair", () => {
  const log = "Bundling 100%\nRendered 10/3600, time remaining: 4m\nRendered 11/3600, time remaining: 4m\n";
  assert.deepEqual(latestProgress(log), { done: 11, total: 3600 });
});

test("latestProgress is null before the first frame", () => {
  assert.equal(latestProgress("Bundling 42%"), null);
});

test("rateBetween measures frames per second between two samples", () => {
  assert.equal(rateBetween(null, { done: 100, at: 1000 }), null);
  assert.equal(rateBetween({ done: 100, at: 0 }, { done: 400, at: 60000 }), 5);
  assert.equal(rateBetween({ done: 100, at: 5 }, { done: 120, at: 5 }), null);
});

test("isRenderFinished waits for the exit line, not encoding progress", () => {
  assert.equal(isRenderFinished("Rendered 3600/3600\nEncoded 2209/3600\n"), false);
  assert.equal(isRenderFinished("Encoded 3600/3600\nexit 0\n"), true);
});
