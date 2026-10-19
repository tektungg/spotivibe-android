import React from "react";
import { interpolate, interpolateColors } from "remotion";
import { COLOR, FONT, type AccentSet } from "../theme/tokens";
import type { Lang, LyricRow } from "../data/tracks";

/** Distance blur from the app: 1.25 dp per line, capped at 6.83 dp. */
const BLUR_PER_LINE = 1.25;
const BLUR_MAX = 6.83;
const DISTANCE_STOPS = [0, 1, 2, 3];
const OPACITY_STOPS = [1, 0.85, 0.55, 0.35];

export const scriptFont = (lang: Lang): string => ({ KOR: FONT.kor, JPN: FONT.jpn, ZHO: FONT.zho })[lang];

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
  pitch?: number;
  activeSize?: number;
  passiveSize?: number;
  /** Fraction of the height where the active line sits. */
  anchor?: number;
  height: number;
  blur?: boolean;
  padX?: number;
}

/** Characters of the active line, swept left to right with lift + glow. */
const KaraokeText: React.FC<{ text: string; sweep: number; accent: AccentSet }> = ({ text, sweep, accent }) => {
  const chars = [...text];
  const head = sweep * chars.length;
  return (
    <>
      {chars.map((c, j) => {
        const lit = Math.max(0, Math.min(1, head - j));
        const singing = Math.max(0, 1 - Math.abs(head - j - 0.5) / 1.5);
        return (
          <span
            key={j}
            style={{
              display: "inline-block",
              whiteSpace: "pre",
              color: interpolateColors(lit, [0, 1], [COLOR.ink2, accent.accent]),
              transform: `translateY(${-3 * singing}px) scale(${1 + 0.06 * singing})`,
              textShadow: singing > 0 ? `0 0 ${14 * singing}px ${accent.accent}aa` : undefined,
            }}
          >
            {c}
          </span>
        );
      })}
    </>
  );
};

const LyricRowView: React.FC<{ row: LyricRow; distance: number; props: LyricsListProps; isActive: boolean }> = ({
  row,
  distance,
  props,
  isActive,
}) => {
  const { accent, lang, sweep, roman = 1, activeSize = 26, passiveSize = 20, blur = true } = props;
  const d = Math.min(3, distance);
  const fontSize = interpolate(d, [0, 1], [activeSize, passiveSize], { extrapolateRight: "clamp" });
  const color = interpolateColors(d, DISTANCE_STOPS, [accent.accent, COLOR.ink2, COLOR.ink3, COLOR.ink4]);
  const blurPx = blur ? Math.min(BLUR_MAX, distance * BLUR_PER_LINE) : 0;
  return (
    <div style={{ opacity: interpolate(d, DISTANCE_STOPS, OPACITY_STOPS), filter: blurPx > 0.05 ? `blur(${blurPx}px)` : undefined }}>
      <div
        style={{
          fontFamily: scriptFont(lang),
          fontSize,
          lineHeight: 1.2,
          fontWeight: d < 0.5 ? 500 : 400,
          color,
          whiteSpace: "nowrap",
        }}
      >
        {isActive && sweep !== undefined ? <KaraokeText text={row.k} sweep={sweep} accent={accent} /> : row.k}
      </div>
      <div
        style={{
          fontFamily: FONT.news,
          fontStyle: "italic",
          fontWeight: 300,
          fontSize: interpolate(d, [0, 1], [14, 12], { extrapolateRight: "clamp" }),
          color: accent.dim,
          marginTop: 3,
          opacity: roman,
          height: 18 * roman,
          whiteSpace: "nowrap",
        }}
      >
        {row.r}
      </div>
    </div>
  );
};

/** Scrolling lyric list: every line positioned by its distance to `active`. */
export const LyricsList: React.FC<LyricsListProps> = (props) => {
  const { lines, active, pitch = 74, height, anchor = 0.38, padX = 24 } = props;
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
      {lines.map((row, i) => {
        const offset = i - active;
        return (
          <div key={i} style={{ position: "absolute", left: padX, right: 0, top: height * anchor + offset * pitch }}>
            <LyricRowView row={row} distance={Math.abs(offset)} props={props} isActive={i === activeRow} />
          </div>
        );
      })}
    </div>
  );
};
