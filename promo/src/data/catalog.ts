/**
 * The promo's song catalog: types, the real-song manifest, scene roles, and
 * the fictional catalog.
 *
 * Two catalogs share one shape:
 * - real (default): built by scripts/catalog/fetch-catalog.mjs from iTunes
 *   (metadata, cover art) and LRCLIB (synced lyrics) into the gitignored
 *   public/generated/catalog.json. Lyrics and covers belong to their
 *   labels and publishers and are never committed.
 * - fictional: the design handoff's made-up songs below, safe to publish.
 *   Render it with REMOTION_CATALOG=fictional.
 *
 * No imports on purpose: Node imports this file directly (scripts, tests).
 */

/** CJK languages carry a romanization line; ENG and IND do not. */
export type Lang = "KOR" | "JPN" | "ZHO" | "ENG" | "IND";
export type TrackId = "eng" | "kor" | "jpn" | "zho" | "ind";

export interface AccentSet {
  accent: string;
  dim: string;
  ink: string;
}

export interface LyricRow {
  /** Original script. */
  k: string;
  /** Romanization; empty when the line has no CJK script. */
  r: string;
}

/** A track as stored: cover is a file (real) or a CSS gradient (fictional). */
export interface TrackData {
  id: TrackId;
  title: string;
  artist: string;
  album: string;
  year: string;
  duration: string;
  lang: Lang;
  languageName: string;
  romanSystem: string;
  accent: AccentSet;
  coverFile?: string;
  coverGradient?: string;
  lyrics: LyricRow[];
}

/** A track ready to render: `cover` is a CSS background value. */
export interface Track extends TrackData {
  cover: string;
  /** True for real artwork, which gets no placeholder label or halftone. */
  isArtwork: boolean;
}

export interface PlayerCard {
  player: string;
  trackId: TrackId;
}

export interface CatalogData {
  source: "real" | "fictional";
  tracks: Record<TrackId, TrackData>;
}

export interface Catalog {
  source: "real" | "fictional";
  tracks: Record<TrackId, Track>;
}

export const ROMAN_SYSTEMS: Record<Lang, string> = {
  KOR: "REVISED ROMANIZATION",
  JPN: "HEPBURN ROMAJI",
  ZHO: "PINYIN · TONE MARKS",
  ENG: "",
  IND: "",
};

/** Lines kept per song: the hero scrolls through all of them. */
export const LINES_PER_TRACK = 10;
/** Scene 2 "finds" these five lines of the hero song; scene 3 folds them into the mark. */
export const FOUND_SLICE: readonly [number, number] = [2, 7];

/**
 * Real songs, chosen by Rama (the Mandarin pick is free). iTunes ids are
 * looked up in the store that carries the original-script title.
 * `fromSeconds` starts the lyric window there, past the intro.
 */
export const REAL_SONGS: readonly { id: TrackId; itunesId: number; country: string; lang: Lang; languageName: string; fromSeconds: number }[] = [
  { id: "eng", itunesId: 1889992113, country: "US", lang: "ENG", languageName: "ENGLISH", fromSeconds: 40 },
  { id: "kor", itunesId: 1773694547, country: "US", lang: "KOR", languageName: "KOREAN", fromSeconds: 40 },
  { id: "jpn", itunesId: 1544083979, country: "JP", lang: "JPN", languageName: "JAPANESE", fromSeconds: 40 },
  { id: "zho", itunesId: 535824738, country: "TW", lang: "ZHO", languageName: "MANDARIN", fromSeconds: 60 },
  { id: "ind", itunesId: 6784585105, country: "US", lang: "IND", languageName: "INDONESIAN", fromSeconds: 40 },
];

/** Which song plays where. */
export const ROLES = {
  hero: "eng",
  // 怪物 and 晴天 lock to nearly the same accent, so they are not adjacent.
  cycle: ["ind", "jpn", "kor", "zho"],
  spread: ["jpn", "kor", "zho"],
  overlay: "jpn",
  notification: "zho",
} as const satisfies Record<string, TrackId | readonly TrackId[]>;

/** Cold-open cards, one per beat: the same songs across every player. */
export const PLAYER_CARDS: readonly PlayerCard[] = [
  { player: "SPOTIFY", trackId: "eng" },
  { player: "YOUTUBE MUSIC", trackId: "kor" },
  { player: "SPOTIFY FREE", trackId: "ind" },
  { player: "ANY PLAYER", trackId: "jpn" },
  { player: "YOUTUBE MUSIC", trackId: "zho" },
  { player: "ANY PLAYER", trackId: "eng" },
  { player: "SPOTIFY", trackId: "ind" },
  { player: "SPOTIFY FREE", trackId: "kor" },
];

