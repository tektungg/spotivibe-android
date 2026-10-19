import React from "react";
import { interpolate, random } from "remotion";
import { COLOR, FONT } from "../theme/tokens";

const CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789·/+-";
const FLIP_FRAMES = 2;
const SETTLE_BOUNCE_FRAMES = 5;
const CHAR_STAGGER = 1;

export interface FlapStyle {
  cell: { width: number; height: number; fontSize: number; gap: number };
  color: string;
}

/** Blank before the start, a seeded random glyph while spinning, then the target. */
function glyphAt({ frame, start, isSpinning, seed, target }: { frame: number; start: number; isSpinning: boolean; seed: string; target: string }): string {
  if (frame < start) return " ";
  if (!isSpinning) return target;
  return CHARSET[Math.floor(random(seed) * CHARSET.length)];
}

/**
 * One split-flap cell: blank before `start`, spinning random glyphs until
 * `settle`, then the target glyph with a small mechanical bounce.
 */
const FlapCell: React.FC<{ target: string; start: number; settle: number; frame: number; seed: string; style: FlapStyle }> = ({
  target,
  start,
  settle,
  frame,
  seed,
  style,
}) => {
  const { width, height, fontSize } = style.cell;
  const isSpinning = frame >= start && frame < settle;
  const step = Math.floor((frame - start) / FLIP_FRAMES);
  const glyph = glyphAt({ frame, start, isSpinning, seed: `${seed}-${step}`, target });
  const phase = isSpinning ? ((frame - start) % FLIP_FRAMES) / FLIP_FRAMES : 0;
  const bounce = frame >= settle ? interpolate(frame - settle, [0, SETTLE_BOUNCE_FRAMES], [0.82, 1], { extrapolateRight: "clamp" }) : 1;
  return (
    <div
      style={{
        position: "relative",
        width,
        height,
        borderRadius: 4,
        background: `linear-gradient(180deg, ${COLOR.bg3} 0%, ${COLOR.bg2} 49.5%, ${COLOR.bg1} 50.5%, ${COLOR.bg2} 100%)`,
        boxShadow: "inset 0 1px 0 rgba(255,255,255,0.05), 0 2px 4px rgba(0,0,0,0.4)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
      }}
    >
      <span style={{ fontFamily: FONT.mono, fontSize, fontWeight: 600, color: isSpinning ? COLOR.ink3 : style.color, transform: `scaleY(${(1 - phase * 0.5) * bounce})`, whiteSpace: "pre" }}>{glyph}</span>
      <div style={{ position: "absolute", left: 0, right: 0, top: "50%", height: 2, marginTop: -1, background: "rgba(0,0,0,0.55)" }} />
    </div>
  );
};

/** A row of flap cells spelling `text`, padded to `length` cells. */
export const FlapText: React.FC<{ text: string; length: number; start: number; spin: number; frame: number; seed: string; style: FlapStyle }> = ({
  text,
  length,
  start,
  spin,
  frame,
  seed,
  style,
}) => {
  const chars = [...text.padEnd(length, " ")];
  return (
    <div style={{ display: "flex", gap: style.cell.gap }}>
      {chars.map((c, i) => (
        <FlapCell key={i} target={c} start={start} settle={start + spin + i * CHAR_STAGGER} frame={frame} seed={`${seed}-${i}`} style={style} />
      ))}
    </div>
  );
};
