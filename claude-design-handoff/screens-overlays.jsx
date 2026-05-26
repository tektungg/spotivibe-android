/* Spotivibe — Floating overlay (mini + expanded), share card, notification */

// ─── Floating overlay states — shown inside a host app context ──────
// We'll fake a host: a generic chat or browser layer behind, with the
// overlay sitting on top. Distinctive: editorial chrome, hairline border,
// monospace meta strip, instant-recognizable as "Spotivibe".

function HostBackdrop({ theme = 'dark', children }) {
  return (
    <div style={{
      position: 'relative', width: '100%', height: '100%',
      background: theme === 'dark' ? 'oklch(0.18 0.005 270)' : 'oklch(0.94 0.005 90)',
      overflow: 'hidden',
    }}>
      {/* Fake host UI — chat bubbles */}
      <div style={{ padding: '40px 18px', display: 'flex', flexDirection: 'column', gap: 8, opacity: 0.5 }}>
        <div className="t-mono-up" style={{ color: theme === 'dark' ? 'oklch(0.55 0.01 270)' : 'oklch(0.5 0.01 270)' }}>HOST APP · CHAT</div>
        {[180, 220, 140, 260, 200].map((w, i) => (
          <div key={i} style={{
            alignSelf: i % 2 ? 'flex-end' : 'flex-start',
            width: w, height: 38,
            background: theme === 'dark' ? 'oklch(0.24 0.01 270)' : 'oklch(0.88 0.01 90)',
            borderRadius: 16,
          }} />
        ))}
      </div>
      {children}
    </div>
  );
}

// Compact bar variant
function OverlayMini({ track, theme = 'dark' }) {
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ background: 'transparent' }}>
      <HostBackdrop theme={theme}>
        <StatusBar />
        <div style={{
          position: 'absolute', left: 18, right: 18, top: 130,
          background: 'var(--bg-0)',
          border: '1px solid var(--rule)',
          boxShadow: '0 20px 60px oklch(0 0 0 / 0.4)',
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '10px 12px',
        }}>
          <AlbumCover track={track} size={36} />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div className="t-mono-up" style={{ color: 'var(--accent)', fontSize: 9, marginBottom: 2 }}>● LIVE</div>
            <div className="t-news" style={{
              fontSize: 14, lineHeight: 1.15, color: 'var(--ink-1)',
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
            }}>{track.lyrics.find(l => l.active).k}</div>
          </div>
          <button style={iconBtn}><Icon name="expand" size={14} stroke={1.2} /></button>
          <div style={{ width: 1, height: 22, background: 'var(--rule)' }} />
          <button style={iconBtn}><Icon name="close" size={14} stroke={1.2} /></button>
        </div>

        {/* Annotation tag */}
        <div style={{
          position: 'absolute', left: 24, top: 198,
          fontFamily: 'JetBrains Mono', fontSize: 9, color: 'oklch(0.5 0.05 30)',
          letterSpacing: '0.12em', textTransform: 'uppercase',
        }}>
          ↑ DRAG TO REPOSITION · SNAPS TO EDGES
        </div>
      </HostBackdrop>
    </PhoneShell>
  );
}

