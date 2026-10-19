// Image checks shared by check-frames and social-preview (ffmpeg/ffprobe).
import { execFileSync } from "node:child_process";

/** Luma spread (YMAX - YMIN) of an image via ffmpeg signalstats; a flat or blank image scores low. */
export function lumaSpread(file) {
  const log = execFileSync(
    "ffmpeg",
    // file=- sends the metadata to stdout instead of the stderr log.
    ["-hide_banner", "-loglevel", "error", "-i", file, "-vf", "signalstats,metadata=print:file=-", "-f", "null", "-"],
    { encoding: "utf8", stdio: ["ignore", "pipe", "inherit"] },
  );
  const read = (key) => Number((log.match(new RegExp(`${key}=([0-9]+)`)) ?? [])[1]);
  const spread = read("lavfi.signalstats.YMAX") - read("lavfi.signalstats.YMIN");
  if (Number.isNaN(spread)) throw new Error(`image: no signalstats for ${file}`);
  return spread;
}

/** [width, height] of an image via ffprobe. */
export function imageSize(file) {
  const out = execFileSync("ffprobe", ["-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", file], { encoding: "utf8" });
  return out.trim().split(",").map(Number);
}
