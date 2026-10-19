import React from "react";
import { AbsoluteFill, interpolateColors, useCurrentFrame } from "remotion";
import { Hairline } from "../components/Backdrop";
import { displayFont } from "../components/LyricsList";
import { EMPTY_SLOT_FRACTIONS, EMPTY_SLOT_WIDTH } from "../components/FiveLines";
import { TypedEyebrow } from "../components/Type";
import { PLAYER_CARDS, TRACKS, type Lang, type PlayerCard } from "../data/tracks";
import { beat, progress, springAt, tween } from "../lib/motion";
import { ACCENTS, COLOR, EASE, EYEBROW_TRACKING, FONT } from "../theme/tokens";
import { WIDTH } from "../timeline";

const BASELINE_Y = 540;
const CARD = { width: 1180, cover: 190, titleRoom: 800 };
const CARD_OFFSETS_X = [-170, 160, 0, -140, 190, -40, 150, -180];
const GRID_START = beat(9);
const QUESTION = "WHERE ARE THE LYRICS?";
/** Average advance per glyph in em, to fit a title on one line. */
const GLYPH_EM = { latin: 0.42, cjk: 1 } as const;
const COVER_BY_ACCENT = { coral: TRACKS.eng.coverGradient, violet: TRACKS.jpn.coverGradient, jade: TRACKS.zho.coverGradient };

const mono: React.CSSProperties = { fontFamily: FONT.mono, letterSpacing: EYEBROW_TRACKING, textTransform: "uppercase", fontSize: 18, fontWeight: 500 };

const isCjk = (lang: Lang) => lang === "KOR" || lang === "JPN" || lang === "ZHO";

/** Largest title size (capped) that keeps the title on one line. */
function titleSize(card: PlayerCard, room: number, cap: number): number {
  const em = isCjk(card.lang) ? GLYPH_EM.cjk : GLYPH_EM.latin;
  return Math.min(cap, room / ([...card.title].length * em));
}

/** Frame 0: a coral lyric bar slams across, then thins into the baseline. */
const OpeningBar: React.FC<{ frame: number }> = ({ frame }) => {
  const grow = progress(frame, 0, 9);
  const thin = progress(frame, 12, 30, EASE.inOut);
  const height = 44 - 43 * thin;
  const width = 640 + (WIDTH - 640) * thin;
  const color = interpolateColors(thin, [0, 1], [ACCENTS.coral.accent, COLOR.rule]);
  return (
    <div style={{ position: "absolute", left: (WIDTH - width) / 2, top: BASELINE_Y - height / 2, width, height, borderRadius: height / 2, background: color, transform: `scaleX(${grow})`, boxShadow: `0 0 ${120 * (1 - thin)}px ${ACCENTS.coral.accent}99` }} />
  );
};

const EmptyLyrics: React.FC<{ scale?: number }> = ({ scale = 1 }) => (
  <div style={{ display: "flex", flexDirection: "column", gap: 16 * scale }}>
    {EMPTY_SLOT_FRACTIONS.map((w, i) => (
      <div key={i} style={{ width: EMPTY_SLOT_WIDTH * w * scale, height: (i === 2 ? 14 : 10) * scale, borderRadius: 7 * scale, background: COLOR.ink4, opacity: 0.45 }} />
    ))}
  </div>
);

/** A "now playing" card from some player: song known, lyrics missing. */
const NowPlayingCard: React.FC<{ card: PlayerCard }> = ({ card }) => {
  const accent = ACCENTS[card.accent].accent;
  return (
    <div style={{ width: CARD.width, padding: 48, background: "rgba(20,21,27,0.94)", border: `1px solid ${COLOR.rule}`, boxShadow: "0 40px 100px rgba(0,0,0,0.6)" }}>
      <div style={{ ...mono, display: "flex", justifyContent: "space-between", color: accent }}>
        <span>● NOW PLAYING · {card.player}</span>
        <span style={{ color: COLOR.ink3 }}>{card.lang}</span>
      </div>
      <div style={{ display: "flex", gap: 40, alignItems: "center", margin: "34px 0 40px" }}>
        <div style={{ width: CARD.cover, height: CARD.cover, flexShrink: 0, background: COVER_BY_ACCENT[card.accent], boxShadow: "0 12px 30px rgba(0,0,0,0.5)" }} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontFamily: displayFont(card.lang), fontSize: titleSize(card, CARD.titleRoom, 118), lineHeight: 1.05, color: COLOR.ink1, whiteSpace: "nowrap" }}>{card.title}</div>
          <div style={{ ...mono, fontSize: 22, color: COLOR.ink3, marginTop: 14 }}>{card.artist}</div>
        </div>
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
        <EmptyLyrics />
        <div style={{ ...mono, fontSize: 22, color: COLOR.ink4 }}>NO LYRICS</div>
      </div>
    </div>
  );
};

