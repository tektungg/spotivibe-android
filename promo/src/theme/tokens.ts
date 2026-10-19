import { Easing } from "remotion";
import { ACCENT_PRESETS } from "../data/catalog";

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

export type { AccentSet } from "../data/catalog";

/**
 * Brand accent presets from Color.kt (coral is the brand colour). Songs carry
 * their own accent, locked from their cover the way the app does it.
 */
export const ACCENTS = ACCENT_PRESETS;

export const FONT = {
  display: "'Instrument Serif', Georgia, serif",
  news: "'Newsreader', Georgia, serif",
  // CJK artist/album names in mono labels fall back to the Noto subsets.
  mono: "'JetBrains Mono', 'Noto Serif KR', 'Noto Serif JP', 'Noto Serif TC', ui-monospace, monospace",
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
