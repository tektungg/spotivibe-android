// Romanization, ported from the app's data/RomanizationService.kt so the
// promo shows exactly what the app shows:
//   Korean    Revised Romanization, per syllable from the jamo tables
//   Japanese  kuromoji (IPADIC, same dictionary as the app) readings,
//             katakana -> hiragana -> Hepburn
//   Mandarin  per character, first reading, tone marks (the app uses
//             pinyin4j; pinyin-pro gives the same tone-marked syllables)
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { pinyin } from "pinyin-pro";

const require = createRequire(import.meta.url);

// ---- Korean -----------------------------------------------------------------

const HANGUL_FIRST = 0xac00;
const HANGUL_LAST = 0xd7a3;
const JUNG_COUNT = 21;
const JONG_COUNT = 28;
const CHOSEONG = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"];
const JUNGSEONG = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"];
const JONGSEONG = ["", "k", "kk", "ks", "n", "nj", "nh", "t", "l", "lk", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h"];

/** Each Hangul syllable -> initial + medial + final; everything else passes through. */
export function romanizeKorean(text) {
  let out = "";
  for (const ch of text) {
    const code = ch.codePointAt(0);
    if (code < HANGUL_FIRST || code > HANGUL_LAST) {
      out += ch;
      continue;
    }
    const idx = code - HANGUL_FIRST;
    out += CHOSEONG[Math.floor(idx / (JUNG_COUNT * JONG_COUNT))];
    out += JUNGSEONG[Math.floor((idx % (JUNG_COUNT * JONG_COUNT)) / JONG_COUNT)];
    out += JONGSEONG[idx % JONG_COUNT];
  }
  return out.trim();
}

// ---- Japanese ---------------------------------------------------------------

const KATAKANA_FIRST = 0x30a1;
const KATAKANA_LAST = 0x30f6;
const KANA_OFFSET = 0x60;
const VOWELS = "aiueo";

/* eslint-disable quote-props */
const HIRAGANA_TO_ROMAJI = {
  あ: "a", い: "i", う: "u", え: "e", お: "o",
  か: "ka", き: "ki", く: "ku", け: "ke", こ: "ko", が: "ga", ぎ: "gi", ぐ: "gu", げ: "ge", ご: "go",
  さ: "sa", し: "shi", す: "su", せ: "se", そ: "so", ざ: "za", じ: "ji", ず: "zu", ぜ: "ze", ぞ: "zo",
  た: "ta", ち: "chi", つ: "tsu", て: "te", と: "to", だ: "da", ぢ: "ji", づ: "zu", で: "de", ど: "do",
  な: "na", に: "ni", ぬ: "nu", ね: "ne", の: "no",
  は: "ha", ひ: "hi", ふ: "fu", へ: "he", ほ: "ho", ば: "ba", び: "bi", ぶ: "bu", べ: "be", ぼ: "bo",
  ぱ: "pa", ぴ: "pi", ぷ: "pu", ぺ: "pe", ぽ: "po",
  ま: "ma", み: "mi", む: "mu", め: "me", も: "mo", や: "ya", ゆ: "yu", よ: "yo",
  ら: "ra", り: "ri", る: "ru", れ: "re", ろ: "ro", わ: "wa", ゐ: "wi", ゑ: "we", を: "o", ん: "n",
  きゃ: "kya", きゅ: "kyu", きょ: "kyo", ぎゃ: "gya", ぎゅ: "gyu", ぎょ: "gyo",
  しゃ: "sha", しゅ: "shu", しょ: "sho", しぇ: "she", じゃ: "ja", じゅ: "ju", じょ: "jo", じぇ: "je",
  ちゃ: "cha", ちゅ: "chu", ちょ: "cho", ちぇ: "che", ぢゃ: "ja", ぢゅ: "ju", ぢょ: "jo",
  にゃ: "nya", にゅ: "nyu", にょ: "nyo", ひゃ: "hya", ひゅ: "hyu", ひょ: "hyo",
  びゃ: "bya", びゅ: "byu", びょ: "byo", ぴゃ: "pya", ぴゅ: "pyu", ぴょ: "pyo",
  みゃ: "mya", みゅ: "myu", みょ: "myo", りゃ: "rya", りゅ: "ryu", りょ: "ryo",
  ふぁ: "fa", ふぃ: "fi", ふぇ: "fe", ふぉ: "fo", うぃ: "wi", うぇ: "we", うぉ: "wo",
  ゔぁ: "va", ゔぃ: "vi", ゔ: "vu", ゔぇ: "ve", ゔぉ: "vo", てぃ: "ti", でぃ: "di", とぅ: "tu", どぅ: "du",
  ぁ: "a", ぃ: "i", ぅ: "u", ぇ: "e", ぉ: "o", ゃ: "ya", ゅ: "yu", ょ: "yo",
};
/* eslint-enable quote-props */

export function katakanaToHiragana(s) {
  return [...s]
    .map((ch) => {
      const code = ch.codePointAt(0);
      return code >= KATAKANA_FIRST && code <= KATAKANA_LAST ? String.fromCodePoint(code - KANA_OFFSET) : ch;
    })
    .join("");
}

/** Longest match first (yoon pairs), then a single kana. */
function lookupKanaAt(s, pos) {
  const two = s.slice(pos, pos + 2);
  if (two.length === 2 && HIRAGANA_TO_ROMAJI[two]) return [HIRAGANA_TO_ROMAJI[two], 2];
  const one = s[pos];
  return HIRAGANA_TO_ROMAJI[one] ? [HIRAGANA_TO_ROMAJI[one], 1] : null;
}

/** Sokuon doubles the next consonant (ch -> tch); ー repeats the previous vowel. */
function sokuon(s, i) {
  const next = lookupKanaAt(s, i + 1);
  if (!next) return { text: "", consumed: 1 };
  const [romaji, consumed] = next;
  let prefix = "";
  if (romaji && !VOWELS.includes(romaji[0])) prefix = romaji.startsWith("ch") ? "t" : romaji[0];
  return { text: prefix + romaji, consumed: 1 + consumed };
}

export function hepburnFromKana(s) {
  let out = "";
  let i = 0;
  while (i < s.length) {
    const ch = s[i];
    if (ch === "っ") {
      const { text, consumed } = sokuon(s, i);
      out += text;
      i += consumed;
      continue;
    }
    if (ch === "ー") {
      const last = out[out.length - 1];
      if (last && VOWELS.includes(last)) out += last;
      i += 1;
      continue;
    }
    const match = lookupKanaAt(s, i);
    out += match ? match[0] : ch;
    i += match ? match[1] : 1;
  }
  return out;
}

let tokenizerPromise = null;

function japaneseTokenizer() {
  if (!tokenizerPromise) {
    const kuromoji = require("kuromoji");
    const dicPath = join(dirname(require.resolve("kuromoji/package.json")), "dict");
    tokenizerPromise = new Promise((resolve, reject) =>
      kuromoji.builder({ dicPath }).build((err, tokenizer) => (err ? reject(err) : resolve(tokenizer))),
    );
  }
  return tokenizerPromise;
}

/** Token readings -> Hepburn, joined with spaces; unknown tokens keep their surface. */
export async function romanizeJapanese(text) {
  const tokenizer = await japaneseTokenizer();
  const pieces = tokenizer.tokenize(text).map((tok) => {
    const reading = tok.reading && tok.reading !== "*" ? tok.reading : null;
    return reading ? hepburnFromKana(katakanaToHiragana(reading)) : tok.surface_form;
  });
  return pieces.filter((p) => p.trim()).join(" ").trim();
}

// ---- Mandarin ---------------------------------------------------------------

const HAN = /[一-鿿]/u;

export function romanizeMandarin(text) {
  const parts = [...text].map((ch) => (HAN.test(ch) ? ` ${pinyin(ch, { toneType: "symbol" })} ` : ch));
  return parts.join("").replace(/\s+/g, " ").trim();
}

/** Romanization for a lyric line, or "" for Latin-script languages. */
export async function romanize(text, lang) {
  if (lang === "KOR") return romanizeKorean(text);
  if (lang === "JPN") return romanizeJapanese(text);
  if (lang === "ZHO") return romanizeMandarin(text);
  return "";
}
