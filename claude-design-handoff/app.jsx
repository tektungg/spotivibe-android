/* Spotivibe — Design Canvas root */

function App() {
  // Global tweak: theme is dark or light, applies to artboards that opt-in
  // Most artboards are explicitly dark or light to show variants;
  // but the user can flip with the Tweaks panel to invert.
  const [tweaks, setTweak] = useTweaks(/*EDITMODE-BEGIN*/{
    "previewTheme": "as-designed"
  }/*EDITMODE-END*/);

  // Helper: when previewTheme is "all-dark" or "all-light", override
  // the artboard's chosen theme. Default "as-designed" respects the
  // artboard's own theme prop (so dark/light comparisons stay intact).
  const resolveTheme = (t) => {
    if (tweaks.previewTheme === 'all-dark') return 'dark';
    if (tweaks.previewTheme === 'all-light') return 'light';
    return t;
  };
  window.resolveTheme = resolveTheme;

  // ── Auto-size the design-system artboards ────────────────────
  // Render each system sheet hidden offscreen at intrinsic size,
  // then feed the measured dimensions to the matching DCArtboard.
  // Fonts can arrive after first paint and change the height — we
  // re-measure when document.fonts.ready resolves.
  const [autoSize, setAutoSize] = React.useState({});
  const measureRef = React.useRef(null);

  const measure = React.useCallback(() => {
    if (!measureRef.current) return;
    const out = {};
    for (const el of measureRef.current.querySelectorAll('[data-am]')) {
      const r = el.getBoundingClientRect();
      if (r.width > 10 && r.height > 10) {
        out[el.dataset.am] = { w: Math.ceil(r.width), h: Math.ceil(r.height) };
      }
    }
    setAutoSize((s) => ({ ...s, ...out }));
  }, []);

  React.useLayoutEffect(() => {
    measure();
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(() => measure());
    }
  }, [measure]);

  // Returns {width, height} for an auto-sized artboard, with fallbacks
  // while measurement is pending.
  const sz = (id, fw, fh) => ({
    width: autoSize[id]?.w ?? fw,
    height: autoSize[id]?.h ?? fh,
  });

  return (
    <>
      {/* Hidden measurement layer — renders each system sheet at its
          natural intrinsic size so we can read its real width/height. */}
      <div ref={measureRef} aria-hidden style={{
        position: 'fixed', top: 0, left: -99999,
        visibility: 'hidden', pointerEvents: 'none', zIndex: -9999,
      }}>
        <div data-am="cover"     style={{ display: 'inline-block' }}><SystemCover /></div>
        <div data-am="type"      style={{ display: 'inline-block' }}><TypeSheet /></div>
        <div data-am="color"     style={{ display: 'inline-block' }}><ColorSheet /></div>
        <div data-am="tokens"    style={{ display: 'inline-block' }}><TokenSheet /></div>
        <div data-am="icons"     style={{ display: 'inline-block' }}><IconSheet /></div>
        <div data-am="acc-dark"  style={{ display: 'inline-block' }}><AccentReactivity theme="dark" /></div>
        <div data-am="acc-light" style={{ display: 'inline-block' }}><AccentReactivity theme="light" /></div>
        <div data-am="anim-dark" style={{ display: 'inline-block' }}><AnimationStrip theme="dark" /></div>
        <div data-am="anim-light" style={{ display: 'inline-block' }}><AnimationStrip theme="light" /></div>
        <div data-am="replace"   style={{ display: 'inline-block' }}><ReplacementsDoc /></div>
      </div>

      <DesignCanvas minScale={0.1} maxScale={3}>

        {/* ─────────── 00 · System ─────────── */}
        <DCSection id="system" title="00 · The system" subtitle="Direction, type, color, tokens — artboards auto-fit their content">
          <DCArtboard id="cover" label="Cover · Editorial direction" {...sz('cover', 760, 720)}>
            <SystemCover />
          </DCArtboard>
          <DCArtboard id="type" label="Typography · 3 faces" {...sz('type', 580, 680)}>
            <TypeSheet />
          </DCArtboard>
          <DCArtboard id="color" label="Color · Neutrals + accents" {...sz('color', 760, 720)}>
            <ColorSheet />
          </DCArtboard>
          <DCArtboard id="tokens" label="Spacing · Radius · Motion" {...sz('tokens', 620, 560)}>
            <TokenSheet />
          </DCArtboard>
          <DCArtboard id="icons" label="Icons · Hairline 1px" {...sz('icons', 720, 600)}>
            <IconSheet />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 01 · Accent reactivity proof ─────────── */}
        <DCSection id="accent" title="01 · Accent reactivity" subtitle="Same screen, three songs, three hues — extracted from album art">
          <DCArtboard id="acc-dark" label="Dark mode · 3 accents" {...sz('acc-dark', 1040, 540)}>
            <AccentReactivity theme="dark" />
          </DCArtboard>
          <DCArtboard id="acc-light" label="Light mode · 3 accents" {...sz('acc-light', 1040, 540)}>
            <AccentReactivity theme="light" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 02 · Now Playing — portrait ─────────── */}
        <DCSection id="np-portrait" title="02 · Now Playing — portrait" subtitle="The hero. Dark and light, three accents.">
          <DCArtboard id="np-dark-coral" label="Dark · Coral · K-pop" width={390} height={844}>
            <NowPlaying track={TRACKS.coral} theme={(window.resolveTheme||((x)=>x))('dark')} />
          </DCArtboard>
          <DCArtboard id="np-dark-violet" label="Dark · Violet · J-pop" width={390} height={844}>
            <NowPlaying track={TRACKS.violet} theme={(window.resolveTheme||((x)=>x))('dark')} />
          </DCArtboard>
          <DCArtboard id="np-dark-jade" label="Dark · Jade · Mandopop" width={390} height={844}>
            <NowPlaying track={TRACKS.jade} theme={(window.resolveTheme||((x)=>x))('dark')} />
          </DCArtboard>
          <DCArtboard id="np-light-coral" label="Light · Coral" width={390} height={844}>
            <NowPlaying track={TRACKS.coral} theme={(window.resolveTheme||((x)=>x))('light')} />
          </DCArtboard>
          <DCArtboard id="np-light-violet" label="Light · Violet" width={390} height={844}>
            <NowPlaying track={TRACKS.violet} theme={(window.resolveTheme||((x)=>x))('light')} />
          </DCArtboard>
          <DCArtboard id="np-light-jade" label="Light · Jade" width={390} height={844}>
            <NowPlaying track={TRACKS.jade} theme={(window.resolveTheme||((x)=>x))('light')} />
          </DCArtboard>
          <DCArtboard id="np-word" label="Word-sync (LRC+)" width={390} height={844}>
            <NowPlayingKaraokeWord track={TRACKS.coral} theme="dark" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 03 · Now Playing — tablet ─────────── */}
        <DCSection id="np-tablet" title="03 · Now Playing — tablet landscape" subtitle="Album + meta left half, lyrics scroll right half (per your spec)">
          <DCArtboard id="tb-dark" label="Tablet · Dark · Coral" width={1280} height={800}>
            <NowPlayingTablet track={TRACKS.coral} theme="dark" />
          </DCArtboard>
          <DCArtboard id="tb-light" label="Tablet · Light · Violet" width={1280} height={800}>
            <NowPlayingTablet track={TRACKS.violet} theme="light" />
          </DCArtboard>
          <DCArtboard id="tb-jade" label="Tablet · Dark · Jade" width={1280} height={800}>
            <NowPlayingTablet track={TRACKS.jade} theme="dark" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 04 · Selection mode ─────────── */}
        <DCSection id="select" title="04 · Selection mode + share" subtitle="Subtle tint + checkmark gutter (per your pick). Action bar replaces transport.">
          <DCArtboard id="sel-empty" label="Selection · 0 / 5" width={390} height={844}>
            <NowPlayingSelect track={TRACKS.coral} theme="dark" selectedSet={new Set()} />
          </DCArtboard>
          <DCArtboard id="sel-active" label="Selection · 3 / 5 (typical)" width={390} height={844}>
            <NowPlayingSelect track={TRACKS.coral} theme="dark" selectedSet={new Set([2,3,4])} />
          </DCArtboard>
          <DCArtboard id="sel-full" label="Selection · 5 / 5 (max)" width={390} height={844}>
            <NowPlayingSelect track={TRACKS.coral} theme="dark" selectedSet={new Set([1,2,3,4,5])} />
          </DCArtboard>
          <DCArtboard id="sel-light" label="Selection · Light variant" width={390} height={844}>
            <NowPlayingSelect track={TRACKS.violet} theme="light" selectedSet={new Set([2,3,4])} />
          </DCArtboard>
          <DCArtboard id="share-card" label="Share card · Output (1080×1920)" width={540} height={960}>
            <ShareCard track={TRACKS.coral} />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 05 · Karaoke fullscreen ─────────── */}
        <DCSection id="karaoke" title="05 · Karaoke fullscreen" subtitle="Immersive, lyrics-only. 1.7× type, minimal chrome.">
          <DCArtboard id="k-coral" label="Karaoke · Coral" width={390} height={844}>
            <KaraokeFullscreen track={TRACKS.coral} theme="dark" />
          </DCArtboard>
          <DCArtboard id="k-violet" label="Karaoke · Violet" width={390} height={844}>
            <KaraokeFullscreen track={TRACKS.violet} theme="dark" />
          </DCArtboard>
          <DCArtboard id="k-jade" label="Karaoke · Jade" width={390} height={844}>
            <KaraokeFullscreen track={TRACKS.jade} theme="dark" />
          </DCArtboard>
          <DCArtboard id="k-light" label="Karaoke · Light" width={390} height={844}>
            <KaraokeFullscreen track={TRACKS.coral} theme="light" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 06 · Connect ─────────── */}
        <DCSection id="connect" title="06 · Connect — first launch" subtitle="The cover of the magazine.">
          <DCArtboard id="conn-dark" label="Connect · Dark" width={390} height={844}>
            <Connect theme="dark" accent="coral" />
          </DCArtboard>
          <DCArtboard id="conn-light" label="Connect · Light" width={390} height={844}>
            <Connect theme="light" accent="coral" />
          </DCArtboard>
          <DCArtboard id="conn-violet" label="Connect · Violet test" width={390} height={844}>
            <Connect theme="dark" accent="violet" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 07 · Empty states ─────────── */}
        <DCSection id="empty" title="07 · Empty states" subtitle="Editorial kicker + big serif headline + monospace error code">
          <DCArtboard id="e-nothing" label="No track playing" width={390} height={844}>
            <EmptyState kind="nothing" theme="dark" accent="coral" />
          </DCArtboard>
          <DCArtboard id="e-notfound" label="Lyrics not found" width={390} height={844}>
            <EmptyState kind="notFound" theme="dark" accent="violet" />
          </DCArtboard>
          <DCArtboard id="e-premium" label="Premium required" width={390} height={844}>
            <EmptyState kind="premium" theme="dark" accent="jade" />
          </DCArtboard>
          <DCArtboard id="e-refresh" label="Reconnecting" width={390} height={844}>
            <EmptyState kind="rateLimit" theme="dark" accent="coral" />
          </DCArtboard>
          <DCArtboard id="e-light" label="No track · Light" width={390} height={844}>
            <EmptyState kind="nothing" theme="light" accent="coral" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 08 · Settings ─────────── */}
        <DCSection id="settings" title="08 · Settings" subtitle="Magazine sections — Appearance, Lyrics, Spotify, Colophon">
          <DCArtboard id="set-dark" label="Settings · Dark" width={390} height={844}>
            <Settings track={TRACKS.coral} theme="dark" />
          </DCArtboard>
          <DCArtboard id="set-light" label="Settings · Light" width={390} height={844}>
            <Settings track={TRACKS.coral} theme="light" />
          </DCArtboard>
          <DCArtboard id="set-violet" label="Settings · Violet accent" width={390} height={844}>
            <Settings track={TRACKS.violet} theme="dark" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 09 · Floating overlay ─────────── */}
        <DCSection id="overlay" title="09 · Floating overlay + notification" subtitle="Above other apps. Editorial chrome — instantly recognizable.">
          <DCArtboard id="ov-mini" label="Overlay · Mini bar" width={390} height={844}>
            <OverlayMini track={TRACKS.coral} theme="dark" />
          </DCArtboard>
          <DCArtboard id="ov-exp" label="Overlay · Expanded card" width={390} height={844}>
            <OverlayExpanded track={TRACKS.coral} theme="dark" />
          </DCArtboard>
          <DCArtboard id="ov-light" label="Overlay · Mini, light host" width={390} height={844}>
            <OverlayMini track={TRACKS.violet} theme="light" />
          </DCArtboard>
          <DCArtboard id="notif" label="Persistent notification" width={390} height={844}>
            <NotificationView track={TRACKS.coral} theme="dark" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 10 · Track-change animation ─────────── */}
        <DCSection id="anim" title="10 · Track-change animation" subtitle="Cross-fade reference frames (540ms total)">
          <DCArtboard id="anim-dark" label="Animation strip · Dark" {...sz('anim-dark', 1280, 460)}>
            <AnimationStrip theme="dark" />
          </DCArtboard>
          <DCArtboard id="anim-light" label="Animation strip · Light" {...sz('anim-light', 1280, 460)}>
            <AnimationStrip theme="light" />
          </DCArtboard>
        </DCSection>

        {/* ─────────── 11 · Handoff doc ─────────── */}
        <DCSection id="handoff" title="11 · Handoff" subtitle="Replacements + reasoning, for Claude Code / Jetpack Compose">
          <DCArtboard id="replace" label="Replacements doc" {...sz('replace', 960, 760)}>
            <ReplacementsDoc />
          </DCArtboard>
        </DCSection>

      </DesignCanvas>

      <TweaksPanel title="Tweaks">
        <TweakSection label="Now Playing portrait">
          <TweakRadio
            label="Theme override"
            value={tweaks.previewTheme}
            options={[
              { value: 'as-designed', label: 'As-is' },
              { value: 'all-dark',    label: 'Dark' },
              { value: 'all-light',   label: 'Light' },
            ]}
            onChange={(v) => setTweak('previewTheme', v)}
          />
        </TweakSection>
      </TweaksPanel>
    </>
  );
}

// Optional helper (some screens reference window.resolveTheme)
window.resolveTheme = (t) => t;

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
