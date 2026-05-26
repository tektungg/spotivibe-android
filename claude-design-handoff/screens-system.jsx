/* Spotivibe — Design system displays + animation reference

   System sheets (Cover, Type, Color, Tokens, Icons) are intrinsically
   sized: they declare their natural width via outer style and grow in
   height to fit their content. App.jsx measures them on mount and
   feeds the dimensions to DCArtboard so artboards always fit.
*/

// ─── System cover (the masthead) ─────────────────────────────────────
function SystemCover() {
  return (
    <div style={{
      width: 760,
      background: 'oklch(0.135 0.012 270)',
      color: 'oklch(0.965 0.008 80)',
      padding: '40px 44px',
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)' }}>SPOTIVIBE · VOL.02</div>
        <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)' }}>REDESIGN · MAY 2026</div>
      </div>
      <div style={{ height: 1, background: 'oklch(0.32 0.01 270)', marginTop: 16, marginBottom: 40 }} />

      <div style={{
        fontFamily: 'Instrument Serif', fontSize: 220, lineHeight: 0.88,
        letterSpacing: '-0.04em', color: 'oklch(0.965 0.008 80)',
      }}>
        Code.<br/>
        <span style={{ color: 'oklch(0.74 0.18 30)' }}>Vibe.</span><br/>
        <span style={{ fontStyle: 'italic', fontFamily: 'Newsreader', fontWeight: 300 }}>Sing along.</span>
      </div>

      <div style={{ marginTop: 48 }}>
        <div style={{ height: 1, background: 'oklch(0.32 0.01 270)', marginBottom: 18 }} />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)' }}>EDITORIAL DIRECTION</div>
            <div style={{ fontFamily: 'Newsreader', fontStyle: 'italic', fontWeight: 300, fontSize: 28, marginTop: 4, color: 'oklch(0.78 0.008 80)' }}>
              A lyrics-first music magazine, in your pocket.
            </div>
          </div>
          <div className="t-mono-up tabular" style={{ color: 'oklch(0.55 0.01 80)' }}>NO.01</div>
        </div>
      </div>
    </div>
  );
}

// ─── Typography spec sheet ─────────────────────────────────────
function TypeSheet() {
  return (
    <div style={{
      width: 580,
      background: 'oklch(0.965 0.008 80)',
      color: 'oklch(0.18 0.012 270)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>01 — TYPOGRAPHY</div>
      <div style={{ height: 1, background: 'oklch(0.18 0.012 270)', margin: '12px 0 28px' }} />

      {/* Instrument Serif */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 16, marginBottom: 8 }}>
          <span className="t-mono tabular" style={{ fontSize: 12, color: 'oklch(0.5 0.01 270)' }}>A1</span>
          <span className="t-mono-up">INSTRUMENT SERIF — DISPLAY · LATIN LYRICS</span>
        </div>
        <div style={{ fontFamily: 'Instrument Serif', fontSize: 80, lineHeight: 0.96, letterSpacing: '-0.025em' }}>
          A lyric is a small letter, sung.
        </div>
        <div style={{ display: 'flex', gap: 24, marginTop: 14, fontFamily: 'Instrument Serif' }}>
          <span style={{ fontSize: 32 }}>32 · h1</span>
          <span style={{ fontSize: 56 }}>56 · h0</span>
          <span style={{ fontSize: 72 }}>72 · display</span>
        </div>
      </div>

      {/* Newsreader */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 16, marginBottom: 8 }}>
          <span className="t-mono tabular" style={{ fontSize: 12, color: 'oklch(0.5 0.01 270)' }}>A2</span>
          <span className="t-mono-up">NEWSREADER — BODY LYRICS · ROMANIZATION</span>
        </div>
        <div style={{ fontFamily: 'Newsreader', fontSize: 28, lineHeight: 1.25 }}>
          내일의 너에게 편지를 써<br/>
          <span style={{ fontStyle: 'italic', fontWeight: 300, fontSize: 18, color: 'oklch(0.4 0.01 270)' }}>
            naeil-ui neo-ege pyeonji-reul sseo
          </span>
        </div>
      </div>

      {/* JetBrains Mono */}
      <div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 16, marginBottom: 8 }}>
          <span className="t-mono tabular" style={{ fontSize: 12, color: 'oklch(0.5 0.01 270)' }}>A3</span>
          <span className="t-mono-up">JETBRAINS MONO — METADATA · LABELS</span>
        </div>
        <div style={{ fontFamily: 'JetBrains Mono', fontSize: 14, lineHeight: 1.6 }}>
          <span style={{ letterSpacing: '0.12em' }}>BPM 128 · KEY F♯m · LANG KOR · TRK 04/12 · 01:24 / 03:42</span>
        </div>
        <div style={{ fontFamily: 'JetBrains Mono', fontSize: 11, marginTop: 8, color: 'oklch(0.5 0.01 270)', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
          Micro-label · 11px · 0.12em tracking · used everywhere
        </div>
      </div>

      <div style={{ marginTop: 32, display: 'flex', justifyContent: 'flex-end' }} className="t-mono-up">
        <span style={{ color: 'oklch(0.5 0.01 270)' }}>03 FACES · 1 ROLE EACH</span>
      </div>
    </div>
  );
}

