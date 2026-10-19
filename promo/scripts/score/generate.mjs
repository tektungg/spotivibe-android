// Generates public/generated/score.wav: the promo's 60-second soundtrack.
// Usage: node scripts/score/generate.mjs
import { mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { DURATION_FRAMES, FPS } from "../../src/timeline.ts";
import { buildEvents } from "./arrangement.mjs";
import { SAMPLE_RATE, writeWav } from "./dsp.mjs";
import { levels, renderScore } from "./mix.mjs";

const ROOT = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const OUT_FILE = join(ROOT, "public", "generated", "score.wav");

async function main() {
  const started = performance.now();
  const durationSec = DURATION_FRAMES / FPS;
  const events = buildEvents();
  const score = renderScore(events, { durationSec });
  await mkdir(dirname(OUT_FILE), { recursive: true });
  await writeWav(OUT_FILE, score);
  const { peak, rms } = levels(score);
  const rmsDb = (20 * Math.log10(rms)).toFixed(1);
  const seconds = ((performance.now() - started) / 1000).toFixed(1);
  console.log(
    `score: ${events.length} events, ${(score.L.length / SAMPLE_RATE).toFixed(2)} s, ` +
      `peak ${peak.toFixed(3)}, rms ${rmsDb} dBFS, ${seconds} s -> ${OUT_FILE}`,
  );
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
