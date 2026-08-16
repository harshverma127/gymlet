import type { SVGProps } from "react";

/**
 * Small pixel-flavoured icons: square line caps, blocky shapes, sharp corners.
 * Kept as raw SVGs so they stay crisp at any size.
 */

interface IconProps extends SVGProps<SVGSVGElement> {
  size?: number;
}

function base(size: number | undefined, props: IconProps) {
  const s = size ?? 22;
  return {
    width: s,
    height: s,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    strokeLinecap: "square" as const,
    strokeLinejoin: "miter" as const,
    ...props,
  };
}

export function DumbbellIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M6.5 9.5v5M17.5 9.5v5" />
      <path d="M3 10v4M2.2 10.5v3" />
      <path d="M21 10v4M21.8 10.5v3" />
      <path d="M6.5 12h11" />
    </svg>
  );
}

export function CheckIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)} strokeWidth={3}>
      <path d="M5 12.5l4.5 4.5L19 7" />
    </svg>
  );
}

export function FlagIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M6 21V4" />
      <path d="M6 5h11l-2.5 3.5L17 12H6" />
    </svg>
  );
}

export function ChartIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M4 20h16" />
      <path d="M6 17v-5M11 17V8M16 17v-8" />
    </svg>
  );
}

export function CalendarIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <rect x="3.5" y="5" width="17" height="15.5" rx="1" />
      <path d="M3.5 9.5h17M8 3v3.5M16 3v3.5" />
      <path d="M8 13.5h3M8 16.5h5" />
    </svg>
  );
}

export function UserIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c1.5-3.5 4-5 7-5s5.5 1.5 7 5" />
    </svg>
  );
}

export function TimerIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <circle cx="12" cy="13" r="7.5" />
      <path d="M12 9.5V13l2.5 2.5" />
      <path d="M9.5 3h5" />
    </svg>
  );
}

export function NoteIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <rect x="4.5" y="3.5" width="15" height="17" rx="1" />
      <path d="M8.5 8h7M8.5 12h7M8.5 16h4" />
    </svg>
  );
}

export function TrashIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M4.5 6.5h15" />
      <path d="M9 3.5h6" />
      <path d="M6.5 6.5l1 14h9l1-14" />
      <path d="M10 10.5v6.5M14 10.5v6.5" />
    </svg>
  );
}

export function PlusIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function MinusIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M5 12h14" />
    </svg>
  );
}

export function ArrowLeftIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M19 12H5M10.5 6L5 12l5.5 6" />
    </svg>
  );
}

export function ArrowRightIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M5 12h14M13.5 6L19 12l-5.5 6" />
    </svg>
  );
}

export function StarIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)} fill="currentColor" stroke="none">
      <path d="M12 2.5l2.6 6.2 6.7.6-5 4.3 1.5 6.6L12 16.6l-5.8 3.6 1.5-6.6-5-4.3 6.7-.6z" />
    </svg>
  );
}

export function XIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

export function PlayIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)} fill="currentColor" stroke="none">
      <path d="M7 4.5L20 12 7 19.5z" />
    </svg>
  );
}

export function PauseIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)} fill="currentColor" stroke="none">
      <rect x="6.5" y="4.5" width="4" height="15" />
      <rect x="13.5" y="4.5" width="4" height="15" />
    </svg>
  );
}

export function ResetIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M4.5 12a7.5 7.5 0 1 1 2.2 5.3" />
      <path d="M4.5 19v-4.5H9" />
    </svg>
  );
}

export function DownloadIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M12 3.5v11M7.5 10l4.5 4.5 4.5-4.5" />
      <path d="M4.5 20h15" />
    </svg>
  );
}

export function ClockIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 7v5.5l3.5 2" />
    </svg>
  );
}

export function SparkleIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)} fill="currentColor" stroke="none">
      <path d="M12 2l1.8 5.2L19 9l-5.2 1.8L12 16l-1.8-5.2L5 9l5.2-1.8z" />
      <path d="M19 14l.9 2.6L22.5 17.5l-2.6.9L19 21l-.9-2.6-2.6-.9 2.6-.9z" opacity=".6" />
    </svg>
  );
}

export function LogoutIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M9 4H5.5A1.5 1.5 0 0 0 4 5.5v13A1.5 1.5 0 0 0 5.5 20H9" />
      <path d="M15 8l4 4-4 4" />
      <path d="M19 12H9" />
    </svg>
  );
}

export function ChevronDownIcon(props: IconProps) {
  const { size, ...rest } = props;
  return (
    <svg {...base(size, rest)}>
      <path d="M6 9.5l6 6 6-6" />
    </svg>
  );
}
