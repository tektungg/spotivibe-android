import React from "react";
import { useCurrentFrame } from "remotion";
import { progress, typedChars } from "../lib/motion";
import { EASE, EYEBROW_TRACKING, FONT } from "../theme/tokens";

const RISE_FRAMES = 22;

/**
 * Words rise out of a clipping mask one after another (editorial headline
 * reveal). `out` (0..1) pushes them up and away again for exits.
 */
export const RevealWords: React.FC<{
  text: string;
  start: number;
  stagger?: number;
  out?: number;
  style?: React.CSSProperties;
  wordStyle?: (index: number) => React.CSSProperties;
}> = ({ text, start, stagger = 4, out = 0, style, wordStyle }) => {
  const frame = useCurrentFrame();
  const words = text.split(" ");
  return (
    <div style={{ display: "flex", flexWrap: "wrap", columnGap: "0.24em", ...style }}>
      {words.map((w, i) => {
        const p = progress(frame, start + i * stagger, start + i * stagger + RISE_FRAMES);
        const y = (1 - p) * 110 - out * 110;
        return (
          <span key={i} style={{ display: "inline-block", overflow: "hidden", paddingBottom: "0.12em", marginBottom: "-0.12em" }}>
            <span style={{ display: "inline-block", transform: `translateY(${y}%)`, ...wordStyle?.(i) }}>{w}</span>
          </span>
        );
      })}
    </div>
  );
};

/** Mono uppercase eyebrow (MonoUp) typed on character by character. */
export const TypedEyebrow: React.FC<{
  text: string;
  start: number;
  framesPerChar?: number;
  color: string;
  size?: number;
  cursor?: boolean;
  style?: React.CSSProperties;
}> = ({ text, start, framesPerChar = 2, color, size = 22, cursor = true, style }) => {
  const frame = useCurrentFrame();
  const shown = typedChars(frame, start, framesPerChar, text.length);
  const blinkOn = Math.floor(frame / 15) % 2 === 0;
  return (
    <div
      style={{
        fontFamily: FONT.mono,
        textTransform: "uppercase",
        letterSpacing: EYEBROW_TRACKING,
        fontSize: size,
        fontWeight: 500,
        color,
        whiteSpace: "pre",
        opacity: frame >= start ? 1 : 0,
        ...style,
      }}
    >
      {text.slice(0, shown)}
      {cursor && <span style={{ opacity: blinkOn || shown < text.length ? 1 : 0 }}>▍</span>}
    </div>
  );
};

/** Fade + slide-up entrance for any block. */
export const Rise: React.FC<{ start: number; distance?: number; children: React.ReactNode; style?: React.CSSProperties }> = ({
  start,
  distance = 30,
  children,
  style,
}) => {
  const frame = useCurrentFrame();
  const p = progress(frame, start, start + RISE_FRAMES, EASE.out);
  return <div style={{ opacity: p, transform: `translateY(${(1 - p) * distance}px)`, ...style }}>{children}</div>;
};
