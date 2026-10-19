import React, { useMemo } from "react";
import { AbsoluteFill } from "remotion";
import { AmbientGlow, Grain, Halftone, Vignette } from "../components/Backdrop";
import { Lockup } from "../components/Lockup";
import { LogoMark, MARK_SIZE } from "../components/LogoMark";
import { NowPlayingScreen, eyebrow } from "../components/NowPlayingScreen";
import { PhoneFrame } from "../components/Phone";
import { TAGLINE } from "../data/brand";
import { FICTIONAL_CATALOG, ROLES, type Track } from "../data/catalog";
import { CatalogProvider, resolveCatalog } from "../data/useCatalog";
import { ACCENTS, COLOR, FONT } from "../theme/tokens";

/**
 * GitHub social preview (repo Settings -> Social preview): 1280 x 640, the
 * size GitHub recommends. Public image, so it always uses the fictional
 * catalog: no copyrighted lyrics or cover art.
 *
 * Everything that matters sits inside the centre 1200 x 630, so platforms
 * that crop to 1.91:1 (X, LinkedIn, Facebook) lose nothing.
 */
export const SOCIAL_PREVIEW = { width: 1280, height: 640 } as const;

const CORAL = ACCENTS.coral.accent;
/** A still renders frame 0; starting every entrance this early shows it finished. */
const ALREADY_REVEALED = -600;
const SAFE_X = 80;
const MARK_UNIT = 1.6;
const PHONE_SCALE = 0.8;
const PHONE_STATE = { active: 3, sweep: 0.55, seek: 0.42 };
const REPO = "github.com/tektungg/spotivibe-android";

const TaglineBlock: React.FC = () => {
  const [first, second] = TAGLINE.split(". ");
  return (
    <div style={{ fontFamily: FONT.display, fontSize: 84, lineHeight: 0.98, letterSpacing: "-0.02em", color: COLOR.ink1 }}>
      {first}.
      <br />
      <span style={{ fontStyle: "italic", color: CORAL }}>{second}</span>
    </div>
  );
};

const Copy: React.FC = () => (
  <div style={{ position: "absolute", left: SAFE_X, top: 62, width: 720, display: "flex", flexDirection: "column", height: 520 }}>
    <Lockup mark={<LogoMark unit={MARK_UNIT} accent={CORAL} glow={0.5} />} markHeight={MARK_SIZE.h * MARK_UNIT} start={ALREADY_REVEALED} size={60} />
    <div style={{ marginTop: 54 }}>
      <TaglineBlock />
    </div>
    <div style={{ ...eyebrow, fontSize: 17, color: COLOR.ink2, marginTop: 30, lineHeight: 1.7 }}>
      SYNCED LYRICS FOR SPOTIFY · YOUTUBE MUSIC
      <br />
      AND ANY OTHER PLAYER ON ANDROID
    </div>
    <div style={{ marginTop: "auto", display: "flex", alignItems: "center", gap: 18 }}>
      {["ANDROID", "FREE", "NO LOGIN"].map((label) => (
        <span key={label} style={{ ...eyebrow, fontSize: 14, color: COLOR.ink1, border: `1px solid ${COLOR.rule}`, padding: "8px 12px", whiteSpace: "nowrap" }}>
          {label}
        </span>
      ))}
      <span style={{ ...eyebrow, fontSize: 13, color: COLOR.ink3, marginLeft: 8, textTransform: "none", letterSpacing: "0.04em", whiteSpace: "nowrap" }}>{REPO}</span>
    </div>
  </div>
);

const Phone: React.FC<{ hero: Track }> = ({ hero }) => (
  <AbsoluteFill style={{ perspective: 1800 }}>
    <div style={{ position: "absolute", left: 828, top: 70, transform: `scale(${PHONE_SCALE}) rotateY(-16deg) rotateX(4deg)`, transformOrigin: "0 0" }}>
      <PhoneFrame glare={0.4}>
        <NowPlayingScreen track={hero} accent={hero.accent} active={PHONE_STATE.active} sweep={PHONE_STATE.sweep} roman={0} seek={PHONE_STATE.seek} />
      </PhoneFrame>
    </div>
  </AbsoluteFill>
);

export const SocialPreview: React.FC = () => {
  const catalog = useMemo(() => resolveCatalog(FICTIONAL_CATALOG), []);
  const hero = catalog.tracks[ROLES.hero];
  return (
    <CatalogProvider catalog={catalog}>
      <AbsoluteFill style={{ background: COLOR.bg0, overflow: "hidden" }}>
        <AmbientGlow gradient={hero.cover} opacity={0.42} blur={90} />
        <Halftone size={4} alpha={0.35} />
        <Phone hero={hero} />
        <AbsoluteFill style={{ background: `linear-gradient(90deg, ${COLOR.bg0} 0%, ${COLOR.bg0}e6 45%, transparent 66%)` }} />
        <Copy />
        <div style={{ position: "absolute", left: SAFE_X, right: SAFE_X, bottom: 0, height: 6, background: CORAL, borderRadius: "3px 3px 0 0", boxShadow: `0 0 40px ${CORAL}` }} />
        <Vignette strength={0.45} />
        <Grain opacity={0.06} />
      </AbsoluteFill>
    </CatalogProvider>
  );
};

