/* Spotivibe — Now Playing screens (portrait + landscape) */

function NowPlaying({ track, theme = 'dark', focusIdx = 3 }) {
  const lines = track.lyrics;
  // Find active line; default to index 3 (the "active" lyric)
  const activeIdx = lines.findIndex(l => l.active) >= 0 ? lines.findIndex(l => l.active) : focusIdx;
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ position: 'relative' }}>
      {theme === 'dark' ? <AmbientBg track={track} /> : <AmbientBgLight track={track} />}
      <StatusBar />
      <NPHeader track={track} dark={theme === 'dark'} />

      {/* Lyrics scroll area */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: '12px 0 16px',
        height: 590,
        maskImage: 'linear-gradient(180deg, transparent 0, #000 12%, #000 80%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(180deg, transparent 0, #000 12%, #000 80%, transparent 100%)',
        overflow: 'hidden',
      }}>
        <div className="lyrics-track" style={{ paddingTop: 80 }}>
          {lines.map((l, i) => (
            <LyricLine key={i} line={l} state={lyricState(i, activeIdx)} />
          ))}
        </div>
      </div>

      <Transport elapsed={track.elapsed} duration={track.duration} progress={0.38} />
    </PhoneShell>
  );
}

// Per-word karaoke variant: shows mid-line word highlight on active line.
// To demo: split active line into words, highlight first half.
function NowPlayingKaraokeWord({ track, theme = 'dark' }) {
  const lines = track.lyrics;
  const activeIdx = lines.findIndex(l => l.active);
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ position: 'relative' }}>
      {theme === 'dark' ? <AmbientBg track={track} /> : <AmbientBgLight track={track} />}
      <StatusBar />
      <NPHeader track={track} dark={theme === 'dark'} />
      <div style={{ padding: '40px 24px', height: 590, position: 'relative', zIndex: 2 }}>
        {lines.slice(activeIdx - 2, activeIdx + 3).map((l, i) => {
          const realIdx = activeIdx - 2 + i;
          const state = lyricState(realIdx, activeIdx);
          if (state === 'active') {
            const words = l.k.split(' ');
            const splitAt = Math.ceil(words.length * 0.6);
            return (
              <div key={i} style={{ padding: '14px 0' }}>
                <div className="t-news" style={{ fontSize: 28, lineHeight: 1.18, fontWeight: 500, letterSpacing: '-0.005em' }}>
                  {words.map((w, j) => (
                    <span key={j} style={{ color: j < splitAt ? 'var(--accent)' : 'var(--ink-3)', transition: 'color 80ms linear' }}>
                      {w}{j < words.length - 1 ? ' ' : ''}
                    </span>
                  ))}
                </div>
                <div className="t-news-i" style={{ fontSize: 14, color: 'var(--accent-dim)', marginTop: 4, fontWeight: 300 }}>{l.r}</div>
              </div>
            );
          }
          return <LyricLine key={i} line={l} state={state} />;
        })}
      </div>
      <Transport elapsed={track.elapsed} duration={track.duration} progress={0.38} />
    </PhoneShell>
  );
}

