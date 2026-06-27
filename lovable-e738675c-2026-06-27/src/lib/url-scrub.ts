/**
 * Strip credentials from a URL before logging or persisting it.
 * Removes userinfo (user:pass@) and the common credential query params
 * (username, password, token, auth, key). Returns "[invalid-url]" for
 * non-URL strings so callers can log unconditionally.
 */
const SECRET_PARAMS = ["username", "password", "token", "auth", "key", "apikey", "api_key"];

export function scrubUrl(input: string | null | undefined): string {
  if (!input) return "";
  try {
    const u = new URL(input);
    if (u.username || u.password) {
      u.username = "";
      u.password = "";
    }
    for (const p of SECRET_PARAMS) {
      if (u.searchParams.has(p)) u.searchParams.set(p, "***");
    }
    return u.toString();
  } catch {
    return "[invalid-url]";
  }
}

/** Scrub any object/string for safe logging. */
export function scrubForLog(value: unknown): unknown {
  if (typeof value === "string") {
    // Only scrub when it looks like a URL.
    return /^https?:\/\//i.test(value) ? scrubUrl(value) : value;
  }
  return value;
}
