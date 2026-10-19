import React from "react";
import { AbsoluteFill, random, useCurrentFrame } from "remotion";

const GRAIN_TILE =
  "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 0.5  0 0 0 0 0.5  0 0 0 0 0.5  0 0 0 0.9 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>";
const GRAIN_TILE_PX = 220;
/** Grain jumps every 2 frames: 30 Hz film grain on a 60 fps timeline. */
const GRAIN_HOLD_FRAMES = 2;

/** Film grain from tokens.css (.sv-grain), animated by jittering the tile. */
export const Grain: React.FC<{ opacity?: number }> = ({ opacity = 0.07 }) => {
  const frame = useCurrentFrame();
  const step = Math.floor(frame / GRAIN_HOLD_FRAMES);
  const x = Math.floor(random(`gx${step}`) * GRAIN_TILE_PX);
  const y = Math.floor(random(`gy${step}`) * GRAIN_TILE_PX);
  return (
    <AbsoluteFill
      style={{
        backgroundImage: `url("${GRAIN_TILE}")`,
        backgroundPosition: `${x}px ${y}px`,
        opacity,
        mixBlendMode: "overlay",
        pointerEvents: "none",
      }}
    />
  );
};

/** Halftone dot screen (tokens.css .sv-halftone). */
export const Halftone: React.FC<{ size?: number; alpha?: number; style?: React.CSSProperties }> = ({
  size = 4,
  alpha = 0.55,
  style,
}) => (
  <AbsoluteFill
    style={{
      backgroundImage: `radial-gradient(rgba(0,0,0,${alpha}) ${size / 4}px, transparent ${size / 4 + 0.2}px)`,
      backgroundSize: `${size}px ${size}px`,
      mixBlendMode: "multiply",
      ...style,
    }}
  />
);

export const Vignette: React.FC<{ strength?: number }> = ({ strength = 0.6 }) => (
  <AbsoluteFill
    style={{
      background: `radial-gradient(ellipse 75% 70% at 50% 50%, transparent 55%, rgba(0,0,0,${strength}) 100%)`,
      pointerEvents: "none",
    }}
  />
);

/** Blurred album-art glow behind a scene (AmbientBg in the handoff). */
export const AmbientGlow: React.FC<{ gradient: string; opacity?: number; blur?: number; scale?: number }> = ({
  gradient,
  opacity = 0.5,
  blur = 120,
  scale = 1.2,
}) => (
  <AbsoluteFill style={{ overflow: "hidden" }}>
    <AbsoluteFill
      style={{ background: gradient, filter: `blur(${blur}px) saturate(1.3)`, transform: `scale(${scale})`, opacity }}
    />
  </AbsoluteFill>
);

/** Hairline rule, the signature element of the editorial layout. */
export const Hairline: React.FC<{
  length: number;
  progress: number;
  vertical?: boolean;
  color: string;
  thickness?: number;
  origin?: "start" | "center" | "end";
  style?: React.CSSProperties;
}> = ({ length, progress, vertical = false, color, thickness = 1, origin = "start", style }) => {
  const originCss = { start: "0% 0%", center: "50% 50%", end: "100% 100%" }[origin];
  return (
    <div
      style={{
        position: "absolute",
        width: vertical ? thickness : length,
        height: vertical ? length : thickness,
        background: color,
        transform: vertical ? `scaleY(${progress})` : `scaleX(${progress})`,
        transformOrigin: originCss,
        ...style,
      }}
    />
  );
};
