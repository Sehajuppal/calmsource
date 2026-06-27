// Generic content provider model. A provider is anything that can list
// live channels / VOD / series and resolve a playable URL on demand.
// Today only Xtream is implemented; tomorrow this is where Stremio
// extensions, debrid resolvers, or custom backends plug in.
//
// Pseudo URLs of the form `provider://<providerId>/<kind>/<streamId>`
// flow through the app as opaque strings. PlaybackResolver expands them
// at play time via the ProviderRegistry, so credentials/tokens can rotate
// without rewriting any stored channel.

export type ProviderKind = "live" | "vod" | "series";

export interface ProviderStream {
  /** Stable provider-local id (Xtream `stream_id`, etc.) */
  id: string;
  name: string;
  logo?: string;
  group?: string;
  /** Pre-built pseudo URL the app stores: `provider://<id>/<kind>/<sid>` */
  url: string;
}

export interface Provider {
  /** Stable id used in pseudo URLs. */
  id: string;
  /** Human label for UI. */
  label: string;
  kind: "xtream" | "m3u" | "custom";
  listLive(): Promise<ProviderStream[]>;
  listVod(): Promise<ProviderStream[]>;
  listSeries(): Promise<ProviderStream[]>;
  /** Expand a pseudo URL into a real playable URL. */
  resolve(kind: ProviderKind, streamId: string): string;
}

export function makeProviderUrl(providerId: string, kind: ProviderKind, streamId: string): string {
  return `provider://${providerId}/${kind}/${streamId}`;
}

export function parseProviderUrl(
  url: string,
): { providerId: string; kind: ProviderKind; streamId: string } | null {
  if (!url.startsWith("provider://")) return null;
  const rest = url.slice("provider://".length);
  const [providerId, kind, ...sidParts] = rest.split("/");
  if (!providerId || !kind || sidParts.length === 0) return null;
  if (kind !== "live" && kind !== "vod" && kind !== "series") return null;
  return { providerId, kind, streamId: sidParts.join("/") };
}
