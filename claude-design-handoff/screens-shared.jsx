/* Spotivibe — shared primitives + sample data
   ----------------------------------------------------------------
   Album art placeholders, icons, status bar, sample lyric data.
   Loaded before all screen files. Exposes via window.SV.
*/

// ─── Sample tracks ────────────────────────────────────────────
const TRACKS = {
  coral: {
    accent: 'coral',
    title: '내일의 너에게',
    titleRoman: 'Naeil-ui Neoege',
    titleEn: 'To You, Tomorrow',
    artist: 'YIEUN',
    album: 'PAPER MOON',
    year: '2025',
    duration: '3:42',
    elapsed: '1:24',
    bpm: 128,
    key: 'F♯m',
    lang: 'KOR',
    track: '04',
    coverGradient: 'radial-gradient(ellipse 80% 60% at 30% 20%, oklch(0.88 0.18 35), transparent 60%), radial-gradient(ellipse 90% 70% at 70% 80%, oklch(0.55 0.20 15), transparent 70%), linear-gradient(135deg, oklch(0.32 0.14 25), oklch(0.22 0.08 350))',
    lyrics: [
      { t: -3, k: '오, 오, 오',                 r: 'oh, oh, oh' },
      { t: -2, k: '오',                          r: 'oh' },
      { t: -1, k: '내일의 너에게 편지를 써',     r: 'naeil-ui neo-ege pyeonji-reul sseo' },
      { t:  0, k: '오늘의 내가 미안하다고',       r: 'oneul-ui nae-ga mianhada-go', active: true },
      { t:  1, k: '눈을 감으면 들리는 목소리',   r: 'nun-eul gameumyeon deullineun moksori' },
      { t:  2, k: '잊고 있던 그날의 약속',        r: 'itgo itdeon geunal-ui yaksok' },
      { t:  3, k: '다시 한번, 다시 한번만',       r: 'dasi hanbeon, dasi hanbeon-man' },
      { t:  4, k: '나는 너의 봄이 될게',          r: 'naneun neo-ui bom-i doel-ge' },
    ],
  },
  violet: {
    accent: 'violet',
    title: '夜明けのスタンス',
    titleRoman: 'Yoake no Stance',
    titleEn: 'Dawn Stance',
    artist: 'AOI',
    album: 'NEON HEIAN',
    year: '2025',
    duration: '4:18',
    elapsed: '2:07',
    bpm: 96,
    key: 'Cm',
    lang: 'JPN',
    track: '02',
    coverGradient: 'radial-gradient(ellipse at 20% 30%, oklch(0.55 0.22 290), transparent 55%), radial-gradient(ellipse at 80% 70%, oklch(0.35 0.18 270), transparent 60%), linear-gradient(160deg, oklch(0.22 0.10 290), oklch(0.18 0.08 320))',
    lyrics: [
      { t: -3, k: '街の灯が消える前に',          r: 'machi no hi ga kieru mae ni' },
      { t: -2, k: '君に伝えたい',                 r: 'kimi ni tsutaetai' },
      { t: -1, k: 'まだ言葉にできない',           r: 'mada kotoba ni dekinai' },
      { t:  0, k: '夜明けのスタンス',             r: 'yoake no sutansu', active: true },
      { t:  1, k: '僕らの距離は',                 r: 'bokura no kyori wa' },
      { t:  2, k: '光より速く',                   r: 'hikari yori hayaku' },
      { t:  3, k: '消えていくのに',               r: 'kieteiku noni' },
      { t:  4, k: 'なぜ振り返るの',               r: 'naze furikaeru no' },
    ],
  },
  jade: {
    accent: 'jade',
    title: '未來進行式',
    titleRoman: 'Wèilái Jìnxíngshì',
    titleEn: 'Future Continuous',
    artist: 'LIN HAO',
    album: 'CASSETTE THEORY',
    year: '2024',
    duration: '3:55',
    elapsed: '0:48',
    bpm: 112,
    key: 'A♭',
    lang: 'ZHO',
    track: '07',
    coverGradient: 'radial-gradient(ellipse at 30% 30%, oklch(0.72 0.18 165), transparent 55%), radial-gradient(ellipse at 70% 80%, oklch(0.45 0.14 180), transparent 60%), linear-gradient(140deg, oklch(0.28 0.12 165), oklch(0.18 0.06 200))',
    lyrics: [
      { t: -3, k: '時間是一條河',                r: 'shíjiān shì yītiáo hé' },
      { t: -2, k: '我們在岸上',                  r: 'wǒmen zài àn shàng' },
      { t: -1, k: '看著彼此的倒影',              r: 'kànzhe bǐcǐ de dàoyǐng' },
      { t:  0, k: '未來進行式',                  r: 'wèilái jìnxíngshì', active: true },
      { t:  1, k: '是現在的我',                  r: 'shì xiànzài de wǒ' },
      { t:  2, k: '愛著明天的你',                r: 'àizhe míngtiān de nǐ' },
      { t:  3, k: '在錄音帶倒轉之前',            r: 'zài lùyīndài dào zhuǎn zhīqián' },
      { t:  4, k: '把這句話留住',                r: 'bǎ zhè jù huà liúzhù' },
    ],
  },
};
window.TRACKS = TRACKS;

