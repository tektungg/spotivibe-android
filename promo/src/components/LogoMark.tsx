import React from "react";
import { interpolate, interpolateColors } from "remotion";
import { COLOR } from "../theme/tokens";

/**
 * The Spotivibe mark (logo/spotivibe-mark.svg): five lyric lines, one lit.
 * Geometry is the SVG's own (viewBox 58 x 53), scaled by `unit`.
 */
export const MARK_BARS = [
  { x: 14, y: 0, w: 30, h: 5 },
  { x: 6, y: 11.5, w: 46, h: 6 },
  { x: 0, y: 22.5, w: 58, h: 8 },
  { x: 8, y: 35.5, w: 42, h: 6 },
  { x: 16, y: 48, w: 26, h: 5 },
] as const;
export const MARK_SIZE = { w: 58, h: 53 } as const;
export const MARK_ACTIVE_INDEX = 2;

/** Bar color + alpha by distance from the lit bar (Active / Near / Far). */
export function barTone(distance: number, accent: string): { color: string; opacity: number } {
  const d = Math.min(2, Math.abs(distance));
  return {
    color: interpolateColors(d, [0, 1, 2], [accent, COLOR.ink2, COLOR.ink3]),
    opacity: interpolate(d, [0, 1, 2], [1, 0.85, 0.55]),
  };
}

interface LogoMarkProps {
  unit: number;
  accent: string;
  /** Per-bar reveal 0..1 (scaleX from center). Defaults to fully shown. */
  reveal?: readonly number[];
  /** Fractional index of the lit bar; animating it "scrolls" the lyrics. */
  litIndex?: number;
  glow?: number;
  style?: React.CSSProperties;
}

export const LogoMark: React.FC<LogoMarkProps> = ({
  unit,
  accent,
  reveal = [1, 1, 1, 1, 1],
  litIndex = MARK_ACTIVE_INDEX,
  glow = 0,
  style,
}) => (
  <div style={{ position: "relative", width: MARK_SIZE.w * unit, height: MARK_SIZE.h * unit, ...style }}>
    {MARK_BARS.map((bar, i) => {
      const tone = barTone(i - litIndex, accent);
      const isLit = Math.abs(i - litIndex) < 0.5;
      return (
        <div
          key={bar.y}
          style={{
            position: "absolute",
            left: bar.x * unit,
            top: bar.y * unit,
            width: bar.w * unit,
            height: bar.h * unit,
            borderRadius: (bar.h * unit) / 2,
            background: tone.color,
            opacity: tone.opacity,
            transform: `scaleX(${reveal[i] ?? 1})`,
            boxShadow: isLit && glow > 0 ? `0 0 ${glow * 60}px ${glow * 8}px ${accent}66` : undefined,
          }}
        />
      );
    })}
  </div>
);
