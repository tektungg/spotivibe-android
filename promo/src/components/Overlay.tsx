import React from "react";
import { COLOR, FONT, PHONE, type AccentSet } from "../theme/tokens";
import type { Track } from "../data/tracks";
import { Halftone } from "./Backdrop";
import { Icon } from "./Icon";
import { LogoMark } from "./LogoMark";
import { LyricsList, displayFont, scriptFont } from "./LyricsList";
import { eyebrow } from "./NowPlayingScreen";
import { AlbumCover, StatusBar } from "./Phone";

const BUBBLE_WIDTHS = [180, 230, 140, 260, 200, 170, 240, 150];
const MINI_HEIGHT = 58;
const EXPANDED_EXTRA = 204;

/** Fake host app (a chat) the overlay floats above. `scroll` in px. */
export const HostChat: React.FC<{ scroll: number }> = ({ scroll }) => (
  <div style={{ position: "absolute", inset: 0, background: "#1d1e24" }}>
    <StatusBar />
    <div style={{ ...eyebrow, position: "relative", zIndex: 2, background: "#1d1e24", color: COLOR.ink4, padding: "14px 20px", borderBottom: `1px solid ${COLOR.ruleSoft}` }}>HOST APP · CHAT</div>
    <div style={{ padding: "20px 18px", display: "flex", flexDirection: "column", gap: 12, transform: `translateY(${-scroll}px)` }}>
      {[...BUBBLE_WIDTHS, ...BUBBLE_WIDTHS].map((w, i) => (
        <div key={i} style={{ alignSelf: i % 2 ? "flex-end" : "flex-start", width: w, height: 40 + (i % 3) * 14, background: i % 2 ? "#2f3a52" : "#2a2b33", borderRadius: 18 }} />
      ))}
    </div>
  </div>
);

/** Floating overlay: mini bar at expand = 0, expanded card at expand = 1. */
export const OverlayCard: React.FC<{ track: Track; accent: AccentSet; expand: number; active: number; sweep: number }> = ({
  track,
  accent,
  expand,
  active,
  sweep,
}) => {
  const line = track.lyrics[Math.round(active)];
  return (
    <div style={{ width: PHONE.width - 28, height: MINI_HEIGHT + EXPANDED_EXTRA * expand, background: COLOR.bg0, border: `1px solid ${COLOR.rule}`, boxShadow: "0 24px 60px rgba(0,0,0,0.55)", overflow: "hidden", position: "relative" }}>
      <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", gap: 12, padding: "10px 12px", opacity: Math.max(0, 1 - expand * 2) }}>
        <AlbumCover track={track} size={36} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ ...eyebrow, color: accent.accent, fontSize: 9, marginBottom: 2 }}>● LIVE</div>
          <div style={{ fontFamily: scriptFont(track.lang), fontSize: 15, color: COLOR.ink1, whiteSpace: "nowrap", overflow: "hidden" }}>{line.k}</div>
        </div>
        <Icon name="expand" size={14} color={COLOR.ink2} />
        <div style={{ width: 1, height: 22, background: COLOR.rule }} />
        <Icon name="close" size={14} color={COLOR.ink2} />
      </div>
      <div style={{ opacity: Math.max(0, expand * 2 - 1) }}>
        <div style={{ padding: "8px 14px", display: "flex", alignItems: "center", gap: 10, borderBottom: `1px solid ${COLOR.ruleSoft}` }}>
          <Icon name="drag" size={14} color={COLOR.ink3} />
          <span style={{ ...eyebrow, color: COLOR.ink3, fontSize: 9 }}>OVERLAY · EXPANDED</span>
          <span style={{ marginLeft: "auto" }}>
            <Icon name="close" size={12} color={COLOR.ink2} />
          </span>
        </div>
        <div style={{ padding: "12px 14px", display: "flex", gap: 10, borderBottom: `1px solid ${COLOR.ruleSoft}` }}>
          <AlbumCover track={track} size={48} />
          <div>
            <div style={{ ...eyebrow, color: COLOR.ink3, fontSize: 9 }}>{track.album}</div>
            <div style={{ fontFamily: displayFont(track.lang), fontSize: 18, color: COLOR.ink1, marginTop: 2 }}>{track.title}</div>
            <div style={{ fontFamily: FONT.news, fontStyle: "italic", fontSize: 11, color: COLOR.ink3 }}>{track.artist}</div>
          </div>
        </div>
        <LyricsList lines={track.lyrics} lang={track.lang} accent={accent} active={active} sweep={sweep} height={150} pitch={48} activeSize={18} passiveSize={14} anchor={0.32} padX={14} blur={false} />
      </div>
    </div>
  );
};

/** Lock screen behind the notification: blurred art, halftone, big clock. */
export const LockScreen: React.FC<{ track: Track; children: React.ReactNode }> = ({ track, children }) => (
  <div style={{ position: "absolute", inset: 0, background: COLOR.bg0, isolation: "isolate" }}>
    <div style={{ position: "absolute", inset: "-20%", background: track.coverGradient, filter: "blur(70px) saturate(1.2) brightness(0.7)" }} />
    <Halftone />
    <StatusBar />
    <div style={{ position: "relative", zIndex: 2 }}>
      <div style={{ textAlign: "center", marginTop: 40, fontFamily: FONT.display, fontSize: 112, color: COLOR.ink1, lineHeight: 1 }}>15:07</div>
      <div style={{ ...eyebrow, textAlign: "center", color: COLOR.ink2, marginTop: 10 }}>LOCKED · NOW PLAYING</div>
    </div>
    {children}
  </div>
);

/** Notification with prev / now / next lines, karaoke on the current one. */
export const NotificationCard: React.FC<{ track: Track; accent: AccentSet; active: number; sweep: number }> = ({ track, accent, active, sweep }) => (
  <div style={{ width: PHONE.width - 24, background: "rgba(20,21,27,0.94)", border: `1px solid ${COLOR.rule}`, borderRadius: 20, padding: "14px 16px 12px", boxShadow: "0 20px 50px rgba(0,0,0,0.5)" }}>
    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
      <LogoMark unit={0.28} accent={accent.accent} />
      <span style={{ ...eyebrow, fontSize: 10, color: COLOR.ink2 }}>SPOTIVIBE</span>
      <span style={{ ...eyebrow, fontSize: 10, color: COLOR.ink4 }}>· NOW</span>
      <span style={{ marginLeft: "auto", display: "flex", gap: 14, color: COLOR.ink2 }}>
        <Icon name="prev" size={13} />
        <Icon name="pause" size={13} />
        <Icon name="next" size={13} />
      </span>
    </div>
    <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
      <AlbumCover track={track} size={56} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <LyricsList lines={track.lyrics} lang={track.lang} accent={accent} active={active} sweep={sweep} height={124} pitch={40} activeSize={18} passiveSize={13} anchor={0.36} padX={0} blur={false} />
      </div>
    </div>
  </div>
);
