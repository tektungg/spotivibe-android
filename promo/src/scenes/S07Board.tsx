import React from "react";
import { AbsoluteFill, Easing, useCurrentFrame } from "remotion";
import { AmbientGlow, Hairline } from "../components/Backdrop";
import { eyebrow } from "../components/NowPlayingScreen";
import { FlapText, type FlapStyle } from "../components/SplitFlap";
import { RevealWords } from "../components/Type";
import { TRACKS } from "../data/tracks";
import { beat, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT } from "../theme/tokens";

const CORAL = ACCENTS.coral.accent;
const ROW_STEP = beat(2);
const SPIN_FRAMES = 8;
const RACE_AT = beat(12);
const BOARD_LEFT = 195;
const CELL = { width: 46, height: 66, fontSize: 40, gap: 4 };
const ROWS = [
  { feature: "LYRIC SOURCES", status: "4 RACING" },
  { feature: "ROMANIZATION", status: "KR · JP · ZH" },
  { feature: "SYNC OFFSET", status: "3 LAYERS" },
  { feature: "OFFLINE CACHE", status: "30 DAYS" },
  { feature: "PLAYERS", status: "ANY · NO LOGIN" },
  { feature: "KARAOKE", status: "PER SYLLABLE" },
];
const FEATURE_CELLS = 13;
const STATUS_CELLS = 14;

/** Lyric sources from the README; finish frame and timing tier per lane. */
const LANES = [
  { name: "LRCLIB", finish: 62, tier: "LINE" },
  { name: "AMLL TTML DB", finish: 96, tier: "WORD" },
  { name: "LRC.RED", finish: 80, tier: "LINE" },
  { name: "LRCMUX", finish: 112, tier: "PLAIN" },
];
const WINNER = 1;
const VERDICT_AT = 130;
const LANE_X = { label: 250, track: 640, trackWidth: 860 };

const style = (color: string): FlapStyle => ({ cell: CELL, color });

const BoardHeader: React.FC<{ frame: number }> = ({ frame }) => (
  <>
    <div style={{ ...eyebrow, position: "absolute", left: BOARD_LEFT, top: 120, fontSize: 24, color: COLOR.ink2 }}>SPOTIVIBE · FEATURE BOARD</div>
    <div style={{ ...eyebrow, position: "absolute", right: BOARD_LEFT, top: 120, fontSize: 24, color: CORAL, fontVariantNumeric: "tabular-nums" }}>
      15:07:{String(10 + Math.floor(frame / 60)).padStart(2, "0")}
    </div>
    <div style={{ ...eyebrow, position: "absolute", left: BOARD_LEFT, top: 200, fontSize: 16, color: COLOR.ink4, display: "flex" }}>
      <span style={{ width: 140 }}>NO.</span>
      <span style={{ width: 690 }}>FEATURE</span>
      <span>STATUS</span>
    </div>
    <Hairline length={1530} progress={progress(frame, 0, 30, EASE.inOut)} color={COLOR.rule} style={{ left: BOARD_LEFT, top: 236 }} />
  </>
);

const Board: React.FC<{ frame: number }> = ({ frame }) => (
  <>
    <BoardHeader frame={frame} />
    {ROWS.map((row, i) => {
      const start = i * ROW_STEP;
      const lit = frame >= start + SPIN_FRAMES && frame < start + ROW_STEP;
      return (
        <div key={row.feature} style={{ position: "absolute", left: BOARD_LEFT, top: 262 + i * 88, display: "flex", gap: 40, alignItems: "center" }}>
          <FlapText text={String(i + 1).padStart(2, "0")} length={2} start={start} spin={SPIN_FRAMES} frame={frame} seed={`n${i}`} style={style(COLOR.ink3)} />
          <FlapText text={row.feature} length={FEATURE_CELLS} start={start} spin={SPIN_FRAMES} frame={frame} seed={`f${i}`} style={style(COLOR.ink1)} />
          <FlapText text={row.status} length={STATUS_CELLS} start={start + 4} spin={SPIN_FRAMES} frame={frame} seed={`s${i}`} style={style(lit ? CORAL : COLOR.ink2)} />
        </div>
      );
    })}
  </>
);