// Expanded card variant
function OverlayExpanded({ track, theme = 'dark' }) {
  const lines = track.lyrics;
  const activeIdx = lines.findIndex(l => l.active);
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ background: 'transparent' }}>
      <HostBackdrop theme={theme}>
        <StatusBar />
        <div style={{
          position: 'absolute', left: 14, right: 14, top: 100,
          background: 'var(--bg-0)',
          border: '1px solid var(--rule)',
          boxShadow: '0 20px 60px oklch(0 0 0 / 0.4)',
        }}>
          {/* Drag handle */}
          <div style={{
            padding: '8px 14px', display: 'flex', alignItems: 'center', gap: 10,
            borderBottom: '1px solid var(--rule-soft)',
          }}>
            <Icon name="drag" size={14} style={{ color: 'var(--ink-3)' }} />
            <span className="t-mono-up" style={{ color: 'var(--ink-3)', fontSize: 9 }}>OVERLAY · EXPANDED</span>
            <button style={{ ...iconBtn, marginLeft: 'auto' }}><Icon name="pip" size={12} /></button>
            <button style={iconBtn}><Icon name="close" size={12} /></button>
          </div>
          {/* Header */}
          <div style={{ padding: '12px 14px', display: 'flex', gap: 10, borderBottom: '1px solid var(--rule-soft)' }}>
            <AlbumCover track={track} size={48} />
            <div style={{ minWidth: 0, flex: 1 }}>
              <div className="t-mono-up" style={{ color: 'var(--ink-3)', fontSize: 9 }}>{track.album}</div>
              <div className="t-serif" style={{ fontSize: 17, letterSpacing: '-0.01em', lineHeight: 1.1, marginTop: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{track.title}</div>
              <div className="t-news-i" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{track.artist}</div>
            </div>
          </div>
          {/* Lyrics — 3 lines */}
          <div style={{ padding: '12px 14px 6px' }}>
            {lines.slice(activeIdx - 1, activeIdx + 2).map((l, i) => {
              const realIdx = activeIdx - 1 + i;
              const state = lyricState(realIdx, activeIdx);
              return (
                <div key={i} style={{ padding: '4px 0' }}>
                  <div className="t-news" style={{
                    fontSize: state === 'active' ? 17 : 14,
                    fontWeight: state === 'active' ? 500 : 400,
                    color: state === 'active' ? 'var(--accent)' : 'var(--ink-3)',
                    lineHeight: 1.2,
                  }}>{l.k}</div>
                  <div className="t-news-i" style={{
                    fontSize: state === 'active' ? 11 : 10,
                    color: 'var(--accent-dim)',
                  }}>{l.r}</div>
                </div>
              );
            })}
          </div>
          {/* Mini transport */}
          <div style={{ padding: '8px 14px 12px', borderTop: '1px solid var(--rule-soft)', display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="t-mono tabular" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{track.elapsed}</span>
            <div style={{ flex: 1, height: 1, background: 'var(--rule)', position: 'relative' }}>
              <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '38%', background: 'var(--accent)' }} />
            </div>
            <span className="t-mono tabular" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{track.duration}</span>
            <div style={{ width: 1, height: 14, background: 'var(--rule)' }} />
            <button style={iconBtn}><Icon name="prev" size={14} style={{ color: 'var(--ink-2)' }} /></button>
            <button style={iconBtn}><Icon name="pause" size={14} style={{ color: 'var(--accent)' }} /></button>
            <button style={iconBtn}><Icon name="next" size={14} style={{ color: 'var(--ink-2)' }} /></button>
          </div>
        </div>
      </HostBackdrop>
    </PhoneShell>
  );
}

// ─── Persistent notification ─────────────────────────────────────
function NotificationView({ track, theme = 'dark' }) {
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{ background: 'transparent' }}>
      <div style={{
        position: 'absolute', inset: 0,
        background: theme === 'dark' ? 'oklch(0.10 0.005 270)' : 'oklch(0.92 0.005 90)',
      }}>
        <StatusBar />
        {/* Editorial clock + date */}
        <div style={{ padding: '16px 18px', textAlign: 'center' }}>
          <div className="t-display" style={{ fontSize: 72, lineHeight: 0.9, color: 'var(--ink-1)' }}>15:08</div>
          <div className="t-mono-up" style={{ color: 'var(--ink-3)', marginTop: 6 }}>TUESDAY · 26 MAY</div>
        </div>
        {/* Notification stack */}
        <div style={{ padding: '0 12px 12px' }}>
          <div className="t-mono-up" style={{ color: 'var(--ink-3)', padding: '0 8px 8px' }}>NOTIFICATIONS · 1</div>
          {/* Active Spotivibe notification */}
          <div style={{
            background: 'var(--bg-0)',
            border: '1px solid var(--rule)',
            borderLeft: '3px solid var(--accent)',
          }}>
            <div style={{ padding: '10px 14px', display: 'flex', gap: 10, alignItems: 'center', borderBottom: '1px solid var(--rule-soft)' }}>
              <div style={{ width: 20, height: 20, background: track.coverGradient, borderRadius: 2 }} />
              <div className="t-mono-up" style={{ color: 'var(--ink-3)', fontSize: 9 }}>SPOTIVIBE · NOW</div>
              <span style={{ marginLeft: 'auto' }} className="t-mono-up" style={{ color: 'var(--accent)', fontSize: 9 }}>● LIVE</span>
            </div>
            <div style={{ padding: '12px 14px' }}>
              <div className="t-serif" style={{ fontSize: 16, letterSpacing: '-0.01em', lineHeight: 1.15 }}>{track.title}</div>
              <div className="t-news-i" style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 8 }}>{track.artist}</div>
              <div className="t-news" style={{
                fontSize: 15, color: 'var(--accent)', lineHeight: 1.2, fontWeight: 500,
                padding: '8px 0 0', borderTop: '1px solid var(--rule-soft)',
              }}>
                <span style={{ color: 'var(--ink-3)', fontFamily: 'JetBrains Mono', fontSize: 9, letterSpacing: '0.12em', marginRight: 8 }}>NOW</span>
                {track.lyrics.find(l => l.active).k}
              </div>
              <div className="t-news-i" style={{ fontSize: 11, color: 'var(--accent-dim)', marginTop: 2 }}>
                {track.lyrics.find(l => l.active).r}
              </div>
            </div>
            {/* Action row */}
            <div style={{ display: 'flex', borderTop: '1px solid var(--rule-soft)' }}>
              <button style={notifBtn}><Icon name="prev" size={12} style={{ color: 'var(--ink-2)' }} /></button>
              <button style={notifBtn}><Icon name="pause" size={12} style={{ color: 'var(--accent)' }} /></button>
              <button style={notifBtn}><Icon name="next" size={12} style={{ color: 'var(--ink-2)' }} /></button>
              <button style={{ ...notifBtn, flex: 2, fontFamily: 'JetBrains Mono', fontSize: 10, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--ink-2)' }}>
                Open
              </button>
            </div>
          </div>
        </div>
      </div>
    </PhoneShell>
  );
}
const notifBtn = {
  flex: 1, padding: '10px',
  background: 'transparent', border: 'none',
  borderRight: '1px solid var(--rule-soft)',
  cursor: 'pointer',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
};

