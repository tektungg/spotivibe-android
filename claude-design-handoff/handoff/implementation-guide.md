# Spotivibe — Implementation guide for Claude Code

Map of where each design decision lands in the Android codebase. Pair this
with `tokens.kt.md` and the canvas screenshots.

## What changes (high level)

| Area | From (current app) | To (this redesign) | Files to touch |
|---|---|---|---|
| Theme tokens | scattered `Color(...)` calls | one source in `ui/theme/Color.kt` + `Type.kt` + `Tokens.kt` | `ui/theme/*` |
| Fonts | system defaults | Instrument Serif + Newsreader + JetBrains Mono in `res/font/` | `res/font/`, `Type.kt` |
| Now Playing | uniform yellow accent | dynamic per-track accent + magazine layout | `feature/nowplaying/NowPlayingScreen.kt` |
| Lyrics list | rounded sans, single color | Newsreader serif, active = accent, romanization = accent-dim italic | `feature/nowplaying/LyricsList.kt` |
| Background | blurred album cover | blurred + halftone overlay + bottom gradient | `feature/nowplaying/AmbientBg.kt` |
| Transport | TRK/BPM side labels + 3 buttons | only prev/play/next centered, accent circle play | `feature/nowplaying/Transport.kt` |
| Header | row of icons | hairline icons + small album thumb + mono album/year above title | `feature/nowplaying/NowPlayingHeader.kt` |
| Selection mode | accent border on selected lines | subtle accent-ghost tint + left-gutter checkmark + N/5 dot counter | `feature/nowplaying/SelectionMode.kt` |
| Share output | (new) | 1080×1920 image renderer with editorial layout | `feature/share/ShareCardRenderer.kt` |
| Settings | Material toggles + chip rows | numbered sections (01 Appearance / 02 Lyrics / 03 Spotify / 04 Colophon), hairline rules, tick sliders, seg controls | `feature/settings/SettingsScreen.kt` |
| Connect | centered pill CTA | editorial cover (vol/issue eyebrow → big serif → accent CTA at bottom) | `feature/connect/ConnectScreen.kt` |
| Empty states | plain text | editorial kicker + serif headline + mono error code (NP-002 / 404 / 403 / 429) | `feature/nowplaying/EmptyStates.kt` |
| Floating overlay | rounded card | hairline-bordered card + drag handle row + mono eyebrow | `service/OverlayService.kt`, `feature/overlay/*` |
| Notification | default Media style | custom RemoteViews with mono eyebrow + lyric line on its own row | `service/PlaybackNotification.kt` |
| Karaoke fullscreen | (exists) | minimal chrome — close X top-right, mono meta top-left, oversized lyrics, hairline progress | `feature/karaoke/KaraokeScreen.kt` |
| Tablet landscape | (none) | split: album+meta left / scrolling lyrics right | `feature/nowplaying/NowPlayingTabletScreen.kt` |

## Layout primitives to add

- `HairlineRule(soft: Boolean = false)` — 1.dp height, RuleSoftDark / RuleDark.
- `MonoEyebrow(text)` — JetBrainsMono 11sp, letterSpacing 1.3sp, uppercase, color = InkDark3.
- `SegControl(options, selected, onSelect)` — hairline border, active = AccentInk on Accent fill.
- `TickSlider(value, range, twoSided = false)` — 21 tick marks, accent-filled portion, vertical handle bar.
- `Toggle(on, onChange)` — accent fill when on, hairline border when off, no Material elevation.
- `AccentCtaButton(label, icon, onClick)` — square corners (radius 1.dp), accent fill, AccentInk text, mono uppercase label.

## Animation specs

- **Track change** — 540ms total. Old lyrics fade to alpha 0 over 0-220ms while sliding -20.dp. New header crossfades from alpha 0 at 180ms, slides +10.dp -> 0. Album thumb alpha-blends, no flip. Use `SvMotion.EaseOut`.
- **Active line transition** — color + size lerp over 280ms ease-out. Bold weight ramps in lockstep.
- **Lyric scroll** — Spring(stiffness Medium, dampingRatio NoBouncy) on the LazyColumn `animateScrollBy`. Snap mode skips the spring.
- **Toggle / chip** — 140ms ease-out on the thumb translation and background color.

## Persistent contracts (do not regress)

- Dynamic accent per song (palette extraction).
- Sync offset, line spacing, font size, theme, smooth/snap, haptic — all live in DataStore, persist across launches.
- LRCLIB lookup unchanged.
- Per-word karaoke when LRC+ data is present.
- System notification + foreground service unchanged in shape; only visual chrome changes.
- Floating overlay still draggable, snaps to edges, two states.

## What to delete / replace explicitly

1. Round purple Spotify-like CTA pill → square accent CTA.
2. Material 3 `Switch` calls → custom `Toggle` (no track elevation).
3. Material 3 `Slider` calls → `TickSlider`.
4. Material 3 `FilterChip` / `AssistChip` → mono uppercase labels in a `Row` with `HairlineRule` separators.
5. Premium "gold" accent — replaced everywhere by the dynamic per-track accent.
6. Yellow `#FFC857` (or wherever it lives) — remove the constant.

## Accessibility notes

- Lyric font scale obeys both the in-app slider AND the OS text scale (multiply, don't override).
- High-contrast switch in Settings forces InkDark1 ↔ BgDark0 only and disables blur on background (still readable outdoors).
- Tap targets stay ≥ 44dp.