// ─── Status bar ────────────────────────────────────────────
function StatusBar({ time = '15:07', dark = true }) {
  return (
    <div className="sv-statusbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span>{time}</span>
      </div>
      <div className="right">
        <span style={{ fontSize: 10, opacity: 0.7 }}>5G</span>
        <Icon name="signal" size={12} />
        <Icon name="wifi" size={12} />
        <span style={{ fontSize: 10, marginLeft: 2 }}>66</span>
      </div>
      <div className="sv-notch" />
    </div>
  );
}

// ─── Hairline icon set ────────────────────────────────────────
function Icon({ name, size = 18, stroke = 1, style }) {
  const props = { width: size, height: size, viewBox: '0 0 24 24', className: 'sv-icon', style, strokeWidth: stroke };
  switch (name) {
    case 'play':       return <svg {...props}><polygon points="7,4 7,20 20,12" fill="currentColor" stroke="none"/></svg>;
    case 'pause':      return <svg {...props}><rect x="6" y="5" width="3.5" height="14" fill="currentColor" stroke="none"/><rect x="14.5" y="5" width="3.5" height="14" fill="currentColor" stroke="none"/></svg>;
    case 'prev':       return <svg {...props}><path d="M19 4L8 12l11 8z" fill="currentColor" stroke="none"/><path d="M6 4v16" stroke="currentColor" strokeWidth="1.5"/></svg>;
    case 'next':       return <svg {...props}><path d="M5 4l11 8-11 8z" fill="currentColor" stroke="none"/><path d="M18 4v16" stroke="currentColor" strokeWidth="1.5"/></svg>;
    case 'romanize':   return <svg {...props}><text x="2" y="17" fontFamily="JetBrains Mono" fontSize="11" fontWeight="500" fill="currentColor" stroke="none">Rm</text></svg>;
    case 'pip':        return <svg {...props}><rect x="3" y="4" width="18" height="16" rx="1"/><rect x="11" y="11" width="9" height="7" rx="1" fill="currentColor" stroke="none" opacity="0.6"/></svg>;
    case 'kebab':      return <svg {...props}><circle cx="12" cy="5" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="19" r="1.2" fill="currentColor" stroke="none"/></svg>;
    case 'close':      return <svg {...props}><path d="M5 5l14 14M19 5L5 19"/></svg>;
    case 'back':       return <svg {...props}><path d="M14 5l-7 7 7 7M7 12h13"/></svg>;
    case 'fullscreen': return <svg {...props}><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg>;
    case 'gear':       return <svg {...props}><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"/></svg>;
    case 'sun':        return <svg {...props}><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5"/></svg>;
    case 'moon':       return <svg {...props}><path d="M20 14.5A8 8 0 119.5 4a6.5 6.5 0 0010.5 10.5z"/></svg>;
    case 'logout':     return <svg {...props}><path d="M9 4H4v16h5M16 16l4-4-4-4M9 12h11"/></svg>;
    case 'external':   return <svg {...props}><path d="M14 4h6v6M20 4L11 13M18 14v5H5V6h5"/></svg>;
    case 'check':      return <svg {...props}><path d="M4 12l5 5L20 6"/></svg>;
    case 'share':      return <svg {...props}><path d="M8 12L16 6M8 12l8 6M8 12a3 3 0 11-6 0 3 3 0 016 0zM22 6a3 3 0 11-6 0 3 3 0 016 0zM22 18a3 3 0 11-6 0 3 3 0 016 0z"/></svg>;
    case 'github':     return <svg {...props}><path d="M9 19c-4 1.5-4-2-6-2.5M15 21v-3.5a3 3 0 00-.9-2.3c2.97-.3 6-1.5 6-6.5a4.7 4.7 0 00-1.3-3.3 4.4 4.4 0 00-.1-3.2s-1-.3-3.4 1.3a11.5 11.5 0 00-6 0C6.9 1.4 5.9 1.7 5.9 1.7a4.4 4.4 0 00-.1 3.2A4.7 4.7 0 004.5 8.2c0 5 3 6.2 6 6.5a3 3 0 00-.9 2.3V21"/></svg>;
    case 'signal':     return <svg {...props}><rect x="2" y="14" width="3" height="6" fill="currentColor" stroke="none"/><rect x="7" y="10" width="3" height="10" fill="currentColor" stroke="none"/><rect x="12" y="6" width="3" height="14" fill="currentColor" stroke="none"/><rect x="17" y="2" width="3" height="18" fill="currentColor" stroke="none"/></svg>;
    case 'wifi':       return <svg {...props}><path d="M2 9a17 17 0 0120 0M5 13a12 12 0 0114 0M8.5 17a7 7 0 017 0M12 21v0"/></svg>;
    case 'spotify':    return <svg {...props}><circle cx="12" cy="12" r="9"/><path d="M7 9c3-1 8-1 11 1M7.5 13c2.5-.8 6.5-.8 9 1M8 16c2-.6 5-.5 7 .6"/></svg>;
    case 'expand':     return <svg {...props}><path d="M4 4h6M4 4v6M20 4h-6M20 4v6M4 20h6M4 20v-6M20 20h-6M20 20v-6"/></svg>;
    case 'pin':        return <svg {...props}><path d="M12 22v-7M8 4h8l-1 6 3 3H6l3-3-1-6z"/></svg>;
    case 'drag':       return <svg {...props}><circle cx="9" cy="6" r="1" fill="currentColor" stroke="none"/><circle cx="15" cy="6" r="1" fill="currentColor" stroke="none"/><circle cx="9" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="15" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="9" cy="18" r="1" fill="currentColor" stroke="none"/><circle cx="15" cy="18" r="1" fill="currentColor" stroke="none"/></svg>;
    default: return null;
  }
}

