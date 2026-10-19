// Builds public/generated/catalog.json and public/generated/covers/ for the
// real songs in src/data/catalog.ts (REAL_SONGS), from the same sources the
// app uses: iTunes for metadata and artwork, LRCLIB for synced lyrics.
// Everything it writes is gitignored: lyrics and covers are copyrighted.
//
// Usage: node scripts/catalog/fetch-catalog.mjs
import { execFileSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { LINES_PER_TRACK, REAL_SONGS, ROMAN_SYSTEMS } from "../../src/data/catalog.ts";
import { fetchJson, fetchOk } from "../lib/http.mjs";
import { lockAccent, pickSourceColor } from "./accent.mjs";
import { cleanAlbum, formatDuration, lyricWindow, needsRomanization, parseLrc } from "./lrc.mjs";
import { romanize } from "./romanize.mjs";

const ROOT = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const OUT_DIR = join(ROOT, "public", "generated");
export const CATALOG_FILE = join(OUT_DIR, "catalog.json");
const ARTWORK_SIZE = "600x600bb";
const ACCENT_SAMPLE_PX = 32;
const LRCLIB = "https://lrclib.net/api";
const MS_PER_SECOND = 1000;

async function itunesTrack({ itunesId, country }) {
  const data = await fetchJson(`https://itunes.apple.com/lookup?id=${itunesId}&country=${country}`);
  const track = data?.results?.[0];
  if (!track) throw new Error(`fetch-catalog: iTunes has no track ${itunesId} in ${country}`);
  return track;
}

/** RGB bytes of the cover scaled down, for the accent pick (ffmpeg decodes the JPEG). */
function coverPixels(file) {
  return execFileSync("ffmpeg", ["-hide_banner", "-loglevel", "error", "-i", file, "-vf", `scale=${ACCENT_SAMPLE_PX}:${ACCENT_SAMPLE_PX}`, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]);
}

async function downloadCover(song, track) {
  const url = track.artworkUrl100.replace("100x100bb", ARTWORK_SIZE);
  const res = await fetchOk(url);
  const relative = `generated/covers/${song.id}.jpg`;
  const file = join(ROOT, "public", relative);
  await writeFile(file, Buffer.from(await res.arrayBuffer()));
  return { relative, file };
}

/** LRCLIB exact signature first (what the app does), then the closest synced search hit. */
async function syncedLyrics(track) {
  const params = new URLSearchParams({ artist_name: track.artistName, track_name: track.trackName, album_name: track.collectionName, duration: String(Math.round(track.trackTimeMillis / MS_PER_SECOND)) });
  const exact = await fetchJson(`${LRCLIB}/get?${params}`, { allow404: true });
  if (exact?.syncedLyrics) return { lrc: exact.syncedLyrics, lrclibId: exact.id };
  const search = new URLSearchParams({ track_name: track.trackName, artist_name: track.artistName });
  const hits = ((await fetchJson(`${LRCLIB}/search?${search}`)) ?? []).filter((h) => h.syncedLyrics);
  if (!hits.length) throw new Error(`fetch-catalog: no synced lyrics on LRCLIB for "${track.trackName}"`);
  const seconds = track.trackTimeMillis / MS_PER_SECOND;
  hits.sort((a, b) => Math.abs(a.duration - seconds) - Math.abs(b.duration - seconds) || a.id - b.id);
  return { lrc: hits[0].syncedLyrics, lrclibId: hits[0].id };
}

async function buildTrack(song) {
  const track = await itunesTrack(song);
  const cover = await downloadCover(song, track);
  const { lrc, lrclibId } = await syncedLyrics(track);
  const window = lyricWindow(parseLrc(lrc), song.fromSeconds, LINES_PER_TRACK);
  const lyrics = [];
  for (const line of window) {
    lyrics.push({ k: line.text, r: needsRomanization(line.text, song.lang) ? await romanize(line.text, song.lang) : "" });
  }
  console.log(`  ${song.id}: "${track.trackName}" by ${track.artistName}, ${lyrics.length} lines (LRCLIB ${lrclibId})`);
  return {
    id: song.id,
    title: track.trackName,
    artist: track.artistName.toUpperCase(),
    album: cleanAlbum(track.collectionName).toUpperCase(),
    year: track.releaseDate.slice(0, 4),
    duration: formatDuration(track.trackTimeMillis / MS_PER_SECOND),
    lang: song.lang,
    languageName: song.languageName,
    romanSystem: ROMAN_SYSTEMS[song.lang],
    accent: lockAccent(pickSourceColor(coverPixels(cover.file))),
    coverFile: cover.relative,
    lyrics,
  };
}

export async function buildCatalog() {
  await mkdir(join(OUT_DIR, "covers"), { recursive: true });
  const tracks = {};
  for (const song of REAL_SONGS) tracks[song.id] = await buildTrack(song);
  const catalog = { source: "real", tracks };
  await writeFile(CATALOG_FILE, `${JSON.stringify(catalog, null, 2)}\n`);
  console.log(`fetch-catalog: ${REAL_SONGS.length} songs -> ${CATALOG_FILE}`);
  return catalog;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  buildCatalog().catch((err) => {
    console.error(err);
    process.exitCode = 1;
  });
}
