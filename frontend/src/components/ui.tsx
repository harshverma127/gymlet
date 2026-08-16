import { useEffect, type ButtonHTMLAttributes, type ReactNode } from "react";
import { CheckIcon, XIcon } from "./Icons";

/* ---------------------------------- Card --------------------------------- */

export function Card({ className = "", children }: { className?: string; children: ReactNode }) {
  return <div className={`card ${className}`}>{children}</div>;
}

/* --------------------------------- Button -------------------------------- */

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: "md" | "lg" | "sm";
}

export function Button({ variant = "primary", size = "md", className = "", children, ...rest }: ButtonProps) {
  return (
    <button className={`btn btn-${variant} btn-${size} ${className}`} {...rest}>
      {children}
    </button>
  );
}

/* ----------------------------- Pixel checkbox ----------------------------- */

export function PixelCheckbox({
  checked,
  onToggle,
  disabled,
  label,
}: {
  checked: boolean;
  onToggle: () => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <button
      type="button"
      className={`pixel-check ${checked ? "checked" : ""} ${disabled ? "disabled" : ""}`}
      onClick={onToggle}
      disabled={disabled}
      aria-label={label ?? "Mark set complete"}
      aria-pressed={checked}
    >
      {checked && (
        <span className="pixel-check-mark">
          <CheckIcon size={15} />
        </span>
      )}
    </button>
  );
}

/* -------------------------- Segmented progress ---------------------------- */

export function SegmentedProgress({ value, max }: { value: number; max: number }) {
  const total = Math.min(max, 60);
  const filled = Math.min(value, total);
  return (
    <div className="seg-progress" role="progressbar" aria-valuenow={filled} aria-valuemax={total}>
      {Array.from({ length: total }, (_, i) => (
        <span key={i} className={`seg ${i < filled ? "on" : ""}`} />
      ))}
    </div>
  );
}

/* --------------------------------- Spinner -------------------------------- */

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="spinner-wrap">
      <div className="spinner">
        <span />
        <span />
        <span />
        <span />
      </div>
      <span className="spinner-label">{label}</span>
    </div>
  );
}

/* --------------------------------- Skeleton -------------------------------- */

export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="skeleton" aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className="skeleton-line" style={{ width: `${96 - i * 12}%` }} />
      ))}
    </div>
  );
}

/* ---------------------------------- Modal --------------------------------- */

export function Modal({
  open,
  onClose,
  title,
  children,
  footer,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3 className="modal-title">{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <XIcon size={16} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

/* -------------------------------- Empty state ------------------------------ */

export function EmptyState({ icon, title, text }: { icon?: ReactNode; title: string; text?: string }) {
  return (
    <div className="empty-state">
      {icon && <div className="empty-icon">{icon}</div>}
      <p className="empty-title">{title}</p>
      {text && <p className="empty-text">{text}</p>}
    </div>
  );
}

/* ---------------------------------- Pill ----------------------------------- */

export function Pill({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "sage" | "peach" | "blue" }) {
  return <span className={`pill pill-${tone}`}>{children}</span>;
}
