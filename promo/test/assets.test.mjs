import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { allTracks, glyphsFor } from "../scripts/prepare-assets.mjs";
import { FICTIONAL_TRACKS, PLAYER_CARDS, TRACK_IDS } from "../src/data/catalog.ts";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const SRC = join(ROOT, "src");
const DATA_FILE = join(SRC, "data", "catalog.ts");
const CJK = /[぀-ヿ㐀-鿿가-힯]/u;
const HANGUL = /[가-힯]/u;
const KANA = /[぀-ヿ]/u;

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : [path];
  });
}

test("CJK text lives only in src/data/catalog.ts, so the font subset covers it", () => {
  for (const file of sourceFiles(SRC)) {
    if (file === DATA_FILE) continue;
    const line = readFileSync(file, "utf8").split("\n").find((l) => CJK.test(l));
    assert.equal(line, undefined, `${relative(ROOT, file)} has CJK text outside the data file: ${line}`);
  }
});

test("each language's font subset holds every CJK glyph either catalog renders", () => {
  const byLang = { KOR: [], JPN: [], ZHO: [] };
  for (const t of allTracks()) byLang[t.lang]?.push(t.title, t.artist, t.album, ...t.lyrics.map((l) => l.k));
  for (const [lang, texts] of Object.entries(byLang)) {
    const subset = new Set(glyphsFor(lang));
    for (const ch of texts.join("")) {
      if (CJK.test(ch)) assert.ok(subset.has(ch), `${lang} subset is missing "${ch}"`);
    }
  }
});

test("fictional titles are written in the script their language says", () => {
  for (const t of Object.values(FICTIONAL_TRACKS)) {
    if (t.lang === "KOR") assert.ok(HANGUL.test(t.title), `${t.title} tagged KOR without Hangul`);
    else assert.ok(!HANGUL.test(t.title), `${t.title} tagged ${t.lang} but has Hangul`);
    if (t.lang === "ZHO") assert.ok(!KANA.test(t.title), `${t.title} tagged ZHO but has kana`);
    if (t.lang === "ENG" || t.lang === "IND") assert.ok(!CJK.test(t.title), `${t.title} tagged ${t.lang} but has CJK`);
  }
});

test("player cards point at tracks every catalog has", () => {
  for (const card of PLAYER_CARDS) assert.ok(TRACK_IDS.includes(card.trackId), `unknown track ${card.trackId}`);
  assert.ok(PLAYER_CARDS.length >= TRACK_IDS.length, "every song gets at least one card");
});
