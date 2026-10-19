import React from "react";
import { interpolate, interpolateColors } from "remotion";
import { COLOR, FONT, type AccentSet } from "../theme/tokens";
import type { Lang, LyricRow } from "../data/catalog";
import { fitFontSize, wrappedLines } from "../lib/textFit";

/** Distance blur from the app: 1.25 dp per line, capped at 6.83 dp. */
const BLUR_PER_LINE = 1.25;
const BLUR_MAX = 6.83;
const DISTANCE_STOPS = [0, 1, 2, 3];
const OPACITY_STOPS = [1, 0.85, 0.55, 0.35];
/** LyricLine.kt lineTint: high contrast multiplies inactive alpha by 0.85. */
const HIGH_CONTRAST_INACTIVE = 0.85;
/** Long lines wrap like the app's TextWrap, up to two lines, then shrink. */
const MAX_WRAP_LINES = 2;
const LINE_HEIGHT = 1.2;
const ROMAN_SIZE = { active: 14, passive: 12 };
const DEFAULT_TEXT_WIDTH = 350;

/** Body (lyric) face for a language: Noto Serif per CJK script, Newsreader for Latin. */
export const scriptFont = (lang: Lang): string => ({ KOR: FONT.kor, JPN: FONT.jpn, ZHO: FONT.zho, ENG: FONT.news, IND: FONT.news })[lang];

/** Display (title) face: Instrument Serif, falling back to the script's face for CJK. */
export const displayFont = (lang: Lang): string => `'Instrument Serif', ${scriptFont(lang)}`;

export interface LyricsListProps {
  lines: readonly LyricRow[];
  lang: Lang;
  accent: AccentSet;
  /** Fractional active line index; animate it to scroll. */
  active: number;
  /** Karaoke sweep across the active line, 0..1. */
  sweep?: number;
  /** Romanization visibility 0..1 (the Rm toggle). */
  roman?: number;
  /** High contrast mode 0..1: no distance blur, inactive lines at 0.85 alpha. */
  highContrast?: number;
  pitch?: number;
  activeSize?: number;
  passiveSize?: number;
  /** Fraction of the height where the active line sits. */
  anchor?: number;
  height: number;
  /** Width available to the text, for wrapping. */
  width?: number;
  blur?: boolean;
  padX?: number;
}

/**
 * Active line swept left to right; each character lifts and glows as it is
 * sung. Words stay unbroken so long lines wrap between words.
 */
export const KaraokeText: React.FC<{ text: string; sweep: number; accent: AccentSet }> = ({ text, sweep, accent }) => {
  const head = sweep * [...text].length;
  let index = 0;
  const charView = (c: string) => {
    const j = index++;
    const lit = Math.max(0, Math.min(1, head - j));
    const singing = Math.max(0, 1 - Math.abs(head - j - 0.5) / 1.5);
    return (
      <span key={j} style={{ display: "inline-block", color: interpolateColors(lit, [0, 1], [COLOR.ink2, accent.accent]), transform: `translateY(${-3 * singing}px) scale(${1 + 0.06 * singing})`, textShadow: singing > 0 ? `0 0 ${14 * singing}px ${accent.accent}aa` : undefined }}>
        {c}
      </span>
    );
  };
  return (
    <>
      {text.split(/(\s+)/).map((part, w) => {
        if (/^\s+$/.test(part)) {
          index += [...part].length;
          return part;
        }
        return <span key={`w${w}`} style={{ display: "inline-block", whiteSpace: "nowrap" }}>{[...part].map(charView)}</span>;
      })}
    </>
  );
};

interface RowLayout {
  size: number;
  top: number;
}

