/**
 * Text width estimates for fitting lyrics without measuring the DOM (frames
 * must render the same every time). Average glyph advances in em, measured
 * once from the app fonts: Newsreader body, Instrument Serif display, and
 * full-width CJK.
 *
 * No imports on purpose: unit-tested directly by Node.
 */

export type Face = "news" | "display";

export const GLYPH_EM = {
  cjk: 1,
  space: 0.26,
  news: 0.47,
  display: 0.42,
} as const;

/** Wrapping is never perfect, so only this share of the box counts as usable. */
const USABLE_WIDTH = 0.92;
/** Full-width scripts: Hangul jamo, kana, CJK ideographs, Hangul syllables, full-width forms. */
const WIDE_RANGES: readonly [number, number][] = [
  [0x1100, 0x11ff],
  [0x3040, 0x30ff],
  [0x3400, 0x9fff],
  [0xac00, 0xd7af],
  [0xff00, 0xffef],
];

const isWide = (ch: string): boolean => {
  const code = ch.codePointAt(0) ?? 0;
  return WIDE_RANGES.some(([from, to]) => code >= from && code <= to);
};

/** Estimated width of `text` in em for `face`. */
export function estimateEm(text: string, face: Face): number {
  let em = 0;
  for (const ch of text) {
    if (ch === " ") em += GLYPH_EM.space;
    else if (isWide(ch)) em += GLYPH_EM.cjk;
    else em += GLYPH_EM[face];
  }
  return em;
}

/** Lines `text` wraps to at `fontSize` in a box `maxWidth` wide (at least 1). */
export function wrappedLines(text: string, face: Face, fontSize: number, maxWidth: number): number {
  return Math.max(1, Math.ceil((estimateEm(text, face) * fontSize) / (maxWidth * USABLE_WIDTH)));
}

/** Largest size up to `size` at which `text` fits in `maxLines` lines of `maxWidth`. */
export function fitFontSize(text: string, face: Face, size: number, maxWidth: number, maxLines = 1): number {
  const em = estimateEm(text, face);
  if (em === 0) return size;
  return Math.min(size, (maxLines * maxWidth * USABLE_WIDTH) / em);
}
