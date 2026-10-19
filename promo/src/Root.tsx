import React from "react";
import { Composition, cancelRender, continueRender, delayRender } from "remotion";
import { Promo } from "./Promo";
import { loadAllFonts } from "./theme/fonts";
import { DURATION_FRAMES, FPS, HEIGHT, WIDTH } from "./timeline";

const fontsHandle = delayRender("Loading Spotivibe fonts");
loadAllFonts()
  .then(() => continueRender(fontsHandle))
  // cancelRender fails the render at once instead of hanging until timeout.
  .catch((err: unknown) => cancelRender(new Error(`Root: font loading failed, run "npm run assets" first (${String(err)})`)));

export const Root: React.FC = () => (
  <Composition id="SpotivibePromo" component={Promo} durationInFrames={DURATION_FRAMES} fps={FPS} width={WIDTH} height={HEIGHT} />
);
