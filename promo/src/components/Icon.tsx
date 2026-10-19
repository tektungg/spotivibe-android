import React from "react";
import { FONT } from "../theme/tokens";

/** Hairline icon set, ported from claude-design-handoff/screens-shared.jsx. */
export type IconName =
  | "play" | "pause" | "prev" | "next" | "romanize" | "pip" | "kebab"
  | "close" | "expand" | "drag" | "signal" | "wifi" | "check" | "bell";

const FILLED = { fill: "currentColor", stroke: "none" } as const;
const dots = (points: [number, number][], r: number) =>
  points.map(([cx, cy]) => <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r={r} {...FILLED} />);

const PATHS: Record<IconName, React.ReactNode> = {
  play: <polygon points="7,4 7,20 20,12" {...FILLED} />,
  pause: (
    <>
      <rect x="6" y="5" width="3.5" height="14" {...FILLED} />
      <rect x="14.5" y="5" width="3.5" height="14" {...FILLED} />
    </>
  ),
  prev: (
    <>
      <path d="M19 4L8 12l11 8z" {...FILLED} />
      <path d="M6 4v16" strokeWidth="1.5" />
    </>
  ),
  next: (
    <>
      <path d="M5 4l11 8-11 8z" {...FILLED} />
      <path d="M18 4v16" strokeWidth="1.5" />
    </>
  ),
  romanize: (
    <text x="2" y="17" fontFamily={FONT.mono} fontSize="11" fontWeight="500" {...FILLED}>
      Rm
    </text>
  ),
  pip: (
    <>
      <rect x="3" y="4" width="18" height="16" rx="1" />
      <rect x="11" y="11" width="9" height="7" rx="1" {...FILLED} opacity="0.6" />
    </>
  ),
  kebab: <>{dots([[12, 5], [12, 12], [12, 19]], 1.2)}</>,
  close: <path d="M5 5l14 14M19 5L5 19" />,
  expand: <path d="M4 4h6M4 4v6M20 4h-6M20 4v6M4 20h6M4 20v-6M20 20h-6M20 20v-6" />,
  drag: <>{dots([[9, 6], [15, 6], [9, 12], [15, 12], [9, 18], [15, 18]], 1)}</>,
  signal: (
    <>
      {[[2, 14, 6], [7, 10, 10], [12, 6, 14], [17, 2, 18]].map(([x, y, h]) => (
        <rect key={x} x={x} y={y} width="3" height={h} {...FILLED} />
      ))}
    </>
  ),
  wifi: <path d="M2 9a17 17 0 0120 0M5 13a12 12 0 0114 0M8.5 17a7 7 0 017 0" />,
  check: <path d="M4 12l5 5L20 6" />,
  bell: <path d="M6 16V11a6 6 0 0112 0v5l2 2H4zM10 20a2 2 0 004 0" />,
};

export const Icon: React.FC<{ name: IconName; size?: number; stroke?: number; color?: string; style?: React.CSSProperties }> = ({
  name,
  size = 18,
  stroke = 1,
  color = "currentColor",
  style,
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    style={{ color, display: "block", ...style }}
    fill="none"
    stroke="currentColor"
    strokeWidth={stroke}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    {PATHS[name]}
  </svg>
);