// ─── Album cover ─────────────────────────────────────
function AlbumCover({ track, size = 56, style }) {
  return (
    <div style={{
      width: size, height: size, flexShrink: 0,
      background: track.coverGradient,
      borderRadius: 2,
      position: 'relative',
      overflow: 'hidden',
      boxShadow: '0 1px 0 oklch(1 0 0 / 0.08) inset, 0 8px 24px oklch(0 0 0 / 0.4)',
      ...style,
    }}>
      <div style={{
        position: 'absolute',
        inset: 0,
        backgroundImage: "radial-gradient(oklch(0 0 0 / 0.18) 0.7px, transparent 0.9px)",
        backgroundSize: `${Math.max(3, size/20)}px ${Math.max(3, size/20)}px`,
        mixBlendMode: 'multiply',
      }} />
      <div style={{
        position: 'absolute', left: 6, top: 6,
        fontFamily: 'JetBrains Mono, monospace',
        fontSize: Math.max(8, size/12), fontWeight: 500,
        color: 'oklch(1 0 0 / 0.7)', letterSpacing: '0.1em',
      }}>{track.album.split(' ')[0]}</div>
    </div>
  );
}

// ─── Blurred background (album art + halftone) ─────────────────
function AmbientBg({ track, className = '' }) {
  return (
    <div style={{ position: 'absolute', inset: 0, overflow: 'hidden', zIndex: 0 }} className={className}>
      <div style={{
        position: 'absolute', inset: '-15%',
        background: track.coverGradient,
        filter: 'blur(60px) saturate(1.3)',
        transform: 'scale(1.15)',
      }} />
      <div style={{
        position: 'absolute', inset: 0,
        backgroundImage: 'radial-gradient(oklch(0 0 0 / 0.55) 1px, transparent 1.2px)',
        backgroundSize: '4px 4px',
        mixBlendMode: 'multiply',
      }} />
      <div style={{
        position: 'absolute', inset: 0,
        background: 'linear-gradient(180deg, oklch(0 0 0 / 0.35) 0%, oklch(0 0 0 / 0.55) 50%, oklch(0 0 0 / 0.75) 100%)',
      }} />
    </div>
  );
}

