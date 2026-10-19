import React from "react";
import { Composition, cancelRender, continueRender, delayRender } from "remotion";
import { Promo, type PromoProps } from "./Promo";
import { FICTIONAL_CATALOG } from "./data/catalog";
import { loadCatalog } from "./data/useCatalog";
import { loadAllFonts } from "./theme/fonts";
import { DURATION_FRAMES, FPS, HEIGHT, WIDTH } from "./timeline";

const fontsHandle = delayRender("Loading Spotivibe fonts");
loadAllFonts()
  .then(() => continueRender(fontsHandle))
  // cancelRender fails the render at once instead of hanging until timeout.
  .catch((err: unknown) => cancelRender(new Error(`Root: font loading failed, run "npm run assets" first (${String(err)})`)));

const DEFAULT_PROPS: PromoProps = { catalog: FICTIONAL_CATALOG };

export const Root: React.FC = () => (
  <Composition
    id="SpotivibePromo"
    component={Promo}
    durationInFrames={DURATION_FRAMES}
    fps={FPS}
    width={WIDTH}
    height={HEIGHT}
    defaultProps={DEFAULT_PROPS}
    calculateMetadata={async ({ props }) => ({ props: { ...props, catalog: await loadCatalog() } })}
  />
);
