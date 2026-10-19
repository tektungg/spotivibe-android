# Spotivibe promo video

A 60-second motion-graphics promo for Spotivibe, built from the app's own
design system and rendered frame-exact with [Remotion](https://www.remotion.dev)
4.0.533. Output: `out/spotivibe-promo.mp4`, 1920x1080, 60 fps, H.264 + AAC.

Nothing here is a screen recording. Every screen is the design handoff
(`claude-design-handoff/`) rebuilt as React components with the same tokens as
`app/.../ui/theme/Color.kt`, the same fonts as `app/src/main/res/font/`, and
the logo geometry of `logo/spotivibe-mark.svg`. The soundtrack is synthesized
from code, so the repo carries no licensed audio.

## Commands

```bash
cd promo
npm install
npm run build          # assets + score + render -> out/spotivibe-promo.mp4
npm test               # gate tests, < 2 s
npm run typecheck
npm run check-frames   # 27 stills (3 per scene), fails on flat/blank frames
node scripts/eval-render.mjs   # eval of the rendered MP4 (format, loudness, black, cuts on beat)
npm run studio         # live preview with a timeline scrubber
```

`npm run build` runs, in order:

1. `npm run assets`: copies the app fonts into `public/generated/fonts/` and
   downloads a Noto Serif KR / JP / TC subset holding only the CJK glyphs in
   `src/data/tracks.ts` (about 40 glyphs each). Needs network once.
2. `npm run score`: writes `public/generated/score.wav` (60.00 s, about 13 s).
3. `npm run render`: about 4 minutes on a 16-thread machine.

`public/generated/` and `out/` are gitignored; everything in them is rebuilt
from source.

## How timing works

`src/timeline.ts` is the single source of truth. Everything is counted in
beats at 120 BPM and 60 fps, so one beat is exactly 30 frames and no cut ever
rounds. The video (`src/Promo.tsx`) and the score
(`scripts/score/arrangement.mjs`) both read the same scene table, and
`test/score.test.mjs` fails if any scene cut has no impact or kick on its beat.

| # | Beats | Seconds | Scene | What moves |
|---|-------|---------|-------|------------|
| 1 | 0-12 | 0-6 | Cold open | Coral lyric bar slams in on frame 0 and thins into a baseline; eight KOR/JPN/ZHO words slam one per beat with an outlined shockwave; "CAN'T READ IT?" |
| 2 | 12-20 | 6-10 | Answer | 내일의 너에게 lights up syllable by syllable while its romanization drops in; "Now you can sing it." |
| 3 | 20-28 | 10-14 | Logo morph | Five lyric lines collapse into the five bars of the mark, then the lockup from `spotivibe-wordmark.svg` |
| 4 | 28-48 | 14-24 | Hero | 3D phone, Now Playing with scroll, distance blur (1.25 dp per line, max 6.83 dp, as in the app), per-character karaoke, Rm tap, camera push |
| 5 | 48-64 | 24-32 | Dynamic accent | Covers wipe in from the album art every 2 beats, copy accent interpolates; three phones fan out in 3D |
| 6 | 64-76 | 32-38 | Everywhere | Floating overlay dragged and expanded over a chat app; whip pan to the lock-screen notification |
| 7 | 76-96 | 38-48 | Feature board | Split-flap station board, one row per 2 beats; then the four lyric sources race |
| 8 | 96-110 | 48-55 | Build and drop | 3D tunnel of every lyric line accelerating into a white flash; Code. Vibe. Sing along. one word per beat; words collapse into one coral bar |
| 9 | 110-120 | 55-60 | End card | That bar lands as the mark's lit line, lockup, tagline, CTA; the lit bar "sings", drifts to center and closes, where frame 0 began |

## Editing

- Copy and lyrics: `src/data/tracks.ts`. CJK text must live only there
  (`test/assets.test.mjs` enforces it) so the font subset covers it. Rerun
  `npm run assets` after changing lyrics.
- Timing: change beats in `SCENES` (`src/timeline.ts`); the total must stay
  120 beats. Then `npm run score` so the music follows.
- Colors and type: `src/theme/tokens.ts`, mirrored from the app. The app file
  wins on any conflict.
- One scene per file in `src/scenes/`; shared pieces in `src/components/`.

## Verification

| Lane | Command | What it proves |
|------|---------|----------------|
| Gate | `npm test` | 60.000 s exactly, cuts on whole frames and beats, scenes contiguous, every cut has a hit, kicks on every hero beat, score normalized and deterministic, CJK only in the data file, font subsets cover every glyph |
| Gate | `npm run typecheck` | Strict TypeScript; every scene id maps to a component |
| Periodic | `npm run check-frames` | Each scene renders real content (luma spread >= 60), not a blank or flat frame |
| Periodic | `node scripts/eval-render.mjs` | Final MP4 format and duration, loudness, no long black, share of hard visual changes on the beat grid |

Last full run (2026-10-19): 22/22 gate tests, 27/27 stills, and
`eval-render` 6/6: 1920x1080 at 60.00 fps, 60.011 s, -11 LUFS integrated,
-1 dBTP true peak, no black stretch, 9/9 hard visual changes within two
frames of the beat grid. Render time about 8 minutes, file about 32 MB.

`check-frames` caught two real defects while building: an empty frame in the
middle of the scene-6 whip pan (the phones did not overlap) and placeholder
scenes. A stacking bug (a screen's `z-index` leaking above the circular wipe
in scene 5) was found by reading the stills; the fix isolates
`NowPlayingScreen`'s stacking context.

## Licensing notes

- Remotion is free for individuals and companies of up to three people; a
  larger company needs a Remotion company license.
- Fonts: Instrument Serif, Newsreader, JetBrains Mono and Noto Serif are all
  SIL Open Font License.
- Artists, albums and lyrics in the promo are fictional (from the design
  handoff). "Not affiliated with Spotify AB" stays on the end card.
