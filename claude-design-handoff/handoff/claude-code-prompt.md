# Claude Code prompt — Spotivibe redesign

Copy everything below the line into your Claude Code session.

---

I'm rebuilding the UI of my existing Android app **Spotivibe** — a lyrics-overlay companion for Spotify users in Indonesia who listen to K-pop / J-pop / Mandopop. The app does NOT play audio; Spotify is the player. We display real-time synced lyrics + romanization with a dynamic accent color extracted from album art.

I've completed a full visual redesign as a working HTML + React project. I'm attaching:

1. **The current codebase** — all of it. Read `app/build.gradle.kts` first to confirm the Compose / Kotlin / minSdk setup, then map the screens you'll touch.
2. **`design-canvas/`** — the redesign itself as a working HTML + React + CSS project. **This is the source of truth for visuals.** Open `index.html` in a browser to see every screen, every variant, every state side-by-side. Read `tokens.css` for the exact CSS custom properties. Read `screens-shared.jsx`, `screens-nowplaying.jsx`, `screens-settings.jsx`, `screens-overlays.jsx`, `screens-system.jsx` for the layout of each screen — these are real React components, not screenshots. Render the page locally if you need to see anything in motion.
3. **`handoff/tokens.kt.md`** — Color / Type / Spacing / Radius / Motion tokens already translated to Kotlin/Compose. Use these verbatim in `ui/theme/`; don't invent new values. They mirror what's in `design-canvas/tokens.css`.
4. **`handoff/implementation-guide.md`** — table mapping each design decision to the Android files that need changes, plus animation specs and the list of Material 3 primitives that get swapped out.
5. **`handoff/replacements.md`** — every visual decision that changed from the current app, with reasoning. Trust this when it conflicts with the HTML source (it won't, but just in case).

### Design direction in one paragraph

Editorial magazine. Square corners as default (radius 1–2dp), hairline 1dp rules, monospace micro-labels (JetBrains Mono, 11sp, 0.12em tracking, uppercase) used as eyebrows and metadata strips. Big editorial serif (Instrument Serif) for display + Latin lyrics. Newsreader serif for body lyrics — active line in dynamic accent, romanization in `--accent-dim` (same hue, lightness ~0.50) italic underneath. Background = blurred album art + halftone dot screen (multiply blend) + bottom dark gradient. Accent hue extracted from album art per track; chroma and lightness LOCKED at L≈0.74 C≈0.17 (dark) / L≈0.55 C≈0.13 (light) so contrast against ink-1 stays predictable for any song. Dark mode is primary; light mode is a warm paper-white outdoor-readable variant.

### What I want you to do, in this order

1. **Read the codebase**. List the screens you'll modify and the files involved. Don't write code yet.
2. **Drop in the design tokens** — create / overwrite `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Tokens.kt`, `ui/theme/Theme.kt` exactly per `handoff/tokens.kt.md`. Wire the `MaterialTheme` (or your existing `SpotivibeTheme`) to expose them. Add the three font families to `res/font/` (download from Google Fonts: Instrument Serif, Newsreader, JetBrains Mono — weights I listed in the tokens doc).
3. **Build the new layout primitives** in `ui/components/`: `HairlineRule`, `MonoEyebrow`, `SegControl`, `TickSlider`, `Toggle` (custom, not Material), `AccentCtaButton`. Match the screenshots.
4. **Refactor screen by screen**, in this priority order:
   1. NowPlayingScreen (portrait) — the hero. Match the magazine header, lyrics list, transport per the screenshots.
   2. AmbientBg — blur + halftone + gradient.
   3. LyricsList — Newsreader font, accent active line, accent-dim italic romanization underneath, smooth scroll, per-word karaoke when LRC+ data is present.
   4. Transport — only prev / play / next centered. No TRK / BPM side labels. Play button = accent circle.
   5. SelectionMode — subtle accent-ghost tint + left-gutter checkmark + "N / 5" dot counter in the header. Bottom action bar replaces the transport in this mode.
   6. SettingsScreen — numbered sections, hairline dividers, TickSliders, SegControls, custom Toggle.
   7. ConnectScreen — editorial cover (vol/issue eyebrow, big serif "Sing along.", accent CTA at bottom).
   8. Empty states (NP-002 / 404 / 403 / 429) — editorial kicker + big serif headline + mono error code.
   9. KaraokeFullscreen — 1.7× lyrics, minimal chrome, close X top right.
   10. NowPlayingTabletScreen — album+meta left half, lyrics right half (landscape only).
   11. FloatingOverlay — mini bar + expanded card. Hairline border, drag handle row.
   12. Notification — custom RemoteViews with mono eyebrow + lyric on its own row.
5. **Animation pass**. Apply the 540ms track-change crossfade and the 280ms active-line transition. Lyric scroll uses Spring (medium stiffness, no bounce); snap mode skips the spring.
6. **Don't touch**: LRCLIB lookup, OAuth flow, Spotify SDK plumbing, DataStore schema, foreground service lifecycle. Only the UI shell.

### Constraints

- Compose only, no XML rewrites unless a screen is XML today.
- Material 3 stays as the base theme provider, but replace `Switch`, `Slider`, `FilterChip`, `AssistChip`, and any pill `Button` with the custom primitives from step 3.
- Persist nothing new — DataStore schema unchanged.
- Don't change package names, file paths, or class names of public APIs the service / receivers depend on.
- Keep the existing palette-extraction logic for accent hue; only LOCK chroma + lightness per the tokens doc.
- Light mode must remain readable in sunlight — when high-contrast is on, skip the blur and use solid `BgLight0`.

### How to verify as you go

After each screen lands, take a screenshot of the Compose Preview and compare to the matching screen in `design-canvas/index.html` — same artboard, same accent, same theme. Tell me which artboard you used as reference. I'll either approve or send back tweaks. Don't move to the next screen until the current one is approved.

Start with step 1 (read the codebase, list affected files). Wait for my go-ahead before writing tokens.

---

## What to attach when you start the Claude Code session

```
/your-repo/                          ← the full current Android codebase
/handoff/
   README.md
   claude-code-prompt.md             ← this file
   tokens.kt.md                      ← Kotlin/Compose tokens
   implementation-guide.md           ← screen→file map
   replacements.md                   ← what changed and why
/design-canvas/                      ← the full design as a runnable web project
   index.html                        ← open this in a browser to see everything
   tokens.css                        ← CSS source for all design tokens
   screens-shared.jsx                ← shared primitives (LyricLine, Transport, etc)
   screens-nowplaying.jsx
   screens-settings.jsx
   screens-overlays.jsx
   screens-system.jsx                ← design-system sheets (tokens, accents, icons)
   app.jsx                           ← all artboards composed into a canvas
   design-canvas.jsx, tweaks-panel.jsx  ← canvas / tweaks runtime (ignore for porting)
```

The `design-canvas/` folder is the design — Claude Code can render it in a sandboxed browser to see exactly how everything looks, and read the JSX to see the precise layout / spacing / props of each component. No PNG round-trip needed.

## Tips for the Claude Code session

- Start it in **agent mode** (full filesystem access) and grant it write access to your project root.
- After step 1 (codebase read), ask it to summarize the architecture back to you in 5–10 lines. If anything sounds wrong, correct it before moving on.
- For each screen, ask it to **also write a Compose Preview** so you can hit the preview button and see all variants without rebuilding.
- The `replacements.md` table is the most useful "what to change" reference — give it priority when something feels ambiguous.
- The mapping from React component → Compose Composable is usually 1:1. E.g. `NowPlaying` in `screens-nowplaying.jsx` → `NowPlayingScreen` in `feature/nowplaying/`. The JSX uses CSS variables (`var(--accent)`, `var(--ink-2)`); these map directly to the Kotlin constants in `tokens.kt.md`.
- If Claude Code starts hallucinating Material 3 primitives that the design replaces, paste this line: *"Re-read handoff/implementation-guide.md — Switch / Slider / Chip are replaced by custom primitives in ui/components/."*
