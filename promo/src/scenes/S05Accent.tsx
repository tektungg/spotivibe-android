import React from "react";
import { AbsoluteFill, interpolateColors, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { Chapter } from "../components/Chapter";
import { NowPlayingScreen, eyebrow } from "../components/NowPlayingScreen";
import { PhoneFrame } from "../components/Phone";
import { RevealWords, Rise } from "../components/Type";
import { ROLES, type Track } from "../data/catalog";
import { useCatalog } from "../data/useCatalog";
import { SPRING_SOFT, beat, lyricClock, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT, PHONE } from "../theme/tokens";

const SWAP_FRAMES = beat(2);
const WIPE_FRAMES = 24;
const CONTRAST_AT = beat(8);
const SPREAD_AT = beat(12);
/** Toggle flips, then the screen follows: AmbientBg.kt drops to solid bg0. */
const TOGGLE_AT = CONTRAST_AT + 16;
const CONTRAST_FADE = { start: TOGGLE_AT + 4, end: TOGGLE_AT + 22 };
/** Album cover center inside the phone header, origin of the wipe. */
const COVER_CENTER = "40px 68px";
const PHONE_W = PHONE.width + PHONE.bezel * 2;
const PHONE_H = PHONE.height + PHONE.bezel * 2;
const COPY_LEFT = 1130;
/** The player each spread phone is "following", left to right. */
const SPREAD_PLAYERS = ["YOUTUBE MUSIC", "SPOTIFY", "ANY PLAYER"] as const;
const SPREAD_SIDES = [-1, 0, 1] as const;

const useCycle = (): Track[] => {
  const { tracks } = useCatalog();
  return ROLES.cycle.map((id) => tracks[id]);
};

const cycleIndex = (frame: number, length: number) => Math.min(length - 1, Math.floor(frame / SWAP_FRAMES));

/** Accent color at a frame of the cover cycle, interpolated across wipes. */
function cycleAccent(frame: number, cycle: Track[]): string {
  const stops = cycle.flatMap((_, i) => [i * SWAP_FRAMES, i * SWAP_FRAMES + WIPE_FRAMES]);
  const colors = cycle.flatMap((t, i) => [cycle[Math.max(0, i - 1)].accent.accent, t.accent.accent]);
  return interpolateColors(frame, stops, colors);
}

/** Lyrics advance one line every two beats, counted from `origin`. */
const Screen: React.FC<{ track: Track; frame: number; origin: number; base: number; highContrast?: number }> = ({ track, frame, origin, base, highContrast = 0 }) => {
  const { active, sweep } = lyricClock(frame, { origin, base, lineFrames: SWAP_FRAMES });
  return <NowPlayingScreen track={track} accent={track.accent} active={Math.min(active, track.lyrics.length - 1)} sweep={sweep} highContrast={highContrast} />;
};

/** One phone; every two beats the next cover wipes in from its art. Then high contrast. */
const CyclingPhone: React.FC<{ frame: number; cycle: Track[] }> = ({ frame, cycle }) => {
  const i = cycleIndex(frame, cycle.length);
  const wipe = progress(frame - i * SWAP_FRAMES, 0, WIPE_FRAMES, EASE.inOut);
  const hc = progress(frame, CONTRAST_FADE.start, CONTRAST_FADE.end, EASE.inOut);
  return (
    <PhoneFrame>
      <Screen track={cycle[Math.max(0, i - 1)]} frame={frame} origin={Math.max(0, i - 1) * SWAP_FRAMES} base={2} />
      <div style={{ position: "absolute", inset: 0, clipPath: `circle(${i === 0 ? 1200 : wipe * 1200}px at ${COVER_CENTER})` }}>
        <Screen track={cycle[i]} frame={frame} origin={i * SWAP_FRAMES} base={2} highContrast={hc} />
      </div>
    </PhoneFrame>
  );
};

/** Each cycle song with the accent the app locks from its cover. */
const Swatches: React.FC<{ frame: number; cycle: Track[] }> = ({ frame, cycle }) => {
  const current = cycle[cycleIndex(frame, cycle.length)];
  return (
    <div style={{ position: "absolute", left: COPY_LEFT, top: 700, display: "grid", gridTemplateColumns: "repeat(2, 300px)", rowGap: 22 }}>
      {cycle.map((t, i) => {
        const isOn = t.id === current.id;
        const a = t.accent.accent;
        return (
          <Rise key={t.id} start={30 + i * 5}>
            <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
              <div style={{ width: 44, height: 44, borderRadius: "50%", background: a, flexShrink: 0, boxShadow: isOn ? `0 0 0 5px ${COLOR.bg0}, 0 0 0 7px ${a}, 0 0 36px ${a}` : undefined, transform: `scale(${isOn ? 1.1 : 0.9})` }} />
              <div style={{ ...eyebrow, fontSize: 14, color: isOn ? COLOR.ink1 : COLOR.ink4, lineHeight: 1.5, whiteSpace: "nowrap" }}>
                {t.artist}
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

/** The High contrast switch from Settings, flipping on at TOGGLE_AT. */
const ContrastToggle: React.FC<{ frame: number; accent: string }> = ({ frame, accent }) => {
  const on = progress(frame, TOGGLE_AT, TOGGLE_AT + 8, EASE.out);
  return (
    <Rise start={CONTRAST_AT + 6} style={{ position: "absolute", left: COPY_LEFT, top: 720, display: "flex", alignItems: "center", gap: 22, padding: "18px 24px", border: `1px solid ${COLOR.rule}`, background: "rgba(20,21,27,0.9)" }}>
      <span style={{ ...eyebrow, fontSize: 18, color: COLOR.ink1 }}>HIGH CONTRAST</span>
      <div style={{ position: "relative", width: 62, height: 32, borderRadius: 16, background: interpolateColors(on, [0, 1], [COLOR.rule, accent]) }}>
        <div style={{ position: "absolute", top: 4, left: 4 + 30 * on, width: 24, height: 24, borderRadius: "50%", background: COLOR.ink1 }} />
      </div>
    </Rise>
  );
};

/** Three phones fan out: three languages, three players. */
const Spread: React.FC<{ frame: number }> = ({ frame }) => {
  const { tracks } = useCatalog();
  const local = frame - SPREAD_AT;
  const s = springAt(local, 6, SPRING_SOFT);
  return (
    <AbsoluteFill style={{ perspective: 2400 }}>
      {ROLES.spread.map((id, i) => {
        const track = tracks[id];
        const side = SPREAD_SIDES[i];
        const scale = side === 0 ? 0.84 : 0.76;
        return (
          <div key={id} style={{ position: "absolute", left: 960 + side * 560 * s - PHONE_W / 2, top: 575 - PHONE_H / 2, transform: `scale(${scale}) rotateY(${-side * 24 * s}deg) translateZ(${side === 0 ? 0 : -160 * s}px)`, zIndex: side === 0 ? 2 : 1 }}>
            <PhoneFrame>
              <Screen track={track} frame={frame} origin={SPREAD_AT} base={3 + side} />
            </PhoneFrame>
            <div style={{ ...eyebrow, position: "absolute", left: -200, right: -200, top: PHONE_H + 40, textAlign: "center", whiteSpace: "nowrap", fontSize: 22, color: track.accent.accent, opacity: progress(local, 20, 40) }}>
              VIA {SPREAD_PLAYERS[i]} · {track.languageName}
            </div>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

export const S05Accent: React.FC = () => {
  const frame = useCurrentFrame();
  const cycle = useCycle();
  const accent = cycleAccent(frame, cycle);
  const current = cycle[cycleIndex(frame, cycle.length)];
  const hc = progress(frame, CONTRAST_FADE.start, CONTRAST_FADE.end, EASE.inOut);
  const toCenter = progress(frame, SPREAD_AT - 16, SPREAD_AT + 6, EASE.inOut);
  const isSpread = frame >= SPREAD_AT;
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, overflow: "hidden" }}>
      <AmbientGlow gradient={current.cover} opacity={0.3 * (1 - 0.8 * hc)} />
      {!isSpread && (
        <div style={{ position: "absolute", left: 560 + 400 * toCenter - PHONE_W / 2, top: 540 - PHONE_H / 2, transform: `scale(${1.02 - 0.18 * toCenter})` }}>
          <CyclingPhone frame={frame} cycle={cycle} />
        </div>
      )}
      <Chapter copy={{ number: "04", label: "DYNAMIC ACCENT", headline: "Colors that follow the cover.", accentFrom: 3, sub: "PALETTE API · LOCKED CHROMA + LIGHTNESS" }} start={6} end={CONTRAST_AT - 4} accent={accent} size={100} width={700} style={{ left: COPY_LEFT, top: 250 }} />
      {frame < CONTRAST_AT - 4 && <Swatches frame={frame} cycle={cycle} />}
      <Chapter copy={{ number: "05", label: "HIGH CONTRAST", headline: "Readable in direct sunlight.", accentFrom: 2, sub: "SOLID BACKGROUND · NO BLUR · SHARP LINES" }} start={CONTRAST_AT} end={SPREAD_AT - 10} accent={accent} size={100} width={700} style={{ left: COPY_LEFT, top: 250 }} />
      {frame >= CONTRAST_AT && frame < SPREAD_AT - 10 && <ContrastToggle frame={frame} accent={accent} />}
      {isSpread && <Spread frame={frame} />}
      {isSpread && (
        <RevealWords
          text="Any player. Any language."
          start={SPREAD_AT + 6}
          stagger={5}
          style={{ position: "absolute", top: 34, left: 0, right: 0, justifyContent: "center", fontFamily: FONT.display, fontSize: 76, color: COLOR.ink1, letterSpacing: "-0.01em" }}
          wordStyle={(i) => (i % 2 === 1 ? { fontStyle: "italic", color: ACCENTS.coral.accent } : {})}
        />
      )}
    </AbsoluteFill>
  );
};
