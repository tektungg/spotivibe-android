import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { glyphsFor } from "../scripts/prepare-assets.mjs";
import { ANSWER_SYLLABLES, COLD_OPEN_WORDS, TRACKS } from "../src/data/tracks.ts";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const SRC = join(ROOT, "src");
const DATA_FILE = join(SRC, "data", "tracks.ts");
const CJK = /[぀-ヿ㐀-鿿가-힯]/u;
const HANGUL = /[가-힯]/u;
const KANA = /[぀-ヿ]/u;

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : [path];
  });
}

test("CJK text lives only in src/data/tracks.ts, so the font subset covers it", () => {
  for (const file of sourceFiles(SRC)) {
    if (file === DATA_FILE) continue;
    const line = readFileSync(file, "utf8").split("\n").find((l) => CJK.test(l));
    assert.equal(line, undefined, `${relative(ROOT, file)} has CJK text outside the data file: ${line}`);
  }
});

test("each language's font subset contains every CJK glyph it renders", () => {
  const byLang = { KOR: [], JPN: [], ZHO: [] };
  for (const t of Object.values(TRACKS)) byLang[t.lang].push(t.title, ...t.lyrics.map((l) => l.k));
  for (const w of COLD_OPEN_WORDS) byLang[w.lang].push(w.text);
  byLang.KOR.push(...ANSWER_SYLLABLES.map((s) => s.k));
  for (const [lang, texts] of Object.entries(byLang)) {
    const subset = new Set(glyphsFor(lang));
    for (const ch of texts.join("")) {
      if (CJK.test(ch)) assert.ok(subset.has(ch), `${lang} subset is missing "${ch}"`);
    }
  }
});

test("slam words are tagged with the script they are written in", () => {
  for (const w of COLD_OPEN_WORDS) {
    if (w.lang === "KOR") assert.ok(HANGUL.test(w.text), `${w.text} tagged KOR without Hangul`);
    else assert.ok(!HANGUL.test(w.text), `${w.text} tagged ${w.lang} but has Hangul`);
    if (w.lang === "ZHO") assert.ok(!KANA.test(w.text), `${w.text} tagged ZHO but has kana`);
  }
});

test("every lyric row carries a romanization", () => {
  for (const t of Object.values(TRACKS)) {
    for (const row of t.lyrics) assert.ok(row.r.trim().length > 0, `${t.title}: "${row.k}" has no romanization`);
  }
});
