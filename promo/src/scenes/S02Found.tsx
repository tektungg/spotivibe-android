import React from "react";
import { AbsoluteFill, Easing, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { MARK_ACTIVE_INDEX, barTone } from "../components/LogoMark";
import { KaraokeText, scriptFont } from "../components/LyricsList";
import { eyebrow } from "../components/NowPlayingScreen";
import { RevealWords } from "../components/Type";
import { TAGLINE } from "../data/brand";
import { FOUND_ROWS, TRACKS } from "../data/tracks";
import { EMPTY_SLOT_FRACTIONS, EMPTY_SLOT_WIDTH, FIVE_LINE_GAP, FIVE_LINE_SIZES } from "../components/FiveLines";
import { beat, progress } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT } from "../theme/tokens";

const TRACK = TRACKS.eng;
const CORAL = ACCENTS.coral;
const SLOT_WIDTHS = EMPTY_SLOT_FRACTIONS.map((w) => w * EMPTY_SLOT_WIDTH);
const LINE_STEP = beat(0.5);
const LINE_START = beat(0.5);
const MORPH_FRAMES = 14;
const SWEEP = { start: beat(3), end: beat(6) };
const LIFT_START = beat(4);

/** One empty lyric slot that turns into its line of lyrics. */
const FoundLine: React.FC<{ index: number; frame: number }> = ({ index, frame }) => {
  const at = LINE_START + index * LINE_STEP;
  const m = progress(frame, at, at + MORPH_FRAMES, EASE.out);
  const tone = barTone(index - MARK_ACTIVE_INDEX, CORAL.accent);
  const isActive = index === MARK_ACTIVE_INDEX;
  const size = FIVE_LINE_SIZES[index];
  const sweep = progress(frame, SWEEP.start, SWEEP.end, Easing.linear);
  return (
    <div style={{ position: "absolute", left: "50%", top: "50%", transform: `translate(-50%, -50%) translateY(${(index - MARK_ACTIVE_INDEX) * FIVE_LINE_GAP}px)` }}>
      <div style={{ position: "absolute", left: "50%", top: "50%", width: SLOT_WIDTHS[index], height: isActive ? 14 : 10, marginLeft: -SLOT_WIDTHS[index] / 2, marginTop: -6, borderRadius: 7, background: COLOR.ink4, opacity: 0.45 * (1 - m), transform: `scaleX(${1 - 0.3 * m})` }} />
      <div style={{ fontFamily: scriptFont(TRACK.lang), fontSize: size, fontWeight: isActive ? 500 : 400, color: tone.color, opacity: tone.opacity * m, whiteSpace: "nowrap", textAlign: "center", transform: `scaleY(${0.3 + 0.7 * m})`, filter: m < 1 ? `blur(${(1 - m) * 8}px)` : undefined }}>
        {isActive && frame >= SWEEP.start ? <KaraokeText text={FOUND_ROWS[index].k} sweep={sweep} accent={CORAL} /> : FOUND_ROWS[index].k}
      </div>
    </div>
  );
};

export const S02Found: React.FC = () => {
  const frame = useCurrentFrame();
  const lift = progress(frame, LIFT_START, LIFT_START + 30, EASE.inOut);
  const exit = progress(frame, beat(7.6), beat(8), EASE.in);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, opacity: 1 - exit, filter: `blur(${exit * 12}px)` }}>
      <AmbientGlow gradient={TRACK.coverGradient} opacity={0.22 + 0.15 * lift} />
      <div style={{ ...eyebrow, position: "absolute", top: 110, width: "100%", textAlign: "center", fontSize: 22, color: CORAL.accent, opacity: progress(frame, 0, 12) }}>
        ● LYRICS FOUND · IN SYNC · {TRACK.title.toUpperCase()}
      </div>
      <AbsoluteFill style={{ transform: `translateY(${-150 * lift}px) scale(${1 - 0.22 * lift})` }}>
        {FOUND_ROWS.map((_, i) => (
          <FoundLine key={i} index={i} frame={frame} />
        ))}
      </AbsoluteFill>
      <RevealWords
        text={TAGLINE}
        start={LIFT_START + 12}
        stagger={6}
        style={{ position: "absolute", top: 780, width: "100%", justifyContent: "center", fontFamily: FONT.display, fontSize: 150, color: COLOR.ink1, letterSpacing: "-0.02em", lineHeight: 1 }}
        wordStyle={(i) => (i >= 2 ? { fontStyle: "italic", color: CORAL.accent } : {})}
      />
    </AbsoluteFill>
  );
};
