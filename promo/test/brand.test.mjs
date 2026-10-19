import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { TAGLINE, TAGLINE_EYEBROW, eyebrowOf } from "../src/data/brand.ts";

const PROMO = dirname(dirname(fileURLToPath(import.meta.url)));
const REPO = dirname(PROMO);
const BRAND_KT = join(REPO, "app", "src", "main", "java", "com", "tglabs", "spotivibe", "ui", "theme", "Brand.kt");
/** The VS Code era tagline, in either of its forms. */
const OLD_TAGLINE = /code\W+vibe\W+sing/i;
const SCANNED = [join(REPO, "app", "src", "main"), join(PROMO, "src"), join(REPO, "README.md"), join(REPO, "logo")];
const TEXT_FILE = /\.(kt|kts|xml|ts|tsx|mjs|md|svg)$/;

function files(path) {
  if (!statSync(path).isDirectory()) return [path];
  return readdirSync(path).flatMap((name) => files(join(path, name)));
}

test("promo tagline matches the app's Brand.TAGLINE", () => {
  const kotlin = readFileSync(BRAND_KT, "utf8").match(/const val TAGLINE = "([^"]+)"/);
  assert.ok(kotlin, "Brand.TAGLINE not found in Brand.kt");
  assert.equal(TAGLINE, kotlin[1]);
});

test("eyebrow form follows Brand.eyebrowOf", () => {
  assert.equal(TAGLINE_EYEBROW, "EVERY PLAYER · EVERY LINE");
  assert.equal(eyebrowOf("  One. Two. Three. "), "ONE · TWO · THREE");
});

test("the old Code. Vibe. Sing tagline is gone from app, promo, README and logo", () => {
  for (const file of SCANNED.flatMap(files).filter((f) => TEXT_FILE.test(f))) {
    const line = readFileSync(file, "utf8").split("\n").find((l) => OLD_TAGLINE.test(l));
    assert.equal(line, undefined, `${relative(REPO, file)} still has the old tagline: ${line?.trim()}`);
  }
});
