// Copies the app's own fonts into public/generated/fonts and downloads a
// glyph subset of Noto Serif KR / JP / TC that covers exactly the CJK text in
// src/data/tracks.ts. The output is gitignored; rerun after editing lyrics.
//
// Usage: node scripts/prepare-assets.mjs
import { copyFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { ANSWER_SYLLABLES, COLD_OPEN_WORDS, TRACKS } from "../src/data/tracks.ts";

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

// Google Fonts serves woff2 only to a modern browser user agent.
const BROWSER_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const CJK_RANGE = /[぀-ヿ㐀-鿿가-힯＀-￯]/u;

/** Unique CJK characters used by one language, plus Latin fallbacks. */
export function glyphsFor(lang) {
  const texts = [];
  for (const track of Object.values(TRACKS)) {
    if (track.lang !== lang) continue;
    texts.push(track.title, ...track.lyrics.map((l) => l.k));
  }
  texts.push(...COLD_OPEN_WORDS.filter((w) => w.lang === lang).map((w) => w.text));
  if (lang === "KOR") texts.push(...ANSWER_SYLLABLES.map((s) => s.k));
  const chars = new Set([...texts.join("")].filter((c) => CJK_RANGE.test(c)));
  return [...chars].sort().join("");
}

const FETCH_ATTEMPTS = 4;

/** fetch with retries: the Google Fonts edge times out now and then. */
async function fetchOk(url) {
  let lastError;
  for (let attempt = 1; attempt <= FETCH_ATTEMPTS; attempt++) {
    try {
      const res = await fetch(url, { headers: { "User-Agent": BROWSER_UA } });
      if (res.ok) return res;
      lastError = new Error(`status ${res.status}`);
    } catch (err) {
      lastError = err;
    }
    console.warn(`  retry ${attempt}/${FETCH_ATTEMPTS} for ${url.slice(0, 80)}...`);
  }
  throw new Error(`prepare-assets: GET ${url} failed: ${lastError}`);
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
