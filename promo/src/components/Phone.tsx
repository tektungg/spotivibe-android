import React from "react";
import { COLOR, FONT, PHONE } from "../theme/tokens";
import type { Track } from "../data/catalog";
import { Icon } from "./Icon";

/** Halftone and album label drawn over a placeholder gradient cover. */
const PlaceholderMarks: React.FC<{ track: Track; size: number }> = ({ track, size }) => {
  const dot = Math.max(3, size / 20);
  return (
    <>
      <div style={{ position: "absolute", inset: 0, backgroundImage: `radial-gradient(rgba(0,0,0,0.18) ${dot / 6}px, transparent ${dot / 6 + 0.2}px)`, backgroundSize: `${dot}px ${dot}px`, mixBlendMode: "multiply" }} />
      <div style={{ position: "absolute", left: size * 0.1, top: size * 0.1, fontFamily: FONT.mono, fontSize: Math.max(8, size / 12), fontWeight: 500, color: "rgba(255,255,255,0.75)", letterSpacing: "0.1em" }}>
        {track.album.split(" ")[0]}
      </div>
    </>
  );
};

/** Album cover: real artwork, or a placeholder gradient with halftone and the album's first word. */
export const AlbumCover: React.FC<{ track: Track; size: number; style?: React.CSSProperties }> = ({ track, size, style }) => (
  <div
    style={{
      width: size,
      height: size,
      flexShrink: 0,
      background: track.cover,
      borderRadius: 2,
      position: "relative",
      overflow: "hidden",
      boxShadow: "0 1px 0 rgba(255,255,255,0.08) inset, 0 8px 24px rgba(0,0,0,0.4)",
      ...style,
    }}
  >
    {!track.isArtwork && <PlaceholderMarks track={track} size={size} />}
  </div>
);

export const StatusBar: React.FC<{ time?: string }> = ({ time = "15:07" }) => (
  <div
    style={{
      position: "relative",
      zIndex: 5,
      height: 38,
      padding: "0 22px",
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      fontFamily: FONT.mono,
      fontSize: 12,
      fontWeight: 500,
      letterSpacing: "0.04em",
      color: COLOR.ink1,
    }}
  >
    <span>{time}</span>
    <div style={{ display: "flex", gap: 6, alignItems: "center", opacity: 0.9 }}>
      <span style={{ fontSize: 10, opacity: 0.7 }}>5G</span>
      <Icon name="signal" size={12} />
      <Icon name="wifi" size={12} />
      <span style={{ fontSize: 10, marginLeft: 2 }}>66</span>
    </div>
    <div
      style={{
        position: "absolute",
        left: "50%",
        top: 9,
        width: 22,
        height: 22,
        marginLeft: -11,
        background: COLOR.black,
        borderRadius: 11,
      }}
    />
  </div>
);

/**
 * Phone body at the handoff's 390 x 844 artboard. The screen clips its
 * children; `glare` adds a moving specular highlight for 3D shots.
 */
export const PhoneFrame: React.FC<{
  children: React.ReactNode;
  background?: string;
  glare?: number;
  style?: React.CSSProperties;
}> = ({ children, background = COLOR.bg0, glare = 0, style }) => (
  <div
    style={{
      position: "relative",
      width: PHONE.width + PHONE.bezel * 2,
      height: PHONE.height + PHONE.bezel * 2,
      borderRadius: PHONE.radius + PHONE.bezel,
      padding: PHONE.bezel,
      background: "linear-gradient(145deg, #2c2d34 0%, #101115 45%, #1d1e24 100%)",
      boxShadow:
        "0 0 0 1px #45464e inset, 0 0 0 2px #0a0a0d, 0 60px 120px rgba(0,0,0,0.55), 0 20px 40px rgba(0,0,0,0.35)",
      ...style,
    }}
  >
    <div
      style={{
        position: "relative",
        width: PHONE.width,
        height: PHONE.height,
        borderRadius: PHONE.radius,
        overflow: "hidden",
        background,
        isolation: "isolate",
      }}
    >
      {children}
      {glare > 0 && (
        <div
          style={{
            position: "absolute",
            inset: 0,
            background: `linear-gradient(115deg, transparent ${glare * 100 - 30}%, rgba(255,255,255,0.09) ${glare * 100}%, transparent ${glare * 100 + 30}%)`,
            pointerEvents: "none",
            zIndex: 20,
          }}
        />
      )}
    </div>
  </div>
);