// ─── Selection mode ─────────────────────────────────────
function NowPlayingSelect({ track, theme = 'dark', selectedSet = new Set([2, 3, 4]) }) {
  const lines = track.lyrics;
  const activeIdx = lines.findIndex(l => l.active);
  const max = 5;
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ position: 'relative' }}>
      {theme === 'dark' ? <AmbientBg track={track} /> : <AmbientBgLight track={track} />}
      <StatusBar />
      {/* Replace header with selection mode header */}
      <div style={{
        position: 'relative', zIndex: 3,
        padding: '10px 18px 14px',
        display: 'flex', alignItems: 'center', gap: 14,
        borderBottom: '1px solid var(--rule-soft)',
      }}>
        <button style={iconBtn}><Icon name="close" size={18} /></button>
        <div style={{ flex: 1 }}>
          <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>SELECTION MODE</div>
          <div className="t-serif" style={{ fontSize: 18, letterSpacing: '-0.01em', lineHeight: 1.1, marginTop: 1 }}>
            {selectedSet.size} <span style={{ color: 'var(--ink-3)' }}>of {max} lines</span>
          </div>
        </div>
        {/* Counter pip */}
        <div style={{
          display: 'flex', gap: 3,
        }}>
          {Array.from({length: max}).map((_, i) => (
            <span key={i} style={{
              width: 6, height: 6, borderRadius: '50%',
              background: i < selectedSet.size ? 'var(--accent)' : 'var(--rule)',
            }} />
          ))}
        </div>
      </div>

      {/* Lyrics with selection */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: '12px 0 16px',
        height: 572,
        maskImage: 'linear-gradient(180deg, transparent 0, #000 8%, #000 90%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(180deg, transparent 0, #000 8%, #000 90%, transparent 100%)',
        overflow: 'hidden',
      }}>
        <div className="lyrics-track" style={{ paddingTop: 40, gap: 8 }}>
          {lines.map((l, i) => {
            const selected = selectedSet.has(i);
            return (
              <div key={i} style={{
                position: 'relative',
                background: selected ? 'var(--accent-ghost)' : 'transparent',
                transition: 'background 180ms ease',
              }}>
                {/* Left gutter checkmark */}
                <div style={{
                  position: 'absolute', left: 10, top: 14,
                  width: 16, height: 16,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: selected ? 'var(--accent)' : 'var(--rule)',
                }}>
                  {selected
                    ? <Icon name="check" size={14} stroke={1.6} />
                    : <span style={{ width: 9, height: 9, border: '1px solid currentColor' }} />
                  }
                </div>
                <div style={{ paddingLeft: 36, paddingRight: 24, padding: '10px 24px 10px 36px' }}>
                  <LyricLine line={l} state={lyricState(i, activeIdx)} />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Selection action bar */}
      <div style={{
        position: 'relative', zIndex: 3,
        padding: '14px 18px 20px',
        borderTop: '1px solid var(--rule-soft)',
        display: 'flex', alignItems: 'center', gap: 12,
        background: 'var(--bg-0)',
      }}>
        <button style={{
          flex: 1,
          padding: '12px 16px',
          background: 'transparent',
          border: '1px solid var(--rule)',
          color: 'var(--ink-2)',
          fontFamily: 'JetBrains Mono', fontSize: 12, letterSpacing: '0.1em', textTransform: 'uppercase', fontWeight: 500,
          cursor: 'pointer',
        }}>Cancel</button>
        <button style={{
          flex: 2,
          padding: '12px 16px',
          background: 'var(--accent)',
          border: 'none',
          color: 'var(--accent-ink)',
          fontFamily: 'JetBrains Mono', fontSize: 12, letterSpacing: '0.1em', textTransform: 'uppercase', fontWeight: 600,
          cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
        }}>
          <Icon name="share" size={14} stroke={1.4} />
          Share as card
        </button>
      </div>
    </PhoneShell>
  );
}

// ─── Karaoke fullscreen ─────────────────────────────────────
function KaraokeFullscreen({ track, theme = 'dark' }) {
  const lines = track.lyrics;
  const activeIdx = lines.findIndex(l => l.active);
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ position: 'relative' }}>
      {theme === 'dark' ? <AmbientBg track={track} /> : <AmbientBgLight track={track} />}
      <StatusBar />
      {/* Just a close X in top right */}
      <div style={{
        position: 'absolute', top: 38, right: 18, zIndex: 4,
        width: 36, height: 36, borderRadius: '50%',
        border: '1px solid var(--rule)',
        background: 'oklch(0 0 0 / 0.4)', backdropFilter: 'blur(8px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--ink-1)',
      }}>
        <Icon name="close" size={16} />
      </div>
      {/* Editorial track meta in top-left */}
      <div style={{ position: 'absolute', top: 50, left: 18, zIndex: 4 }}>
        <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>FULLSCREEN · {track.lang}</div>
        <div className="t-serif" style={{ fontSize: 14, color: 'var(--ink-2)', marginTop: 2 }}>{track.artist}</div>
      </div>

      {/* Massive lyrics centered */}
      <div style={{
        position: 'absolute', inset: 0, zIndex: 2,
        display: 'flex', flexDirection: 'column', justifyContent: 'center',
        padding: '0 24px',
      }}>
        {lines.slice(activeIdx - 1, activeIdx + 3).map((l, i) => {
          const realIdx = activeIdx - 1 + i;
          const state = lyricState(realIdx, activeIdx);
          return (
            <div key={i} style={{ padding: '18px 0', textAlign: 'left' }}>
              <div className="t-news" style={{
                fontSize: state === 'active' ? 46 : 30,
                lineHeight: 1.12,
                fontWeight: state === 'active' ? 500 : 400,
                letterSpacing: '-0.01em',
                color: state === 'active' ? 'var(--accent)' : (state === 'near' ? 'var(--ink-2)' : 'var(--ink-4)'),
                opacity: state === 'distant' ? 0.4 : 1,
                transition: 'all 280ms ease',
              }}>{l.k}</div>
              <div className="t-news-i" style={{
                fontSize: state === 'active' ? 20 : 14,
                color: 'var(--accent-dim)',
                marginTop: 4, fontWeight: 300,
                opacity: state === 'distant' ? 0.4 : 0.85,
              }}>{l.r}</div>
            </div>
          );
        })}
      </div>

      {/* Bottom minimal seek + time */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0, zIndex: 4,
        padding: '14px 22px 22px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 10 }}>
          <span className="t-mono tabular" style={{ fontSize: 11, color: 'var(--ink-2)' }}>{track.elapsed}</span>
          <div style={{ flex: 1, height: 1, background: 'var(--rule)', position: 'relative' }}>
            <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '38%', background: 'var(--accent)' }} />
          </div>
          <span className="t-mono tabular" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{track.duration}</span>
        </div>
        <div className="t-serif" style={{ fontSize: 13, color: 'var(--ink-3)', textAlign: 'center' }}>
          tap × to exit fullscreen
        </div>
      </div>
    </PhoneShell>
  );
}

