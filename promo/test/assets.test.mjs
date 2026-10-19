import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { glyphsFor } from "../scripts/prepare-assets.mjs";
import { PLAYER_CARDS, TRACKS } from "../src/data/tracks.ts";

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
  for (const t of Object.values(TRACKS)) byLang[t.lang]?.push(t.title, ...t.lyrics.map((l) => l.k));
  for (const c of PLAYER_CARDS) byLang[c.lang]?.push(c.title);
  for (const [lang, texts] of Object.entries(byLang)) {
    const subset = new Set(glyphsFor(lang));
    for (const ch of texts.join("")) {
      if (CJK.test(ch)) assert.ok(subset.has(ch), `${lang} subset is missing "${ch}"`);
    }
  }
});

test("player cards are tagged with the script their title is written in", () => {
  for (const c of PLAYER_CARDS) {
    if (c.lang === "KOR") assert.ok(HANGUL.test(c.title), `${c.title} tagged KOR without Hangul`);
    else assert.ok(!HANGUL.test(c.title), `${c.title} tagged ${c.lang} but has Hangul`);
    if (c.lang === "ZHO") assert.ok(!KANA.test(c.title), `${c.title} tagged ZHO but has kana`);
    if (c.lang === "ENG" || c.lang === "IND") assert.ok(!CJK.test(c.title), `${c.title} tagged ${c.lang} but has CJK`);
  }
});

test("CJK lyric rows carry a romanization, Latin rows do not", () => {
  for (const t of Object.values(TRACKS)) {
    const isCjk = ["KOR", "JPN", "ZHO"].includes(t.lang);
    for (const row of t.lyrics) {
      assert.equal(row.r.trim().length > 0, isCjk, `${t.title}: "${row.k}" romanization does not match its script`);
    }
  }
});
