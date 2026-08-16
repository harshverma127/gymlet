import type { Unit } from "../types";

export const KG_TO_LB = 2.2046226218;

/** Convert a kg value to the display unit. */
export function toDisplay(kg: number | null | undefined, unit: Unit): string {
  if (kg == null) return "";
  const v = unit === "LB" ? kg * KG_TO_LB : kg;
  return trimNum(v);
}

/** Parse a display-unit input back into kg. */
export function toKg(display: number, unit: Unit): number {
  const kg = unit === "LB" ? display / KG_TO_LB : display;
  return Math.round(kg * 100) / 100;
}

/** Weight increment for suggestions, in the display unit. */
export function incrementFor(unit: Unit): number {
  return unit === "LB" ? 5 : 2.5;
}

export function trimNum(v: number): string {
  const r = Math.round(v * 100) / 100;
  if (Math.abs(r - Math.round(r)) < 0.001) return String(Math.round(r));
  const s = r.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
  return s;
}

export function formatVolume(kg: number | null | undefined, unit: Unit): string {
  if (kg == null) return "—";
  const v = unit === "LB" ? kg * KG_TO_LB : kg;
  return `${Math.round(v).toLocaleString()} ${unit.toLowerCase()}`;
}

export function formatWeight(kg: number | null | undefined, unit: Unit): string {
  if (kg == null) return "—";
  return `${toDisplay(kg, unit)} ${unit.toLowerCase()}`;
}
