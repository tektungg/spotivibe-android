// Renders stills and fails on blank or flat frames.
//
//   node scripts/check-frames.mjs                 one still per scene (25/50/75 %)
//   node scripts/check-frames.mjs --frames 6,40   specific absolute frames
//   node scripts/check-frames.mjs --out <dir>     output directory (default out/stills)
//
// A frame "passes" when its luma spread (YMAX - YMIN, ffmpeg signalstats)
// is at least MIN_LUMA_SPREAD: a scene that failed to render (black, or one
// flat color) cannot pass.
import { bundle } from "@remotion/bundler";
import { openBrowser, renderStill, selectComposition } from "@remotion/renderer";
import { execFileSync } from "node:child_process";
import { mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { timedScenes } from "../src/timeline.ts";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const COMPOSITION_ID = "SpotivibePromo";
const MIN_LUMA_SPREAD = 60;
const SAMPLE_POINTS = [0.25, 0.5, 0.75];

/** REMOTION_* variables (e.g. REMOTION_CATALOG=fictional) reach the bundle like they do with the CLI. */
const envVariables = Object.fromEntries(Object.entries(process.env).filter(([key]) => key.startsWith("REMOTION_")));

function parseArgs(argv) {
  const args = { frames: null, out: join(ROOT, "out", "stills") };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--frames") args.frames = argv[++i].split(",").map(Number);
    else if (argv[i] === "--out") args.out = resolve(argv[++i]);
  }
  return args;
}

function defaultFrames() {
  return timedScenes().flatMap((s) =>
    SAMPLE_POINTS.map((p) => ({ frame: s.startFrame + Math.floor(s.durationFrames * p), scene: s.id })),
  );
}

/** Luma min/max of a PNG via ffmpeg signalstats. */
function lumaSpread(file) {
  const log = execFileSync(
    "ffmpeg",
    // file=- sends the metadata to stdout instead of the stderr log.
    ["-hide_banner", "-loglevel", "error", "-i", file, "-vf", "signalstats,metadata=print:file=-", "-f", "null", "-"],
    { encoding: "utf8", stdio: ["ignore", "pipe", "inherit"] },
  );
  const read = (key) => Number((log.match(new RegExp(`${key}=(\\d+)`)) ?? [])[1]);
  const spread = read("lavfi.signalstats.YMAX") - read("lavfi.signalstats.YMIN");
  if (Number.isNaN(spread)) throw new Error(`check-frames: no signalstats for ${file}`);
  return spread;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  mkdirSync(args.out, { recursive: true });
  const targets = args.frames ? args.frames.map((frame) => ({ frame, scene: "manual" })) : defaultFrames();
  const serveUrl = await bundle({ entryPoint: join(ROOT, "src", "index.ts") });
  const browser = await openBrowser("chrome", { chromiumOptions: { gl: "angle" } });
  const composition = await selectComposition({ serveUrl, id: COMPOSITION_ID, puppeteerInstance: browser, envVariables });
  const failures = [];
  for (const { frame, scene } of targets) {
    const output = join(args.out, `f${String(frame).padStart(4, "0")}.png`);
    await renderStill({ composition, serveUrl, output, frame, puppeteerInstance: browser, imageFormat: "png", envVariables });
    const spread = lumaSpread(output);
    const ok = spread >= MIN_LUMA_SPREAD;
    if (!ok) failures.push(frame);
    console.log(`${ok ? "ok  " : "FAIL"} frame ${frame} (${scene}) luma spread ${spread} -> ${output}`);
  }
  await browser.close({ silent: true });
  if (failures.length) {
    console.error(`check-frames: ${failures.length} flat frame(s): ${failures.join(", ")}`);
    process.exitCode = 1;
  } else {
    console.log(`check-frames: ${targets.length} frame(s) ok`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