// ─── Tablet landscape NowPlaying ─────────────────────────────────────
function NowPlayingTablet({ track, theme = 'dark' }) {
  const lines = track.lyrics;
  const activeIdx = lines.findIndex(l => l.active);
  return (
    <div className={`sv-tablet theme-${theme} acc-${track.accent}`} style={{
      width: '100%', height: '100%',
      background: 'var(--bg-0)',
      color: 'var(--ink-1)',
      position: 'relative', overflow: 'hidden',
      fontFamily: 'JetBrains Mono, monospace',
    }}>
      {theme === 'dark' ? <AmbientBg track={track} /> : <AmbientBgLight track={track} />}

      <div style={{ position: 'relative', zIndex: 2, height: '100%', display: 'grid', gridTemplateColumns: '1fr 1.4fr' }}>
        {/* LEFT — album cover + meta */}
        <div style={{
          padding: '48px 48px 36px',
          display: 'flex', flexDirection: 'column',
          borderRight: '1px solid var(--rule)',
          background: theme === 'dark' ? 'oklch(0 0 0 / 0.25)' : 'oklch(1 0 0 / 0.35)',
        }}>
          <div className="t-mono-up" style={{ color: 'var(--accent)', marginBottom: 18 }}>
            •&nbsp;&nbsp;NOW PLAYING · {track.lang}
          </div>
          {/* Album */}
          <div style={{
            width: '100%', aspectRatio: '1 / 1', maxWidth: 360,
            background: track.coverGradient,
            position: 'relative', overflow: 'hidden',
            boxShadow: '0 20px 60px oklch(0 0 0 / 0.5)',
            marginBottom: 32,
          }}>
            <div style={{ position: 'absolute', inset: 0, backgroundImage: "radial-gradient(oklch(0 0 0 / 0.18) 0.7px, transparent 0.9px)", backgroundSize: '6px 6px', mixBlendMode: 'multiply' }} />
            <div style={{ position: 'absolute', left: 18, top: 18, fontFamily: 'JetBrains Mono', fontSize: 11, fontWeight: 500, color: 'oklch(1 0 0 / 0.85)', letterSpacing: '0.15em' }}>
              {track.album}
            </div>
            <div style={{ position: 'absolute', left: 18, bottom: 18, fontFamily: 'JetBrains Mono', fontSize: 10, color: 'oklch(1 0 0 / 0.6)', letterSpacing: '0.12em' }}>
              SIDE A · {track.year}
            </div>
          </div>
          {/* Track meta */}
          <div className="t-serif" style={{ fontSize: 42, lineHeight: 1.04, letterSpacing: '-0.015em', marginBottom: 4 }}>
            {track.title}
          </div>
          <div className="t-news-i" style={{ fontSize: 18, color: 'var(--ink-3)', marginBottom: 18 }}>
            {track.titleRoman}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 18 }}>
            <div>
              <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>ARTIST</div>
              <div className="t-serif" style={{ fontSize: 20, marginTop: 2 }}>{track.artist}</div>
            </div>
            <div style={{ width: 1, height: 28, background: 'var(--rule)' }} />
            <div>
              <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>BPM</div>
              <div className="t-serif tabular" style={{ fontSize: 20, marginTop: 2 }}>{track.bpm}</div>
            </div>
            <div style={{ width: 1, height: 28, background: 'var(--rule)' }} />
            <div>
              <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>KEY</div>
              <div className="t-serif" style={{ fontSize: 20, marginTop: 2 }}>{track.key}</div>
            </div>
          </div>
          {/* Bottom transport */}
          <div style={{ marginTop: 'auto', paddingTop: 24, borderTop: '1px solid var(--rule)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 18 }}>
              <span className="t-mono tabular" style={{ fontSize: 12, color: 'var(--ink-2)' }}>{track.elapsed}</span>
              <div style={{ flex: 1, height: 2, background: 'var(--rule)', position: 'relative' }}>
                <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '38%', background: 'var(--accent)' }} />
                <div style={{ position: 'absolute', left: '38%', top: '50%', width: 2, height: 16, background: 'var(--accent)', transform: 'translate(-1px, -50%)' }} />
              </div>
              <span className="t-mono tabular" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{track.duration}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 36 }}>
              <button style={iconBtn}><Icon name="prev" size={24} style={{ color: 'var(--ink-2)' }} /></button>
              <button style={{ ...iconBtn, width: 60, height: 60, borderRadius: '50%', background: 'var(--accent)', color: 'var(--accent-ink)', justifyContent: 'center' }}><Icon name="pause" size={24} /></button>
              <button style={iconBtn}><Icon name="next" size={24} style={{ color: 'var(--ink-2)' }} /></button>
            </div>
          </div>
        </div>

        {/* RIGHT — lyrics */}
        <div style={{ position: 'relative', padding: '40px 0 40px' }}>
          <div style={{ position: 'absolute', top: 36, right: 36, display: 'flex', gap: 18, color: 'var(--ink-2)' }}>
            <button style={iconBtn}><Icon name="romanize" size={18} /></button>
            <button style={iconBtn}><Icon name="fullscreen" size={18} /></button>
            <button style={iconBtn}><Icon name="kebab" size={18} /></button>
          </div>
          <div style={{ paddingLeft: 56, paddingRight: 56 }}>
            <div className="t-mono-up" style={{ color: 'var(--ink-3)', marginBottom: 10 }}>LYRICS</div>
          </div>
          <div style={{
            height: 660,
            maskImage: 'linear-gradient(180deg, transparent 0, #000 8%, #000 92%, transparent 100%)',
            WebkitMaskImage: 'linear-gradient(180deg, transparent 0, #000 8%, #000 92%, transparent 100%)',
            overflow: 'hidden',
            paddingTop: 80,
          }}>
            <div className="lyrics-track" style={{ gap: 24 }}>
              {lines.map((l, i) => {
                const state = lyricState(i, activeIdx);
                const fontSize = state === 'active' ? 42 : (state === 'near' ? 28 : (state === 'far' ? 22 : 18));
                return (
                  <div key={i} style={{ padding: '0 56px' }}>
                    <div className="t-news" style={{
                      fontSize, lineHeight: 1.15, fontWeight: state === 'active' ? 500 : 400,
                      letterSpacing: '-0.005em',
                      color: state === 'active' ? 'var(--accent)' : (state === 'near' ? 'var(--ink-2)' : (state === 'far' ? 'var(--ink-3)' : 'var(--ink-4)')),
                      opacity: state === 'distant' ? 0.35 : 1,
                      transition: 'all 280ms ease',
                    }}>{l.k}</div>
                    <div className="t-news-i" style={{
                      fontSize: state === 'active' ? 18 : 13,
                      color: 'var(--accent-dim)', marginTop: 4, fontWeight: 300,
                      opacity: state === 'distant' ? 0.4 : 0.85,
                    }}>{l.r}</div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { NowPlaying, NowPlayingKaraokeWord, NowPlayingSelect, KaraokeFullscreen, NowPlayingTablet });
