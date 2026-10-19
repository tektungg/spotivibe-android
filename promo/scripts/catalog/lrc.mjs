// LRC parsing and lyric windowing. Pure functions, unit-tested.

const TIMESTAMP = /\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g;
const SECONDS_PER_MINUTE = 60;

/** "[01:02.50]text" lines -> [{ t: seconds, text }], sorted, metadata tags skipped. */
export function parseLrc(lrc) {
  const out = [];
  for (const raw of lrc.split(/\r?\n/)) {
    const stamps = [...raw.matchAll(TIMESTAMP)];
    if (!stamps.length) continue;
    const text = raw.replace(TIMESTAMP, "").trim();
    for (const [, mm, ss, frac = "0"] of stamps) {
      const t = Number(mm) * SECONDS_PER_MINUTE + Number(ss) + Number(`0.${frac}`);
      out.push({ t, text });
    }
  }
  return out.sort((a, b) => a.t - b.t);
}

/**
 * `count` consecutive non-empty lines starting at the first one at or after
 * `fromSeconds`. Slides back when the song ends too soon, so the result is
 * always `count` lines when the song has that many.
 */
export function lyricWindow(lines, fromSeconds, count) {
  const sung = lines.filter((l) => l.text.length > 0);
  const firstAfter = sung.findIndex((l) => l.t >= fromSeconds);
  const start = firstAfter === -1 ? 0 : firstAfter;
  const from = Math.max(0, Math.min(start, sung.length - count));
  return sung.slice(from, from + count);
}

const CJK_BY_LANG = {
  KOR: /[가-힯]/u,
  JPN: /[぀-ヿ㐀-鿿]/u,
  ZHO: /[㐀-鿿]/u,
};

/** True when a line of a song in `lang` contains script worth romanizing. */
export function needsRomanization(text, lang) {
  return CJK_BY_LANG[lang]?.test(text) ?? false;
}

/** 224 -> "3:44". */
export function formatDuration(seconds) {
  const s = Math.round(seconds);
  return `${Math.floor(s / SECONDS_PER_MINUTE)}:${String(s % SECONDS_PER_MINUTE).padStart(2, "0")}`;
}

/** "Teh Hijau - Single" -> "Teh Hijau". */
export function cleanAlbum(name) {
  return name.replace(/\s+-\s+(Single|EP)$/i, "").trim();
}
