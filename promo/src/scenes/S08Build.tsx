import React, { useMemo } from "react";
import { AbsoluteFill, Easing, random, useCurrentFrame } from "remotion";
import { eyebrow } from "../components/NowPlayingScreen";
import { scriptFont } from "../components/LyricsList";
import { TRACK_IDS, type Track } from "../data/catalog";
import { useCatalog } from "../data/useCatalog";
import { fitFontSize } from "../lib/textFit";
import { beat, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, FONT } from "../theme/tokens";
import { BUILD_DROP_BEAT } from "../timeline";

const DROP = beat(BUILD_DROP_BEAT);
const COLLAPSE_AT = beat(12);
const ITEM_SPACING_Z = 430;
const CAMERA_LINEAR = 300;
const CAMERA_CUBIC = 9800;
const FAR_Z = -7000;
const NEAR_Z = 650;
const FRAME_SPACING_Z = 700;
const CORAL = ACCENTS.coral;

const TUNNEL_TEXT = { size: 72, romanSize: 34, maxWidth: 900 };

/** Every lyric row of every track, interleaved, with its accent and a seeded 3D slot. */
function tunnelItems(tracks: Track[]) {
  return tracks
    .flatMap((t) => t.lyrics.map((row, i) => ({ row, lang: t.lang, accent: t.accent.accent, i })))
    .sort((a, b) => a.i - b.i)
    .map((item, n) => ({
      ...item,
      z: -n * ITEM_SPACING_Z - 600,
      x: ((n % 3) - 1) * 560 + (random(`tx${n}`) - 0.5) * 200,
      y: (random(`ty${n}`) - 0.5) * 640,
    }));
}

const depthOpacity = (depth: number) => progress(depth, FAR_Z, FAR_Z + 2500, Easing.linear) * (1 - progress(depth, NEAR_Z - 300, NEAR_Z, Easing.linear));

/** Beats 0-8: camera flies through lyric lines, accelerating into the drop. */
const Tunnel: React.FC<{ frame: number }> = ({ frame }) => {
  const { tracks } = useCatalog();
  const items = useMemo(() => tunnelItems(TRACK_IDS.map((id) => tracks[id])), [tracks]);
  const t = frame / DROP;
  const cam = CAMERA_LINEAR * t * 8 + CAMERA_CUBIC * t ** 3;
  const shake = t > 0.85 ? (random(`sh${frame}`) - 0.5) * 14 * t : 0;
  const synced = Math.floor(t ** 2 * 2048);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, perspective: 800, overflow: "hidden" }}>
      <AbsoluteFill style={{ background: `radial-gradient(circle at 50% 50%, ${CORAL.accent}${Math.round(t * 60).toString(16).padStart(2, "0")} 0%, transparent 60%)` }} />
      <AbsoluteFill style={{ transformStyle: "preserve-3d", transform: `translate(${shake}px, ${shake * 0.6}px)` }}>
        {Array.from({ length: 14 }, (_, k) => {
          const depth = -k * FRAME_SPACING_Z + (cam % FRAME_SPACING_Z);
          return <div key={`f${k}`} style={{ position: "absolute", left: 160, top: 90, width: 1600, height: 900, border: `2px solid ${COLOR.rule}`, transform: `translateZ(${depth}px)`, opacity: depthOpacity(depth) * 0.8 }} />;
        })}
        {items.map((item, n) => {
          const depth = item.z + cam;
          if (depth < FAR_Z || depth > NEAR_Z) return null;
          return (
            <div key={n} style={{ position: "absolute", left: 960 + item.x, top: 540 + item.y, transform: `translate(-50%, -50%) translateZ(${depth}px)`, opacity: depthOpacity(depth), whiteSpace: "nowrap", textAlign: "center" }}>
              <div style={{ fontFamily: scriptFont(item.lang), fontSize: fitFontSize(item.row.k, "news", TUNNEL_TEXT.size, TUNNEL_TEXT.maxWidth), fontWeight: 500, color: item.accent }}>{item.row.k}</div>
              <div style={{ fontFamily: FONT.news, fontStyle: "italic", fontSize: fitFontSize(item.row.r, "news", TUNNEL_TEXT.romanSize, TUNNEL_TEXT.maxWidth), color: COLOR.ink2 }}>{item.row.r}</div>
            </div>
          );
        })}
      </AbsoluteFill>
      <div style={{ ...eyebrow, position: "absolute", bottom: 70, width: "100%", textAlign: "center", fontSize: 22, color: COLOR.ink2, fontVariantNumeric: "tabular-nums" }}>
        {String(synced).padStart(4, "0")} LINES IN SYNC
      </div>
      <AbsoluteFill style={{ background: COLOR.ink1, opacity: progress(frame, DROP - 20, DROP, EASE.in) }} />
    </AbsoluteFill>
  );
};

