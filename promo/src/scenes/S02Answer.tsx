import React from "react";
import { AbsoluteFill, interpolateColors, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { RevealWords, Rise } from "../components/Type";
import { eyebrow } from "../components/NowPlayingScreen";
import { ANSWER_SYLLABLES, TRACKS } from "../data/tracks";
import { beat, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT } from "../theme/tokens";

const CORAL = ACCENTS.coral;
const SYLLABLE_STEP = beat(0.5);
const SYLLABLE_START = 6;
const LIFT_START = beat(4);
const SYSTEMS = ["KR → REVISED ROMANIZATION", "JP → HEPBURN ROMAJI", "ZH → PINYIN"];

/** One Hangul syllable that lights up and drops its romanization below. */
const Syllable: React.FC<{ k: string; r: string; at: number; frame: number }> = ({ k, r, at, frame }) => {
  const lit = progress(frame, at, at + 8);
  const drop = springAt(frame, at + 2);
  const isSpace = k === " ";
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", width: isSpace ? 50 : undefined }}>
      <div style={{ fontFamily: FONT.kor, fontSize: 170, fontWeight: 500, lineHeight: 1.1, color: interpolateColors(lit, [0, 1], [COLOR.ink3, CORAL.accent]), textShadow: `0 0 ${50 * lit * (1 - progress(frame, at + 8, at + 40))}px ${CORAL.accent}` }}>
        {k}
      </div>
      <div style={{ fontFamily: FONT.news, fontStyle: "italic", fontWeight: 300, fontSize: 64, color: COLOR.ink1, opacity: drop, transform: `translateY(${(1 - drop) * -40}px)`, minHeight: 80, whiteSpace: "pre" }}>
        {r}
      </div>
    </div>
  );
};

export const S02Answer: React.FC = () => {
  const frame = useCurrentFrame();
  const lift = progress(frame, LIFT_START, LIFT_START + 30, EASE.inOut);
  const exit = progress(frame, beat(7.6), beat(8), EASE.in);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, opacity: 1 - exit, filter: `blur(${exit * 12}px)` }}>
      <AmbientGlow gradient={TRACKS.coral.coverGradient} opacity={0.25 + 0.15 * lift} />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", transform: `translateY(${-210 * lift}px) scale(${1 - 0.35 * lift})` }}>
        <div style={{ ...eyebrow, fontSize: 22, color: CORAL.accent, marginBottom: 26, opacity: progress(frame, 0, 12) }}>KOR · {TRACKS.coral.romanSystem}</div>
        <div style={{ display: "flex", alignItems: "flex-start" }}>
          {ANSWER_SYLLABLES.map((s, i) => (
            <Syllable key={i} k={s.k} r={s.r} at={SYLLABLE_START + i * SYLLABLE_STEP} frame={frame} />
          ))}
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingTop: 330 }}>
        <RevealWords
          text="Now you can sing it."
          start={LIFT_START + 10}
          stagger={5}
          style={{ fontFamily: FONT.display, fontSize: 168, color: COLOR.ink1, letterSpacing: "-0.02em", lineHeight: 1 }}
          wordStyle={(i) => (i >= 3 ? { fontStyle: "italic", color: CORAL.accent } : {})}
        />
        <div style={{ display: "flex", gap: 22, marginTop: 56 }}>
          {SYSTEMS.map((s, i) => (
            <Rise key={s} start={beat(5.5) + i * 6}>
              <div style={{ ...eyebrow, fontSize: 18, color: COLOR.ink2, border: `1px solid ${COLOR.rule}`, padding: "12px 18px" }}>{s}</div>
            </Rise>
          ))}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
