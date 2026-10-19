import React from "react";
import { AbsoluteFill, Easing, useCurrentFrame } from "remotion";
import { AmbientGlow, Hairline } from "../components/Backdrop";
import { Lockup } from "../components/Lockup";
import { LogoMark, MARK_ACTIVE_INDEX, MARK_BARS, MARK_SIZE } from "../components/LogoMark";
import { eyebrow } from "../components/NowPlayingScreen";
import { RevealWords, Rise } from "../components/Type";
import { TRACKS } from "../data/tracks";
import { beat, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT } from "../theme/tokens";
import { END_SCROLL_BEAT } from "../timeline";

const CORAL = ACCENTS.coral.accent;
const UNIT = 4.6;
const WORDMARK_SIZE = 190;
/** Wordmark width ≈ 3.1 em in Instrument Serif; centers the lockup. */
const LOCKUP_WIDTH = MARK_SIZE.w * UNIT + 2 * 0.28 * WORDMARK_SIZE + 1 + 3.1 * WORDMARK_SIZE;
const LOCKUP_LEFT = (1920 - LOCKUP_WIDTH) / 2;
const LOCKUP_TOP = 300;
const RULE_HEIGHT = MARK_SIZE.h * UNIT * 1.05;
const MARK_TOP = LOCKUP_TOP + (RULE_HEIGHT - MARK_SIZE.h * UNIT) / 2;
const LAND = 26;
const SWEEP_AT = beat(END_SCROLL_BEAT);
const OUTRO_AT = beat(8);
const ACTIVE = MARK_BARS[MARK_ACTIVE_INDEX];
const REVEAL_ORDER = [2, 1, 0, 1, 2];
const CTA = ["ANDROID", "FREE", "NO LOGIN"];

/** Frames 0-26: the full-width bar from the build lands as the mark's lit bar. */
const LandingBar: React.FC<{ frame: number }> = ({ frame }) => {
  const p = progress(frame, 0, LAND, EASE.inOut);
  const from = { x: 0, y: 528, w: 1920, h: 24 };
  const to = { x: LOCKUP_LEFT + ACTIVE.x * UNIT, y: MARK_TOP + ACTIVE.y * UNIT, w: ACTIVE.w * UNIT, h: ACTIVE.h * UNIT };
  const lerp = (a: number, b: number) => a + (b - a) * p;
  const h = lerp(from.h, to.h);
  return <div style={{ position: "absolute", left: lerp(from.x, to.x), top: lerp(from.y, to.y), width: lerp(from.w, to.w), height: h, borderRadius: h / 2, background: CORAL, boxShadow: `0 0 ${80 - 50 * p}px ${CORAL}aa` }} />;
};

/** The lit bar "sings": a highlight sweeps across it, like the karaoke line. */
const SweepHighlight: React.FC<{ frame: number }> = ({ frame }) => {
  const p = progress(frame, SWEEP_AT, SWEEP_AT + beat(1.5), Easing.inOut(Easing.quad));
  if (p <= 0 || p >= 1) return null;
  const x = LOCKUP_LEFT + ACTIVE.x * UNIT;
  return (
    <div style={{ position: "absolute", left: x, top: MARK_TOP + ACTIVE.y * UNIT, width: ACTIVE.w * UNIT, height: ACTIVE.h * UNIT, borderRadius: (ACTIVE.h * UNIT) / 2, overflow: "hidden" }}>
      <div style={{ position: "absolute", top: 0, bottom: 0, left: `${p * 140 - 40}%`, width: "40%", background: "linear-gradient(90deg, transparent, rgba(255,255,255,0.75), transparent)" }} />
    </div>
  );
};

/** Outro: everything but the lit bar fades, then the bar closes to a point. */
function outro(frame: number) {
  return {
    fade: progress(frame, OUTRO_AT, OUTRO_AT + 30, EASE.inOut),
    close: progress(frame, OUTRO_AT + 34, beat(10) - 4, EASE.in),
  };
}

export const S09EndCard: React.FC = () => {
  const frame = useCurrentFrame();
  const { fade, close } = outro(frame);
  const reveal = MARK_BARS.map((_, i) => (i === MARK_ACTIVE_INDEX ? (frame >= LAND ? 1 : 0) : springAt(frame, LAND - 8 + REVEAL_ORDER[i] * 5)));
  const glow = frame >= SWEEP_AT ? Math.exp(-(frame - SWEEP_AT) / 30) : 0;
  const mark = <LogoMark unit={UNIT} accent={CORAL} reveal={reveal} glow={0.4 + glow} />;
  // The lit bar drifts to screen center as the rest fades: the video ends
  // where its first frame began.
  const w = ACTIVE.w * UNIT;
  const h = ACTIVE.h * UNIT;
  const lit = {
    x: LOCKUP_LEFT + ACTIVE.x * UNIT + (960 - w / 2 - LOCKUP_LEFT - ACTIVE.x * UNIT) * fade,
    y: MARK_TOP + ACTIVE.y * UNIT + (540 - h / 2 - MARK_TOP - ACTIVE.y * UNIT) * fade,
    w,
    h,
  };
  return (
    <AbsoluteFill style={{ background: COLOR.bg0 }}>
      <AbsoluteFill style={{ opacity: 1 - fade }}>
        <AmbientGlow gradient={TRACKS.coral.coverGradient} opacity={0.22} />
        <div style={{ position: "absolute", left: LOCKUP_LEFT, top: LOCKUP_TOP }}>
          <Lockup mark={mark} markHeight={MARK_SIZE.h * UNIT} start={LAND - 4} size={WORDMARK_SIZE} />
        </div>
        <RevealWords text="Code. Vibe. Sing along." start={beat(2.5)} stagger={5} style={{ position: "absolute", top: 650, width: "100%", justifyContent: "center", fontFamily: FONT.display, fontSize: 84, color: COLOR.ink2 }} wordStyle={(i) => (i >= 2 ? { fontStyle: "italic", color: CORAL } : {})} />
        <div style={{ position: "absolute", top: 820, width: "100%", display: "flex", justifyContent: "center", alignItems: "center", gap: 28 }}>
          {CTA.map((label, i) => (
            <React.Fragment key={label}>
              {i > 0 && <div style={{ position: "relative", width: 1, height: 26 }}><Hairline length={26} vertical progress={progress(frame, beat(4) + i * 4, beat(4) + i * 4 + 12)} color={COLOR.rule} /></div>}
              <Rise start={beat(4) + i * 4} distance={16}>
                <span style={{ ...eyebrow, fontSize: 24, color: COLOR.ink1 }}>{label}</span>
              </Rise>
            </React.Fragment>
          ))}
        </div>
        <div style={{ ...eyebrow, position: "absolute", bottom: 48, width: "100%", textAlign: "center", fontSize: 14, color: COLOR.ink4, opacity: progress(frame, beat(4.5), beat(5.5)) }}>
          NOT AFFILIATED WITH SPOTIFY AB
        </div>
      </AbsoluteFill>
      {frame < LAND && <LandingBar frame={frame} />}
      <SweepHighlight frame={frame} />
      {fade > 0 && (
        <div style={{ position: "absolute", left: lit.x + (lit.w / 2) * close, top: lit.y, width: lit.w * (1 - close), height: lit.h, borderRadius: lit.h / 2, background: CORAL, boxShadow: `0 0 ${40 + 40 * fade}px ${CORAL}`, opacity: 1 - close * 0.3 }} />
      )}
    </AbsoluteFill>
  );
};
