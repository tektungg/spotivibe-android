import React from "react";
import { AbsoluteFill, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { Lockup } from "../components/Lockup";
import { MARK_ACTIVE_INDEX, MARK_BARS, MARK_SIZE, barTone } from "../components/LogoMark";
import { scriptFont } from "../components/LyricsList";
import { FOUND_ROWS, TRACKS } from "../data/tracks";
import { FIVE_LINE_GAP, FIVE_LINE_SIZES } from "../components/FiveLines";
import { beat, beatPulse, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE } from "../theme/tokens";

const CORAL = ACCENTS.coral.accent;
const UNIT = 5.2;
const MORPH_START = beat(2);
const MORPH_STAGGER = beat(0.25);
const MORPH_FRAMES = 26;
const LOCKUP_START = beat(4);

/**
 * One lyric line that collapses into its bar of the mark: the text squashes
 * and fades while a pill of the same tone grows into the mark geometry.
 */
const MorphLine: React.FC<{ index: number; frame: number; glow: number }> = ({ index, frame, glow }) => {
  const bar = MARK_BARS[index];
  const tone = barTone(index - MARK_ACTIVE_INDEX, CORAL);
  const appear = springAt(frame, index * 4);
  const at = MORPH_START + index * MORPH_STAGGER;
  const m = progress(frame, at, at + MORPH_FRAMES, EASE.inOut);
  const lineY = (index - MARK_ACTIVE_INDEX) * FIVE_LINE_GAP;
  const barY = (bar.y + bar.h / 2 - MARK_SIZE.h / 2) * UNIT;
  const barX = (bar.x + bar.w / 2 - MARK_SIZE.w / 2) * UNIT;
  const y = lineY + (barY - lineY) * m;
  const width = 900 * (1 - m) + bar.w * UNIT * m;
  const height = FIVE_LINE_SIZES[index] * 0.2 * (1 - m) + bar.h * UNIT * m;
  return (
    <div style={{ position: "absolute", left: "50%", top: "50%", transform: `translate(-50%, -50%) translate(${barX * m}px, ${y}px)` }}>
      <div style={{ fontFamily: scriptFont(TRACKS.eng.lang), fontSize: FIVE_LINE_SIZES[index], fontWeight: index === MARK_ACTIVE_INDEX ? 500 : 400, color: tone.color, opacity: tone.opacity * appear * (1 - m), whiteSpace: "nowrap", textAlign: "center", transform: `scaleY(${1 - 0.8 * m}) translateY(${(1 - appear) * 40}px)`, filter: `blur(${m * 10}px)` }}>
        {FOUND_ROWS[index].k}
      </div>
      <div style={{ position: "absolute", left: "50%", top: "50%", width, height, marginLeft: -width / 2, marginTop: -height / 2, borderRadius: height / 2, background: tone.color, opacity: tone.opacity * Math.min(1, m * 3), boxShadow: index === MARK_ACTIVE_INDEX && glow > 0 ? `0 0 ${70 * glow}px ${10 * glow}px ${CORAL}88` : undefined }} />
    </div>
  );
};

export const S03LogoMorph: React.FC = () => {
  const frame = useCurrentFrame();
  const slide = progress(frame, LOCKUP_START - 10, LOCKUP_START + 20, EASE.inOut);
  const glow = frame > beat(6) ? beatPulse(frame, 14) : 0;
  const exit = progress(frame, beat(7.5), beat(8), EASE.in);
  const markW = MARK_SIZE.w * UNIT;
  const markH = MARK_SIZE.h * UNIT;
  const mark = (
    <div style={{ position: "relative", width: markW, height: markH }}>
      {MARK_BARS.map((_, i) => (
        <MorphLine key={i} index={i} frame={frame} glow={glow} />
      ))}
    </div>
  );
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, transform: `scale(${1 + 0.04 * progress(frame, beat(5), beat(8)) + exit * 2.5})`, opacity: 1 - exit, filter: `blur(${exit * 8}px)` }}>
      <AmbientGlow gradient={TRACKS.eng.coverGradient} opacity={0.18} />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
        <div style={{ transform: `translateX(${(1 - slide) * 485}px)` }}>
          <Lockup mark={mark} markHeight={markH} start={LOCKUP_START} size={210} />
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
