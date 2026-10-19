import React from "react";
import { AbsoluteFill, interpolateColors, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { Chapter } from "../components/Chapter";
import { NowPlayingScreen, eyebrow } from "../components/NowPlayingScreen";
import { PhoneFrame } from "../components/Phone";
import { RevealWords, Rise } from "../components/Type";
import { TRACKS, type Track } from "../data/tracks";
import { SPRING_SOFT, beat, lyricClock, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT, PHONE } from "../theme/tokens";

const CYCLE = [TRACKS.coral, TRACKS.violet, TRACKS.jade, TRACKS.coral];
const SWAP_FRAMES = beat(2);
const WIPE_FRAMES = 24;
const SPREAD_AT = beat(8);
/** Album cover center inside the phone header, origin of the wipe. */
const COVER_CENTER = "40px 68px";
const PHONE_W = PHONE.width + PHONE.bezel * 2;
const PHONE_H = PHONE.height + PHONE.bezel * 2;
const SWATCHES = [TRACKS.coral, TRACKS.violet, TRACKS.jade];

const accentOf = (t: Track) => ACCENTS[t.accent];

/** Accent color at a frame of the cover cycle, interpolated across wipes. */
function cycleAccent(frame: number): string {
  const stops = CYCLE.flatMap((_, i) => [i * SWAP_FRAMES, i * SWAP_FRAMES + WIPE_FRAMES]);
  const colors = CYCLE.flatMap((t, i) => [accentOf(CYCLE[Math.max(0, i - 1)]).accent, accentOf(t).accent]);
  return interpolateColors(frame, stops, colors);
}

/** Lyrics advance one line every two beats, counted from `origin`. */
const Screen: React.FC<{ track: Track; frame: number; origin: number; base: number }> = ({ track, frame, origin, base }) => {
  const { active, sweep } = lyricClock(frame, { origin, base, lineFrames: SWAP_FRAMES });
  return <NowPlayingScreen track={track} accent={accentOf(track)} active={Math.min(active, track.lyrics.length - 1)} sweep={sweep} />;
};

/** Part A: one phone; every two beats the next cover wipes in from its art. */
const CyclingPhone: React.FC<{ frame: number }> = ({ frame }) => {
  const i = Math.min(CYCLE.length - 1, Math.floor(frame / SWAP_FRAMES));
  const wipe = progress(frame - i * SWAP_FRAMES, 0, WIPE_FRAMES, EASE.inOut);
  const prev = CYCLE[Math.max(0, i - 1)];
  return (
    <PhoneFrame>
      <Screen track={i === 0 ? CYCLE[0] : prev} frame={frame} origin={Math.max(0, i - 1) * SWAP_FRAMES} base={2} />
      <div style={{ position: "absolute", inset: 0, clipPath: `circle(${i === 0 ? 1200 : wipe * 1200}px at ${COVER_CENTER})` }}>
        <Screen track={CYCLE[i]} frame={frame} origin={i * SWAP_FRAMES} base={2} />
      </div>
    </PhoneFrame>
  );
};

const Swatches: React.FC<{ frame: number }> = ({ frame }) => {
  const current = CYCLE[Math.min(CYCLE.length - 1, Math.floor(frame / SWAP_FRAMES))];
  return (
    <div style={{ display: "flex", gap: 44, position: "absolute", left: 1130, top: 760 }}>
      {SWATCHES.map((t, i) => {
        const isOn = t.accent === current.accent;
        const a = accentOf(t).accent;
        return (
          <Rise key={t.accent} start={30 + i * 5}>
            <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
              <div style={{ width: 54, height: 54, borderRadius: "50%", background: a, boxShadow: isOn ? `0 0 0 6px ${COLOR.bg0}, 0 0 0 8px ${a}, 0 0 40px ${a}` : undefined, transform: `scale(${isOn ? 1.1 : 0.9})` }} />
              <div style={{ ...eyebrow, fontSize: 16, color: isOn ? COLOR.ink1 : COLOR.ink4, lineHeight: 1.5 }}>
                {t.accent}
                <br />
                {a}
              </div>
            </div>
          </Rise>
        );
      })}
    </div>
  );
};

const SPREAD = [
  { track: TRACKS.violet, side: -1 },
  { track: TRACKS.jade, side: 1 },
  { track: TRACKS.coral, side: 0 },
];

/** Part B: violet and jade phones fan out from behind the coral one. */
const Spread: React.FC<{ frame: number }> = ({ frame }) => {
  const local = frame - SPREAD_AT;
  const s = springAt(local, 6, SPRING_SOFT);
  return (
    <AbsoluteFill style={{ perspective: 2400 }}>
      {SPREAD.map(({ track, side }) => {
        const x = 960 + side * 560 * s;
        const scale = side === 0 ? 0.84 : 0.76;
        const rot = -side * 24 * s;
        return (
          <div key={track.accent} style={{ position: "absolute", left: x - PHONE_W / 2, top: 575 - PHONE_H / 2, transform: `scale(${scale}) rotateY(${rot}deg) translateZ(${side === 0 ? 0 : -160 * s}px)`, zIndex: side === 0 ? 2 : 1 }}>
            <PhoneFrame>
              <Screen track={track} frame={frame} origin={SPREAD_AT} base={3 + side} />
            </PhoneFrame>
            <div style={{ ...eyebrow, position: "absolute", left: 0, right: 0, top: PHONE_H + 40, textAlign: "center", fontSize: 22, color: accentOf(track).accent, opacity: progress(local, 20, 40) }}>
              {track.lang} · {track.romanSystem}
            </div>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

export const S05Accent: React.FC = () => {
  const frame = useCurrentFrame();
  const accent = cycleAccent(frame);
  const toCenter = progress(frame, SPREAD_AT - 16, SPREAD_AT + 6, EASE.inOut);
  const current = CYCLE[Math.min(CYCLE.length - 1, Math.floor(frame / SWAP_FRAMES))];
  const isSpread = frame >= SPREAD_AT;
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, overflow: "hidden" }}>
      <AmbientGlow gradient={current.coverGradient} opacity={0.3} />
      {!isSpread && (
        <div style={{ position: "absolute", left: 560 + 400 * toCenter - PHONE_W / 2, top: 540 - PHONE_H / 2, transform: `scale(${1.02 - 0.18 * toCenter})` }}>
          <CyclingPhone frame={frame} />
        </div>
      )}
      {!isSpread && <Chapter copy={{ number: "04", label: "DYNAMIC ACCENT", headline: "Colors that follow the cover.", accentFrom: 3, sub: "PALETTE API · LOCKED CHROMA + LIGHTNESS" }} start={6} end={SPREAD_AT - 10} accent={accent} size={100} width={700} style={{ left: 1130, top: 250 }} />}
      {!isSpread && frame < SPREAD_AT - 10 && <Swatches frame={frame} />}
      {isSpread && <Spread frame={frame} />}
      {isSpread && (
        <RevealWords
          text="Korean. Japanese. Mandarin."
          start={SPREAD_AT + 10}
          stagger={6}
          style={{ position: "absolute", top: 34, left: 0, right: 0, justifyContent: "center", fontFamily: FONT.display, fontSize: 76, color: COLOR.ink1, letterSpacing: "-0.01em" }}
          wordStyle={(i) => ({ fontStyle: "italic", color: accentOf(SWATCHES[i]).accent })}
        />
      )}
    </AbsoluteFill>
  );
};
