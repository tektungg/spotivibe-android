import React from "react";
import { AbsoluteFill, interpolateColors, useCurrentFrame } from "remotion";
import { Hairline } from "../components/Backdrop";
import { scriptFont } from "../components/LyricsList";
import { TypedEyebrow } from "../components/Type";
import { COLD_OPEN_WORDS, type SlamWord } from "../data/tracks";
import { beat, progress, springAt, tween } from "../lib/motion";
import { ACCENTS, COLOR, EASE, EYEBROW_TRACKING, FONT } from "../theme/tokens";
import { HEIGHT, WIDTH } from "../timeline";

const BASELINE_Y = 690;
const SLAM_SIZE = 340;
const SLAM_OFFSETS_X = [-260, 240, 0, -200, 280, -40, 220, -240];
const GRID_START = beat(9);
const QUESTION = "CAN'T READ IT?";

const mono: React.CSSProperties = { fontFamily: FONT.mono, letterSpacing: EYEBROW_TRACKING, textTransform: "uppercase", fontSize: 18, fontWeight: 500 };

/** Frame 0: a coral lyric bar slams across, then thins into the baseline. */
const OpeningBar: React.FC<{ frame: number }> = ({ frame }) => {
  const grow = progress(frame, 0, 9);
  const thin = progress(frame, 12, 30, EASE.inOut);
  const height = 44 - 43 * thin;
  const color = interpolateColors(thin, [0, 1], [ACCENTS.coral.accent, COLOR.rule]);
  const width = 640 + (WIDTH - 640) * thin;
  return (
    <div
      style={{
        position: "absolute",
        left: (WIDTH - width) / 2,
        top: BASELINE_Y - height / 2,
        width,
        height,
        borderRadius: height / 2,
        background: color,
        transform: `scaleX(${grow})`,
        boxShadow: `0 0 ${120 * (1 - thin)}px ${ACCENTS.coral.accent}99`,
      }}
    />
  );
};

/** One word slammed on its beat with an outlined shockwave echo behind it. */
const Slam: React.FC<{ word: SlamWord; index: number; local: number }> = ({ word, index, local }) => {
  const accent = ACCENTS[word.accent].accent;
  const s = springAt(local, 0);
  const scale = 1.4 - 0.4 * s;
  const blur = tween(local, [0, 7], [26, 0]);
  const echo = progress(local, 0, 26);
  const x = SLAM_OFFSETS_X[index];
  const text: React.CSSProperties = { fontFamily: scriptFont(word.lang), fontSize: SLAM_SIZE, fontWeight: 500, lineHeight: 1, whiteSpace: "nowrap" };
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "flex-end", paddingBottom: HEIGHT - BASELINE_Y + 24 }}>
      <AbsoluteFill style={{ background: accent, opacity: 0.16 * (1 - progress(local, 0, 18)) }} />
      <div style={{ position: "relative", transform: `translateX(${x}px)` }}>
        <div style={{ ...text, position: "absolute", inset: 0, color: "transparent", WebkitTextStroke: `2px ${accent}`, transform: `scale(${1 + echo * 0.5})`, opacity: 0.7 * (1 - echo) }}>
          {word.text}
        </div>
        <div style={{ ...text, color: COLOR.ink1, transform: `scale(${scale})`, filter: `blur(${blur}px)` }}>{word.text}</div>
        <div style={{ position: "absolute", left: 4, right: 4, bottom: -14, height: 10, borderRadius: 5, background: accent, transform: `scaleX(${s})`, transformOrigin: "0% 50%", boxShadow: `0 0 30px ${accent}88` }} />
        <div style={{ ...mono, position: "absolute", left: 6, top: -34, color: accent }}>
          {String(index + 1).padStart(2, "0")} · {word.lang}
        </div>
        <div style={{ ...mono, position: "absolute", left: 6, bottom: -64, color: COLOR.ink4, fontSize: 26, letterSpacing: "0.5em" }}>? ? ?</div>
      </div>
    </AbsoluteFill>
  );
};

/** Beats 9-12: every word shrinks into a grid behind the question. */
const WordGrid: React.FC<{ frame: number }> = ({ frame }) => (
  <AbsoluteFill style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gridTemplateRows: "repeat(2, 1fr)", padding: "180px 160px" }}>
    {COLD_OPEN_WORDS.map((w, i) => {
      const s = springAt(frame, GRID_START + i * 2);
      return (
        <div key={w.text} style={{ display: "flex", alignItems: "center", justifyContent: "center", fontFamily: scriptFont(w.lang), fontSize: 120, color: COLOR.ink4, opacity: 0.55 * s, transform: `scale(${0.6 + 0.4 * s})`, filter: "blur(1.5px)" }}>
          {w.text}
        </div>
      );
    })}
  </AbsoluteFill>
);

const Chrome: React.FC<{ frame: number; wordIndex: number }> = ({ frame, wordIndex }) => {
  const p = progress(frame, 8, 30);
  const corner = (pos: React.CSSProperties, content: React.ReactNode) => (
    <div style={{ ...mono, position: "absolute", color: COLOR.ink3, opacity: p, ...pos }}>{content}</div>
  );
  return (
    <>
      {corner({ left: 80, top: 64 }, "SPOTIVIBE · SIDE A")}
      {corner({ right: 80, top: 64 }, "KOR / JPN / ZHO")}
      {corner({ left: 80, bottom: 60 }, `${String(Math.max(0, wordIndex + 1)).padStart(2, "0")} / 08`)}
      {corner({ right: 80, bottom: 60 }, "LYRICS · LIVE")}
    </>
  );
};

export const S01ColdOpen: React.FC = () => {
  const frame = useCurrentFrame();
  const wordIndex = Math.floor(frame / beat(1)) - 1;
  const showWord = wordIndex >= 0 && wordIndex < COLD_OPEN_WORDS.length;
  const exit = progress(frame, beat(11.5), beat(12), EASE.in);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, transform: `scale(${1 + exit * 0.15})`, filter: `blur(${exit * 16}px)`, opacity: 1 - exit * 0.6 }}>
      <OpeningBar frame={frame} />
      {showWord && <Slam word={COLD_OPEN_WORDS[wordIndex]} index={wordIndex} local={frame - (wordIndex + 1) * beat(1)} />}
      {frame >= GRID_START && <WordGrid frame={frame} />}
      {frame >= GRID_START && (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
          <TypedEyebrow text={QUESTION} start={GRID_START + 8} framesPerChar={3} color={COLOR.ink1} size={78} style={{ textShadow: "0 0 40px rgba(0,0,0,0.9)" }} />
        </AbsoluteFill>
      )}
      <Hairline length={WIDTH} progress={progress(frame, GRID_START, GRID_START + 20)} color={COLOR.bg0} thickness={3} origin="center" style={{ left: 0, top: BASELINE_Y - 1 }} />
      <Chrome frame={frame} wordIndex={Math.min(wordIndex, COLD_OPEN_WORDS.length - 1)} />
    </AbsoluteFill>
  );
};
