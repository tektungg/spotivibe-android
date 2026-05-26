# Spotivibe — Replacements

Every visual decision that changes from the current app to the redesign, and why. Hand this to Claude Code with priority over the screenshots when they conflict.

| # | Was (current) | Becomes (redesign) | Why |
|---|---|---|---|
| 01 | Round purple Spotify-like CTA pill | Square, full-width accent-colored CTA with monospace label | Editorial brevity. Pills read as consumer/social; squares read as magazine/print. |
| 02 | Material toggles + chip-style buttons | Hairline-bordered seg controls + custom tick sliders | Material chrome is the loudest signal of "generic Android app". Replacing it with hairline + tick marks gets us a station-board feel without sacrificing affordance. |
| 03 | Horizontal row of equal-weight icon buttons in header | Hairline 1px monoline icons + small album thumb + monospace album / year above title | Pulls more weight onto track metadata — the magazine feel. |
| 04 | Lyric font: rounded sans (current) | Newsreader (body) + Instrument Serif (display) | Serifs read more "lyrical" and editorial; rounded sans reads as UI text. |
| 05 | Romanization color: gray ink-4 | Accent-dim — same hue as accent, lightness ~0.50 | User feedback: gray feels disconnected from the song. Accent-dim ties romanization to the track without competing with the active line. |
| 06 | Track meta strip (LIVE · KOR · 128 BPM · key · TRK 04) below header | Removed entirely | Visual noise that competed with lyrics for attention. |
| 07 | Transport row: TRK 04/12 left, controls center, BPM right | Only prev / play / next centered. Play = accent circle, prev/next = monoline icons | The side labels added nothing and broke symmetry. |
| 08 | Selection mode: accent border on lines | Subtle accent-ghost background tint + left-gutter checkmark + N/5 dot counter in header | Border-only is easy to miss; gutter checkmark is glanceable. User asked for subtle. |
| 09 | Generic blurred album bg | Blurred art + halftone dot screen (multiply blend) + bottom dark gradient | Halftone is the most direct visual quote of print/editorial. Also helps contrast for outdoor readability. |
| 10 | Yellow active-line on Premium gold accent | Accent extracted from album art, locked at L ≈ 0.74 C ≈ 0.17 | Per-song reactivity is the differentiator. Locked L+C means it stays legible for ANY hue (purple K-pop / red anime OST / green city-pop). |
| 11 | Empty states are plain text | Editorial kicker + big serif headline + monospace error code (NP-002 / 404 / 403 / 429) | Empty states could be moments of personality. Turning them into magazine-page covers also makes errors feel intentional, not broken. |
| 12 | Connect screen is centered pill on dark | Editorial cover with vol/issue eyebrow, big display type "Sing along.", accent CTA at bottom | First impression should set the magazine tone immediately. |
| 13 | Floating overlay = generic rounded card | Hairline-bordered card with monospace eyebrow + drag handle row, square corners | Distinguishes Spotivibe overlay from the dozens of other system overlays a user sees. |
| 14 | Settings sections labeled with icon + colored text | Numbered sections (01 Appearance / 02 Lyrics / 03 Spotify / 04 Colophon), magazine-style headers with hairline dividers | Magazine table-of-contents feel. The numbering also doubles as a visual hierarchy when scrolling. |
| 15 | Notification: default Material media style | Custom RemoteViews — mono "SPOTIVIBE · NOW" eyebrow, track + artist, current lyric line as its own row in accent, transport row | The lyric line IS the unique value the app provides; surface it. |
| 16 | Karaoke fullscreen: stock close button | Hairline-circle close button top-right + mono meta top-left + 1.7× oversized lyrics + hairline progress bar at bottom | Minimum chrome, maximum lyric. |
| 17 | Tablet layout: none (just stretched portrait) | Split: album + meta left half, scrolling lyrics right half, larger control surface | Tablet is for sitting at a desk / counter — the larger meta gives the song context that the small phone hides. |

## Animation contracts

- **Track change**: 540ms total. 0–220ms: old lyrics fade to alpha 0, slide −20dp. 180–360ms: new header crossfades in at +10dp → 0. Album thumb alpha-blends, no flip. Easing: `cubic-bezier(0.16, 1, 0.3, 1)`.
- **Active line transition**: 280ms — color + size lerp simultaneously. Weight ramps in lockstep.
- **Lyric scroll**: Spring (medium stiffness, no bounce) on the LazyColumn. Snap mode skips the spring.
- **Toggle / chip**: 140ms ease-out.

## Token contracts (see `tokens.kt.md`)

- Accent hue is dynamic. Chroma + lightness are LOCKED.
- Three font families only: Instrument Serif (display + Latin lyrics), Newsreader (body lyrics + italic romanization), JetBrains Mono (metadata + labels).
- Radius default = 1–2dp. Pill / fully-round corners are forbidden on primary surfaces.
- Spacing scale is 4dp base: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64.
