// Evaluates a rendered promo MP4 against measurable targets. Slow (decodes
// the whole video), so it runs before shipping, not on every commit.
//
// Usage: node scripts/eval-render.mjs [out/spotivibe-promo.mp4]
//
// Checks:
//   format     1920x1080, 60 fps, H.264 + AAC, 60.0 s (+-0.05)
//   loudness   integrated -16..-8 LUFS, true peak <= -0.5 dBTP
//   black      no black stretch longer than MAX_BLACK_S (the outro may close)
//   on-beat    share of hard visual changes within BEAT_TOLERANCE_F of a beat
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { BPM, FPS, FRAMES_PER_BEAT } from "../src/timeline.ts";

const FILE = process.argv[2] ?? "out/spotivibe-promo.mp4";
const TARGET = { width: 1920, height: 1080, fps: FPS, seconds: 60, secondsTolerance: 0.05 };
const LUFS_RANGE = [-16, -8];
const MAX_TRUE_PEAK = -0.5;
const MAX_BLACK_S = 0.5;
const SCENE_THRESHOLD = 0.3;
const BEAT_TOLERANCE_F = 2;
const MIN_ON_BEAT_SHARE = 0.6;

/** ffmpeg filter logs go to stderr; spawnSync returns it on success too. */
function ffmpegStderr(args) {
  const res = spawnSync("ffmpeg", ["-hide_banner", "-nostats", ...args, "-f", "null", "-"], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (res.status !== 0) throw new Error(`eval-render: ffmpeg ${args.join(" ")} failed: ${res.stderr.slice(-400)}`);
  return res.stderr;
}

function probe() {
  const json = execFileSync("ffprobe", ["-v", "error", "-show_streams", "-show_format", "-of", "json", FILE], { encoding: "utf8" });
  const { streams, format } = JSON.parse(json);
  const video = streams.find((s) => s.codec_type === "video");
  const audio = streams.find((s) => s.codec_type === "audio");
  const [num, den] = video.r_frame_rate.split("/").map(Number);
  return { video, audio, fps: num / den, seconds: Number(format.duration) };
}

/** Last numeric value of a summary line such as "I: -11.2 LUFS". */
const lastNumber = (text, pattern) => Number((text.match(pattern) ?? []).pop()?.match(/-?[\d.]+/)?.[0]);

function checkFormat() {
  const p = probe();
  const isFrameOk = p.video.width === TARGET.width && p.video.height === TARGET.height && Math.abs(p.fps - TARGET.fps) < 0.01;
  const isCodecOk = p.video.codec_name === "h264" && p.audio?.codec_name === "aac";
  return [
    { name: "format", ok: isFrameOk && isCodecOk, detail: `${p.video.width}x${p.video.height} ${p.fps.toFixed(2)} fps ${p.video.codec_name}/${p.audio?.codec_name}` },
    { name: "duration", ok: Math.abs(p.seconds - TARGET.seconds) <= TARGET.secondsTolerance, detail: `${p.seconds.toFixed(3)} s` },
  ];
}

function checkLoudness() {
  const log = ffmpegStderr(["-i", FILE, "-af", "ebur128=peak=true", "-vn"]);
  const lufs = lastNumber(log, /I:\s+(-?[\d.]+) LUFS/g);
  const truePeak = lastNumber(log, /Peak:\s+(-?[\d.]+) dBFS/g);
  return [
    { name: "loudness", ok: lufs >= LUFS_RANGE[0] && lufs <= LUFS_RANGE[1], detail: `${lufs} LUFS` },
    { name: "true peak", ok: truePeak <= MAX_TRUE_PEAK, detail: `${truePeak} dBTP` },
  ];
}

function checkBlack() {
  const log = ffmpegStderr(["-i", FILE, "-vf", "blackdetect=d=0.1:pix_th=0.05", "-an"]);
  const blacks = [...log.matchAll(/black_start:([\d.]+) black_end:([\d.]+) black_duration:([\d.]+)/g)].map((m) => ({ start: Number(m[1]), dur: Number(m[3]) }));
  // The outro is allowed to close to black in its last second.
  const midBlack = blacks.filter((b) => b.dur > MAX_BLACK_S && b.start < TARGET.seconds - 1);
  const detail = blacks.map((b) => `${b.start.toFixed(2)}s+${b.dur.toFixed(2)}`).join(", ") || "none";
  return [{ name: "no long black", ok: midBlack.length === 0, detail }];
}

function checkCutsOnBeat() {
  const log = ffmpegStderr(["-i", FILE, "-vf", `select='gt(scene,${SCENE_THRESHOLD})',showinfo`, "-an"]);
  const times = [...log.matchAll(/pts_time:([\d.]+)/g)].map((m) => Number(m[1]));
  const isOnBeat = (t) => {
    const offset = (t * FPS) % FRAMES_PER_BEAT;
    return Math.min(offset, FRAMES_PER_BEAT - offset) <= BEAT_TOLERANCE_F;
  };
  const onBeat = times.filter(isOnBeat).length;
  const share = times.length ? onBeat / times.length : 0;
  const detail = `${onBeat}/${times.length} hard changes within ${BEAT_TOLERANCE_F} frames of the ${BPM} BPM grid (${(share * 100).toFixed(0)}%)`;
  return [{ name: "cuts on beat", ok: share >= MIN_ON_BEAT_SHARE, detail }];
}

function main() {
  if (!existsSync(FILE)) throw new Error(`eval-render: ${FILE} does not exist, run "npm run render" first`);
  const results = [...checkFormat(), ...checkLoudness(), ...checkBlack(), ...checkCutsOnBeat()];
  for (const r of results) console.log(`${r.ok ? "PASS" : "FAIL"}  ${r.name.padEnd(14)} ${r.detail}`);
  const failed = results.filter((r) => !r.ok).length;
  console.log(failed ? `eval-render: ${failed} check(s) failed` : `eval-render: all ${results.length} checks passed`);
  process.exitCode = failed ? 1 : 0;
}

main();
