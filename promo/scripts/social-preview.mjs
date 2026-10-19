// Renders the GitHub social preview image and checks it against GitHub's
// rules, so the file is ready to upload as is:
//   repo Settings -> General -> Social preview -> Edit -> Upload an image
//
// Usage: node scripts/social-preview.mjs   (npm run social)
//
// Output: out/social-preview.png, or out/social-preview.jpg when the PNG is
// over GitHub's 1 MB limit.
import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import { rmSync, statSync } from "node:fs";
import { mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { imageSize, lumaSpread } from "./lib/image.mjs";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const OUT_DIR = join(ROOT, "out");
const COMPOSITION_ID = "SocialPreview";
/** GitHub: at least 640 x 320, 1280 x 640 recommended, under 1 MB. */
const EXPECTED = { width: 1280, height: 640 };
/** Decimal megabyte: the stricter reading of GitHub's "under 1 MB". */
const MAX_BYTES = 1_000_000;
const MIN_LUMA_SPREAD = 60;
const JPEG_QUALITY = 92;

async function render(serveUrl, composition, format) {
  const output = join(OUT_DIR, `social-preview.${format === "jpeg" ? "jpg" : "png"}`);
  await renderStill({ composition, serveUrl, output, imageFormat: format, jpegQuality: format === "jpeg" ? JPEG_QUALITY : undefined, chromiumOptions: { gl: "angle" } });
  return output;
}

/** Problems that would make GitHub reject the image or the preview look broken. */
function problemsOf(file) {
  const [width, height] = imageSize(file);
  const bytes = statSync(file).size;
  const problems = [];
  if (width !== EXPECTED.width || height !== EXPECTED.height) problems.push(`size ${width}x${height}, expected ${EXPECTED.width}x${EXPECTED.height}`);
  if (bytes >= MAX_BYTES) problems.push(`${(bytes / 1024).toFixed(0)} KB, GitHub allows under 1 MB`);
  if (lumaSpread(file) < MIN_LUMA_SPREAD) problems.push("image is flat or blank");
  return { problems, bytes, width, height };
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });
  const serveUrl = await bundle({ entryPoint: join(ROOT, "src", "index.ts") });
  const composition = await selectComposition({ serveUrl, id: COMPOSITION_ID });
  let file = await render(serveUrl, composition, "png");
  let check = problemsOf(file);
  if (check.problems.some((p) => p.includes("1 MB"))) {
    console.log(`social-preview: PNG is ${(check.bytes / 1024).toFixed(0)} KB, trying JPEG`);
    rmSync(file);
    file = await render(serveUrl, composition, "jpeg");
    check = problemsOf(file);
  }
  if (check.problems.length) {
    console.error(`social-preview: FAIL ${file}: ${check.problems.join("; ")}`);
    process.exitCode = 1;
    return;
  }
  console.log(`social-preview: ok ${check.width}x${check.height}, ${(check.bytes / 1024).toFixed(0)} KB -> ${file}`);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
