import type { MuscleVolume, Unit } from "../types";
import { formatVolume, toDisplay } from "../lib/units";
import { formatDate } from "../lib/format";

/* --------------------------------- LineChart -------------------------------- */

export function LineChart({
  points,
  labels,
  unit,
  height = 150,
}: {
  points: number[];
  labels: string[];
  unit: Unit;
  height?: number;
}) {
  if (points.length < 2) {
    return (
      <div className="line-chart-empty">
        {points.length === 1 ? `${toDisplay(points[0], unit)} ${unit.toLowerCase()}` : "Not enough data yet"}
      </div>
    );
  }

  const W = 560;
  const H = height;
  const padL = 34;
  const padR = 10;
  const padT = 14;
  const padB = 26;

  const min = Math.min(...points);
  const max = Math.max(...points);
  const span = max - min || 1;
  const x = (i: number) => padL + (i * (W - padL - padR)) / (points.length - 1);
  const y = (v: number) => padT + (1 - (v - min) / span) * (H - padT - padB);

  const path = points.map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  const gridVals = [min + span * 0.25, min + span * 0.75];

  return (
    <svg className="line-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Progress line chart">
      {gridVals.map((v, i) => (
        <g key={i}>
          <line x1={padL} x2={W - padR} y1={y(v)} y2={y(v)} className="chart-grid" />
          <text x={2} y={y(v) + 4} className="chart-y">
            {toDisplay(v, unit)}
          </text>
        </g>
      ))}
      <line x1={padL} x2={W - padR} y1={y(min)} y2={y(min)} className="chart-grid chart-grid-base" />
      <path d={path} className="chart-line" />
      {points.map((v, i) => (
        <g key={i}>
          <circle cx={x(i)} cy={y(v)} r={i === points.length - 1 ? 5 : 3.5} className={`chart-dot ${i === points.length - 1 ? "chart-dot-last" : ""}`} />
          <title>{`${labels[i]}: ${toDisplay(v, unit)} ${unit.toLowerCase()}`}</title>
        </g>
      ))}
      <text x={padL} y={H - 8} className="chart-x">
        {formatDate(labels[0])}
      </text>
      <text x={W - padR} y={H - 8} className="chart-x chart-x-right">
        {formatDate(labels[labels.length - 1])}
      </text>
    </svg>
  );
}

/* -------------------------------- MuscleBars -------------------------------- */

export function MuscleBars({ data, unit }: { data: MuscleVolume[]; unit: Unit }) {
  const max = Math.max(...data.map((d) => d.weeklyVolume), 1);
  return (
    <div className="muscle-bars">
      {data.map((d) => {
        const pct = Math.max(8, Math.round((d.weeklyVolume / max) * 100));
        return (
          <div key={d.muscleGroup} className="muscle-row">
            <div className="muscle-label">{titleCase(d.muscleGroup)}</div>
            <div className="muscle-bar-track">
              <div className="muscle-bar-fill" style={{ width: `${pct}%` }} />
            </div>
            <div className="muscle-meta">
              <span className="muscle-vol">{formatVolume(d.weeklyVolume, unit)}</span>
              <span className="muscle-sets">
                {d.weeklySets} set{d.weeklySets === 1 ? "" : "s"} · {d.frequency}×/wk
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function titleCase(s: string): string {
  return s.charAt(0) + s.slice(1).toLowerCase();
}
