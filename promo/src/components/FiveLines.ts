/**
 * Layout of the five centered lyric lines shared by the "found" scene and the
 * logo morph: the found scene ends exactly where the morph begins, so both
 * must use the same sizes and spacing. Index 2 is the active (lit) line, the
 * same as the mark's lit bar.
 */
export const FIVE_LINE_SIZES = [46, 60, 86, 60, 46] as const;
export const FIVE_LINE_GAP = 112;

/** Empty lyric slots before lyrics are found: fractions of SLOT_WIDTH, in the mark's rhythm. */
export const EMPTY_SLOT_FRACTIONS = [0.52, 0.8, 1, 0.72, 0.45] as const;
export const EMPTY_SLOT_WIDTH = 640;