/** One card slammed on its beat, with an outline echo expanding behind it. */
const Slam: React.FC<{ card: PlayerCard; index: number; local: number }> = ({ card, index, local }) => {
  const accent = ACCENTS[card.accent].accent;
  const s = springAt(local, 0);
  const echo = progress(local, 0, 26);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
      <AbsoluteFill style={{ background: accent, opacity: 0.12 * (1 - progress(local, 0, 18)) }} />
      <div style={{ position: "relative", transform: `translateX(${CARD_OFFSETS_X[index]}px) rotate(${(index % 2 ? 1.5 : -1.5) * (1 - s)}deg)` }}>
        <div style={{ position: "absolute", inset: 0, border: `2px solid ${accent}`, transform: `scale(${1 + echo * 0.25})`, opacity: 0.7 * (1 - echo) }} />
        <div style={{ transform: `scale(${1.25 - 0.25 * s}) translateY(${(1 - s) * 50}px)`, filter: `blur(${tween(local, [0, 7], [22, 0])}px)` }}>
          <NowPlayingCard card={card} />
        </div>
      </div>
    </AbsoluteFill>
  );
};

/** Beats 9-12: every card shrinks into a grid behind the question. */
const CardGrid: React.FC<{ frame: number }> = ({ frame }) => (
  <AbsoluteFill style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gridTemplateRows: "repeat(2, 1fr)", gap: 28, padding: "170px 110px" }}>
    {PLAYER_CARDS.map((card, i) => {
      const s = springAt(frame, GRID_START + i * 2);
      return (
        <div key={`${card.title}-${i}`} style={{ border: `1px solid ${COLOR.ruleSoft}`, padding: 22, opacity: 0.5 * s, transform: `scale(${0.6 + 0.4 * s})`, filter: "blur(1.2px)" }}>
          <div style={{ ...mono, fontSize: 13, color: ACCENTS[card.accent].accent }}>{card.player}</div>
          <div style={{ fontFamily: displayFont(card.lang), fontSize: titleSize(card, 340, 46), color: COLOR.ink2, margin: "10px 0 16px", whiteSpace: "nowrap" }}>{card.title}</div>
          <EmptyLyrics scale={0.42} />
        </div>
      );
    })}
  </AbsoluteFill>
);

const Chrome: React.FC<{ frame: number; index: number }> = ({ frame, index }) => {
  const p = progress(frame, 8, 30);
  const corner = (pos: React.CSSProperties, content: React.ReactNode) => <div style={{ ...mono, position: "absolute", color: COLOR.ink3, opacity: p, ...pos }}>{content}</div>;
  return (
    <>
      {corner({ left: 80, top: 64 }, "SPOTIVIBE · NOW PLAYING")}
      {corner({ right: 80, top: 64 }, "SPOTIFY / YOUTUBE MUSIC / ANY PLAYER")}
      {corner({ left: 80, bottom: 60 }, `${String(Math.max(0, index + 1)).padStart(2, "0")} / ${String(PLAYER_CARDS.length).padStart(2, "0")}`)}
      {corner({ right: 80, bottom: 60 }, "LYRICS · NONE")}
    </>
  );
};

export const S01ColdOpen: React.FC = () => {
  const frame = useCurrentFrame();
  const index = Math.floor(frame / beat(1)) - 1;
  const showCard = index >= 0 && index < PLAYER_CARDS.length;
  const exit = progress(frame, beat(11.5), beat(12), EASE.in);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, transform: `scale(${1 + exit * 0.15})`, filter: `blur(${exit * 16}px)`, opacity: 1 - exit * 0.6 }}>
      <OpeningBar frame={frame} />
      {showCard && <Slam card={PLAYER_CARDS[index]} index={index} local={frame - (index + 1) * beat(1)} />}
      {frame >= GRID_START && <CardGrid frame={frame} />}
      {frame >= GRID_START && (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
          <div style={{ background: "rgba(20,21,27,0.9)", padding: "26px 44px", opacity: progress(frame, GRID_START + 4, GRID_START + 12) }}>
            <TypedEyebrow text={QUESTION} start={GRID_START + 8} framesPerChar={2.4} color={COLOR.ink1} size={64} />
          </div>
        </AbsoluteFill>
      )}
      <Hairline length={WIDTH} progress={progress(frame, GRID_START, GRID_START + 20)} color={COLOR.bg0} thickness={3} origin="center" style={{ left: 0, top: BASELINE_Y - 1 }} />
      <Chrome frame={frame} index={Math.min(index, PLAYER_CARDS.length - 1)} />
    </AbsoluteFill>
  );
};
