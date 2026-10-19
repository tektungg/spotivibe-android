import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

/**
 * Loads the app's own type (copied from app/src/main/res/font by
 * scripts/prepare-assets.mjs) and the CJK subsets. loadFont delays the
 * render until every face is ready, so no frame renders with a fallback.
 */
const dir = (file: string) => staticFile(`generated/fonts/${file}`);

interface Face {
  family: string;
  file: string;
  weight?: string;
  style?: "normal" | "italic";
}

const VARIABLE_WEIGHTS = "200 800";
const CJK_FACES: Face[] = ["kr", "jp", "tc"].flatMap((lang) =>
  ["400", "500"].map((weight) => ({
    family: `Noto Serif ${lang.toUpperCase()}`,
    file: `noto-serif-${lang}-${weight}.woff2`,
    weight,
  })),
);

const FACES: Face[] = [
  { family: "Instrument Serif", file: "instrument_serif_regular.ttf" },
  { family: "Instrument Serif", file: "instrument_serif_italic.ttf", style: "italic" },
  { family: "Newsreader", file: "newsreader_variable.ttf", weight: VARIABLE_WEIGHTS },
  { family: "Newsreader", file: "newsreader_italic_variable.ttf", weight: VARIABLE_WEIGHTS, style: "italic" },
  { family: "JetBrains Mono", file: "jetbrains_mono_variable.ttf", weight: "100 800" },
  ...CJK_FACES,
];

export function loadAllFonts(): Promise<unknown> {
  return Promise.all(
    FACES.map((f) =>
      loadFont({ family: f.family, url: dir(f.file), weight: f.weight ?? "400", style: f.style ?? "normal" }),
    ),
  );
}
