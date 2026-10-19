/**
 * Sample tracks, ported from claude-design-handoff/screens-shared.jsx.
 * Fictional artists and lyrics, so the promo shows no real catalog.
 *
 * No imports on purpose: scripts/prepare-assets.mjs imports this file with
 * Node to collect every CJK glyph and download a font subset for them.
 */

/** CJK languages carry a romanization line; ENG and IND do not. */
export type Lang = "KOR" | "JPN" | "ZHO" | "ENG" | "IND";
export type TrackId = "kor" | "jpn" | "zho" | "eng";

export interface LyricRow {
  /** Original script. */
  k: string;
  /** Romanization; empty for Latin-script languages. */
  r: string;
}

export interface Track {
  accent: "coral" | "violet" | "jade";
  title: string;
  titleRoman: string;
  artist: string;
  album: string;
  year: string;
  duration: string;
  lang: Lang;
  /** Language name for labels, e.g. "KOREAN". */
  languageName: string;
  romanSystem: string;
  coverGradient: string;
  lyrics: LyricRow[];
}

export const TRACKS: Record<TrackId, Track> = {
  kor: {
    accent: "coral",
    title: "내일의 너에게",
    titleRoman: "Naeil-ui Neoege",
    artist: "YIEUN",
    album: "PAPER MOON",
    year: "2025",
    duration: "3:42",
    lang: "KOR",
    languageName: "KOREAN",
    romanSystem: "REVISED ROMANIZATION",
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
    accent: "violet",
    title: "夜明けのスタンス",
    titleRoman: "Yoake no Stance",
    artist: "AOI",
    album: "NEON HEIAN",
    year: "2025",
    duration: "4:18",
    lang: "JPN",
    languageName: "JAPANESE",
    romanSystem: "HEPBURN ROMAJI",
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
    ],
  },
  zho: {
    accent: "jade",
    title: "未來進行式",
    titleRoman: "Wèilái Jìnxíngshì",
    artist: "LIN HAO",
    album: "CASSETTE THEORY",
    year: "2024",
    duration: "3:55",
    lang: "ZHO",
    languageName: "MANDARIN",
    romanSystem: "PINYIN · TONE MARKS",
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
    ],
  },
  eng: {
    accent: "coral",
    title: "Midnight Static",
    titleRoman: "",
    artist: "THE LATE BLOOMS",
    album: "LOW TIDE RADIO",
    year: "2026",
    duration: "3:28",
    lang: "ENG",
    languageName: "ENGLISH",
    romanSystem: "",
    coverGradient:
      "radial-gradient(ellipse 80% 60% at 70% 25%, oklch(0.86 0.15 65), transparent 60%), radial-gradient(ellipse 90% 70% at 25% 80%, oklch(0.55 0.19 25), transparent 70%), linear-gradient(150deg, oklch(0.34 0.12 45), oklch(0.20 0.07 10))",
    lyrics: [
      { k: "Static on the dashboard", r: "" },
      { k: "City lights in slow motion", r: "" },
      { k: "I keep the radio on", r: "" },
      { k: "Every word comes back to me", r: "" },
      { k: "Singing with the windows down", r: "" },
      { k: "Midnight, you know the song", r: "" },
      { k: "Turn it up, don't let it fade", r: "" },
      { k: "Every line, every line", r: "" },
      { k: "We know it all by heart", r: "" },
      { k: "Static on the dashboard", r: "" },
    ],
  },
};

/** The five lines Spotivibe "finds" in scene 2 and folds into the mark in scene 3. */
export const FOUND_ROWS = TRACKS.eng.lyrics.slice(2, 7);

/** The three CJK tracks in accent order (coral, violet, jade). */
export const TRACK_ORDER = [TRACKS.kor, TRACKS.jpn, TRACKS.zho] as const;

/** A song playing in some player, before Spotivibe finds its lyrics. */
export interface PlayerCard {
  player: string;
  title: string;
  artist: string;
  lang: Lang;
  accent: "coral" | "violet" | "jade";
}

/**
 * Cold-open cards, one per beat. Players are the ones the README names
 * (Spotify incl. Free, YouTube Music) plus "any player" for every other
 * MediaSession app. Songs and artists are fictional.
 */
export const PLAYER_CARDS: readonly PlayerCard[] = [
  { player: "SPOTIFY", title: "Midnight Static", artist: "THE LATE BLOOMS", lang: "ENG", accent: "coral" },
  { player: "YOUTUBE MUSIC", title: "내일의 너에게", artist: "YIEUN", lang: "KOR", accent: "violet" },
  { player: "SPOTIFY FREE", title: "Hujan di Jendela Kereta", artist: "SENJA RADIO", lang: "IND", accent: "jade" },
  { player: "ANY PLAYER", title: "夜明けのスタンス", artist: "AOI", lang: "JPN", accent: "coral" },
  { player: "YOUTUBE MUSIC", title: "Golden Hour Drive", artist: "MARA VALE", lang: "ENG", accent: "violet" },
  { player: "SPOTIFY", title: "未來進行式", artist: "LIN HAO", lang: "ZHO", accent: "jade" },
  { player: "ANY PLAYER", title: "Lampu Kota Terakhir", artist: "DUA SENJA", lang: "IND", accent: "coral" },
  { player: "SPOTIFY FREE", title: "Neon Laundromat", artist: "KITE & CO", lang: "ENG", accent: "violet" },
];
