// Builds the real song catalog (scripts/catalog/fetch-catalog.mjs), copies
// the app's own fonts into public/generated/fonts, and downloads a glyph
// subset of Noto Serif KR / JP / TC covering exactly the CJK text of both
// catalogs (real and fictional). Output is gitignored; rerun after changing
// songs or lyrics.
//
// Usage: node scripts/prepare-assets.mjs
import { existsSync, readFileSync } from "node:fs";
import { copyFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { FICTIONAL_TRACKS } from "../src/data/catalog.ts";
import { CATALOG_FILE, buildCatalog } from "./catalog/fetch-catalog.mjs";
import { fetchOk } from "./lib/http.mjs";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const APP_FONTS = join(ROOT, "..", "app", "src", "main", "res", "font");
const OUT = join(ROOT, "public", "generated", "fonts");

const APP_FONT_FILES = [
  "instrument_serif_regular.ttf",
  "instrument_serif_italic.ttf",
  "newsreader_variable.ttf",
  "newsreader_italic_variable.ttf",
  "jetbrains_mono_variable.ttf",
];

const CJK_FAMILIES = [
  { lang: "KOR", family: "Noto Serif KR", slug: "noto-serif-kr" },
  { lang: "JPN", family: "Noto Serif JP", slug: "noto-serif-jp" },
  { lang: "ZHO", family: "Noto Serif TC", slug: "noto-serif-tc" },
];
export const CJK_WEIGHTS = [400, 500];

const CJK_RANGE = /[぀-ヿ㐀-鿿가-힯＀-￯]/u;

/** Unique CJK characters used by one language, plus Latin fallbacks. */
/** Tracks of both catalogs; the real one only once fetch-catalog has run. */
export function allTracks() {
  const real = existsSync(CATALOG_FILE) ? Object.values(JSON.parse(readFileSync(CATALOG_FILE, "utf8")).tracks) : [];
  return [...Object.values(FICTIONAL_TRACKS), ...real];
}

export function glyphsFor(lang) {
  const texts = [];
  for (const track of allTracks()) {
    if (track.lang !== lang) continue;
    texts.push(track.title, track.artist, track.album, ...track.lyrics.map((l) => l.k));
  }
  const chars = new Set([...texts.join("")].filter((c) => CJK_RANGE.test(c)));
  return [...chars].sort().join("");
}

async function fetchText(url) {
  return (await fetchOk(url)).text();
}

async function downloadSubset({ lang, family, slug }) {
  const text = encodeURIComponent(glyphsFor(lang));
  const weights = CJK_WEIGHTS.join(";");
  const cssUrl = `https://fonts.googleapis.com/css2?family=${family.replace(/ /g, "+")}:wght@${weights}&text=${text}`;
  const css = await fetchText(cssUrl);
  const faces = [...css.matchAll(/font-weight:\s*(\d+);[\s\S]*?src:\s*url\(([^)]+)\)/g)];
  if (faces.length !== CJK_WEIGHTS.length) {
    throw new Error(`prepare-assets: expected ${CJK_WEIGHTS.length} faces for ${family}, got ${faces.length}`);
  }
  for (const [, weight, url] of faces) {
    const res = await fetchOk(url);
    const file = join(OUT, `${slug}-${weight}.woff2`);
    await writeFile(file, Buffer.from(await res.arrayBuffer()));
    console.log(`  ${family} ${weight}: ${glyphsFor(lang).length} glyphs -> ${file}`);
  }
}

async function main() {
  try {
    await buildCatalog();
  } catch (err) {
    console.error(`prepare-assets: real catalog failed (${err.message}); REMOTION_CATALOG=fictional still renders`);
    process.exitCode = 1;
  }
  await mkdir(OUT, { recursive: true });
  for (const name of APP_FONT_FILES) {
    await copyFile(join(APP_FONTS, name), join(OUT, name));
    console.log(`  copied ${name}`);
  }
  await Promise.all(CJK_FAMILIES.map(downloadSubset));
  console.log("prepare-assets: done");
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((err) => {
    console.error(err);
    process.exitCode = 1;
  });
}
