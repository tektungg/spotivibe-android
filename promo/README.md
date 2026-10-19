# Spotivibe promo video

A 60-second motion-graphics promo for Spotivibe, built from the app's own
design system and rendered frame-exact with [Remotion](https://www.remotion.dev)
4.0.533. Output: `out/spotivibe-promo.mp4` (real songs) and
`out/spotivibe-promo-fictional.mp4` (made-up songs, safe to publish), both
1920x1080, 60 fps, H.264 + AAC.

Nothing here is a screen recording. Every screen is the design handoff
(`claude-design-handoff/`) rebuilt as React components with the same tokens as
`app/.../ui/theme/Color.kt`, the same fonts as `app/src/main/res/font/`, and
the logo geometry of `logo/spotivibe-mark.svg`. The soundtrack is synthesized
from code, so the repo carries no licensed audio.

## Commands

```bash
cd promo
npm install
npm run build              # assets + score + both renders
npm test                   # gate tests, about 2 s
npm run typecheck
npm run check-frames       # 27 stills (3 per scene), fails on flat/blank frames
npm run eval               # eval of both MP4s (format, loudness, black, cuts on beat)
npm run studio             # live preview with a timeline scrubber
npm run render:fictional   # only the store-safe version
```

`npm run build` runs, in order:

1. `npm run assets`: builds the real song catalog (below), copies the app
   fonts into `public/generated/fonts/`, and downloads a Noto Serif KR / JP /
   TC subset holding only the CJK glyphs of both catalogs (under 100 glyphs
   each). Needs network.
2. `npm run score`: writes `public/generated/score.wav` (60.00 s, about 10 s).
3. `npm run render` and `npm run render:fictional`: about 8 minutes each on a
   16-thread machine.

`public/generated/` and `out/` are gitignored; everything in them is rebuilt
from source.

## Song catalog

Scenes never name a song. They ask for a role (`ROLES` in
`src/data/catalog.ts`: hero, cover cycle, spread, overlay, notification) and
read the track from the catalog that `Root.tsx` loads in `calculateMetadata`.

| Role | Real catalog (default) | Fictional catalog |
|------|------------------------|-------------------|
| Hero, "found" lines, end-card glow | Olivia Rodrigo, drop dead | Midnight Static |
| Cover cycle 1 | Tulus, Teh Hijau | Hujan di Jendela Kereta |
| Cover cycle 2, spread left, overlay | YOASOBI, 怪物 | 夜明けのスタンス |
| Cover cycle 3, spread center | aespa, Armageddon | 내일의 너에게 |
| Cover cycle 4, spread right, notification | 周杰倫, 晴天 | 未來進行式 |

`npm run catalog` (part of `npm run assets`) builds the real one the way the
app finds lyrics:

1. iTunes lookup by id (`REAL_SONGS`) in the store that carries the
   original-script title: title, artist, album, year, duration, and the
   600 px artwork into `public/generated/covers/`.
2. LRCLIB `/api/get` with that exact signature, falling back to the
   `/api/search` hit with the closest duration. 429s back off and honour
   `Retry-After`.
3. Ten sung lines from `fromSeconds` on (`lyricWindow`).
4. Romanization only on lines that carry CJK script, ported from the app's
   `RomanizationService.kt`: Revised Romanization jamo tables, kuromoji
   (IPADIC, the same dictionary as the app) readings to Hepburn, and
   tone-marked pinyin per character.
5. The accent the app would show: a Palette-style vibrant pick from the
   cover, locked to OkLCh L 0.78 / C 0.17 with chroma-only gamut mapping,
   ported from `AccentLock.kt`.

`validateCatalog` rejects a catalog with a missing track, fewer than ten
lines, an unromanized CJK line, a missing cover, or a bad accent, and the
render fails with the fix instead of rendering half a video.

## How timing works

`src/timeline.ts` is the single source of truth. Everything is counted in
beats at 120 BPM and 60 fps, so one beat is exactly 30 frames and no cut ever
rounds. The video (`src/Promo.tsx`) and the score
(`scripts/score/arrangement.mjs`) both read the same scene table, and
`test/score.test.mjs` fails if any scene cut has no impact or kick on its beat.

| # | Beats | Seconds | Scene | What moves |
|---|-------|---------|-------|------------|
| 1 | 0-12 | 0-6 | Cold open | Coral lyric bar slams in on frame 0 and thins into a baseline; one "now playing" card per beat from Spotify, YouTube Music, Spotify Free and any player, songs in five languages, every one with empty lyric slots and "NO LYRICS"; grid and "WHERE ARE THE LYRICS?" |
| 2 | 12-20 | 6-10 | Found | The empty slots grow into synced lines on the 8th notes, the active line sweeps in coral; tagline reveal "Every player. Every line." |
| 3 | 20-28 | 10-14 | Logo morph | The same five lines collapse into the five bars of the mark, then the lockup from `spotivibe-wordmark.svg` with the tagline eyebrow |
| 4 | 28-48 | 14-24 | Hero | 3D phone with Now Playing (distance blur 1.25 dp per line, max 6.83 dp, as in the app). 01 ANY PLAYER with a "now following" pill cycling players; 02 NO LOGIN with a notification-access chip; 03 IN SYNC with a camera push onto "Every line, every line" |
| 5 | 48-64 | 24-32 | Accent, high contrast, spread | Four covers wipe in from the album art every 2 beats while the copy accent follows each cover's locked accent; the High contrast switch flips and the same screen drops to solid black with blur off (`AmbientBg.kt`, `LyricLine.kt`); three phones fan out: "Any player. Any language." |
| 6 | 64-76 | 32-38 | Everywhere | Floating overlay dragged and expanded over a chat app; whip pan to the lock-screen notification |
| 7 | 76-96 | 38-48 | Feature board | Split-flap station board led by PLAYERS (romanization is the last row); then the four lyric sources search and race |
| 8 | 96-110 | 48-55 | Build and drop | 3D tunnel of lyric lines accelerating into a white flash; "Every player. / Every song. / Every line. / In sync." one per beat; collapse into one coral bar |
| 9 | 110-120 | 55-60 | End card | That bar lands as the mark's lit line, lockup, tagline, CTA, "works with Spotify · YouTube Music · any player"; the lit bar "sings", drifts to center and closes, where frame 0 began |

## Message

Spotivibe is a synced-lyrics companion for **any player**: it reads whatever
Spotify, YouTube Music or any other MediaSession app is playing, with one
system permission and no login. The promo is built around that: songs play
everywhere without words (scene 1), Spotivibe finds them (scene 2), and the
features follow in order of importance (any player, no login, sync, accent,
overlay and notification, sources). Romanization is one supporting line
("Any language.", last board row), not the headline.

Tagline: **Every player. Every line.** (eyebrow `EVERY PLAYER · EVERY LINE`).
It replaces the VS Code extension era tagline everywhere: app
(`ui/theme/Brand.kt`), root README, `logo/spotivibe-wordmark.svg`, and here
(`src/data/brand.ts`).

## Editing

- Tagline: `src/data/brand.ts`, which must equal `Brand.TAGLINE` in the app;
  `test/brand.test.mjs` fails when they drift or when the old tagline
  reappears in the app, promo, README or logo.
- Songs: `REAL_SONGS` (iTunes id, store, lyric start) and the fictional
  tracks in `src/data/catalog.ts`; which song plays where is `ROLES`; the
  cold-open cards are `PLAYER_CARDS`. CJK text in source must live only there
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
| Gate | `npm test` | Tagline equals the app's `Brand.TAGLINE` and the old one is gone, 60.000 s exactly, cuts on whole frames and beats, scenes contiguous, every cut has a hit, kicks on every hero beat, score normalized and deterministic, CJK only in the data file, font subsets cover every glyph |
| Gate | `npm run typecheck` | Strict TypeScript; every scene id maps to a component |
| Periodic | `npm run check-frames` | Each scene renders real content (luma spread >= 60), not a blank or flat frame |
| Periodic | `node scripts/eval-render.mjs` | Final MP4 format and duration, loudness, no long black, share of hard visual changes on the beat grid |

Last full run (2026-10-19, real songs + high contrast): 44/44 gate tests,
27/27 stills, and `eval-render` 6/6 on both MP4s: 1920x1080 at 60.00 fps,
60.011 s, -11 LUFS integrated, -1 dBTP true peak, no black stretch, 3/3 hard
visual changes on the beat grid. About 8 minutes and 32 MB per render.

On Windows a render cannot replace an MP4 that is open elsewhere (a player
or an editor preview): Remotion fails at the final rename with EPERM. Close
the file, or render to another name.

`test/brand.test.mjs` caught the old tagline quoted in a KDoc comment of the
new `Brand.kt` on its first run.

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
- **Real catalog: lyrics, cover art and artist names belong to their labels
  and publishers.** They are fetched at build time and never committed, but
  `out/spotivibe-promo.mp4` contains them. Fine for a demo or a pitch;
  publishing it as an ad (Play Store listing video, YouTube ads, social
  promotion) needs permission from the rights holders, or use the fictional
  version.
- Fictional catalog (`out/spotivibe-promo-fictional.mp4`): artists, albums
  and lyrics are made up, covers are gradients. Safe to publish.
- "Not affiliated with Spotify AB" stays on the end card in both.
