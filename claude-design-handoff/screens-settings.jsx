/* Spotivibe — Settings, Connect, Empty states */

// ─── Settings ─────────────────────────────────────
function Settings({ track, theme = 'dark', section = 'top' }) {
  return (
    <PhoneShell accent={track.accent} theme={theme} style={{
      background: 'var(--bg-0)',
      fontFamily: 'JetBrains Mono, monospace',
    }}>
      <StatusBar />
      {/* Editorial header */}
      <div style={{ padding: '14px 22px 18px', borderBottom: '1px solid var(--rule)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 14 }}>
          <button style={iconBtn}><Icon name="back" size={18} /></button>
          <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>SPOTIVIBE / V0.2.0</div>
        </div>
        <div className="t-display" style={{ fontSize: 56, letterSpacing: '-0.025em', lineHeight: 0.95 }}>
          Settings.
        </div>
      </div>

      {/* Sections */}
      <div style={{ height: 692, overflow: 'hidden', position: 'relative' }}>
        <div style={{
          transform: section === 'top' ? 'translateY(0)' : 'translateY(-360px)',
          transition: 'transform 600ms var(--ease-out)',
        }}>
          {/* APPEARANCE */}
          <SettingsSection label="01" title="Appearance" hint="Theme & type">
            <SettingsRow label="Theme">
              <SegSelect options={['Dark', 'Light']} value={theme === 'dark' ? 'Dark' : 'Light'} />
            </SettingsRow>
            <SettingsRow label="Lyrics font size" trail="34sp · large">
              <Slider value={0.6} unit="12 → 56" />
            </SettingsRow>
          </SettingsSection>

          {/* LYRICS */}
          <SettingsSection label="02" title="Lyrics" hint="Sync, spacing, feedback">
            <SettingsRow label="Sync offset" trail="±2000 ms">
              <Slider value={0.5} unit="−2000 / 0 / +2000" twoSided />
            </SettingsRow>
            <SettingsRow label="Line spacing" trail="7 dp">
              <Slider value={0.35} unit="0 → 20" />
            </SettingsRow>
            <SettingsRow label="High contrast" trail="for outdoor use">
              <Toggle on={false} />
            </SettingsRow>
            <SettingsRow label="Scroll" hint="">
              <SegSelect options={['Smooth', 'Snap']} value="Smooth" />
            </SettingsRow>
            <SettingsRow label="Haptic feedback">
              <Toggle on={true} />
            </SettingsRow>
          </SettingsSection>

          {/* SPOTIFY */}
          <SettingsSection label="03" title="Spotify" hint="Connection">
            <div style={{ padding: '16px 22px 4px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--accent)' }} />
                <div>
                  <div className="t-news" style={{ fontSize: 16, color: 'var(--ink-1)' }}>Premium · Terhubung</div>
                  <div className="t-news-i" style={{ fontSize: 13, color: 'var(--ink-3)' }}>connected as tek@gmail.com</div>
                </div>
                <div style={{ marginLeft: 'auto' }} className="t-mono-up">
                  <span style={{ color: 'var(--accent)' }}>● ACTIVE</span>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
                <button style={{ flex: 1, padding: '12px', background: 'transparent', border: '1px solid var(--rule)', color: 'var(--ink-1)', fontFamily: 'JetBrains Mono', fontSize: 12, letterSpacing: '0.1em', textTransform: 'uppercase', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <Icon name="logout" size={14} /> Logout
                </button>
                <button style={{ flex: 1, padding: '12px', background: 'transparent', border: '1px solid var(--rule)', color: 'var(--ink-2)', fontFamily: 'JetBrains Mono', fontSize: 12, letterSpacing: '0.1em', textTransform: 'uppercase', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  Revoke <Icon name="external" size={12} />
                </button>
              </div>
            </div>
          </SettingsSection>

          {/* ABOUT */}
          <SettingsSection label="04" title="Colophon" hint="About this app">
            <div style={{ padding: '14px 22px 22px' }}>
              <div style={{ display: 'flex', gap: 32, marginBottom: 18 }}>
                <div>
                  <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>VERSION</div>
                  <div className="t-serif tabular" style={{ fontSize: 28, lineHeight: 1, marginTop: 4 }}>0.2.0</div>
                </div>
                <div>
                  <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>BUILD</div>
                  <div className="t-serif tabular" style={{ fontSize: 28, lineHeight: 1, marginTop: 4 }}>2026.05</div>
                </div>
                <div>
                  <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>LRCLIB</div>
                  <div className="t-serif" style={{ fontSize: 28, lineHeight: 1, marginTop: 4 }}>v3</div>
                </div>
              </div>
              <button style={{
                width: '100%', padding: '14px 16px',
                background: 'transparent', border: '1px solid var(--rule)',
                color: 'var(--ink-1)', cursor: 'pointer',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                fontFamily: 'JetBrains Mono', fontSize: 13,
              }}>
                <span><Icon name="github" size={14} stroke={1.4} style={{ verticalAlign: 'middle', marginRight: 10 }} /> github.com/tektungg/spotivibe</span>
                <Icon name="external" size={12} />
              </button>
              <div className="t-news-i" style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 18, textAlign: 'center' }}>
                Code. Vibe. Sing along.
              </div>
            </div>
          </SettingsSection>
        </div>
      </div>
    </PhoneShell>
  );
}

function SettingsSection({ label, title, hint, children }) {
  return (
    <div style={{ borderBottom: '1px solid var(--rule)' }}>
      <div style={{
        padding: '18px 22px 14px',
        display: 'flex', alignItems: 'baseline', gap: 14,
      }}>
        <span className="t-mono tabular" style={{ fontSize: 13, color: 'var(--ink-3)' }}>{label}</span>
        <span className="t-display" style={{ fontSize: 32, color: 'var(--ink-1)', lineHeight: 1 }}>{title}</span>
        <span style={{ flex: 1 }} />
        {hint && <span className="t-news-i" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{hint}</span>}
      </div>
      {children}
    </div>
  );
}
function SettingsRow({ label, trail, hint, children }) {
  return (
    <div style={{ padding: '12px 22px', borderTop: '1px solid var(--rule-soft)' }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
        <span className="t-serif" style={{ fontSize: 18, color: 'var(--ink-1)', letterSpacing: '-0.005em' }}>{label}</span>
        {trail && <span style={{ marginLeft: 'auto' }} className="t-mono tabular">
          <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>{trail}</span>
        </span>}
      </div>
      {children}
    </div>
  );
}
function Toggle({ on }) {
  return (
    <div style={{
      width: 44, height: 22,
      background: on ? 'var(--accent)' : 'var(--bg-3)',
      border: '1px solid ' + (on ? 'transparent' : 'var(--rule)'),
      borderRadius: 999,
      position: 'relative',
      marginLeft: 'auto',
      transition: 'all 180ms ease',
    }}>
      <div style={{
        position: 'absolute', top: 1, left: on ? 22 : 1,
        width: 18, height: 18, borderRadius: '50%',
        background: on ? 'var(--accent-ink)' : 'var(--ink-1)',
        transition: 'all 180ms ease',
      }} />
    </div>
  );
}
function SegSelect({ options, value }) {
  return (
    <div style={{ display: 'flex', border: '1px solid var(--rule)', width: 'fit-content', marginLeft: 'auto' }}>
      {options.map((o, i) => (
        <div key={i} style={{
          padding: '8px 16px',
          fontFamily: 'JetBrains Mono', fontSize: 11, letterSpacing: '0.12em', textTransform: 'uppercase',
          background: o === value ? 'var(--accent)' : 'transparent',
          color: o === value ? 'var(--accent-ink)' : 'var(--ink-2)',
          borderLeft: i > 0 ? '1px solid var(--rule)' : 'none',
        }}>{o}</div>
      ))}
    </div>
  );
}
function Slider({ value, unit, twoSided }) {
  return (
    <div>
      <div style={{ position: 'relative', height: 24, display: 'flex', alignItems: 'center' }}>
        {/* ticks */}
        <div style={{ position: 'absolute', left: 0, right: 0, top: '50%', height: 1, background: 'var(--rule)' }} />
        <div style={{ position: 'absolute', left: 0, right: 0, top: '50%', display: 'flex', justifyContent: 'space-between', transform: 'translateY(-50%)' }}>
          {Array.from({length: 21}).map((_, i) => {
            const filled = twoSided
              ? (value >= 0.5 ? (i >= 10 && i <= 10 + Math.round((value-0.5)*20)) : (i <= 10 && i >= 10 - Math.round((0.5-value)*20)))
              : i / 20 <= value;
            return (
              <span key={i} style={{
                width: 1, height: filled ? 8 : 4,
                background: filled ? 'var(--accent)' : 'var(--rule)',
              }} />
            );
          })}
        </div>
        {/* Handle */}
        <div style={{
          position: 'absolute', left: `${value*100}%`, top: '50%',
          transform: 'translate(-50%, -50%)',
          width: 2, height: 18, background: 'var(--ink-1)',
        }} />
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6 }}>
        <span className="t-mono" style={{ fontSize: 10, color: 'var(--ink-4)', letterSpacing: '0.1em' }}>{unit}</span>
      </div>
    </div>
  );
}

// ─── Connect screen ─────────────────────────────────────
function Connect({ theme = 'dark', accent = 'coral' }) {
  return (
    <PhoneShell accent={accent} theme={theme} style={{ position: 'relative' }}>
      <StatusBar />
      {/* Big editorial wordmark */}
      <div style={{ padding: '36px 22px 0', height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Top brand line */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>VOL.01 · ISSUE 02</div>
          <div className="t-mono-up" style={{ color: 'var(--ink-3)' }}>{new Date().getFullYear()}</div>
        </div>
        <div className="sv-rule-soft" style={{ marginTop: 12, marginBottom: 36 }} />

        {/* HERO */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          <div className="t-display" style={{
            fontSize: 84, lineHeight: 0.92, letterSpacing: '-0.035em',
            color: 'var(--ink-1)',
            marginBottom: 8,
          }}>
            Sing<br/>along.
          </div>
          <div className="t-news-i" style={{
            fontSize: 18, color: 'var(--ink-3)', lineHeight: 1.3,
            maxWidth: 280, marginBottom: 32,
          }}>
            Synced lyrics with romanization for K-pop, J-pop, and Mandopop — over your Spotify session.
          </div>

          {/* Big accent CTA */}
          <button style={{
            width: '100%', padding: '20px 24px',
            background: 'var(--accent)', border: 'none',
            color: 'var(--accent-ink)',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            cursor: 'pointer',
          }}>
            <span className="t-mono-up" style={{ fontSize: 12, fontWeight: 600 }}>CONNECT SPOTIFY</span>
            <Icon name="next" size={16} />
          </button>
          <div className="t-news-i" style={{
            fontSize: 13, color: 'var(--ink-3)', marginTop: 14, lineHeight: 1.4,
          }}>
            Pastikan Spotify app sudah ter-install dan login dengan akun Premium.
          </div>
        </div>

        {/* Bottom colophon */}
        <div style={{ paddingBottom: 28 }}>
          <div className="sv-rule-soft" style={{ marginBottom: 12 }} />
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span className="t-mono-up" style={{ color: 'var(--ink-3)' }}>SPOTIVIBE</span>
            <span className="t-mono-up" style={{ color: 'var(--ink-3)' }}>CODE · VIBE · SING</span>
          </div>
        </div>
      </div>
    </PhoneShell>
  );
}

// ─── Empty states ─────────────────────────────────────
function EmptyState({ kind, theme = 'dark', accent = 'coral' }) {
  const states = {
    nothing: {
      eyebrow: 'STATUS — IDLE',
      headline: 'Nothing\nplaying.',
      body: 'Open Spotify and press play on any song. Spotivibe will catch up.',
      cta: 'OPEN SPOTIFY',
    },
    notFound: {
      eyebrow: 'ERR — NO LYRICS',
      headline: 'Silent\nedition.',
      body: 'LRCLIB has no lyrics for this track yet. You can request them or contribute the timing.',
      cta: 'REQUEST LYRICS',
    },
    premium: {
      eyebrow: 'AUTH — UPGRADE',
      headline: 'Free tier\ncan only watch.',
      body: 'Spotify Premium is required to control playback. You can still see lyrics while a Premium device is playing.',
      cta: 'GO TO SPOTIFY',
    },
    rateLimit: {
      eyebrow: 'WAIT — REFRESHING',
      headline: 'A short\nintermission.',
      body: 'Reconnecting to your Spotify session. This will only take a moment.',
      cta: '',
    },
  };
  const s = states[kind];
  return (
    <PhoneShell accent={accent} theme={theme} style={{ position: 'relative' }}>
      <StatusBar />
      <div style={{ padding: '24px 22px', height: 'calc(100% - 38px)', display: 'flex', flexDirection: 'column' }}>
        <div className="t-mono-up" style={{ color: 'var(--accent)' }}>{s.eyebrow}</div>
        <div className="sv-rule-soft" style={{ marginTop: 10, marginBottom: 32 }} />

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          <div className="t-display" style={{
            fontSize: 72, lineHeight: 0.92, letterSpacing: '-0.03em',
            color: 'var(--ink-1)', marginBottom: 18,
            whiteSpace: 'pre-line',
          }}>{s.headline}</div>
          <div className="t-news-i" style={{
            fontSize: 16, color: 'var(--ink-3)', lineHeight: 1.4,
            maxWidth: 280, marginBottom: 28,
          }}>{s.body}</div>
          {s.cta && (
            <button style={{
              padding: '14px 18px', alignSelf: 'flex-start',
              background: 'transparent', border: '1px solid var(--ink-1)',
              color: 'var(--ink-1)', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: 10,
            }}>
              <span className="t-mono-up" style={{ fontSize: 11, fontWeight: 600 }}>{s.cta}</span>
              <Icon name="external" size={12} />
            </button>
          )}
        </div>

        {/* Decoration: pulse line for refreshing */}
        {kind === 'rateLimit' && (
          <div style={{ marginBottom: 80 }}>
            <div style={{
              height: 2, background: 'var(--rule)', overflow: 'hidden', position: 'relative',
            }}>
              <div style={{
                position: 'absolute', left: 0, top: 0, bottom: 0, width: '30%',
                background: 'var(--accent)',
                animation: 'svPulse 1.4s ease-in-out infinite',
              }} />
            </div>
            <style>{`@keyframes svPulse { 0% { transform: translateX(-100%); } 100% { transform: translateX(400%); } }`}</style>
          </div>
        )}

        <div style={{ paddingBottom: 28 }}>
          <div className="sv-rule-soft" style={{ marginBottom: 12 }} />
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span className="t-mono-up" style={{ color: 'var(--ink-3)' }}>SPOTIVIBE</span>
            <span className="t-mono-up tabular" style={{ color: 'var(--ink-3)' }}>{({nothing: 'NP-002', notFound: 'NP-404', premium: 'NP-403', rateLimit: 'NP-429'})[kind]}</span>
          </div>
        </div>
      </div>
    </PhoneShell>
  );
}

Object.assign(window, { Settings, Connect, EmptyState });