function AmbientBgLight({ track }) {
  return (
    <div style={{ position: 'absolute', inset: 0, overflow: 'hidden', zIndex: 0 }}>
      <div style={{
        position: 'absolute', inset: '-15%',
        background: track.coverGradient,
        filter: 'blur(80px) saturate(0.5) brightness(1.6)',
        opacity: 0.35,
        transform: 'scale(1.15)',
      }} />
      <div style={{
        position: 'absolute', inset: 0,
        backgroundImage: 'radial-gradient(oklch(0.2 0 0 / 0.25) 0.7px, transparent 0.9px)',
        backgroundSize: '4px 4px',
        mixBlendMode: 'multiply',
      }} />
      <div style={{
        position: 'absolute', inset: 0,
        background: 'linear-gradient(180deg, oklch(0.97 0.01 85 / 0.55), oklch(0.97 0.01 85 / 0.85))',
      }} />
    </div>
  );
}

// ─── Magazine-style header (track meta + icons) ─────────────────
function NPHeader({ track, dark = true, style = {} }) {
  return (
    <div style={{
      position: 'relative', zIndex: 3,
      padding: '8px 18px 14px',
      display: 'grid',
      gridTemplateColumns: '44px 1fr auto',
      gap: 12,
      alignItems: 'center',
      ...style,
    }}>
      <AlbumCover track={track} size={44} />
      <div style={{ minWidth: 0, overflow: 'hidden' }}>
        <div className="t-mono-up" style={{
          color: 'var(--ink-3)', marginBottom: 3,
          display: 'flex', gap: 8, alignItems: 'center',
        }}>
          <span>{track.album}</span>
          <span style={{ width: 3, height: 3, borderRadius: '50%', background: 'currentColor' }} />
          <span>{track.year}</span>
        </div>
        <div className="t-serif" style={{
          fontSize: 18, color: 'var(--ink-1)',
          letterSpacing: '-0.01em', lineHeight: 1.1,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>{track.title}</div>
        <div className="t-news-i" style={{
          fontSize: 12, color: 'var(--ink-3)', marginTop: 1,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>{track.artist}</div>
      </div>
      <div style={{ display: 'flex', gap: 12, color: 'var(--ink-2)' }}>
        <button style={iconBtn}><Icon name="romanize" size={16} /></button>
        <button style={iconBtn}><Icon name="pip" size={16} /></button>
        <button style={iconBtn}><Icon name="kebab" size={16} /></button>
      </div>
    </div>
  );
}

const iconBtn = {
  border: 'none', background: 'transparent', color: 'currentColor',
  cursor: 'pointer', padding: 4, display: 'flex', alignItems: 'center', justifyContent: 'center',
};

// ─── Lyrics list ─────────────────────────────────────
function LyricLine({ line, state, showRoman = true, big = false, accent = 'var(--accent)' }) {
  const styles = {
    active:  { color: accent, opacity: 1 },
    near:    { color: 'var(--ink-2)', opacity: 0.85 },
    far:     { color: 'var(--ink-3)', opacity: 0.55 },
    distant: { color: 'var(--ink-4)', opacity: 0.35 },
  };
  const s = styles[state] || styles.near;
  const fontSize = big
    ? (state === 'active' ? 36 : 28)
    : (state === 'active' ? 26 : 20);
  return (
    <div style={{ padding: '6px 24px', textAlign: 'left' }}>
      <div className="t-news" style={{
        fontSize, lineHeight: 1.18, fontWeight: state === 'active' ? 500 : 400,
        color: s.color, opacity: s.opacity,
        letterSpacing: '-0.005em',
        transition: 'color 280ms ease, opacity 280ms ease, font-size 280ms ease',
      }}>{line.k}</div>
      {showRoman && (
        <div className="t-news-i" style={{
          fontSize: state === 'active' ? 14 : 12,
          color: 'var(--accent-dim)',
          opacity: s.opacity * 0.95,
          marginTop: 2, lineHeight: 1.3, fontWeight: 300,
        }}>{line.r}</div>
      )}
    </div>
  );
}

// classify line index relative to active line
function lyricState(idx, activeIdx) {
  const d = Math.abs(idx - activeIdx);
  if (d === 0) return 'active';
  if (d <= 1) return 'near';
  if (d <= 2) return 'far';
  return 'distant';
}

// ─── Seek bar + transport ─────────────────────────────────────
function Transport({ elapsed, duration, progress = 0.38, playing = true, accent = 'var(--accent)' }) {
  return (
    <div style={{
      position: 'relative', zIndex: 3,
      padding: '0 22px 22px',
    }}>
      {/* Hairline rule above transport */}
      <div className="sv-rule-soft" style={{ marginBottom: 16 }} />
      {/* Seek */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
        <span className="t-mono tabular" style={{ fontSize: 11, color: 'var(--ink-2)', minWidth: 30 }}>{elapsed}</span>
        <div style={{ flex: 1, position: 'relative', height: 2 }}>
          <div style={{ position: 'absolute', inset: 0, background: 'var(--rule)' }} />
          <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${progress*100}%`, background: accent }} />
          <div style={{
            position: 'absolute', left: `${progress*100}%`, top: '50%',
            width: 2, height: 14, background: accent,
            transform: 'translate(-1px, -50%)',
          }} />
        </div>
        <span className="t-mono tabular" style={{ fontSize: 11, color: 'var(--ink-3)', minWidth: 30, textAlign: 'right' }}>{duration}</span>
      </div>
      {/* Transport row */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 36 }}>
        <button style={iconBtn}><Icon name="prev" size={22} style={{ color: 'var(--ink-2)' }} /></button>
        <button style={{
          ...iconBtn,
          width: 52, height: 52, borderRadius: '50%',
          background: accent, color: 'var(--accent-ink)',
          justifyContent: 'center',
        }}><Icon name={playing ? 'pause' : 'play'} size={22} /></button>
        <button style={iconBtn}><Icon name="next" size={22} style={{ color: 'var(--ink-2)' }} /></button>
      </div>
    </div>
  );
}

// ─── Phone shell ─────────────────────────────────────
function PhoneShell({ accent = 'coral', theme = 'dark', children, style = {} }) {
  return (
    <div className={`sv-phone theme-${theme} acc-${accent}`} style={style}>
      {children}
    </div>
  );
}

Object.assign(window, {
  TRACKS, StatusBar, Icon, AlbumCover, AmbientBg, AmbientBgLight,
  NPHeader, LyricLine, lyricState, Transport, PhoneShell, iconBtn,
});
