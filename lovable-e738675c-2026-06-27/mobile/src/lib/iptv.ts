export type IPTVChannel = {
  id: string;
  name: string;
  logo?: string;
  group?: string;
  url: string;
  /** XMLTV channel id from `tvg-id`, used to match EPG programmes. */
  tvgId?: string;
};

const VALID_URL = /^(https?|rtmp|rtsp|udp|mms):\/\//i;
const safeText = (s: string, max = 200) =>
  s.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, max);
const safeLogo = (s?: string) => {
  if (!s) return undefined;
  const t = s.trim();
  return /^https?:\/\//i.test(t) ? t.slice(0, 500) : undefined;
};

// Minimal M3U / M3U8 playlist parser.
// Accepts text in #EXTM3U format with #EXTINF metadata lines.
export function parseM3U(text: string): IPTVChannel[] {
  if (typeof text !== "string" || !text.trim()) return [];
  const lines = text.split(/\r?\n/);
  const channels: IPTVChannel[] = [];
  let pending: Partial<IPTVChannel> | null = null;
  let idx = 0;

  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith("#EXTM3U")) continue;

    if (line.startsWith("#EXTINF")) {
      const comma = line.indexOf(",");
      const attrPart = comma > -1 ? line.slice(8, comma) : line.slice(8);
      const name = comma > -1 ? safeText(line.slice(comma + 1)) : "Channel";
      const attrs: Record<string, string> = {};
      for (const m of attrPart.matchAll(/([\w-]+)="([^"]*)"/g)) {
        attrs[m[1].toLowerCase()] = m[2];
      }
      pending = {
        name: name || "Channel",
        logo: safeLogo(attrs["tvg-logo"]),
        group: safeText(attrs["group-title"] ?? "Live", 80),
        tvgId: attrs["tvg-id"] ? safeText(attrs["tvg-id"]) : undefined,
      };
      continue;
    }

    if (line.startsWith("#")) continue;
    if (!VALID_URL.test(line)) { pending = null; continue; }

    const url = line;
    const meta = pending ?? { name: `Channel ${idx + 1}`, group: "Live" };
    channels.push({
      id: `ch-${idx++}`,
      name: meta.name ?? `Channel ${idx}`,
      logo: meta.logo,
      group: meta.group ?? "Live",
      tvgId: meta.tvgId,
      url,
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
