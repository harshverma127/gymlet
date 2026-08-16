import { useCallback, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api/client";
import type { BodyWeightSummary, ExerciseStrength, MuscleVolume, PrSummary } from "../types";
import { useProfile } from "../state";
import { formatDate, formatDateLong, todayISO } from "../lib/format";
import { formatVolume, toDisplay, toKg } from "../lib/units";
import { useToast } from "../components/Toast";
import { Button, Card, EmptyState, Skeleton } from "../components/ui";
import { LineChart, MuscleBars, titleCase } from "../components/Charts";
import { MonthCalendar } from "../components/MonthCalendar";
import { SparkleIcon, TrashIcon, DumbbellIcon } from "../components/Icons";

export function ProgressPage() {
  const { unit } = useProfile();
  const toast = useToast();
  const [strength, setStrength] = useState<ExerciseStrength[] | null>(null);
  const [muscles, setMuscles] = useState<MuscleVolume[] | null>(null);
  const [prs, setPrs] = useState<PrSummary | null>(null);
  const [bw, setBw] = useState<BodyWeightSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [s, m, p, b] = await Promise.all([
        api.strength(),
        api.muscles(),
        api.prs(),
        api.bodyWeightSummary(),
      ]);
      setStrength(s);
      setMuscles(m);
      setPrs(p);
      setBw(b);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const current = useMemo(() => {
    if (!strength || strength.length === 0) return null;
    const ex = strength.find((s) => s.exerciseId === selected) ?? strength[0];
    return ex;
  }, [strength, selected]);

  if (error) {
    return (
      <div className="page">
        <Card className="card-error">
          <p>{error}</p>
          <Button onClick={() => void load()}>Try again</Button>
        </Card>
      </div>
    );
  }

  const loading = !strength || !muscles || !prs || !bw;

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Analytics</p>
          <h1>Progress</h1>
        </div>
      </header>

      {loading ? (
        <Skeleton lines={8} />
      ) : (
        <>
          <Card className="section-card">
            <div className="section-title-row">
              <h2 className="section-title">Consistency</h2>
            </div>
            <MonthCalendar />
          </Card>

          <StrengthSection strength={strength} current={current} setSelected={setSelected} unit={unit} />

          <Card className="section-card">
            <div className="section-title-row">
              <h2 className="section-title">Weekly muscle volume</h2>
              <span className="section-sub">Last 7 days</span>
            </div>
            {muscles.length === 0 ? (
              <EmptyState title="No volume yet" text="Log some sets and this fills up." />
            ) : (
              <MuscleBars data={muscles} unit={unit} />
            )}
          </Card>

          <PrSection prs={prs} unit={unit} />

          <BodyWeightSection bw={bw} unit={unit} onChanged={load} toast={toast} />
        </>
      )}
    </div>
  );
}

/* ------------------------------ strength ------------------------------ */

function StrengthSection({
  strength,
  current,
  setSelected,
  unit,
}: {
  strength: ExerciseStrength[];
  current: ExerciseStrength | null;
  setSelected: (n: number | null) => void;
  unit: "KG" | "LB";
}) {
  if (!current) {
    return (
      <Card className="section-card">
        <div className="section-title-row">
          <h2 className="section-title">Strength progress</h2>
        </div>
        <EmptyState icon={<DumbbellIcon size={26} />} title="No lifts logged yet" text="Complete a workout and your per-exercise progress appears here." />
      </Card>
    );
  }

  const weights = current.sessions.map((s) => s.topWeight);
  const labels = current.sessions.map((s) => s.date);
  const arrowLine = weights.map((w) => `${toDisplay(w, unit)} ${unit.toLowerCase()}`).join(" → ");

  return (
    <Card className="section-card">
      <div className="section-title-row">
        <h2 className="section-title">Strength progress</h2>
        <span className="section-sub">Last {current.sessions.length} workouts</span>
      </div>

      <div className="ex-select-row">
        <select
          className="ex-select"
          value={current.exerciseId}
          onChange={(e) => setSelected(Number(e.target.value))}
          aria-label="Choose exercise"
        >
          {strength.map((s) => (
            <option key={s.exerciseId} value={s.exerciseId}>
              {s.name}
            </option>
          ))}
        </select>
        <span className="pill pill-blue">{titleCase(current.muscleGroup)}</span>
      </div>

      <LineChart points={weights} labels={labels} unit={unit} />

      {current.sessions.length > 1 && <p className="weight-history">{arrowLine}</p>}

      <div className="stat-grid">
        <div className="stat-box">
          <span className="stat-label">Best set</span>
          <span className="stat-value">
            {current.bestSet
              ? `${toDisplay(current.bestSet.weight, unit)} × ${current.bestSet.reps}`
              : "—"}
          </span>
          {current.bestSet && <span className="stat-sub">{formatDate(current.bestSet.date)}</span>}
        </div>
        <div className="stat-box">
          <span className="stat-label">Est. 1RM</span>
          <span className="stat-value">{toDisplay(current.best1Rm, unit)} {unit.toLowerCase()}</span>
        </div>
        <div className="stat-box">
          <span className="stat-label">Total volume</span>
          <span className="stat-value">{formatVolume(current.totalVolume, unit)}</span>
        </div>
      </div>
    </Card>
  );
}

/* --------------------------------- PRs --------------------------------- */

