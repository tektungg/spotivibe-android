import React from "react";
import { useCurrentFrame } from "remotion";
import { progress } from "../lib/motion";
import { COLOR, EASE, FONT } from "../theme/tokens";
import { Hairline } from "./Backdrop";
import { TypedEyebrow } from "./Type";
import { TAGLINE_EYEBROW } from "../data/brand";

const WORDMARK = "Spotivibe";
const LETTER_STAGGER = 3;
const LETTER_RISE = 20;

/**
 * Horizontal lockup from logo/spotivibe-wordmark.svg: mark, vertical
 * hairline, serif wordmark, mono eyebrow. The mark is passed in so each
 * scene can animate its bars its own way; `start` drives the rest.
 */
export const Lockup: React.FC<{
  mark: React.ReactNode;
  markHeight: number;
  start: number;
  size?: number;
  style?: React.CSSProperties;
}> = ({ mark, markHeight, start, size = 200, style }) => {
  const frame = useCurrentFrame();
  const gap = size * 0.28;
  return (
    <div style={{ display: "flex", alignItems: "center", ...style }}>
      {mark}
      <div style={{ position: "relative", width: 1, height: markHeight * 1.05, margin: `0 ${gap}px` }}>
        <Hairline length={markHeight * 1.05} vertical progress={progress(frame, start, start + 24, EASE.inOut)} color={COLOR.rule} thickness={2} origin="center" />
      </div>
      <div>
        <div style={{ fontFamily: FONT.display, fontSize: size, lineHeight: 0.95, color: COLOR.ink1, letterSpacing: "-0.02em", display: "flex" }}>
          {[...WORDMARK].map((ch, i) => {
            const p = progress(frame, start + 6 + i * LETTER_STAGGER, start + 6 + i * LETTER_STAGGER + LETTER_RISE);
            return (
              <span key={i} style={{ display: "inline-block", overflow: "hidden", paddingBottom: size * 0.1, marginBottom: -size * 0.1 }}>
                <span style={{ display: "inline-block", transform: `translateY(${(1 - p) * 105}%)` }}>{ch}</span>
              </span>
            );
          })}
        </div>
        <TypedEyebrow text={TAGLINE_EYEBROW} start={start + 30} framesPerChar={1.5} color={COLOR.ink3} size={size * 0.13} cursor={false} style={{ marginTop: size * 0.12, letterSpacing: "0.3em" }} />
      </div>
    </div>
  );
};