/** Font size and vertical offset of every row; wrapped rows take more room. */
function layoutRows({ lines, activeSize = 26, pitch = 74, width = DEFAULT_TEXT_WIDTH }: LyricsListProps): RowLayout[] {
  // Latin lines inside CJK songs fall back to Newsreader too, so "news" fits all.
  const face = "news";
  let top = 0;
  return lines.map((row) => {
    const size = fitFontSize(row.k, face, activeSize, width, MAX_WRAP_LINES);
    const wraps = Math.min(MAX_WRAP_LINES, wrappedLines(row.k, face, size, width));
    const layout = { size, top };
    top += pitch + (wraps - 1) * size * LINE_HEIGHT;
    return layout;
  });
}

/** Scroll offset for a fractional active index, between row tops. */
function scrollTop(rows: RowLayout[], active: number): number {
  const lo = Math.max(0, Math.min(rows.length - 1, Math.floor(active)));
  const hi = Math.min(rows.length - 1, lo + 1);
  const frac = Math.max(0, Math.min(1, active - lo));
  return rows[lo].top + (rows[hi].top - rows[lo].top) * frac;
}

const LyricRowView: React.FC<{ row: LyricRow; layout: RowLayout; distance: number; props: LyricsListProps; isActive: boolean }> = ({ row, layout, distance, props, isActive }) => {
  const { accent, lang, sweep, roman = 1, highContrast = 0, activeSize = 26, passiveSize = 20, blur = true, width = DEFAULT_TEXT_WIDTH } = props;
  const d = Math.min(3, distance);
  const fontSize = interpolate(d, [0, 1], [layout.size, (layout.size * passiveSize) / activeSize], { extrapolateRight: "clamp" });
  const color = interpolateColors(d, DISTANCE_STOPS, [accent.accent, COLOR.ink2, COLOR.ink3, COLOR.ink4]);
  const blurPx = blur ? Math.min(BLUR_MAX, distance * BLUR_PER_LINE) * (1 - highContrast) : 0;
  const contrastAlpha = 1 - (1 - HIGH_CONTRAST_INACTIVE) * highContrast * Math.min(1, d);
  const romanSize = fitFontSize(row.r, "news", interpolate(d, [0, 1], [ROMAN_SIZE.active, ROMAN_SIZE.passive], { extrapolateRight: "clamp" }), width);
  const showRoman = roman > 0 && row.r.length > 0;
  return (
    <div style={{ width, opacity: interpolate(d, DISTANCE_STOPS, OPACITY_STOPS) * contrastAlpha, filter: blurPx > 0.05 ? `blur(${blurPx}px)` : undefined }}>
      <div style={{ fontFamily: scriptFont(lang), fontSize, lineHeight: LINE_HEIGHT, fontWeight: d < 0.5 ? 500 : 400, color }}>
        {isActive && sweep !== undefined ? <KaraokeText text={row.k} sweep={sweep} accent={accent} /> : row.k}
      </div>
      {showRoman && (
        <div style={{ fontFamily: FONT.news, fontStyle: "italic", fontWeight: 300, fontSize: romanSize, color: accent.dim, marginTop: 3, opacity: roman, height: 18 * roman, whiteSpace: "nowrap" }}>
          {row.r}
        </div>
      )}
    </div>
  );
};

/** Scrolling lyric list: every row positioned by its layout offset relative to `active`. */
export const LyricsList: React.FC<LyricsListProps> = (props) => {
  const { lines, active, height, anchor = 0.38, padX = 24 } = props;
  const rows = layoutRows(props);
  const scroll = scrollTop(rows, active);
  const activeRow = Math.round(active);
  return (
    <div
      style={{
        position: "relative",
        height,
        overflow: "hidden",
        maskImage: "linear-gradient(180deg, transparent 0, #000 14%, #000 80%, transparent 100%)",
        WebkitMaskImage: "linear-gradient(180deg, transparent 0, #000 14%, #000 80%, transparent 100%)",
      }}
    >
      {lines.map((row, i) => (
        <div key={i} style={{ position: "absolute", left: padX, top: height * anchor + rows[i].top - scroll }}>
          <LyricRowView row={row} layout={rows[i]} distance={Math.abs(i - active)} props={props} isActive={i === activeRow} />
        </div>
      ))}
    </div>
  );
};
