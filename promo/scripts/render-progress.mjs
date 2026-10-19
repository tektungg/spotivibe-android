// Reads a Remotion render log and appends a progress line (percent, rate,
// ETA) to a status file every INTERVAL_S seconds until the render finishes.
//
// Usage: node scripts/render-progress.mjs <render.log> <progress.log> [interval_s]
// The render must be wrapped so its exit code lands in the log:
//   (npx remotion render ... > render.log 2>&1; echo "exit $?" >> render.log)
import { appendFileSync, existsSync, readFileSync } from "node:fs";

const [, , renderLog, progressLog, intervalArg] = process.argv;
const INTERVAL_S = Number(intervalArg ?? 30);
const TITLE = "spotivibe-render";
const started = Date.now();

/** Latest "Rendered N/TOTAL" pair from the log, or null before the first one. */
export function latestProgress(text) {
  const matches = [...text.matchAll(/Rendered (\d+)\/(\d+)/g)];
  if (!matches.length) return null;
  const [, done, total] = matches[matches.length - 1];
  return { done: Number(done), total: Number(total) };
}

/**
 * True once the wrapper appended "exit N" after Remotion quit. Remotion's own
 * "Encoded x/y" lines are progress, not completion.
 */
export function isRenderFinished(text) {
  return /^exit \d+$/m.test(text);
}

/** Frames per second between two samples; null on the first sample. */
export function rateBetween(prev, next) {
  if (!prev || next.at <= prev.at) return null;
  return (next.done - prev.done) / ((next.at - prev.at) / 1000);
}

let previous = null;

function tick() {
  const text = existsSync(renderLog) ? readFileSync(renderLog, "utf8") : "";
  const p = latestProgress(text);
  const elapsed = (Date.now() - started) / 1000;
  const isFinished = isRenderFinished(text);
  let line = `${new Date().toISOString()} ${TITLE}: waiting for first frame (${elapsed.toFixed(0)} s)`;
  if (p) {
    // Rate between ticks, not since the monitor started: the render may have
    // been running before the monitor attached.
    const sample = { done: p.done, at: Date.now() };
    const rate = rateBetween(previous, sample) ?? 0;
    previous = sample;
    const eta = rate > 0 ? `${((p.total - p.done) / rate).toFixed(0)} s` : "pending";
    line = `${new Date().toISOString()} ${TITLE}: ${((100 * p.done) / p.total).toFixed(1)}% · ${p.done}/${p.total} frames · ${rate.toFixed(1)} fps · ETA ${eta}`;
  }
  appendFileSync(progressLog, `${line}\n`);
  console.log(line);
  if (isFinished) {
    appendFileSync(progressLog, `${new Date().toISOString()} ${TITLE}: render finished\n`);
    process.exit(0);
  }
}

if (renderLog && progressLog) {
  tick();
  setInterval(tick, INTERVAL_S * 1000);
}