/** Two words per slam fit 1920 px at this size even at the 1.3x entry scale. */
const SLAM_SIZE = 300;

const SLAMS = [
  { words: ["Every", "player."], bg: CORAL.accent, ink: CORAL.ink, em: COLOR.ink1, rot: -3 },
  { words: ["Every", "song."], bg: COLOR.bg0, ink: COLOR.ink1, em: CORAL.accent, rot: 3 },
  { words: ["Every", "line."], bg: COLOR.ink1, ink: COLOR.bg0, em: CORAL.accent, rot: -2 },
  { words: ["In", "sync."], bg: COLOR.bg0, ink: COLOR.ink1, em: CORAL.accent, rot: 0 },
];

/** Beats 8-12: one phrase per beat on alternating grounds. */
const Slams: React.FC<{ frame: number }> = ({ frame }) => {
  const local = frame - DROP;
  const index = Math.min(SLAMS.length - 1, Math.floor(local / beat(1)));
  const slam = SLAMS[index];
  const s = springAt(local - index * beat(1), 0);
  const collapse = progress(frame, COLLAPSE_AT, COLLAPSE_AT + 28, EASE.inOut);
  return (
    <AbsoluteFill style={{ background: slam.bg, alignItems: "center", justifyContent: "center" }}>
      <div style={{ display: "flex", gap: 40, fontFamily: FONT.display, fontSize: SLAM_SIZE, lineHeight: 1, letterSpacing: "-0.03em", color: slam.ink, transform: `scale(${(1.3 - 0.3 * s) * (1 - 0.2 * collapse)}, ${(1.3 - 0.3 * s) * (1 - collapse)}) rotate(${slam.rot * (1 - s)}deg)`, filter: collapse > 0 ? `blur(${collapse * 12}px)` : undefined, opacity: 1 - collapse }}>
        {slam.words.map((w, i) => (
          <span key={w} style={i === 1 ? { fontStyle: "italic", color: slam.em } : undefined}>
            {w}
          </span>
        ))}
      </div>
    </AbsoluteFill>
  );
};

/** Beats 12-14: the words collapse into one coral lyric bar, full width. */
const CollapseBar: React.FC<{ frame: number }> = ({ frame }) => {
  const grow = progress(frame, COLLAPSE_AT + 8, COLLAPSE_AT + 40, EASE.inOut);
  const height = 24;
  return <div style={{ position: "absolute", left: 960 - 960 * grow, width: 1920 * grow, top: 540 - height / 2, height, borderRadius: height / 2, background: CORAL.accent, boxShadow: `0 0 80px ${CORAL.accent}aa` }} />;
};

export const S08Build: React.FC = () => {
  const frame = useCurrentFrame();
  if (frame < DROP) return <Tunnel frame={frame} />;
  return (
    <AbsoluteFill style={{ background: COLOR.bg0 }}>
      <Slams frame={frame} />
      {frame >= COLLAPSE_AT && <CollapseBar frame={frame} />}
    </AbsoluteFill>
  );
};
