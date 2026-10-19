import React from "react";
import { AbsoluteFill, Audio, Sequence, staticFile } from "remotion";
import { Grain, Vignette } from "./components/Backdrop";
import { S01ColdOpen } from "./scenes/S01ColdOpen";
import { S02Answer } from "./scenes/S02Answer";
import { S03LogoMorph } from "./scenes/S03LogoMorph";
import { S04Hero } from "./scenes/S04Hero";
import { S05Accent } from "./scenes/S05Accent";
import { S06Everywhere } from "./scenes/S06Everywhere";
import { S07Board } from "./scenes/S07Board";
import { S08Build } from "./scenes/S08Build";
import { S09EndCard } from "./scenes/S09EndCard";
import { COLOR } from "./theme/tokens";
import { timedScenes, type SceneId } from "./timeline";

/** Scene id -> component. Typed as a full Record so a new id cannot be missed. */
export const SCENE_COMPONENTS: Record<SceneId, React.FC> = {
  coldOpen: S01ColdOpen,
  answer: S02Answer,
  logoMorph: S03LogoMorph,
  hero: S04Hero,
  accent: S05Accent,
  everywhere: S06Everywhere,
  board: S07Board,
  build: S08Build,
  endCard: S09EndCard,
};

/** The full 60-second promo: scenes cut on beats, one shared score. */
export const Promo: React.FC = () => (
  <AbsoluteFill style={{ background: COLOR.bg0 }}>
    {timedScenes().map((scene) => {
      const Scene = SCENE_COMPONENTS[scene.id];
      return (
        <Sequence key={scene.id} name={scene.title} from={scene.startFrame} durationInFrames={scene.durationFrames}>
          <Scene />
        </Sequence>
      );
    })}
    <Vignette strength={0.55} />
    <Grain />
    <Audio src={staticFile("generated/score.wav")} />
  </AbsoluteFill>
);