// ─── Color palette + accent reactivity ─────────────────────────────────────
function ColorSheet() {
  const swatches = [
    { name: '--bg-0', val: 'oklch(0.135 0.012 270)' },
    { name: '--bg-1', val: 'oklch(0.165 0.012 270)' },
    { name: '--bg-2', val: 'oklch(0.205 0.012 270)' },
    { name: '--bg-3', val: 'oklch(0.245 0.012 270)' },
    { name: '--rule', val: 'oklch(0.32 0.01 270)' },
    { name: '--ink-4', val: 'oklch(0.38 0.01 80)' },
    { name: '--ink-3', val: 'oklch(0.55 0.01 80)' },
    { name: '--ink-2', val: 'oklch(0.78 0.008 80)' },
    { name: '--ink-1', val: 'oklch(0.965 0.008 80)' },
  ];
  const accents = [
    { name: 'CORAL  · K-pop',  val: 'oklch(0.74 0.18 30)' },
    { name: 'VIOLET · J-pop',  val: 'oklch(0.70 0.18 295)' },
    { name: 'JADE   · Mando',  val: 'oklch(0.76 0.16 165)' },
  ];
  return (
    <div style={{
      width: 760,
      background: 'oklch(0.135 0.012 270)',
      color: 'oklch(0.965 0.008 80)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)' }}>02 — COLOR</div>
      <div style={{ height: 1, background: 'oklch(0.32 0.01 270)', margin: '12px 0 24px' }} />

      {/* Bg + ink scale */}
      <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)', marginBottom: 12 }}>NEUTRALS — BACKGROUND & INK</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(9, 1fr)', gap: 1, marginBottom: 8 }}>
        {swatches.map((s, i) => (
          <div key={s.name} style={{
            background: s.val,
            aspectRatio: '1', position: 'relative',
            outline: '1px solid oklch(0.32 0.01 270)',
          }}>
            <div style={{
              position: 'absolute', bottom: 4, left: 4,
              fontFamily: 'JetBrains Mono', fontSize: 9,
              color: i > 3 ? 'oklch(0.135 0.012 270)' : 'oklch(0.78 0.008 80)',
              letterSpacing: '0.1em',
            }}>{s.name.replace('--', '')}</div>
          </div>
        ))}
      </div>

      {/* Accents */}
      <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)', marginTop: 28, marginBottom: 12 }}>ACCENTS — EXTRACTED FROM ALBUM ART (DYNAMIC)</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
        {accents.map((a) => (
          <div key={a.name} style={{ border: '1px solid oklch(0.32 0.01 270)' }}>
            <div style={{ height: 80, background: a.val }} />
            <div style={{ padding: '10px 12px' }}>
              <div className="t-mono" style={{ fontSize: 11, color: 'oklch(0.78 0.008 80)', letterSpacing: '0.06em' }}>{a.name}</div>
              <div className="t-mono" style={{ fontSize: 9, color: 'oklch(0.55 0.01 80)', marginTop: 2 }}>{a.val}</div>
            </div>
          </div>
        ))}
      </div>

      {/* How they reconcile */}
      <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)', marginTop: 28, marginBottom: 12 }}>USE — CHROMA & LIGHTNESS FIXED, HUE FOLLOWS ART</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
        {accents.map((a) => (
          <div key={a.name} style={{ background: 'oklch(0.205 0.012 270)', padding: 16 }}>
            <div style={{ width: 12, height: 12, background: a.val, marginBottom: 8 }} />
            <div style={{ fontFamily: 'Newsreader', fontSize: 18, color: a.val, fontWeight: 500 }}>active lyric line</div>
            <div style={{ fontFamily: 'Newsreader', fontStyle: 'italic', fontSize: 11, color: 'oklch(0.55 0.01 80)', marginTop: 2 }}>romanization stays neutral</div>
          </div>
        ))}
      </div>

      <div style={{ marginTop: 28, display: 'flex', justifyContent: 'flex-end' }}>
        <span className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)' }}>L ≈ 0.72–0.76 · C ≈ 0.16–0.18</span>
      </div>
    </div>
  );
}

