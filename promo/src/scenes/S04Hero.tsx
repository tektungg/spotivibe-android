import React from "react";
import { AbsoluteFill, Easing, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { Chapter, type ChapterCopy } from "../components/Chapter";
import { NowPlayingScreen } from "../components/NowPlayingScreen";
import { PhoneFrame } from "../components/Phone";
import { TRACKS } from "../data/tracks";
import { SPRING_SOFT, beat, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT, PHONE } from "../theme/tokens";

const TRACK = TRACKS.coral;
const ACCENT = ACCENTS.coral;
const LINE_FRAMES = beat(2);
const SCROLL_FRAMES = 22;
const FIRST_LINE = 1;
const LAST_STEP = 6;
const ROMAN_AT = beat(8);
const PUSH_START = beat(14);
const PUSH_END = beat(16.5);
/** Rm button center inside the phone screen (header row, first icon). */
const RM_BUTTON = { x: 308, y: 68 };

const CHAPTERS: { copy: ChapterCopy; start: number; end: number }[] = [
  { copy: { number: "01", label: "SYNCED LYRICS", headline: "Every line lands on the beat.", accentFrom: 3, sub: "SPOTIFY · YT MUSIC · ANY PLAYER" }, start: 16, end: beat(7.9) },
  { copy: { number: "02", label: "ROMANIZATION", headline: "Read it. Sing it.", accentFrom: 2, sub: "ROMAJI · REVISED ROMANIZATION · PINYIN" }, start: beat(8), end: beat(13.9) },
  { copy: { number: "03", label: "KARAOKE", headline: "Every syllable lights up.", accentFrom: 2, sub: "SWEEP · LIFT · GLOW, PER SYLLABLE" }, start: beat(14), end: beat(20) },
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

const TapRipple: React.FC<{ frame: number; at: number }> = ({ frame, at }) => {
  const p = progress(frame, at, at + 24);
  if (frame < at || p >= 1) return null;
  return (
    <div style={{ position: "absolute", left: RM_BUTTON.x, top: RM_BUTTON.y, width: 0, height: 0, zIndex: 30 }}>
      <div style={{ position: "absolute", width: 90 * p, height: 90 * p, left: -45 * p, top: -45 * p, borderRadius: "50%", border: `2px solid ${ACCENT.accent}`, opacity: 1 - p }} />
      <div style={{ position: "absolute", width: 26, height: 26, left: -13, top: -13, borderRadius: "50%", background: "rgba(255,255,255,0.35)", opacity: 1 - p }} />
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
  const { active, sweep } = heroLyricClock(frame);
  const roman = progress(frame, ROMAN_AT, ROMAN_AT + 20);
  const push = progress(frame, PUSH_START, PUSH_END, EASE.inOut);
  const chapterIndex = CHAPTERS.findIndex((c) => frame >= c.start && frame < c.end);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, overflow: "hidden" }}>
      <AmbientGlow gradient={TRACK.coverGradient} opacity={0.32} />
      <div style={{ position: "absolute", right: 60, bottom: -120, fontFamily: FONT.display, fontSize: 620, color: COLOR.ink1, opacity: 0.04, lineHeight: 1 }}>
        {CHAPTERS[Math.max(0, chapterIndex)].copy.number}
      </div>
      <AbsoluteFill style={{ perspective: push >= 1 ? undefined : 2200 }}>
        <div style={{ position: "absolute", left: 0, top: 0, transform: phoneTransform(frame), transformOrigin: "50% 50%" }}>
          <PhoneFrame glare={progress(frame, 0, beat(20), Easing.linear)}>
            <NowPlayingScreen track={TRACK} accent={ACCENT} active={active} sweep={sweep} roman={roman} seek={0.32 + frame / 4000} />
            <TapRipple frame={frame} at={ROMAN_AT - 4} />
          </PhoneFrame>
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ background: "linear-gradient(90deg, transparent 52%, rgba(12,12,16,0.85) 64%)", opacity: push }} />
      {CHAPTERS.map((c) => (
        <Chapter key={c.copy.number} copy={c.copy} start={c.start} end={c.end} accent={ACCENT.accent} size={104} width={700} style={{ left: 1130, top: 330 }} />
      ))}
    </AbsoluteFill>
  );
};
