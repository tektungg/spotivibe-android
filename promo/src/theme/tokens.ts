import { Easing } from "remotion";

/**
 * Design tokens mirrored from the app:
 * app/src/main/java/com/tglabs/spotivibe/ui/theme/Color.kt and
 * claude-design-handoff/tokens.css. Keep the two in sync by hand; the app
 * file wins on any conflict.
 */
export const COLOR = {
  bg0: "#14151B",
  bg1: "#1A1B22",
  bg2: "#22232A",
  bg3: "#2A2B33",
  ink1: "#F4F1EA",
  ink2: "#C2BFB6",
  ink3: "#8A877F",
  ink4: "#5F5D56",
  rule: "#3C3D44",
  ruleSoft: "#2E2F35",
  black: "#08080B",
} as const;

export type AccentName = "coral" | "violet" | "jade";

export interface AccentSet {
  accent: string;
  dim: string;
  ink: string;
}

/**
 * Accent presets. `accent` and `ink` match Color.kt; `dim` is deliberately
 * lighter than the app's AccentDim, which is too dark to read as 14 px
 * romanization after H.264 compression on a 1080p frame.
 */
export const ACCENTS: Record<AccentName, AccentSet> = {
  coral: { accent: "#EC6A5C", dim: "#B7564A", ink: "#0E0C12" },
  violet: { accent: "#A17BFF", dim: "#7A5FC4", ink: "#0E0C12" },
  jade: { accent: "#3CC9A1", dim: "#2E9A7B", ink: "#0E0C12" },
};

export const FONT = {
  display: "'Instrument Serif', Georgia, serif",
  news: "'Newsreader', Georgia, serif",
  mono: "'JetBrains Mono', ui-monospace, monospace",
  kor: "'Noto Serif KR', 'Newsreader', serif",
  jpn: "'Noto Serif JP', 'Newsreader', serif",
  zho: "'Noto Serif TC', 'Newsreader', serif",
} as const;

export const EASE = {
  out: Easing.bezier(0.16, 1, 0.3, 1),
  inOut: Easing.bezier(0.65, 0, 0.35, 1),
  in: Easing.bezier(0.7, 0, 0.84, 0),
} as const;

/** Mono eyebrow (MonoUp in SvType). */
export const EYEBROW_TRACKING = "0.12em";

/** Phone artboard of the design handoff (390 x 844). */
export const PHONE = { width: 390, height: 844, radius: 46, bezel: 11 } as const;
