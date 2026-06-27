import { ReactNode } from "react";

type Props = {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
};

export function EmptyState({ icon, title, description, action }: Props) {
  return (
    <div className="zero-state" role="status" aria-live="polite">
      {icon && (
        <div className="grid h-14 w-14 place-items-center rounded-2xl bg-[color-mix(in_oklab,var(--brand)_18%,transparent)] text-brand">
          {icon}
        </div>
      )}
      <h3 className="type-title">{title}</h3>
      {description && (
        <p className="type-body max-w-md text-muted-foreground">{description}</p>
      )}
      {action && <div className="pt-2">{action}</div>}
    </div>
  );
}

export default EmptyState;
