import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";
import { CATALOG_FILE } from "../scripts/catalog/fetch-catalog.mjs";
import { cleanAlbum, formatDuration, lyricWindow, needsRomanization, parseLrc } from "../scripts/catalog/lrc.mjs";
import { FICTIONAL_CATALOG, LINES_PER_TRACK, REAL_SONGS, ROLES, TRACK_IDS, validateCatalog } from "../src/data/catalog.ts";
import { fitFontSize, wrappedLines } from "../src/lib/textFit.ts";

const LRC = "[ar:Someone]\n[00:10.00]one\n[00:20.50]\n[00:30.25]two\n[00:40.00][01:40.00]three\n[00:50.00]four";

test("parseLrc reads every timestamp, skips tags and sorts", () => {
  const lines = parseLrc(LRC);
  assert.deepEqual(lines.map((l) => l.text), ["one", "", "two", "three", "four", "three"]);
  assert.equal(lines[2].t, 30.25);
});

test("lyricWindow starts at fromSeconds, skips empty lines and slides back near the end", () => {
  const lines = parseLrc(LRC);
  assert.deepEqual(lyricWindow(lines, 25, 2).map((l) => l.text), ["two", "three"]);
  assert.deepEqual(lyricWindow(lines, 95, 3).map((l) => l.text), ["three", "four", "three"]);
});

test("romanization only for lines that carry CJK script", () => {
  assert.equal(needsRomanization("Armageddon", "KOR"), false);
  assert.equal(needsRomanization("내일", "KOR"), true);
  assert.equal(needsRomanization("怪物", "JPN"), true);
  assert.equal(needsRomanization("晴天", "ZHO"), true);
  assert.equal(needsRomanization("anything", "ENG"), false);
});

test("metadata helpers", () => {
  assert.equal(formatDuration(224), "3:44");
  assert.equal(formatDuration(269.6), "4:30");
  assert.equal(cleanAlbum("Teh Hijau - Single"), "Teh Hijau");
  assert.equal(cleanAlbum("Armageddon - The 1st Album"), "Armageddon - The 1st Album");
});

test("the fictional catalog is complete and valid", () => {
  assert.deepEqual(validateCatalog(FICTIONAL_CATALOG), []);
});

test("validateCatalog names what is wrong", () => {
  const broken = structuredClone(FICTIONAL_CATALOG);
  delete broken.tracks.ind;
  broken.tracks.kor.lyrics[0].r = "";
  const problems = validateCatalog(broken);
  assert.ok(problems.some((p) => p.includes('missing track "ind"')));
  assert.ok(problems.some((p) => p.includes("kor: no romanization")));
});

test("every role and real song refers to a known track", () => {
  const roleIds = Object.values(ROLES).flat();
  for (const id of [...roleIds, ...REAL_SONGS.map((s) => s.id)]) assert.ok(TRACK_IDS.includes(id), `unknown track ${id}`);
  assert.equal(new Set(REAL_SONGS.map((s) => s.id)).size, TRACK_IDS.length);
});

test("the built real catalog, when present, is valid", { skip: !existsSync(CATALOG_FILE) && "run npm run assets first" }, () => {
  const data = JSON.parse(readFileSync(CATALOG_FILE, "utf8"));
  assert.deepEqual(validateCatalog(data), []);
  for (const id of TRACK_IDS) assert.equal(data.tracks[id].lyrics.length, LINES_PER_TRACK);
});

test("fitFontSize keeps text inside its box; wrappedLines counts the overflow", () => {
  const long = "a line that is much too long to fit on a single phone row at all";
  const size = fitFontSize(long, "news", 26, 350, 2);
  assert.ok(size < 26 && size > 10);
  assert.ok(wrappedLines(long, "news", size, 350) <= 2);
  assert.equal(fitFontSize("short", "news", 26, 350), 26);
  assert.equal(wrappedLines("short", "news", 26, 350), 1);
  assert.equal(fitFontSize("", "news", 26, 350), 26);
});
