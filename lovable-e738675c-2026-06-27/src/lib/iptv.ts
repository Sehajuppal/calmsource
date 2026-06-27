export type IPTVChannel = {
  id: string;
  name: string;
  logo?: string;
  group?: string;
  url: string;
  /** XMLTV channel id from `tvg-id`, used to match EPG programmes. */
  tvgId?: string;
};

// Stable, small string hash (djb2 xor) used to derive deterministic
// channel ids from name+url. Not crypto — just collision-resistant enough
// to keep favorites keyed across playlist refreshes.
function hashId(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h * 33) ^ s.charCodeAt(i)) >>> 0;
  return h.toString(36);
}

// Minimal M3U / M3U8 playlist parser.
// Accepts text in #EXTM3U format with #EXTINF metadata lines.
export function parseM3U(text: string): IPTVChannel[] {
  if (typeof text !== "string" || !text.trim()) return [];

  const lines = text.split(/\r?\n/);
  const channels: IPTVChannel[] = [];
  let pending: Partial<IPTVChannel> | null = null;
  let idx = 0;

  for (const raw of lines) {
    const line = (raw ?? "").trim();
    if (!line) continue;
    if (line.startsWith("#EXTM3U")) continue;

    if (line.startsWith("#EXTINF")) {
      try {
        const comma = line.indexOf(",");
        const attrPart = comma > -1 ? line.slice(8, comma) : line.slice(8);
        const rawName = comma > -1 ? line.slice(comma + 1).trim() : "Channel";
        const attrs: Record<string, string> = {};
        for (const m of attrPart.matchAll(/([\w-]+)="([^"]*)"/g)) {
          attrs[m[1].toLowerCase()] = m[2];
        }
        const safeText = (s: string | undefined, max: number) =>
          (s ?? "").replace(/[\u0000-\u001F\u007F<>]/g, "").slice(0, max);
        const safeLogo = (s: string | undefined) => {
          if (!s) return undefined;
          const v = s.trim();
          if (v.length > 2048) return undefined;
          return /^https?:\/\//i.test(v) ? v : undefined;
        };
        pending = {
          name: safeText(rawName, 200) || "Channel",
          logo: safeLogo(attrs["tvg-logo"]),
          group: safeText(attrs["group-title"], 80) || "Live",
          tvgId: safeText(attrs["tvg-id"], 200) || undefined,
        };
      } catch {
        pending = { name: "Channel", group: "Live" };
      }
      continue;
    }

    if (line.startsWith("#")) continue;

    // Only accept known stream URL schemes
    if (!/^(https?|rtmp|rtsp|udp|mms):\/\//i.test(line)) continue;

    const seq = idx++;
    const meta = pending ?? { name: `Channel ${seq + 1}`, group: "Live" };
    const name = meta.name ?? `Channel ${seq + 1}`;
    // Stable id derived from tvg-id (preferred) or name+url. Positional
    // ids broke favorites / continue-watching whenever the playlist was
    // refreshed or the upstream reordered.
    const id = meta.tvgId ? `tvg:${meta.tvgId}` : `hx:${hashId(name + "|" + line)}`;
    channels.push({
      id,
      name,
      logo: meta.logo,
      group: meta.group ?? "Live",
      tvgId: meta.tvgId,
      url: line,
    });
    pending = null;
  }

  return channels;
}

export const SAMPLE_CHANNELS: IPTVChannel[] = [
  {
    id: "demo-bigbuck",
    name: "Big Buck Bunny",
    group: "Demo",
    url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
  },
  {
    id: "demo-sintel",
    name: "Sintel",
    group: "Demo",
    url: "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
  },
  {
    id: "demo-tears",
    name: "Tears of Steel",
    group: "Demo",
    url: "https://test-streams.mux.dev/pts_shift/master.m3u8",
  },
  {
    id: "demo-apple",
    name: "Apple BipBop",
    group: "Demo",
    url: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
  },
];
