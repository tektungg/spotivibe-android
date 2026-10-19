// The social preview is a public image: it must never pull the real catalog
// (copyrighted lyrics and cover art), and must match GitHub's recommended size.
// Sources are read as text because Node cannot import TSX.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const read = (...parts) => readFileSync(join(ROOT, ...parts), "utf8");
const STILL = read("src", "stills", "SocialPreview.tsx");

test("social preview renders the fictional catalog only", () => {
  assert.match(STILL, /resolveCatalog\(FICTIONAL_CATALOG\)/);
  for (const forbidden of ["useCatalog(", "useTrack(", "loadCatalog(", "catalog.json", "coverFile"]) {
    assert.ok(!STILL.includes(forbidden), `SocialPreview.tsx must not use ${forbidden}`);
  }
});

test("social preview is 1280 x 640 and registered as a still", () => {
  assert.match(STILL, /SOCIAL_PREVIEW = \{ width: 1280, height: 640 \}/);
  assert.match(read("src", "Root.tsx"), /<Still id="SocialPreview" component=\{SocialPreview\} width=\{SOCIAL_PREVIEW\.width\} height=\{SOCIAL_PREVIEW\.height\}/);
});

test("social preview makes no open-source claim while the repo has no LICENSE", () => {
  assert.ok(!/OPEN SOURCE/i.test(STILL), "add a LICENSE file before claiming open source");
});
