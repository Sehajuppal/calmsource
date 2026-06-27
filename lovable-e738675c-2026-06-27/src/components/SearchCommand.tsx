import { useEffect, useMemo, useRef, useState } from "react";
import { Clock, Film, Radio, Tv } from "lucide-react";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "./ui/command";
import { FILMS, SERIES, type Title } from "../lib/catalog";
import type { IPTVChannel } from "../lib/iptv";

type Recent =
  | { kind: "title"; id: string; name: string }
  | { kind: "channel"; id: string; name: string };

const RECENTS_KEY = "cs:cmdk:recents";
const MAX_RECENTS = 6;

function loadRecents(): Recent[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(RECENTS_KEY);
    return raw ? (JSON.parse(raw) as Recent[]).slice(0, MAX_RECENTS) : [];
  } catch {
    return [];
  }
}
function pushRecent(r: Recent) {
  if (typeof window === "undefined") return;
  try {
    const cur = loadRecents().filter((x) => !(x.kind === r.kind && x.id === r.id));
    const next = [r, ...cur].slice(0, MAX_RECENTS);
    window.localStorage.setItem(RECENTS_KEY, JSON.stringify(next));
  } catch {
    /* ignore */
  }
}

type Props = {
  channels: IPTVChannel[];
  onPickTitle: (t: Title) => void;
  onPickChannel: (c: IPTVChannel) => void;
  open: boolean;
  setOpen: (v: boolean) => void;
};

export default function SearchCommand({
  channels,
  onPickTitle,
  onPickChannel,
  open,
  setOpen,
}: Props) {
  const [query, setQuery] = useState("");

  // Mirror `open` in a ref so the global keydown handler can read the latest
  // value without depending on it — lets the effect run once for the
  // component's lifetime instead of rebuilding the listener on every toggle.
  const openRef = useRef(open);
  openRef.current = open;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen(!openRef.current);
      } else if (e.key === "/" && !openRef.current) {
        const tag = (e.target as HTMLElement | null)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault();
        setOpen(true);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [setOpen]);

  const [recents, setRecents] = useState<Recent[]>([]);
  useEffect(() => {
    if (open) setRecents(loadRecents());
  }, [open]);

  const q = query.trim().toLowerCase();
  const films = useMemo(
    () => (q ? FILMS.filter((f) => f.name.toLowerCase().includes(q)).slice(0, 8) : FILMS.slice(0, 6)),
    [q],
  );
  const series = useMemo(
    () => (q ? SERIES.filter((f) => f.name.toLowerCase().includes(q)).slice(0, 8) : SERIES.slice(0, 6)),
    [q],
  );
  const chans = useMemo(
    () =>
      q
        ? channels.filter((c) => c.name.toLowerCase().includes(q)).slice(0, 10)
        : channels.slice(0, 6),
    [q, channels],
  );

  const pickTitle = (f: Title) => {
    pushRecent({ kind: "title", id: f.id, name: f.name });
    onPickTitle(f);
    setOpen(false);
  };
  const pickChannel = (c: IPTVChannel) => {
    pushRecent({ kind: "channel", id: c.id, name: c.name });
    onPickChannel(c);
    setOpen(false);
  };

  return (
    <CommandDialog open={open} onOpenChange={setOpen}>
      <CommandInput
        placeholder="Search films, series, channels…  (⌘K)"
        value={query}
        onValueChange={setQuery}
      />
      <CommandList>
        <CommandEmpty>No results.</CommandEmpty>
        {!q && recents.length > 0 && (
          <>
            <CommandGroup heading="Recent">
              {recents.map((r) => (
                <CommandItem
                  key={`r-${r.kind}-${r.id}`}
                  value={`recent-${r.kind}-${r.id}-${r.name}`}
                  onSelect={() => {
                    if (r.kind === "title") {
                      const t = [...FILMS, ...SERIES].find((x) => x.id === r.id);
                      if (t) pickTitle(t);
                    } else {
                      const c = channels.find((x) => x.id === r.id);
                      if (c) pickChannel(c);
                    }
                  }}
                >
                  <Clock className="mr-2 h-4 w-4 opacity-60" />
                  <span>{r.name}</span>
                  <span className="ml-auto text-[11px] text-muted-foreground capitalize">
                    {r.kind}
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
            <CommandSeparator />
          </>
        )}
        {films.length > 0 && (
          <CommandGroup heading="Films">
            {films.map((f) => (
              <CommandItem
                key={f.id}
                value={`film-${f.id}-${f.name}`}
                onSelect={() => pickTitle(f)}
              >
                <Film className="mr-2 h-4 w-4 opacity-60" />
                <span>{f.name}</span>
                <span className="ml-auto text-[11px] text-muted-foreground">{f.year}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}
        {series.length > 0 && (
          <CommandGroup heading="Series">
            {series.map((s) => (
              <CommandItem
                key={s.id}
                value={`series-${s.id}-${s.name}`}
                onSelect={() => pickTitle(s)}
              >
                <Tv className="mr-2 h-4 w-4 opacity-60" />
                <span>{s.name}</span>
                <span className="ml-auto text-[11px] text-muted-foreground">{s.year}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}
        {chans.length > 0 && (
          <CommandGroup heading="Live channels">
            {chans.map((c) => (
              <CommandItem
                key={c.id}
                value={`chan-${c.id}-${c.name}`}
                onSelect={() => pickChannel(c)}
              >
                <Radio className="mr-2 h-4 w-4 opacity-60" />
                <span>{c.name}</span>
                <span className="ml-auto text-[11px] text-muted-foreground">
                  {c.group ?? "Live"}
                </span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}
      </CommandList>
    </CommandDialog>
  );
}
