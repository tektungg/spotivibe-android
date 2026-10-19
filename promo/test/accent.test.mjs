// Port of the app's AccentLock.kt + pickAccentArgb.
import assert from "node:assert/strict";
import { test } from "node:test";
import { CORAL_FALLBACK, hexToRgb, lockAccent, oklabToLinearSrgb, oklchToHex, pickSourceColor, srgbToOklab } from "../scripts/catalog/accent.mjs";

const lightness = (hex) => srgbToOklab(...hexToRgb(hex))[0];
const hue = (hex) => {
  const [, a, b] = srgbToOklab(...hexToRgb(hex));
  return Math.atan2(b, a);
};
const HEX = /^#[0-9A-F]{6}$/;

test("OkLab round trip is stable", () => {
  const [L, a, b] = srgbToOklab(0.2, 0.5, 0.8);
  const back = oklabToLinearSrgb(L, a, b);
  const linear = [0.2, 0.5, 0.8].map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  back.forEach((v, i) => assert.ok(Math.abs(v - linear[i]) < 1e-6));
});

test("locked accent keeps the cover's hue and lands at L 0.78", () => {
  for (const source of ["#1E90FF", "#2E8B57", "#C81E1E", "#B8A000"]) {
    const { accent, dim } = lockAccent(source);
    assert.match(accent, HEX);
    assert.match(dim, HEX);
    assert.ok(Math.abs(lightness(accent) - 0.78) < 0.01, `${source} -> ${accent} L=${lightness(accent)}`);
    const dh = Math.abs(Math.atan2(Math.sin(hue(accent) - hue(source)), Math.cos(hue(accent) - hue(source))));
    assert.ok(dh < 0.05, `${source} -> ${accent} hue moved by ${dh}`);
  }
});

test("out-of-gamut chroma is lowered instead of clipping lightness", () => {
  const red = oklchToHex(0.78, 0.17, Math.atan2(0.1, 0.15));
  assert.ok(Math.abs(lightness(red) - 0.78) < 0.01, `saturated red came out at L=${lightness(red)}`);
});

test("source colour: vibrant swatch first, coral when there are no pixels", () => {
  const pixels = Buffer.alloc(32 * 32 * 3);
  for (let i = 0; i < pixels.length; i += 3) {
    const isVibrant = i < pixels.length / 4;
    pixels.set(isVibrant ? [20, 120, 230] : [40, 40, 40], i);
  }
  const picked = hexToRgb(pickSourceColor(pixels));
  assert.ok(picked[2] > picked[0], `expected the blue swatch, got ${pickSourceColor(pixels)}`);
  assert.equal(pickSourceColor(Buffer.alloc(0)), CORAL_FALLBACK);
});
