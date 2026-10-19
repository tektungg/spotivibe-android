import React from "react";
import { useCurrentFrame } from "remotion";
import { progress } from "../lib/motion";
import { COLOR, EASE, FONT } from "../theme/tokens";
import { Hairline } from "./Backdrop";
import { eyebrow } from "./NowPlayingScreen";
import { RevealWords, TypedEyebrow } from "./Type";

export interface ChapterCopy {
  number: string;
  label: string;
  headline: string;
  /** Index of the first headline word drawn in italic accent. */
  accentFrom: number;
  sub: string;
}

const EXIT_FRAMES = 14;

/**
 * Editorial chapter block: "01 — LABEL" eyebrow, serif headline that rises
 * word by word, hairline, mono sub line. Visible between `start` and `end`.
 */
export const Chapter: React.FC<{
  copy: ChapterCopy;
  start: number;
  end: number;
  accent: string;
  width?: number;
  size?: number;
  style?: React.CSSProperties;
}> = ({ copy, start, end, accent, width = 720, size = 112, style }) => {
  const frame = useCurrentFrame();
  if (frame < start || frame >= end) return null;
  const out = progress(frame, end - EXIT_FRAMES, end, EASE.in);
  return (
    <div style={{ position: "absolute", width, ...style }}>
      <div style={{ display: "flex", gap: 18, alignItems: "center", opacity: 1 - out }}>
        <span style={{ ...eyebrow, fontSize: 22, color: accent }}>{copy.number}</span>
        <div style={{ position: "relative", width: 60, height: 1 }}>
          <Hairline length={60} progress={progress(frame, start, start + 16)} color={accent} />
        </div>
        <TypedEyebrow text={copy.label} start={start + 4} framesPerChar={1} color={COLOR.ink2} size={22} cursor={false} />
      </div>
      <RevealWords
        text={copy.headline}
        start={start + 8}
        stagger={4}
        out={out}
        style={{ fontFamily: FONT.display, fontSize: size, lineHeight: 1, letterSpacing: "-0.02em", color: COLOR.ink1, marginTop: 28 }}
        wordStyle={(i) => (i >= copy.accentFrom ? { fontStyle: "italic", color: accent } : {})}
      />
      <div style={{ position: "relative", height: 1, margin: "40px 0 26px", opacity: 1 - out }}>
        <Hairline length={width * 0.8} progress={progress(frame, start + 20, start + 46, EASE.inOut)} color={COLOR.rule} />
      </div>
      <TypedEyebrow text={copy.sub} start={start + 30} framesPerChar={1} color={COLOR.ink3} size={20} cursor={false} style={{ opacity: 1 - out }} />
    </div>
  );
};
