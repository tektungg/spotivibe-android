import React from "react";
import { interpolateColors } from "remotion";
import { COLOR, EYEBROW_TRACKING, FONT, PHONE, type AccentSet } from "../theme/tokens";
import type { Track } from "../data/tracks";
import { Halftone } from "./Backdrop";
import { Icon } from "./Icon";
import { LyricsList, scriptFont } from "./LyricsList";
import { AlbumCover, StatusBar } from "./Phone";

const HEADER_COVER = 44;
const LYRICS_HEIGHT = 590;
const SECONDS_PER_MINUTE = 60;

const fmtTime = (s: number) => `${Math.floor(s / SECONDS_PER_MINUTE)}:${String(Math.floor(s % SECONDS_PER_MINUTE)).padStart(2, "0")}`;
const parseTime = (t: string) => {
  const [m, s] = t.split(":").map(Number);
  return m * SECONDS_PER_MINUTE + s;
};

export const eyebrow: React.CSSProperties = {
  fontFamily: FONT.mono,
  textTransform: "uppercase",
  letterSpacing: EYEBROW_TRACKING,
  fontSize: 11,
  fontWeight: 500,
};

const PhoneAmbient: React.FC<{ track: Track }> = ({ track }) => (
  <div style={{ position: "absolute", inset: 0, overflow: "hidden" }}>
    <div style={{ position: "absolute", inset: "-15%", background: track.coverGradient, filter: "blur(60px) saturate(1.3)", transform: "scale(1.15)" }} />
    <Halftone />
    <div style={{ position: "absolute", inset: 0, background: "linear-gradient(180deg, rgba(0,0,0,0.35) 0%, rgba(0,0,0,0.55) 50%, rgba(0,0,0,0.78) 100%)" }} />
  </div>
);

const Header: React.FC<{ track: Track; accent: AccentSet; roman: number }> = ({ track, accent, roman }) => (
  <div style={{ position: "relative", zIndex: 3, padding: "8px 18px 14px", display: "grid", gridTemplateColumns: `${HEADER_COVER}px 1fr auto`, gap: 12, alignItems: "center" }}>
    <AlbumCover track={track} size={HEADER_COVER} />
    <div style={{ minWidth: 0, overflow: "hidden" }}>
      <div style={{ ...eyebrow, color: COLOR.ink3, marginBottom: 3, display: "flex", gap: 8, alignItems: "center" }}>
        <span>{track.album}</span>
        <span style={{ width: 3, height: 3, borderRadius: "50%", background: "currentColor" }} />
        <span>{track.year}</span>
      </div>
      <div style={{ fontFamily: `'Instrument Serif', ${scriptFont(track.lang)}`, fontSize: 18, color: COLOR.ink1, lineHeight: 1.15, whiteSpace: "nowrap" }}>
        {track.title}
      </div>
      <div style={{ fontFamily: FONT.news, fontStyle: "italic", fontSize: 12, color: COLOR.ink3, marginTop: 1 }}>{track.artist}</div>
    </div>
    <div style={{ display: "flex", gap: 12, color: COLOR.ink2 }}>
      <Icon name="romanize" size={16} color={interpolateColors(roman, [0, 1], [COLOR.ink2, accent.accent])} />
      <Icon name="pip" size={16} />
      <Icon name="kebab" size={16} />
    </div>
  </div>
);

const Transport: React.FC<{ track: Track; accent: AccentSet; seek: number }> = ({ track, accent, seek }) => (
  <div style={{ position: "relative", zIndex: 3, padding: "0 22px 22px" }}>
    <div style={{ height: 1, background: COLOR.ruleSoft, marginBottom: 16 }} />
    <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 14, fontFamily: FONT.mono, fontSize: 11, fontVariantNumeric: "tabular-nums" }}>
      <span style={{ color: COLOR.ink2, minWidth: 30 }}>{fmtTime(seek * parseTime(track.duration))}</span>
      <div style={{ flex: 1, position: "relative", height: 2 }}>
        <div style={{ position: "absolute", inset: 0, background: COLOR.rule }} />
        <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${seek * 100}%`, background: accent.accent }} />
        <div style={{ position: "absolute", left: `${seek * 100}%`, top: "50%", width: 2, height: 14, background: accent.accent, transform: "translate(-1px, -50%)" }} />
      </div>
      <span style={{ color: COLOR.ink3, minWidth: 30, textAlign: "right" }}>{track.duration}</span>
    </div>
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 36 }}>
      <Icon name="prev" size={22} color={COLOR.ink2} />
      <div style={{ width: 52, height: 52, borderRadius: "50%", background: accent.accent, color: accent.ink, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <Icon name="pause" size={22} />
      </div>
      <Icon name="next" size={22} color={COLOR.ink2} />
    </div>
  </div>
);

export interface NowPlayingProps {
  track: Track;
  accent: AccentSet;
  active: number;
  sweep?: number;
  roman?: number;
  seek?: number;
}

/**
 * The app's Now Playing screen at phone artboard size (390 x 844). Its root
 * isolates the inner z-indexes so screens can be stacked for wipes.
 */
export const NowPlayingScreen: React.FC<NowPlayingProps> = ({ track, accent, active, sweep, roman = 1, seek = 0.38 }) => (
  <div style={{ position: "relative", isolation: "isolate", width: PHONE.width, height: PHONE.height, background: COLOR.bg0, color: COLOR.ink1 }}>
    <PhoneAmbient track={track} />
    <StatusBar />
    <Header track={track} accent={accent} roman={roman} />
    <div style={{ position: "relative", zIndex: 2, padding: "4px 0 8px" }}>
      <LyricsList lines={track.lyrics} lang={track.lang} accent={accent} active={active} sweep={sweep} roman={roman} height={LYRICS_HEIGHT} />
    </div>
    <Transport track={track} accent={accent} seek={seek} />
  </div>
);
