// fetch with retries and an identifying User-Agent, shared by the asset
// scripts. LRCLIB asks clients to identify themselves; the Google Fonts and
// iTunes edges time out now and then.

const ATTEMPTS = 5;
const BASE_DELAY_MS = 1500;
const MS_PER_SECOND = 1000;
const TOO_MANY_REQUESTS = 429;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Retry-After seconds when the server sent one, else exponential backoff. */
function backoffMs(res, attempt) {
  const retryAfter = Number(res?.headers.get("retry-after"));
  return Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * MS_PER_SECOND : BASE_DELAY_MS * 2 ** (attempt - 1);
}
export const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 spotivibe-promo (github.com/tektungg/spotivibe-android)";

/** GET `url`; returns the Response, or null on a 404 when `allow404`. */
export async function fetchOk(url, { allow404 = false } = {}) {
  let lastError;
  for (let attempt = 1; attempt <= ATTEMPTS; attempt++) {
    let res = null;
    try {
      res = await fetch(url, { headers: { "User-Agent": USER_AGENT } });
      if (res.ok) return res;
      if (res.status === 404 && allow404) return null;
      lastError = new Error(`status ${res.status}`);
    } catch (err) {
      lastError = err;
    }
    const wait = backoffMs(res?.status === TOO_MANY_REQUESTS ? res : null, attempt);
    console.warn(`  retry ${attempt}/${ATTEMPTS} in ${wait} ms (${lastError.message}) for ${url.slice(0, 80)}...`);
    await sleep(wait);
  }
  throw new Error(`GET ${url} failed: ${lastError}`);
}

export async function fetchJson(url, options) {
  const res = await fetchOk(url, options);
  return res ? res.json() : null;
}
