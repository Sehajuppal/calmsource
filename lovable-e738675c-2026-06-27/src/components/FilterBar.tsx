import { ArrowDownAZ, ArrowUpAZ, Calendar, Sparkles } from "lucide-react";

export type SortMode = "featured" | "az" | "za" | "year-new" | "year-old";

const SORTS: { id: SortMode; label: string; Icon: typeof Sparkles }[] = [
  { id: "featured", label: "Featured", Icon: Sparkles },
  { id: "az", label: "A–Z", Icon: ArrowDownAZ },
  { id: "za", label: "Z–A", Icon: ArrowUpAZ },
  { id: "year-new", label: "Newest", Icon: Calendar },
  { id: "year-old", label: "Oldest", Icon: Calendar },
];

export function FilterBar({
  genres,
  activeGenre,
  setActiveGenre,
  sort,
  setSort,
}: {
  genres: string[];
  activeGenre: string;
  setActiveGenre: (g: string) => void;
  sort: SortMode;
  setSort: (s: SortMode) => void;
}) {
  return (
    <div className="mx-auto flex max-w-[1600px] flex-col gap-3 px-5 sm:flex-row sm:items-center sm:justify-between sm:px-10">
      <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-hide">
        <Chip active={activeGenre === "all"} onClick={() => setActiveGenre("all")}>
          All
        </Chip>
        {genres.map((g) => (
          <Chip key={g} active={activeGenre === g} onClick={() => setActiveGenre(g)}>
            {g}
          </Chip>
        ))}
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        <label htmlFor="sort-select" className="sr-only">Sort by</label>
        <select
          id="sort-select"
          value={sort}
          onChange={(e) => setSort(e.target.value as SortMode)}
          className="rounded-full border border-white/10 bg-white/5 px-3.5 py-1.5 text-[12px] font-medium focus:border-white/30 focus:outline-none"
        >
          {SORTS.map((s) => (
            <option key={s.id} value={s.id}>Sort: {s.label}</option>
          ))}
        </select>
      </div>
    </div>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`shrink-0 rounded-full px-3.5 py-1.5 text-[12px] font-medium transition ${
        active
          ? "bg-foreground text-background"
          : "border border-white/10 bg-white/5 text-muted-foreground hover:bg-white/10 hover:text-foreground"
      }`}
    >
      {children}
    </button>
  );
}
