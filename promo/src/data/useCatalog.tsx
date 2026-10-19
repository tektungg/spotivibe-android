import React, { createContext, useContext } from "react";
import { staticFile } from "remotion";
import { FICTIONAL_CATALOG, TRACK_IDS, validateCatalog, type Catalog, type CatalogData, type Track, type TrackData, type TrackId } from "./catalog";

const CATALOG_FILE = "generated/catalog.json";
const FALLBACK_COVER = "#22232A";

/**
 * Which catalog to render. Remotion exposes REMOTION_* environment variables
 * to the bundle: `REMOTION_CATALOG=fictional npm run render` renders the
 * store-safe made-up songs; anything else renders the real catalog.
 */
const wantsFictional = (): boolean => process.env.REMOTION_CATALOG === "fictional";

/** Loads the catalog for calculateMetadata. Throws with a fix when it is missing or broken. */
export async function loadCatalog(): Promise<CatalogData> {
  if (wantsFictional()) return FICTIONAL_CATALOG;
  const res = await fetch(staticFile(CATALOG_FILE));
  if (!res.ok) throw new Error(`loadCatalog: ${CATALOG_FILE} is missing (${res.status}); run "npm run assets", or render with REMOTION_CATALOG=fictional`);
  const data = (await res.json()) as CatalogData;
  const problems = validateCatalog(data);
  if (problems.length) throw new Error(`loadCatalog: ${CATALOG_FILE} is unusable: ${problems.join("; ")}`);
  return data;
}

function resolveTrack(t: TrackData): Track {
  if (t.coverFile) return { ...t, cover: `url("${staticFile(t.coverFile)}") center / cover no-repeat`, isArtwork: true };
  return { ...t, cover: t.coverGradient ?? FALLBACK_COVER, isArtwork: false };
}

export function resolveCatalog(data: CatalogData): Catalog {
  const tracks = Object.fromEntries(TRACK_IDS.map((id) => [id, resolveTrack(data.tracks[id])])) as Record<TrackId, Track>;
  return { source: data.source, tracks };
}

const CatalogContext = createContext<Catalog | null>(null);

export const CatalogProvider: React.FC<{ catalog: Catalog; children: React.ReactNode }> = ({ catalog, children }) => (
  <CatalogContext.Provider value={catalog}>{children}</CatalogContext.Provider>
);

export function useCatalog(): Catalog {
  const catalog = useContext(CatalogContext);
  if (!catalog) throw new Error("useCatalog: no CatalogProvider above this component");
  return catalog;
}

/** Shorthand: one track by id. */
export function useTrack(id: TrackId): Track {
  return useCatalog().tracks[id];
}
