import React from "react";
import { AbsoluteFill, Easing, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { Chapter, type ChapterCopy } from "../components/Chapter";
import { Icon } from "../components/Icon";
import { NowPlayingScreen, eyebrow } from "../components/NowPlayingScreen";
import { PhoneFrame } from "../components/Phone";
import { PLAYERS } from "../data/brand";
import { ROLES, type AccentSet } from "../data/catalog";
import { useTrack } from "../data/useCatalog";
import { SPRING_SOFT, beat, beatPulse, progress, springAt } from "../lib/motion";
import { COLOR, EASE, FONT, PHONE } from "../theme/tokens";

const LINE_FRAMES = beat(2);
const SCROLL_FRAMES = 22;
const FIRST_LINE = 1;
/** Holds on "Every line, every line" for the push. */
const LAST_STEP = 6;
const PUSH_START = beat(14);
const PUSH_END = beat(16.5);
const PLAYER_SWAP = beat(2);
const NAME_SLIDE_FRAMES = 10;

const CHAPTERS: { copy: ChapterCopy; start: number; end: number }[] = [
  { copy: { number: "01", label: "ANY PLAYER", headline: "Follows whatever's playing.", accentFrom: 1, sub: "SPOTIFY · YOUTUBE MUSIC · ANY MEDIA APP" }, start: 16, end: beat(7.9) },
  { copy: { number: "02", label: "NO LOGIN", headline: "One permission. No account.", accentFrom: 2, sub: "NOTIFICATION ACCESS · NOTHING ELSE" }, start: beat(8), end: beat(13.9) },
  { copy: { number: "03", label: "IN SYNC", headline: "Every line lands on the beat.", accentFrom: 3, sub: "WORD-LEVEL TIMING · KARAOKE SWEEP" }, start: beat(14), end: beat(20) },
];

/** Active lyric index and karaoke sweep for a frame of this scene. */
function heroLyricClock(frame: number): { active: number; sweep: number } {
  const step = Math.min(LAST_STEP, Math.floor(frame / LINE_FRAMES));
  const local = frame - step * LINE_FRAMES;
  const active = FIRST_LINE + step - 1 + progress(local, 0, SCROLL_FRAMES);
  const isHolding = step === LAST_STEP;
  const sweep = isHolding
    ? progress(frame, LAST_STEP * LINE_FRAMES + 20, PUSH_END + beat(2), Easing.linear)
    : progress(local, 4, LINE_FRAMES - 4, Easing.linear);
  return { active, sweep };
}

const floatingCard: React.CSSProperties = {
  position: "absolute",
  background: "rgba(20,21,27,0.95)",
  border: `1px solid ${COLOR.rule}`,
  boxShadow: "0 24px 60px rgba(0,0,0,0.55)",
  zIndex: 5,
};

/** Chapter 01: which player Spotivibe is following, swapping every two beats. */
const SourcePill: React.FC<{ frame: number; end: number; accent: AccentSet }> = ({ frame, end, accent }) => {
  const show = springAt(frame, 20) * (1 - progress(frame, end - 12, end, EASE.in));
  const index = Math.floor(frame / PLAYER_SWAP) % PLAYERS.length;
  const slide = progress(frame % PLAYER_SWAP, 0, NAME_SLIDE_FRAMES);
  return (
    <div style={{ ...floatingCard, left: 700, top: 170, borderRadius: 999, padding: "16px 26px", display: "flex", alignItems: "center", gap: 14, opacity: show, transform: `translateX(${(1 - show) * 60}px)` }}>
      <span style={{ width: 12, height: 12, borderRadius: "50%", background: accent.accent, boxShadow: `0 0 ${16 * beatPulse(frame)}px ${accent.accent}` }} />
      <span style={{ ...eyebrow, fontSize: 16, color: COLOR.ink3 }}>NOW FOLLOWING</span>
      <span style={{ ...eyebrow, fontSize: 20, color: COLOR.ink1, display: "inline-block", minWidth: 230, transform: `translateY(${(1 - slide) * 18}px)`, opacity: slide }}>{PLAYERS[index]}</span>
    </div>
  );
};

/** Chapter 02: the one permission granted, the login that never happens. */
const AccessChip: React.FC<{ frame: number; start: number; end: number; accent: AccentSet }> = ({ frame, start, end, accent }) => {
  const show = springAt(frame, start + 10) * (1 - progress(frame, end - 12, end, EASE.in));
  const row = (icon: "check" | "close", label: string, state: string, isOn: boolean) => (
    <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 0" }}>
      <Icon name={icon} size={22} stroke={1.6} color={isOn ? accent.accent : COLOR.ink4} />
      <span style={{ ...eyebrow, fontSize: 18, color: isOn ? COLOR.ink1 : COLOR.ink4, textDecoration: isOn ? undefined : "line-through", flex: 1 }}>{label}</span>
      <span style={{ ...eyebrow, fontSize: 16, color: isOn ? accent.accent : COLOR.ink4 }}>{state}</span>
    </div>
  );
  return (
    <div style={{ ...floatingCard, left: 690, top: 700, width: 470, padding: "10px 24px", opacity: show, transform: `translateY(${(1 - show) * 40}px)` }}>
      {row("check", "NOTIFICATION ACCESS", "GRANTED", true)}
      <div style={{ height: 1, background: COLOR.ruleSoft }} />
      {row("close", "ACCOUNT LOGIN", "NOT NEEDED", false)}
    </div>
  );
};

function phoneTransform(frame: number): string {
  const s = springAt(frame, 0, SPRING_SOFT);
  const push = progress(frame, PUSH_START, PUSH_END, EASE.inOut);
  const x = 600 - 40 * push;
  const y = (1 - s) * 1100 + 220 * push;
  const scale = 1.08 + 1.3 * push;
  const rotY = -14 * (1 - push) - 30 * (1 - s);
  const rotX = 4 * (1 - push) + 40 * (1 - s);
  const base = `translate(${x - (PHONE.width + PHONE.bezel * 2) / 2}px, ${540 - (PHONE.height + PHONE.bezel * 2) / 2 + y}px) scale(${scale})`;
  // Flat once the push lands: a 3D layer is rasterized at base scale and
  // would look soft at 2.4x; a 2D transform re-rasterizes crisp.
  return push >= 1 ? base : `${base} rotateY(${rotY}deg) rotateX(${rotX}deg)`;
}

export const S04Hero: React.FC = () => {
  const frame = useCurrentFrame();
  const track = useTrack(ROLES.hero);
  const { active, sweep } = heroLyricClock(frame);
  const push = progress(frame, PUSH_START, PUSH_END, EASE.inOut);
  const chapterIndex = CHAPTERS.findIndex((c) => frame >= c.start && frame < c.end);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, overflow: "hidden" }}>
      <AmbientGlow gradient={track.cover} opacity={0.32} />
      <div style={{ position: "absolute", right: 60, bottom: -120, fontFamily: FONT.display, fontSize: 620, color: COLOR.ink1, opacity: 0.04, lineHeight: 1 }}>
        {CHAPTERS[Math.max(0, chapterIndex)].copy.number}
      </div>
      <AbsoluteFill style={{ perspective: push >= 1 ? undefined : 2200 }}>
        <div style={{ position: "absolute", left: 0, top: 0, transform: phoneTransform(frame), transformOrigin: "50% 50%" }}>
          <PhoneFrame glare={progress(frame, 0, beat(20), Easing.linear)}>
            <NowPlayingScreen track={track} accent={track.accent} active={active} sweep={sweep} roman={0} seek={0.32 + frame / 4000} />
          </PhoneFrame>
        </div>
      </AbsoluteFill>
      {frame < CHAPTERS[0].end && <SourcePill frame={frame} end={CHAPTERS[0].end} accent={track.accent} />}
      {frame >= CHAPTERS[1].start && frame < CHAPTERS[1].end && <AccessChip frame={frame} start={CHAPTERS[1].start} end={CHAPTERS[1].end} accent={track.accent} />}
      <AbsoluteFill style={{ background: "linear-gradient(90deg, transparent 52%, rgba(12,12,16,0.85) 64%)", opacity: push }} />
      {CHAPTERS.map((c) => (
        <Chapter key={c.copy.number} copy={c.copy} start={c.start} end={c.end} accent={track.accent.accent} size={104} width={700} style={{ left: 1130, top: 330 }} />
      ))}
    </AbsoluteFill>
  );
};
