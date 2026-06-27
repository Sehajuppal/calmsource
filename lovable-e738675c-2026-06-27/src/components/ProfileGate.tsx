import { memo, useEffect, useMemo, useState } from "react";
import { Pencil, Plus, Check, Lock } from "lucide-react";
import type { Profile } from "../lib/profiles";
import { FEATURED } from "../lib/catalog";
import { PROFILE_AVATARS, getProfileAvatarUrl } from "../lib/profiles";
import { PinDialog } from "./PinDialog";

function ProfileGate({
  profiles,
  onSelect,
  onAdd,
  onRename,
  onAvatarChange,
  onPinChange,
}: {
  profiles: Profile[];
  onSelect: (p: Profile) => void;
  onAdd: () => void;
  onRename: (id: string, name: string) => void;
  onAvatarChange: (id: string, avatarId: string) => void;
  onPinChange?: (id: string, pin: string | null) => void;
}) {
  const [pinPrompt, setPinPrompt] = useState<Profile | null>(null);
  const [draftPin, setDraftPin] = useState("");
  const [editing, setEditing] = useState(false);
  const [draftId, setDraftId] = useState<string | null>(null);
  const [draftName, setDraftName] = useState("");
  const [draftAvatarId, setDraftAvatarId] = useState<string>(PROFILE_AVATARS[0]?.id ?? "");

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (draftId) {
          setDraftId(null);
          setDraftName("");
        } else if (editing) {
          setEditing(false);
        }
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [editing, draftId]);

  const activeDraftProfile = useMemo(
    () => profiles.find((profile) => profile.id === draftId) ?? null,
    [profiles, draftId],
  );

  const commitDraft = () => {
    if (!draftId) return;
    if (draftName.trim()) onRename(draftId, draftName.trim());
    if (draftAvatarId) onAvatarChange(draftId, draftAvatarId);
    if (onPinChange) {
      const trimmed = draftPin.trim();
      if (trimmed === "") onPinChange(draftId, null);
      else if (/^\d{4}$/.test(trimmed)) onPinChange(draftId, trimmed);
    }
    setDraftId(null);
    setDraftName("");
    setDraftPin("");
  };

  const handlePick = (p: Profile) => {
    if (p.pin) {
      setPinPrompt(p);
    } else {
      onSelect(p);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] overflow-y-auto bg-background" role="dialog" aria-modal="true" aria-labelledby="profile-gate-title">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.28]"
        style={{
          // Use the 640×360 tile asset — at 40px blur, source resolution is
          // indistinguishable, and we save ~7MB of decoded image memory.
          backgroundImage: `url(${FEATURED.tile})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          filter: "blur(40px) saturate(1.1)",
          transform: "scale(1.1)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(900px 600px at 18% 8%, oklch(0.32 0.16 285 / 0.55), transparent 60%), radial-gradient(1000px 700px at 88% 92%, oklch(0.28 0.14 220 / 0.45), transparent 60%)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "linear-gradient(180deg, oklch(0.14 0.02 270 / 0.55) 0%, oklch(0.10 0.02 270 / 0.85) 60%, var(--background) 100%)",
        }}
      />

      <div className="relative mx-auto flex min-h-full w-full max-w-6xl flex-col items-center justify-center px-5 py-20 sm:px-8 sm:py-28">
        <div className="inline-flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.22em] text-muted-foreground sm:text-[11px]">
          <span className="h-1 w-1 rounded-full bg-foreground/70" />
          CalmSource
        </div>

        <h1 id="profile-gate-title" className="mt-6 text-center font-display text-[44px] font-bold leading-[1] tracking-[-0.035em] sm:text-6xl md:text-7xl lg:text-8xl">
          Who's watching?
        </h1>

        <p className="mt-5 max-w-md text-center text-[14px] font-light leading-[1.5] text-muted-foreground sm:text-[15px]">
          {editing
            ? "Tap a profile to rename it and choose a portrait."
            : "Choose a profile to continue your story."}
        </p>

        <div className="mt-14 flex flex-wrap justify-center gap-6 sm:mt-20 sm:gap-10">
          {profiles.map((p) => (
            <ProfileAvatar
              key={p.id}
              profile={p}
              editing={editing}
              isDraft={draftId === p.id}
              draftName={draftName}
              onPick={() => {
                if (editing) {
                  setDraftId(p.id);
                  setDraftName(p.name);
                  setDraftAvatarId(p.avatarId ?? PROFILE_AVATARS[0]?.id ?? "");
                  setDraftPin(p.pin ?? "");
                } else {
                  handlePick(p);
                }
              }}
              onCommit={commitDraft}
              onDraft={setDraftName}
            />
          ))}

          {profiles.length < 10 && (
            <button
              onClick={onAdd}
              className="group flex w-[112px] flex-col items-center gap-4 sm:w-[140px]"
            >
              <div className="poster-card relative aspect-square w-[112px] sm:w-[140px]">
                <div className="tv-tile grid h-full w-full place-items-center rounded-[24px] border border-dashed border-border bg-card/40 text-muted-foreground backdrop-blur-sm transition-colors group-hover:border-foreground/60 group-hover:text-foreground">
                  <Plus className="h-7 w-7" strokeWidth={1.5} />
                </div>
              </div>
              <span className="text-[13px] font-medium leading-[1.35] text-muted-foreground transition-colors group-hover:text-foreground">
                Add Profile
              </span>
            </button>
          )}
        </div>

        {editing && activeDraftProfile && (
          <section className="mt-12 w-full max-w-4xl rounded-[28px] border border-border/70 bg-card/35 p-5 backdrop-blur-xl sm:mt-16 sm:p-6">
            <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">Portrait</p>
                <h2 className="mt-1 text-xl font-medium tracking-[-0.02em] text-foreground">
                  {draftName.trim() || activeDraftProfile.name}
                </h2>
              </div>
              <button
                onClick={commitDraft}
                className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card/50 px-4 py-2 text-[11px] font-medium uppercase tracking-[0.16em] text-muted-foreground transition hover:border-foreground/40 hover:text-foreground"
              >
                <Check className="h-3.5 w-3.5" /> Save
              </button>
            </div>

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {PROFILE_AVATARS.map((avatar) => {
                const active = draftAvatarId === avatar.id;
                return (
                  <button
                    key={avatar.id}
                    type="button"
                    onClick={() => setDraftAvatarId(avatar.id)}
                    aria-pressed={active}
                    aria-label={`Use ${avatar.name} as profile picture`}
                    className={`group rounded-[20px] border p-2 text-left transition ${
                      active
                        ? "border-foreground/70 bg-white/10"
                        : "border-border/70 bg-background/20 hover:border-foreground/35 hover:bg-white/5"
                    }`}
                  >
                    <div className="relative aspect-square overflow-hidden rounded-[16px] ring-1 ring-white/10">
                      <img
                        src={avatar.url}
                        alt=""
                        loading="lazy"
                        className="h-full w-full object-cover"
                      />
                    </div>
                    <span className="mt-2 block text-[12px] font-medium text-muted-foreground transition group-hover:text-foreground">
                      {avatar.name}
                    </span>
                  </button>
                );
              })}
            </div>

            {onPinChange && (
              <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-white/5 pt-5">
                <Lock className="h-4 w-4 text-muted-foreground" />
                <div className="min-w-0">
                  <div className="text-[12px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">PIN lock</div>
                  <div className="text-[11.5px] text-muted-foreground">4 digits — leave blank to disable.</div>
                </div>
                <input
                  type="password"
                  inputMode="numeric"
                  maxLength={4}
                  value={draftPin}
                  onChange={(e) => setDraftPin(e.target.value.replace(/\D/g, "").slice(0, 4))}
                  placeholder="••••"
                  className="ml-auto w-24 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-center font-mono text-sm tracking-[0.4em] focus:border-white/30 focus:outline-none"
                />
              </div>
            )}
          </section>
        )}

        <div className="mt-14 flex justify-center sm:mt-20">
          <button
            onClick={() => {
              setEditing((v) => !v);
              setDraftId(null);
              setDraftName("");
            }}
            className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card/40 px-5 py-2.5 text-[11px] font-medium uppercase tracking-[0.18em] text-muted-foreground backdrop-blur-md transition hover:border-foreground/40 hover:bg-card/70 hover:text-foreground"
          >
            {editing ? (
              <>
                <Check className="h-3.5 w-3.5" /> Done
              </>
            ) : (
              <>
                <Pencil className="h-3.5 w-3.5" /> Manage Profiles
              </>
            )}
          </button>
        </div>
      </div>

      {pinPrompt && (
        <PinDialog
          title={`Welcome, ${pinPrompt.name}`}
          expected={pinPrompt.pin ?? ""}
          onClose={() => setPinPrompt(null)}
          onSuccess={() => {
            const p = pinPrompt;
            setPinPrompt(null);
            onSelect(p);
          }}
        />
      )}
    </div>
  );
}

function ProfileAvatar({
  profile,
  editing,
  isDraft,
  draftName,
  onPick,
  onCommit,
  onDraft,
}: {
  profile: Profile;
  editing: boolean;
  isDraft: boolean;
  draftName: string;
  onPick: () => void;
  onCommit: () => void;
  onDraft: (s: string) => void;
}) {
  const initial = profile.name.trim().charAt(0).toUpperCase() || "·";
  const avatarUrl = getProfileAvatarUrl(profile);
  const label = editing ? `Edit ${profile.name}` : `Switch to ${profile.name}`;

  return (
    <div className="group flex w-[112px] flex-col items-center gap-4 sm:w-[140px]">

      <button
        onClick={onPick}
        className="poster-card relative aspect-square w-[112px] sm:w-[140px]"
        aria-label={label}
      >

        <div className="tv-tile relative h-full w-full overflow-hidden rounded-[24px] ring-1 ring-border">
          {avatarUrl ? (
            <img src={avatarUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
          ) : (
            <>
              <div
                className={`absolute inset-0 bg-gradient-to-br ${profile.color}`}
                aria-hidden
              />
              <div
                className="absolute inset-0"
                aria-hidden
                style={{
                  background:
                    "radial-gradient(120% 80% at 30% 0%, rgba(255,255,255,0.28), transparent 55%)",
                }}
              />
              <div className="absolute inset-0 grid place-items-center">
                <span className="font-display text-[46px] font-bold tracking-[-0.04em] text-white drop-shadow-[0_6px_22px_rgba(0,0,0,0.5)] sm:text-[58px]">
                  {initial}
                </span>
              </div>
            </>
          )}
          {profile.kids && (
            <span className="absolute left-2.5 top-2.5 rounded-full bg-background/70 px-2 py-0.5 text-[9px] font-semibold uppercase tracking-[0.16em] text-foreground backdrop-blur-md">
              Kids
            </span>
          )}
          {editing && (
            <div className="absolute inset-0 grid place-items-center bg-background/55 backdrop-blur-[2px]">
              <div className="grid h-10 w-10 place-items-center rounded-full bg-foreground text-background">
                <Pencil className="h-4 w-4" strokeWidth={2} />
              </div>
            </div>
          )}
        </div>
      </button>

      {isDraft ? (
        <input
          autoFocus
          value={draftName}
          onChange={(e) => onDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") onCommit();
          }}
          className="w-full rounded-md border border-border bg-card/70 px-2 py-1 text-center text-[13px] font-medium leading-[1.35] text-foreground focus:border-foreground/40 focus:outline-none"
        />

      ) : (
        <span className="text-[13px] font-medium leading-[1.35] tracking-[-0.005em] text-muted-foreground transition-colors group-hover:text-foreground">
          {profile.name}
        </span>
      )}
    </div>
  );
}

export default memo(ProfileGate);