function PrSection({ prs, unit }: { prs: PrSummary; unit: "KG" | "LB" }) {
  const [filter, setFilter] = useState("");
  const exercises = prs.exercises.filter((e) =>
    e.name.toLowerCase().includes(filter.trim().toLowerCase()),
  );

  return (
    <Card className="section-card">
      <div className="section-title-row">
        <h2 className="section-title">Personal records</h2>
        <SparkleIcon size={16} />
      </div>

      <div className="pr-highlights">
        <div className="pr-highlight">
          <span className="pr-highlight-label">Best session volume</span>
          <span className="pr-highlight-value">{formatVolume(prs.bestSessionVolume, unit)}</span>
          {prs.bestSessionVolumeDate && (
            <span className="stat-sub">{formatDateLong(prs.bestSessionVolumeDate)}</span>
          )}
        </div>
        <div className="pr-highlight">
          <span className="pr-highlight-label">Current streak</span>
          <span className="pr-highlight-value">
            {prs.currentStreak} day{prs.currentStreak === 1 ? "" : "s"}
          </span>
        </div>
        <div className="pr-highlight">
          <span className="pr-highlight-label">Best streak</span>
          <span className="pr-highlight-value">
            {prs.bestStreak} day{prs.bestStreak === 1 ? "" : "s"}
          </span>
        </div>
      </div>

      <p className="pr-total">{prs.totalWorkouts} workouts logged</p>

      <input
        className="filter-input"
        type="search"
        placeholder="Filter exercises…"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        aria-label="Filter personal records"
      />

      {exercises.length === 0 ? (
        <EmptyState title="No records yet" text="Lift something and your bests will live here." />
      ) : (
        <div className="pr-table">
          <div className="pr-row pr-row-head">
            <span>Exercise</span>
            <span>Heaviest</span>
            <span>Best reps</span>
            <span>Est. 1RM</span>
          </div>
          {exercises.map((e) => (
            <div key={e.name} className="pr-row">
              <span className="pr-ex-name">{e.name}</span>
              <span>{toDisplay(e.highestWeight, unit)} {unit.toLowerCase()}</span>
              <span>{e.bestReps}</span>
              <span>{toDisplay(e.best1Rm, unit)} {unit.toLowerCase()}</span>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

/* ------------------------------ bodyweight ------------------------------ */

function BodyWeightSection({
  bw,
  unit,
  onChanged,
  toast,
}: {
  bw: BodyWeightSummary;
  unit: "KG" | "LB";
  onChanged: () => void;
  toast: ReturnType<typeof useToast>;
}) {
  const [date, setDate] = useState(todayISO());
  const [weight, setWeight] = useState("");
  const [saving, setSaving] = useState(false);

  const add = async () => {
    const w = parseFloat(weight);
    if (!w || w <= 0) {
      toast("Enter a valid weight", "error");
      return;
    }
    setSaving(true);
    try {
      await api.addBodyWeight(date, toKg(w, unit));
      toast("Weight logged");
      setWeight("");
      onChanged();
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't save", "error");
    } finally {
      setSaving(false);
    }
  };

  const trend = bw.history.map((h) => h.weightKg);
  const labels = bw.history.map((h) => h.date);

  return (
    <Card className="section-card">
      <div className="section-title-row">
        <h2 className="section-title">Bodyweight</h2>
        <span className="section-sub">Tracked alongside your lifts</span>
      </div>

      {bw.history.length >= 2 ? (
        <LineChart points={trend} labels={labels} unit={unit} height={130} />
      ) : (
        <EmptyState title="No bodyweight yet" text="Add your first entry below." />
      )}

      <div className="bw-stats">
        <div className="stat-box">
          <span className="stat-label">Current</span>
          <span className="stat-value">{bw.current != null ? `${toDisplay(bw.current, unit)} ${unit.toLowerCase()}` : "—"}</span>
        </div>
        <div className="stat-box">
          <span className="stat-label">Weekly avg</span>
          <span className="stat-value">{bw.weeklyAverage != null ? toDisplay(bw.weeklyAverage, unit) : "—"}</span>
        </div>
        <div className="stat-box">
          <span className="stat-label">This week</span>
          <span className={`stat-value ${bw.changeThisWeek != null && bw.changeThisWeek > 0 ? "up" : bw.changeThisWeek != null && bw.changeThisWeek < 0 ? "down" : ""}`}>
            {bw.changeThisWeek != null ? `${bw.changeThisWeek > 0 ? "+" : ""}${toDisplay(bw.changeThisWeek, unit)}` : "—"}
          </span>
        </div>
      </div>

      <div className="bw-form">
        <label className="field">
          <span className="field-label">Date</span>
          <input type="date" value={date} max={todayISO()} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="field">
          <span className="field-label">Weight ({unit.toLowerCase()})</span>
          <input
            type="number"
            inputMode="decimal"
            min={0}
            step={0.1}
            value={weight}
            placeholder="0.0"
            onChange={(e) => setWeight(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && void add()}
          />
        </label>
        <Button onClick={() => void add()} disabled={saving}>
          {saving ? "Saving…" : "Add"}
        </Button>
      </div>

      {bw.history.length > 0 && (
        <div className="bw-list">
          {[...bw.history].reverse().slice(0, 6).map((h) => (
            <div key={h.id} className="bw-entry">
              <span className="bw-entry-date">{formatDate(h.date)}</span>
              <span className="bw-entry-weight">
                {toDisplay(h.weightKg, unit)} {unit.toLowerCase()}
              </span>
              <button
                className="icon-btn icon-btn-sm"
                onClick={async () => {
                  try {
                    await api.deleteBodyWeight(h.id);
                    onChanged();
                  } catch (e) {
                    toast(e instanceof ApiError ? e.message : "Couldn't delete", "error");
                  }
                }}
                aria-label={`Delete entry ${h.date}`}
              >
                <TrashIcon size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