// ─── Share card (output of selection) ─────────────────────────────────────
function ShareCard({ track }) {
  const lines = track.lyrics;
  const activeIdx = lines.findIndex(l => l.active);
  // Pick 3 selected lines around active
  const selected = lines.slice(activeIdx - 1, activeIdx + 2);
  return (
    <div className={`acc-${track.accent}`} style={{
      width: '100%', height: '100%',
      background: 'oklch(0.96 0.005 80)',
      padding: 24,
      fontFamily: 'JetBrains Mono, monospace',
      color: 'oklch(0.15 0.012 270)',
      display: 'flex', flexDirection: 'column',
      position: 'relative', overflow: 'hidden',
    }}>
      {/* Top eyebrow */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div className="t-mono-up" style={{ color: 'oklch(0.45 0.01 270)' }}>SPOTIVIBE — LYRIC CARD</div>
        <div className="t-mono-up tabular" style={{ color: 'oklch(0.45 0.01 270)' }}>{track.elapsed} / {track.duration}</div>
      </div>
      <div style={{ height: 1, background: 'oklch(0.2 0.012 270)', margin: '12px 0 20px' }} />

      {/* Album block */}
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start', marginBottom: 22 }}>
        <div style={{
          width: 84, height: 84, background: track.coverGradient,
          position: 'relative', overflow: 'hidden',
        }}>
          <div style={{ position: 'absolute', inset: 0, backgroundImage: "radial-gradient(oklch(0 0 0 / 0.18) 0.5px, transparent 0.8px)", backgroundSize: '3px 3px', mixBlendMode: 'multiply' }} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="t-mono-up" style={{ color: 'oklch(0.45 0.01 270)' }}>{track.album} · {track.year}</div>
          <div className="t-serif" style={{ fontSize: 26, lineHeight: 1.05, letterSpacing: '-0.015em', marginTop: 4 }}>{track.title}</div>
          <div className="t-news-i" style={{ fontSize: 13, color: 'oklch(0.4 0.01 270)', marginTop: 2 }}>{track.artist}</div>
        </div>
      </div>

      {/* Selected lyric block — big accent quotation */}
      <div style={{ flex: 1, padding: '8px 0', position: 'relative' }}>
        <span style={{
          position: 'absolute', left: -8, top: -20,
          fontFamily: 'Instrument Serif', fontSize: 120, lineHeight: 1, color: 'var(--accent)', opacity: 0.25,
        }}>“</span>
        {selected.map((l, i) => (
          <div key={i} style={{ padding: '8px 0' }}>
            <div className="t-news" style={{
              fontSize: 22, lineHeight: 1.18, fontWeight: 500,
              color: i === 1 ? 'var(--accent)' : 'oklch(0.18 0.012 270)',
              letterSpacing: '-0.005em',
            }}>{l.k}</div>
            <div className="t-news-i" style={{
              fontSize: 12, color: 'var(--accent-dim)', marginTop: 2,
            }}>{l.r}</div>
          </div>
        ))}
      </div>

      {/* Bottom colophon */}
      <div>
        <div style={{ height: 1, background: 'oklch(0.2 0.012 270)', marginBottom: 12 }} />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <div className="t-mono-up" style={{ color: 'oklch(0.45 0.01 270)' }}>SHARED VIA</div>
            <div className="t-serif" style={{ fontSize: 22, lineHeight: 1, marginTop: 2 }}>Spotivibe</div>
          </div>
          {/* QR placeholder */}
          <div style={{
            width: 44, height: 44, position: 'relative',
            background: 'oklch(0.15 0.012 270)',
            display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gridTemplateRows: 'repeat(6, 1fr)',
            gap: 1, padding: 3,
          }}>
            {Array.from({length: 36}).map((_, i) => (
              <div key={i} style={{ background: Math.random() > 0.5 ? 'oklch(0.96 0.005 80)' : 'transparent' }} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { OverlayMini, OverlayExpanded, NotificationView, ShareCard });