const Lane: React.FC<{ lane: (typeof LANES)[number]; index: number; local: number }> = ({ lane, index, local }) => {
  const p = progress(local, 10 + index * 3, lane.finish, Easing.bezier(0.3, 0, 0.2, 1));
  const verdict = progress(local, VERDICT_AT, VERDICT_AT + 14);
  const isWinner = index === WINNER;
  const barColor = isWinner ? CORAL : COLOR.ink2;
  const dim = isWinner ? 1 : 1 - 0.6 * verdict;
  const stamp = springAt(local, VERDICT_AT + 6);
  const y = 430 + index * 118;
  return (
    <div style={{ position: "absolute", left: 0, top: y, width: "100%", opacity: dim }}>
      <div style={{ ...eyebrow, position: "absolute", left: LANE_X.label, top: -12, fontSize: 24, color: COLOR.ink1 }}>{lane.name}</div>
      <div style={{ position: "absolute", left: LANE_X.track, top: 0, width: LANE_X.trackWidth, height: 2, background: COLOR.rule }} />
      <div style={{ position: "absolute", left: LANE_X.track, top: -4, width: LANE_X.trackWidth * p, height: 10, borderRadius: 5, background: barColor, boxShadow: isWinner ? `0 0 ${30 * verdict}px ${CORAL}` : undefined }} />
      <div style={{ ...eyebrow, position: "absolute", left: LANE_X.track + LANE_X.trackWidth + 36, top: -12, fontSize: 24, color: isWinner ? CORAL : COLOR.ink2, opacity: p >= 1 ? 1 : 0 }}>{lane.tier}</div>
      {isWinner && (
        <div style={{ ...eyebrow, position: "absolute", left: LANE_X.track + LANE_X.trackWidth + 170, top: -30, fontSize: 24, color: ACCENTS.coral.ink, background: CORAL, padding: "12px 18px", transform: `scale(${stamp}) rotate(${-4 * stamp}deg)`, opacity: stamp }}>WINNER</div>
      )}
    </div>
  );
};

const Race: React.FC<{ local: number }> = ({ local }) => (
  <AbsoluteFill>
    <div style={{ position: "absolute", left: LANE_X.label, top: 150 }}>
      <RevealWords text="Four sources race." start={0} stagger={4} style={{ fontFamily: FONT.display, fontSize: 104, color: COLOR.ink1, lineHeight: 1 }} />
      <RevealWords text="The finest timing wins." start={14} stagger={4} style={{ fontFamily: FONT.display, fontStyle: "italic", fontSize: 104, color: CORAL, lineHeight: 1 }} />
    </div>
    {LANES.map((lane, i) => (
      <Lane key={lane.name} lane={lane} index={i} local={local} />
    ))}
    <div style={{ ...eyebrow, position: "absolute", left: LANE_X.label, top: 930, fontSize: 18, color: COLOR.ink3, opacity: progress(local, VERDICT_AT + 10, VERDICT_AT + 30) }}>
      PER WORD &gt; PER LINE &gt; PLAIN TEXT · CACHED 30 DAYS
    </div>
  </AbsoluteFill>
);

export const S07Board: React.FC = () => {
  const frame = useCurrentFrame();
  const recede = progress(frame, RACE_AT - 6, RACE_AT + 18, EASE.inOut);
  const exit = progress(frame, beat(19.6), beat(20), EASE.in);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, opacity: 1 - exit }}>
      <AmbientGlow gradient={TRACKS.coral.coverGradient} opacity={0.14} />
      <AbsoluteFill style={{ transform: `scale(${1 - 0.12 * recede}) translateY(${-60 * recede}px)`, opacity: 1 - 0.9 * recede, filter: recede > 0 ? `blur(${10 * recede}px)` : undefined }}>
        <Board frame={frame} />
      </AbsoluteFill>
      {frame >= RACE_AT && <Race local={frame - RACE_AT} />}
    </AbsoluteFill>
  );
};