// ─── Spacing, radius, motion tokens ─────────────────────────────────────
function TokenSheet() {
  return (
    <div style={{
      width: 620,
      background: 'oklch(0.965 0.008 80)',
      color: 'oklch(0.18 0.012 270)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>03 — SPACING · RADIUS · MOTION</div>
      <div style={{ height: 1, background: 'oklch(0.18 0.012 270)', margin: '12px 0 24px' }} />

      {/* Spacing */}
      <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)', marginBottom: 10 }}>SPACING — 4PX BASE</div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 14, marginBottom: 6, height: 70 }}>
        {[
          { n: 1, v: 4 }, { n: 2, v: 8 }, { n: 3, v: 12 }, { n: 4, v: 16 },
          { n: 5, v: 20 }, { n: 6, v: 24 }, { n: 8, v: 32 }, { n: 10, v: 40 }, { n: 12, v: 48 }, { n: 16, v: 64 },
        ].map((s) => (
          <div key={s.n} style={{ textAlign: 'center' }}>
            <div style={{ width: s.v, height: s.v, background: 'oklch(0.74 0.18 30)' }} />
            <div className="t-mono tabular" style={{ fontSize: 9, marginTop: 4, color: 'oklch(0.5 0.01 270)' }}>{s.v}</div>
            <div className="t-mono" style={{ fontSize: 8, color: 'oklch(0.5 0.01 270)' }}>s-{s.n}</div>
          </div>
        ))}
      </div>

      {/* Radius */}
      <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)', marginTop: 24, marginBottom: 10 }}>RADIUS — MOSTLY SQUARE</div>
      <div style={{ display: 'flex', gap: 18 }}>
        {[
          { n: 'r-0', v: 0, lbl: 'rule' },
          { n: 'r-1', v: 2, lbl: 'default' },
          { n: 'r-2', v: 6, lbl: 'chip' },
          { n: 'r-3', v: 12, lbl: 'sheet' },
          { n: 'r-pill', v: 999, lbl: 'pill' },
        ].map((r) => (
          <div key={r.n} style={{ textAlign: 'center' }}>
            <div style={{ width: 64, height: 46, background: 'oklch(0.18 0.012 270)', borderRadius: r.v }} />
            <div className="t-mono tabular" style={{ fontSize: 9, marginTop: 6, color: 'oklch(0.5 0.01 270)' }}>{r.v === 999 ? '∞' : `${r.v}px`}</div>
            <div className="t-mono" style={{ fontSize: 8, color: 'oklch(0.5 0.01 270)' }}>{r.n} · {r.lbl}</div>
          </div>
        ))}
      </div>

      {/* Motion */}
      <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)', marginTop: 24, marginBottom: 10 }}>MOTION</div>
      <div style={{ display: 'flex', gap: 18 }}>
        {[
          { n: 'd-fast', v: '140ms', lbl: 'micro · toggle' },
          { n: 'd-base', v: '240ms', lbl: 'default · color' },
          { n: 'd-slow', v: '480ms', lbl: 'lyric scroll' },
        ].map((m) => (
          <div key={m.n} style={{ flex: 1 }}>
            <div className="t-serif" style={{ fontSize: 32, lineHeight: 1, fontFamily: 'Instrument Serif' }}>{m.v}</div>
            <div className="t-mono" style={{ fontSize: 9, marginTop: 4, color: 'oklch(0.5 0.01 270)' }}>{m.n}</div>
            <div className="t-news-i" style={{ fontSize: 12, color: 'oklch(0.4 0.01 270)', marginTop: 2 }}>{m.lbl}</div>
          </div>
        ))}
      </div>
      <div className="t-mono" style={{ fontSize: 11, marginTop: 18, color: 'oklch(0.4 0.01 270)' }}>
        ease-out · cubic-bezier(0.16, 1, 0.3, 1) · <span className="t-news-i">snappy, never bouncy</span>
      </div>

      <div style={{ marginTop: 28, display: 'flex', justifyContent: 'flex-end' }} className="t-mono-up">
        <span style={{ color: 'oklch(0.5 0.01 270)' }}>JETPACK COMPOSE READY</span>
      </div>
    </div>
  );
}

