// Ported from the app's RomanizationService.kt; cases follow its rules.
import assert from "node:assert/strict";
import { test } from "node:test";
import { hepburnFromKana, katakanaToHiragana, romanize, romanizeJapanese, romanizeKorean, romanizeMandarin } from "../scripts/catalog/romanize.mjs";

test("Korean: per-syllable Revised Romanization, other characters pass through", () => {
  assert.equal(romanizeKorean("내일"), "naeil");
  assert.equal(romanizeKorean("한번"), "hanbeon");
  assert.equal(romanizeKorean("봄이 될게"), "bomi doelge");
  assert.equal(romanizeKorean("oh, 사랑"), "oh, sarang");
});

test("Hepburn: plain kana, yoon pairs, sokuon and chouonpu", () => {
  assert.equal(hepburnFromKana("よあけ"), "yoake");
  assert.equal(hepburnFromKana("しゃしん"), "shashin");
  assert.equal(hepburnFromKana("きって"), "kitte");
  assert.equal(hepburnFromKana("まっちゃ"), "matcha");
  assert.equal(hepburnFromKana("らーめん"), "raamen");
  assert.equal(hepburnFromKana("ーあ"), "a");
});

test("katakana maps onto hiragana by the 0x60 offset", () => {
  assert.equal(katakanaToHiragana("スタンス"), "すたんす");
  assert.equal(katakanaToHiragana("ABC"), "ABC");
});

test("Japanese: kuromoji readings joined by spaces", async () => {
  assert.equal(await romanizeJapanese("夜明けのスタンス"), "yoake no sutansu");
});

test("Mandarin: one tone-marked syllable per Han character", () => {
  assert.equal(romanizeMandarin("晴天"), "qíng tiān");
  assert.equal(romanizeMandarin("我 love 你"), "wǒ love nǐ");
});

test("Latin-script languages get no romanization", async () => {
  assert.equal(await romanize("drop dead", "ENG"), "");
  assert.equal(await romanize("teh hijau", "IND"), "");
});
