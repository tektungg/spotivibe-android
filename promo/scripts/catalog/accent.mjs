// Accent from cover art, ported from the app:
//   ui/theme/AccentLock.kt        OkLab math, L/C lock with chroma gamut search
//   domain/PlaybackDecisions.kt   vibrant -> light vibrant -> dominant -> fallback
// AndroidX Palette is not available in Node; pickSwatches() approximates its
// targets (5-bit quantization, then Palette's vibrant and light-vibrant
// lightness/saturation windows and weights) on a small RGB downscale.

const DARK_L = 0.78;
const DARK_C = 0.17;
const DIM_L = 0.66;
const DIM_C = 0.11;
const GAMUT_SEARCH_STEPS = 16;
const GAMUT_EPS = 1e-6;
export const CORAL_FALLBACK = "#EC6A5C";
/** AccentInkDark in Color.kt: foreground on an accent-filled surface. */
export const ACCENT_INK = "#0E0C12";

const srgbToLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const linearToSrgb = (c) => {
  const cc = Math.min(1, Math.max(0, c));
  return cc <= 0.0031308 ? 12.92 * cc : 1.055 * cc ** (1 / 2.4) - 0.055;
};

/** sRGB components in 0..1 -> OkLab [L, a, b]. */
export function srgbToOklab(r, g, b) {
  const [lr, lg, lb] = [r, g, b].map(srgbToLinear);
  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}

/** OkLab -> linear sRGB, unclipped (values outside 0..1 mean out of gamut). */
export function oklabToLinearSrgb(L, a, b) {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

function inGamut(L, chroma, hue) {
  return oklabToLinearSrgb(L, chroma * Math.cos(hue), chroma * Math.sin(hue)).every((v) => v >= -GAMUT_EPS && v <= 1 + GAMUT_EPS);
}

/** OkLCh -> sRGB hex; chroma is lowered until the colour fits, L and hue kept. */
export function oklchToHex(L, chroma, hue) {
  let lo = chroma;
  if (!inGamut(L, chroma, hue)) {
    let hi = chroma;
    lo = 0;
    for (let i = 0; i < GAMUT_SEARCH_STEPS; i++) {
      const mid = (lo + hi) / 2;
      if (inGamut(L, mid, hue)) lo = mid;
      else hi = mid;
    }
  }
  const rgb = oklabToLinearSrgb(L, lo * Math.cos(hue), lo * Math.sin(hue)).map(linearToSrgb);
  return `#${rgb.map((v) => Math.round(v * 255).toString(16).padStart(2, "0")).join("").toUpperCase()}`;
}

export function hexToRgb(hex) {
  const n = Number.parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => v / 255);
}

const hueOf = (hex) => {
  const [, a, b] = srgbToOklab(...hexToRgb(hex));
  return Math.atan2(b, a);
};

/** AccentLock.lockedAccent(dark = true) and dimOf(dark = true). */
export function lockAccent(sourceHex) {
  const hue = hueOf(sourceHex);
  return { accent: oklchToHex(DARK_L, DARK_C, hue), dim: oklchToHex(DIM_L, DIM_C, hue), ink: ACCENT_INK };
}

// ---- Palette approximation ---------------------------------------------------

const QUANT_BITS = 5;
const MAX_SWATCHES = 16;
const TARGETS = {
  vibrant: { minL: 0.3, targetL: 0.5, maxL: 0.7, minS: 0.35, targetS: 1 },
  lightVibrant: { minL: 0.55, targetL: 0.74, maxL: 1, minS: 0.35, targetS: 1 },
};
const WEIGHTS = { saturation: 0.24, lightness: 0.52, population: 0.24 };

function hsl([r, g, b]) {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  const d = max - min;
  const s = d === 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
  return { s, l };
}

/** Quantized colour buckets of an RGB byte buffer, most populous first. */
export function swatchesOf(rgbBytes) {
  const shift = 8 - QUANT_BITS;
  const buckets = new Map();
  for (let i = 0; i + 2 < rgbBytes.length; i += 3) {
    const key = (rgbBytes[i] >> shift) << (2 * QUANT_BITS) | (rgbBytes[i + 1] >> shift) << QUANT_BITS | (rgbBytes[i + 2] >> shift);
    const b = buckets.get(key) ?? { r: 0, g: 0, b: 0, population: 0 };
    b.r += rgbBytes[i];
    b.g += rgbBytes[i + 1];
    b.b += rgbBytes[i + 2];
    b.population += 1;
    buckets.set(key, b);
  }
  return [...buckets.values()]
    .sort((x, y) => y.population - x.population)
    .slice(0, MAX_SWATCHES)
    .map((b) => ({ rgb: [b.r, b.g, b.b].map((v) => v / b.population / 255), population: b.population }));
}

function bestFor(swatches, target) {
  const maxPop = Math.max(...swatches.map((s) => s.population));
  let best = null;
  for (const sw of swatches) {
    const { s, l } = hsl(sw.rgb);
    if (l < target.minL || l > target.maxL || s < target.minS) continue;
    const score = WEIGHTS.saturation * (1 - Math.abs(s - target.targetS)) + WEIGHTS.lightness * (1 - Math.abs(l - target.targetL)) + WEIGHTS.population * (sw.population / maxPop);
    if (!best || score > best.score) best = { score, rgb: sw.rgb };
  }
  return best?.rgb ?? null;
}

const toHex = (rgb) => `#${rgb.map((v) => Math.round(v * 255).toString(16).padStart(2, "0")).join("").toUpperCase()}`;

/** pickAccentArgb: vibrant, then light vibrant, then dominant, then coral. */
export function pickSourceColor(rgbBytes) {
  const swatches = swatchesOf(rgbBytes);
  if (!swatches.length) return CORAL_FALLBACK;
  const pick = bestFor(swatches, TARGETS.vibrant) ?? bestFor(swatches, TARGETS.lightVibrant) ?? swatches[0].rgb;
  return toHex(pick);
}