/** Accent presets from Color.kt, used by the fictional catalog and the brand. */
export const ACCENT_PRESETS = {
  coral: { accent: "#EC6A5C", dim: "#B7564A", ink: "#0E0C12" },
  violet: { accent: "#A17BFF", dim: "#7A5FC4", ink: "#0E0C12" },
  jade: { accent: "#3CC9A1", dim: "#2E9A7B", ink: "#0E0C12" },
  /** AccentLock(#E0A030): the fictional Indonesian track's amber. */
  amber: { accent: "#F2A700", dim: "#B8883C", ink: "#0E0C12" },
} as const satisfies Record<string, AccentSet>;

const latin = (lines: string[]): LyricRow[] => lines.map((k) => ({ k, r: "" }));

export const FICTIONAL_TRACKS: Record<TrackId, TrackData> = {
  eng: {
    id: "eng",
    title: "Midnight Static",
    artist: "THE LATE BLOOMS",
    album: "LOW TIDE RADIO",
    year: "2026",
    duration: "3:28",
    lang: "ENG",
    languageName: "ENGLISH",
    romanSystem: "",
    accent: ACCENT_PRESETS.coral,
    coverGradient:
      "radial-gradient(ellipse 80% 60% at 70% 25%, oklch(0.86 0.15 65), transparent 60%), radial-gradient(ellipse 90% 70% at 25% 80%, oklch(0.55 0.19 25), transparent 70%), linear-gradient(150deg, oklch(0.34 0.12 45), oklch(0.20 0.07 10))",
    lyrics: latin([
      "Static on the dashboard",
      "City lights in slow motion",
      "I keep the radio on",
      "Every word comes back to me",
      "Singing with the windows down",
      "Midnight, you know the song",
      "Turn it up, don't let it fade",
      "Every line, every line",
      "We know it all by heart",
      "Static on the dashboard",
    ]),
  },
  kor: {
    id: "kor",
    title: "내일의 너에게",
    artist: "YIEUN",
    album: "PAPER MOON",
    year: "2025",
    duration: "3:42",
    lang: "KOR",
    languageName: "KOREAN",
    romanSystem: ROMAN_SYSTEMS.KOR,
    accent: ACCENT_PRESETS.coral,
    coverGradient:
      "radial-gradient(ellipse 80% 60% at 30% 20%, oklch(0.88 0.18 35), transparent 60%), radial-gradient(ellipse 90% 70% at 70% 80%, oklch(0.55 0.20 15), transparent 70%), linear-gradient(135deg, oklch(0.32 0.14 25), oklch(0.22 0.08 350))",
    lyrics: [
      { k: "오, 오, 오", r: "oh, oh, oh" },
      { k: "내일의 너에게 편지를 써", r: "naeil-ui neo-ege pyeonji-reul sseo" },
      { k: "오늘의 내가 미안하다고", r: "oneul-ui nae-ga mianhada-go" },
      { k: "눈을 감으면 들리는 목소리", r: "nun-eul gameumyeon deullineun moksori" },
      { k: "잊고 있던 그날의 약속", r: "itgo itdeon geunal-ui yaksok" },
      { k: "다시 한번, 다시 한번만", r: "dasi hanbeon, dasi hanbeon-man" },
      { k: "나는 너의 봄이 될게", r: "naneun neo-ui bom-i doel-ge" },
      { k: "내일의 너에게", r: "naeil-ui neo-ege" },
      { k: "이 노래를 보낼게", r: "i norae-reul bonael-ge" },
      { k: "오, 오, 오", r: "oh, oh, oh" },
    ],
  },
  jpn: {
    id: "jpn",
    title: "夜明けのスタンス",
    artist: "AOI",
    album: "NEON HEIAN",
    year: "2025",
    duration: "4:18",
    lang: "JPN",
    languageName: "JAPANESE",
    romanSystem: ROMAN_SYSTEMS.JPN,
    accent: ACCENT_PRESETS.violet,
    coverGradient:
      "radial-gradient(ellipse at 20% 30%, oklch(0.55 0.22 290), transparent 55%), radial-gradient(ellipse at 80% 70%, oklch(0.35 0.18 270), transparent 60%), linear-gradient(160deg, oklch(0.22 0.10 290), oklch(0.18 0.08 320))",
    lyrics: [
      { k: "街の灯が消える前に", r: "machi no hi ga kieru mae ni" },
      { k: "君に伝えたい", r: "kimi ni tsutaetai" },
      { k: "まだ言葉にできない", r: "mada kotoba ni dekinai" },
      { k: "夜明けのスタンス", r: "yoake no sutansu" },
      { k: "僕らの距離は", r: "bokura no kyori wa" },
      { k: "光より速く", r: "hikari yori hayaku" },
      { k: "消えていくのに", r: "kieteiku noni" },
      { k: "なぜ振り返るの", r: "naze furikaeru no" },
      { k: "夜明けのスタンス", r: "yoake no sutansu" },
      { k: "君に伝えたい", r: "kimi ni tsutaetai" },
    ],
  },
  zho: {
    id: "zho",
    title: "未來進行式",
    artist: "LIN HAO",
    album: "CASSETTE THEORY",
    year: "2024",
    duration: "3:55",
    lang: "ZHO",
    languageName: "MANDARIN",
    romanSystem: ROMAN_SYSTEMS.ZHO,
    accent: ACCENT_PRESETS.jade,
    coverGradient:
      "radial-gradient(ellipse at 30% 30%, oklch(0.72 0.18 165), transparent 55%), radial-gradient(ellipse at 70% 80%, oklch(0.45 0.14 180), transparent 60%), linear-gradient(140deg, oklch(0.28 0.12 165), oklch(0.18 0.06 200))",
    lyrics: [
      { k: "時間是一條河", r: "shíjiān shì yītiáo hé" },
      { k: "我們在岸上", r: "wǒmen zài àn shàng" },
      { k: "看著彼此的倒影", r: "kànzhe bǐcǐ de dàoyǐng" },
      { k: "未來進行式", r: "wèilái jìnxíngshì" },
      { k: "是現在的我", r: "shì xiànzài de wǒ" },
      { k: "愛著明天的你", r: "àizhe míngtiān de nǐ" },
      { k: "在錄音帶倒轉之前", r: "zài lùyīndài dào zhuǎn zhīqián" },
      { k: "把這句話留住", r: "bǎ zhè jù huà liúzhù" },
      { k: "未來進行式", r: "wèilái jìnxíngshì" },
      { k: "愛著明天的你", r: "àizhe míngtiān de nǐ" },
    ],
  },
  ind: {
    id: "ind",
    title: "Hujan di Jendela Kereta",
    artist: "SENJA RADIO",
    album: "STASIUN TERAKHIR",
    year: "2026",
    duration: "3:51",
    lang: "IND",
    languageName: "INDONESIAN",
    romanSystem: "",
    accent: ACCENT_PRESETS.amber,
    coverGradient:
      "radial-gradient(ellipse 70% 60% at 30% 30%, oklch(0.84 0.14 80), transparent 60%), radial-gradient(ellipse 80% 70% at 75% 75%, oklch(0.50 0.12 60), transparent 70%), linear-gradient(145deg, oklch(0.32 0.08 70), oklch(0.20 0.05 40))",
    lyrics: latin([
      "Hujan turun di jendela kereta",
      "Kota berlari ke belakang",
      "Kusimpan lagu ini untukmu",
      "Satu baris, satu cerita",
      "Kita bernyanyi pelan-pelan",
      "Sampai stasiun terakhir",
      "Lampu kota mulai menyala",
      "Setiap baris, setiap kata",
      "Masih kuingat semuanya",
      "Hujan turun di jendela kereta",
    ]),
  },
};