// ─── Icon spec sheet ─────────────────────────────────────
function IconSheet() {
  const icons = [
    'play','pause','prev','next','romanize','pip','kebab','close',
    'back','fullscreen','gear','sun','moon','logout','external','check',
    'share','spotify','expand','pin','drag','wifi',
  ];
  return (
    <div style={{
      width: 720,
      background: 'oklch(0.135 0.012 270)',
      color: 'oklch(0.965 0.008 80)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)' }}>04 — ICONS · HAIRLINE · 1PX STROKE</div>
      <div style={{ height: 1, background: 'oklch(0.32 0.01 270)', margin: '12px 0 24px' }} />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 0 }}>
        {icons.map((name) => (
          <div key={name} style={{
            padding: 18,
            border: '1px solid oklch(0.245 0.012 270)',
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10,
          }}>
            <div style={{ color: 'oklch(0.965 0.008 80)' }}>
              <Icon name={name} size={26} stroke={1} />
            </div>
            <span className="t-mono" style={{ fontSize: 10, color: 'oklch(0.55 0.01 80)' }}>{name}</span>
          </div>
        ))}
      </div>

      <div className="t-mono-up" style={{ color: 'oklch(0.55 0.01 80)', marginTop: 28, marginBottom: 12 }}>SIZE SCALE</div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 18 }}>
        {[12, 14, 16, 18, 24, 32].map((s) => (
          <div key={s} style={{ textAlign: 'center' }}>
            <Icon name="play" size={s} stroke={1} />
            <div className="t-mono tabular" style={{ fontSize: 9, marginTop: 4, color: 'oklch(0.55 0.01 80)' }}>{s}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Accent reactivity proof — same NowPlaying screen, 3 accents ─
function AccentReactivity({ theme = 'dark' }) {
  const ts = [TRACKS.coral, TRACKS.violet, TRACKS.jade];
  return (
    <div style={{
      width: 1040,
      background: theme === 'dark' ? 'oklch(0.135 0.012 270)' : 'oklch(0.965 0.008 80)',
      color: theme === 'dark' ? 'oklch(0.965 0.008 80)' : 'oklch(0.18 0.012 270)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: theme === 'dark' ? 'oklch(0.55 0.01 80)' : 'oklch(0.5 0.01 270)' }}>05 — ACCENT REACTIVITY · 3 SONGS, 1 LAYOUT</div>
      <div style={{ height: 1, background: theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.18 0.012 270)', margin: '12px 0 24px' }} />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
        {ts.map((t) => (
          <div key={t.accent} className={`acc-${t.accent}`} style={{
            border: '1px solid ' + (theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.7 0.01 270)'),
            background: theme === 'dark' ? 'oklch(0.165 0.012 270)' : 'oklch(0.94 0.005 80)',
            padding: 18,
            position: 'relative',
            overflow: 'hidden',
          }}>
            {/* Mini blurred art header */}
            <div style={{
              position: 'absolute', inset: '0 0 auto 0', height: 70,
              background: t.coverGradient, filter: 'blur(30px) saturate(1.2)',
              opacity: 0.7,
            }} />
            <div style={{
              position: 'absolute', inset: 0,
              backgroundImage: 'radial-gradient(oklch(0 0 0 / 0.4) 1px, transparent 1.2px)',
              backgroundSize: '4px 4px',
              mixBlendMode: theme === 'dark' ? 'multiply' : 'multiply',
              height: 70,
            }} />
            <div style={{ position: 'relative' }}>
              <div className="t-mono-up" style={{ color: 'var(--accent)' }}>● {t.accent.toUpperCase()}</div>
              <div className="t-serif" style={{ fontSize: 22, lineHeight: 1.05, letterSpacing: '-0.015em', marginTop: 26, color: theme === 'dark' ? 'oklch(0.965 0.008 80)' : 'oklch(0.18 0.012 270)' }}>
                {t.title}
              </div>
              <div className="t-news-i" style={{ fontSize: 12, color: theme === 'dark' ? 'oklch(0.55 0.01 80)' : 'oklch(0.45 0.01 270)' }}>{t.artist}</div>

              <div style={{ height: 1, background: theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.18 0.012 270)', margin: '20px 0 14px' }} />

              {/* Active lyric */}
              <div className="t-news" style={{ fontSize: 18, fontWeight: 500, color: 'var(--accent)', lineHeight: 1.2, letterSpacing: '-0.005em' }}>
                {t.lyrics.find(l => l.active).k}
              </div>
              <div className="t-news-i" style={{ fontSize: 11, color: 'var(--accent-dim)', marginTop: 2 }}>
                {t.lyrics.find(l => l.active).r}
              </div>

              {/* Mini transport */}
              <div style={{ marginTop: 22, display: 'flex', alignItems: 'center', gap: 8 }}>
                <span className="t-mono tabular" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t.elapsed}</span>
                <div style={{ flex: 1, height: 1, background: theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.7 0.01 270)', position: 'relative' }}>
                  <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '38%', background: 'var(--accent)' }} />
                </div>
                <span className="t-mono tabular" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t.duration}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="t-news-i" style={{ marginTop: 22, fontSize: 14, color: theme === 'dark' ? 'oklch(0.78 0.008 80)' : 'oklch(0.35 0.01 270)', lineHeight: 1.5, maxWidth: 720 }}>
        Accent hue is extracted from album art at runtime. Chroma (~0.16–0.18) and lightness (~0.74) are <span style={{ color: 'var(--accent)' }}>fixed</span> so contrast against ink-1 stays predictable for any song, in any language.
      </div>
    </div>
  );
}

// ─── Track-change animation reference frames ─────────────────────
function AnimationStrip({ theme = 'dark' }) {
  // Same artboard, three states: 0ms (current track ending), 220ms (mid-cross), 440ms (next track in)
  const before = TRACKS.coral;
  const after = TRACKS.violet;
  const frames = [
    { t: '0 ms',   label: 'OUT — Current track', track: before, lyricIdx: 7, opacity: 1 },
    { t: '180 ms', label: 'CROSS — Lyrics fade + meta slides', track: before, lyricIdx: 7, opacity: 0.4, slide: -20 },
    { t: '360 ms', label: 'CROSS — New meta enters', track: after, lyricIdx: 0, opacity: 0.6, slide: 10 },
    { t: '540 ms', label: 'IN — Settled', track: after, lyricIdx: 3, opacity: 1 },
  ];
  return (
    <div style={{
      width: 1280,
      background: theme === 'dark' ? 'oklch(0.135 0.012 270)' : 'oklch(0.965 0.008 80)',
      color: theme === 'dark' ? 'oklch(0.965 0.008 80)' : 'oklch(0.18 0.012 270)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative', overflow: 'hidden',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: theme === 'dark' ? 'oklch(0.55 0.01 80)' : 'oklch(0.5 0.01 270)' }}>06 — TRACK CHANGE · ANIMATION FRAMES</div>
      <div style={{ height: 1, background: theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.18 0.012 270)', margin: '12px 0 24px' }} />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        {frames.map((f, i) => (
          <div key={i} className={`acc-${f.track.accent}`} style={{
            border: '1px solid ' + (theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.7 0.01 270)'),
            background: theme === 'dark' ? 'oklch(0.165 0.012 270)' : 'oklch(0.94 0.005 80)',
            padding: 14,
            position: 'relative',
            overflow: 'hidden',
            minHeight: 280,
          }}>
            {/* Header strip */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, transform: `translateY(${f.slide || 0}px)`, opacity: f.opacity }}>
              <div style={{ width: 28, height: 28, background: f.track.coverGradient }} />
              <div style={{ minWidth: 0 }}>
                <div className="t-serif" style={{ fontSize: 13, lineHeight: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 100 }}>{f.track.title}</div>
                <div className="t-news-i" style={{ fontSize: 9, color: theme === 'dark' ? 'oklch(0.55 0.01 80)' : 'oklch(0.45 0.01 270)' }}>{f.track.artist}</div>
              </div>
            </div>
            {/* Lyric body */}
            <div style={{ opacity: f.opacity }}>
              {f.track.lyrics.slice(Math.max(0, f.lyricIdx - 1), f.lyricIdx + 2).map((l, j) => {
                const realIdx = Math.max(0, f.lyricIdx - 1) + j;
                const state = realIdx === f.lyricIdx ? 'active' : (realIdx < f.lyricIdx ? 'far' : 'near');
                return (
                  <div key={j} style={{ padding: '4px 0' }}>
                    <div className="t-news" style={{
                      fontSize: state === 'active' ? 16 : 13,
                      fontWeight: state === 'active' ? 500 : 400,
                      color: state === 'active' ? 'var(--accent)' : (theme === 'dark' ? 'oklch(0.55 0.01 80)' : 'oklch(0.5 0.01 270)'),
                      lineHeight: 1.15,
                    }}>{l.k}</div>
                  </div>
                );
              })}
            </div>
            {/* Frame footer */}
            <div style={{
              position: 'absolute', left: 14, right: 14, bottom: 14,
              borderTop: '1px solid ' + (theme === 'dark' ? 'oklch(0.32 0.01 270)' : 'oklch(0.7 0.01 270)'),
              paddingTop: 8,
            }}>
              <div className="t-display tabular" style={{ fontSize: 22, lineHeight: 1, color: theme === 'dark' ? 'oklch(0.965 0.008 80)' : 'oklch(0.18 0.012 270)' }}>{f.t}</div>
              <div className="t-mono" style={{ fontSize: 9, color: theme === 'dark' ? 'oklch(0.55 0.01 80)' : 'oklch(0.5 0.01 270)', marginTop: 2, letterSpacing: '0.06em' }}>{f.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="t-news-i" style={{ marginTop: 18, fontSize: 14, color: theme === 'dark' ? 'oklch(0.78 0.008 80)' : 'oklch(0.35 0.01 270)', maxWidth: 740, lineHeight: 1.5 }}>
        Cross-fade timing: <span className="t-mono" style={{ letterSpacing: '0.06em' }}>540ms total · easeOut(0.16, 1, 0.3, 1)</span>. Old lyrics blur + dim while new meta slides 10px up into place. Album thumbnail crossfades through alpha — no flip.
      </div>
    </div>
  );
}

// ─── Replacements doc (what changes and why) ─────────────────────
function ReplacementsDoc() {
  const items = [
    { from: 'Round purple Spotify-like CTA pill', to: 'Square, full-width accent-colored CTA with monospace label', why: 'Editorial brevity. Pills read as consumer/social; squares read as magazine/print.' },
    { from: 'Material toggles + chip-style buttons', to: 'Hairline-bordered seg controls + custom tick sliders', why: 'Material chrome is the loudest signal of "generic Android app". Replacing it with hairline + tick marks gets us a station-board feel without sacrificing affordance.' },
    { from: 'Horizontal row of equal-weight icon buttons in header', to: 'Hairline 1px monoline icons + monospace track meta strip directly under', why: 'Pulls more weight onto track metadata (album, year, BPM, key, lang) — the magazine feel.' },
    { from: 'Lyric font: rounded sans (current)', to: 'Newsreader (body) + Instrument Serif (display)', why: 'Serifs read more "lyrical" and editorial; rounded sans reads as UI text.' },
    { from: 'Selection mode: just an accent border on lines', to: 'Subtle background tint + left-gutter checkmark + N/5 dot counter in header', why: 'Border-only is easy to miss; gutter checkmark is glanceable; user requested subtle.' },
    { from: 'Generic blurred album bg', to: 'Blurred art + halftone dot screen + bottom gradient', why: 'Halftone is the most direct visual quote of print/editorial. Also helps contrast for outdoor readability.' },
    { from: 'Yellow active-line on Premium gold accent', to: 'Accent extracted from album art, locked at L≈0.74 C≈0.17', why: 'Per-song reactivity is one of the things you said to preserve; locked L+C means it stays legible for ANY hue.' },
    { from: 'Empty states are plain text', to: 'Editorial kicker + big serif headline + monospace error code', why: 'You said empty states could be moments of personality; turning them into magazine page covers also makes errors feel intentional, not broken.' },
    { from: 'Connect screen is centered pill', to: 'Editorial cover with vol/issue, big display type, accent CTA at bottom', why: 'This is the first impression — should set the magazine tone immediately.' },
    { from: 'Floating overlay = generic rounded card', to: 'Hairline-bordered card with monospace eyebrow + drag handle row', why: 'Distinguishes Spotivibe overlay from the dozens of other system overlays a user sees.' },
  ];
  return (
    <div style={{
      width: 960,
      background: 'oklch(0.965 0.008 80)',
      color: 'oklch(0.18 0.012 270)',
      padding: 36,
      fontFamily: 'JetBrains Mono, monospace',
      position: 'relative',
      boxSizing: 'border-box',
    }}>
      <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>07 — REPLACEMENTS · WHAT CHANGES, AND WHY</div>
      <div style={{ height: 1, background: 'oklch(0.18 0.012 270)', margin: '12px 0 20px' }} />

      <div style={{ display: 'grid', gridTemplateColumns: '24px 1fr 1fr 1.4fr', gap: '0 16px', alignItems: 'baseline' }}>
        <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>#</div>
        <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>WAS</div>
        <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>BECOMES</div>
        <div className="t-mono-up" style={{ color: 'oklch(0.5 0.01 270)' }}>WHY</div>
        <div style={{ gridColumn: '1 / -1', height: 1, background: 'oklch(0.18 0.012 270)', marginTop: 8, marginBottom: 14 }} />
        {items.map((r, i) => (
          <React.Fragment key={i}>
            <div className="t-mono tabular" style={{ fontSize: 11, color: 'oklch(0.5 0.01 270)', alignSelf: 'flex-start', marginTop: 4 }}>{String(i+1).padStart(2,'0')}</div>
            <div className="t-news" style={{ fontSize: 13, color: 'oklch(0.35 0.01 270)', lineHeight: 1.35 }}>{r.from}</div>
            <div className="t-news" style={{ fontSize: 13, color: 'oklch(0.18 0.012 270)', lineHeight: 1.35, fontWeight: 500 }}>{r.to}</div>
            <div className="t-news-i" style={{ fontSize: 13, color: 'oklch(0.4 0.01 270)', lineHeight: 1.4 }}>{r.why}</div>
            <div style={{ gridColumn: '1 / -1', height: 1, background: 'oklch(0.85 0.01 270)', marginTop: 14, marginBottom: 14 }} />
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}

Object.assign(window, { SystemCover, TypeSheet, ColorSheet, TokenSheet, IconSheet, AccentReactivity, AnimationStrip, ReplacementsDoc });
