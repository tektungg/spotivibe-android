/**
 * Spotivibe's tagline, shared with the app: app/.../ui/theme/Brand.kt holds
 * the same string and test/brand.test.mjs fails when they drift apart.
 *
 * No imports on purpose, so Node can import it in tests.
 */
export const TAGLINE = "Every player. Every line.";

/** Upper case, sentences joined with " · ", final period dropped (Brand.eyebrowOf). */
export function eyebrowOf(tagline: string): string {
  return tagline.trim().replace(/\.$/, "").toUpperCase().split(". ").join(" · ");
}

export const TAGLINE_EYEBROW = eyebrowOf(TAGLINE);

/** Players the README names, plus every other MediaSession app. */
export const PLAYERS = ["SPOTIFY", "YOUTUBE MUSIC", "SPOTIFY FREE", "ANY PLAYER"] as const;