export const FICTIONAL_CATALOG: CatalogData = { source: "fictional", tracks: FICTIONAL_TRACKS };

/** Every track id, in a stable order. */
export const TRACK_IDS: readonly TrackId[] = ["eng", "kor", "jpn", "zho", "ind"];

/**
 * Problems that would break a render, empty when the catalog is usable:
 * every track present with enough lines, CJK lines romanized, a cover.
 */
export function validateCatalog(data: CatalogData): string[] {
  const problems: string[] = [];
  for (const id of TRACK_IDS) {
    const t = data.tracks?.[id];
    if (!t) {
      problems.push(`missing track "${id}"`);
      continue;
    }
    if (t.lyrics.length < LINES_PER_TRACK) problems.push(`${id}: ${t.lyrics.length} lyric lines, need ${LINES_PER_TRACK}`);
    if (!t.coverFile && !t.coverGradient) problems.push(`${id}: no cover`);
    if (!/^#[0-9A-F]{6}$/i.test(t.accent?.accent ?? "")) problems.push(`${id}: accent is not a hex colour`);
    const cjk = /[぀-ヿ㐀-鿿가-힯]/u;
    t.lyrics.filter((l) => cjk.test(l.k) && !l.r.trim()).forEach((l) => problems.push(`${id}: no romanization for "${l.k}"`));
  }
  return problems;
}
