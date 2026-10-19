import React from "react";
import { AbsoluteFill, Easing, useCurrentFrame } from "remotion";
import { AmbientGlow } from "../components/Backdrop";
import { Chapter } from "../components/Chapter";
import { HostChat, LockScreen, NotificationCard, OverlayCard } from "../components/Overlay";
import { PhoneFrame } from "../components/Phone";
import { TRACKS } from "../data/tracks";
import { SPRING_BOUNCY, SPRING_SOFT, beat, lyricClock, progress, springAt } from "../lib/motion";
import { ACCENTS, COLOR, EASE, PHONE } from "../theme/tokens";

const OVERLAY_TRACK = TRACKS.violet;
const NOTIF_TRACK = TRACKS.jade;
const LINE_FRAMES = beat(2);
const SWITCH_AT = beat(6);
const DROP_AT = 14;
const DRAG = { start: 56, end: 100, fromY: 140, toY: 330 };
const TAP_AT = 112;
const PHONE_W = PHONE.width + PHONE.bezel * 2;
const PHONE_H = PHONE.height + PHONE.bezel * 2;

/** Finger dot that presses, drags the overlay down, then taps to expand. */
const Finger: React.FC<{ frame: number; y: number }> = ({ frame, y }) => {
  const visible = progress(frame, DRAG.start - 10, DRAG.start) * (1 - progress(frame, TAP_AT + 6, TAP_AT + 16));
  const press = frame >= TAP_AT && frame < TAP_AT + 8 ? 0.8 : 1;
  return <div style={{ position: "absolute", left: 200, top: y + 18, width: 46, height: 46, marginLeft: -23, borderRadius: "50%", background: "rgba(255,255,255,0.28)", border: "2px solid rgba(255,255,255,0.6)", opacity: visible, transform: `scale(${press})`, zIndex: 40 }} />;
};

const OverlayPhone: React.FC<{ frame: number }> = ({ frame }) => {
  const drop = springAt(frame, DROP_AT, SPRING_BOUNCY);
  const drag = progress(frame, DRAG.start, DRAG.end, EASE.inOut);
  const y = -120 + (DRAG.fromY + 120) * drop + (DRAG.toY - DRAG.fromY) * drag;
  const expand = progress(frame, TAP_AT, TAP_AT + 26, EASE.out);
  const { active, sweep } = lyricClock(frame, { origin: 0, base: 2, lineFrames: LINE_FRAMES });
  return (
    <PhoneFrame>
      <HostChat scroll={frame * 0.4} />
      <div style={{ position: "absolute", left: 14, top: y, zIndex: 30 }}>
        <OverlayCard track={OVERLAY_TRACK} accent={ACCENTS.violet} expand={expand} active={active} sweep={sweep} />
      </div>
      <Finger frame={frame} y={y} />
    </PhoneFrame>
  );
};

const NotificationPhone: React.FC<{ frame: number }> = ({ frame }) => {
  const drop = springAt(frame, SWITCH_AT + 20, SPRING_SOFT);
  const { active, sweep } = lyricClock(frame, { origin: SWITCH_AT, base: 3, lineFrames: LINE_FRAMES });
  return (
    <PhoneFrame>
      <LockScreen track={NOTIF_TRACK}>
        <div style={{ position: "absolute", left: 12, top: 300, transform: `translateY(${(1 - drop) * -420}px)`, opacity: drop }}>
          <NotificationCard track={NOTIF_TRACK} accent={ACCENTS.jade} active={active} sweep={sweep} />
        </div>
      </LockScreen>
    </PhoneFrame>
  );
};

/** Horizontal whip between the two halves, with motion blur. */
function whip(frame: number): { out: number; inn: number; blur: number } {
  const out = progress(frame, SWITCH_AT - 12, SWITCH_AT + 4, EASE.in);
  const inn = progress(frame, SWITCH_AT - 10, SWITCH_AT + 16, EASE.out);
  const blur = 14 * Math.sin(Math.PI * progress(frame, SWITCH_AT - 12, SWITCH_AT + 22, Easing.linear));
  return { out, inn, blur };
}

/** Accent light streak that crosses the frame with the whip. */
const WhipStreak: React.FC<{ frame: number }> = ({ frame }) => {
  const p = progress(frame, SWITCH_AT - 10, SWITCH_AT + 10, Easing.linear);
  if (p <= 0 || p >= 1) return null;
  const width = 900 * Math.sin(Math.PI * p);
  return <div style={{ position: "absolute", top: 538, height: 4, left: 1920 - 2400 * p, width, borderRadius: 2, background: ACCENTS.jade.accent, boxShadow: `0 0 40px 6px ${ACCENTS.jade.accent}`, opacity: 0.9 }} />;
};

export const S06Everywhere: React.FC = () => {
  const frame = useCurrentFrame();
  const { out, inn, blur } = whip(frame);
  const isFirstHalf = frame < SWITCH_AT + 4;
  // The halves overlap during the whip so no frame is ever empty.
  const isSecondHalf = frame >= SWITCH_AT - 10;
  const enter = springAt(frame, 0, SPRING_SOFT);
  return (
    <AbsoluteFill style={{ background: COLOR.bg0, overflow: "hidden" }}>
      <AmbientGlow gradient={isFirstHalf ? OVERLAY_TRACK.coverGradient : NOTIF_TRACK.coverGradient} opacity={0.28} />
      {isFirstHalf && (
        <div style={{ position: "absolute", left: 560 - PHONE_W / 2 - 1500 * out, top: 540 - PHONE_H / 2 + (1 - enter) * 300, transform: "scale(1.02)", filter: blur > 0.5 ? `blur(${blur}px)` : undefined }}>
          <OverlayPhone frame={frame} />
        </div>
      )}
      {isSecondHalf && (
        <div style={{ position: "absolute", left: 1360 - PHONE_W / 2 + 1500 * (1 - inn), top: 540 - PHONE_H / 2, transform: "scale(1.02)", filter: blur > 0.5 ? `blur(${blur}px)` : undefined }}>
          <NotificationPhone frame={frame} />
        </div>
      )}
      <WhipStreak frame={frame} />
      <Chapter copy={{ number: "05", label: "FLOATING OVERLAY", headline: "Floats over any app.", accentFrom: 2, sub: "DRAG · SNAP · TAP TO EXPAND" }} start={8} end={SWITCH_AT - 4} accent={ACCENTS.violet.accent} size={104} width={700} style={{ left: 1130, top: 330 }} />
      <Chapter copy={{ number: "06", label: "NOTIFICATION", headline: "Lyrics on your lock screen.", accentFrom: 3, sub: "PREV · NOW · NEXT, LIVE" }} start={SWITCH_AT + 8} end={beat(12)} accent={ACCENTS.jade.accent} size={104} width={720} style={{ left: 150, top: 330 }} />
    </AbsoluteFill>
  );
};
