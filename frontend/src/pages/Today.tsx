import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api/client";
import type { Exercise, FinishSummary, Session, SetLog, Today, TodayExercise, WorkoutDaySummary } from "../types";
import { useProfile } from "../state";
import { toDisplay, toKg, formatVolume } from "../lib/units";
import { formatDateLong, plural } from "../lib/format";
import { useToast } from "../components/Toast";
import { Button, Card, PixelCheckbox, SegmentedProgress, Skeleton } from "../components/ui";
import {
  NoteIcon,
  PauseIcon,
  PlayIcon,
  ResetIcon,
  SparkleIcon,
  StarIcon,
  TimerIcon,
  XIcon,
} from "../components/Icons";
import { titleCase } from "../components/Charts";

/* ------------------------------ main page ------------------------------ */

export function TodayPage() {
  const { unit } = useProfile();
  const toast = useToast();
  const [today, setToday] = useState<Today | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [summary, setSummary] = useState<FinishSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const [showFinishConfirm, setShowFinishConfirm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const t = await api.today();
      setToday(t);
      setError(null);
      if (t.activeSessionId != null) {
        setSession(await api.session(t.activeSessionId));
      } else {
        setSession(null);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const start = async () => {
    setStarting(true);
    try {
      const s = await api.startSession();
      setSession(s);
      toast("Workout started — have fun!");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't start the workout", "error");
    } finally {
      setStarting(false);
    }
  };

  const startForDay = async (dayId: number) => {
    setStarting(true);
    try {
      const s = await api.startSessionForDay(dayId);
      setSession(s);
      toast("Workout started — have fun!");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't start the workout", "error");
    } finally {
      setStarting(false);
    }
  };

  const startCustom = async () => {
    setStarting(true);
    try {
      const s = await api.startCustomSession();
      setSession(s);
      toast("Custom workout started — add exercises!");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't start custom workout", "error");
    } finally {
      setStarting(false);
    }
  };

  if (loading) {
    return (
      <div className="page">
        <Skeleton lines={4} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <Card className="card-error">
          <p className="error-emoji">😿</p>
          <p>{error}</p>
          <Button onClick={() => void load()}>Try again</Button>
        </Card>
      </div>
    );
  }

  if (!today) return null;

  if (today.isRestDay && !session) {
    return <RestDayView today={today} unit={unit} starting={starting} onStartCustom={startCustom} />;
  }

  if (session) {
    return (
      <WorkoutView
        today={today}
        session={session}
        setSession={setSession}
        unit={unit}
        summary={summary}
        setSummary={setSummary}
        finishing={finishing}
        setFinishing={setFinishing}
        showFinishConfirm={showFinishConfirm}
        setShowFinishConfirm={setShowFinishConfirm}
        toast={toast}
        onStartAnother={() => { setSession(null); void load(); }}
      />
    );
  }

  return <ReadyView today={today} unit={unit} starting={starting} onStart={start} onStartForDay={startForDay} onStartCustom={startCustom} />;
}

/* ------------------------------ rest day ------------------------------ */

function RestDayView({ today, unit, starting, onStartCustom }: { today: Today; unit: "KG" | "LB"; starting: boolean; onStartCustom: () => void }) {
  return (
    <div className="page">
      <Card className="rest-card">
        <div className="rest-art" aria-hidden="true">
          <span className="rest-block b1" />
          <span className="rest-block b2" />
          <span className="rest-block b3" />
          <span className="rest-leaf" />
        </div>
        <h1 className="rest-title">Rest day 🌿</h1>
        <p className="rest-sub">Muscles grow while you rest. Enjoy the break!</p>
      </Card>

      <Button size="lg" className="start-btn custom-start-btn" onClick={onStartCustom} disabled={starting}>
        <SparkleIcon size={18} />
        {starting ? "Setting up…" : "Start Custom Workout"}
      </Button>

      <div className="section-head">
        <h2>Up next</h2>
        <span className="section-sub">
          Day {today.nextDayNumber} — {today.nextWorkoutDayName}
        </span>
      </div>
      {today.exercises.map((ex) => (
        <ExerciseCard key={ex.exerciseId} ex={ex} mode="readonly" unit={unit} />
      ))}
    </div>
  );
}

/* ---------------------------- ready (pre-start) ---------------------------- */

function ReadyView({
  today,
  unit,
  starting,
  onStart,
  onStartForDay,
  onStartCustom,
}: {
  today: Today;
  unit: "KG" | "LB";
  starting: boolean;
  onStart: () => void;
  onStartForDay: (dayId: number) => void;
  onStartCustom: () => void;
}) {
  const [selectedDay, setSelectedDay] = useState<number>(today.workoutDayId);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Day {today.dayNumber} · {weekdayToday()}</p>
          <h1>{today.workoutDayName}</h1>
        </div>
      </header>

      {/* Workout selector */}
      {today.availableDays && today.availableDays.length > 1 && (
        <Card className="day-selector-card">
          <label className="field">
            <span className="field-label">Choose workout</span>
            <select
              className="day-selector"
              value={selectedDay}
              onChange={(e) => setSelectedDay(Number(e.target.value))}
            >
              {today.availableDays.map((d: WorkoutDaySummary) => (
                <option key={d.id} value={d.id}>
                  {d.name} (Day {d.dayNumber})
                </option>
              ))}
            </select>
          </label>
        </Card>
      )}

      <Button size="lg" className="start-btn" onClick={() => {
        if (selectedDay === today.workoutDayId) {
          onStart();
        } else {
          onStartForDay(selectedDay);
        }
      }} disabled={starting}>
        <SparkleIcon size={18} />
        {starting ? "Setting up…" : "Start Workout"}
      </Button>

      <Button size="lg" variant="secondary" className="start-btn custom-start-btn" onClick={onStartCustom} disabled={starting}>
        <SparkleIcon size={18} />
        {starting ? "Setting up…" : "Start Custom Workout"}
      </Button>

      <p className="page-hint">Tap start and the app pre-fills last week's weights — just log and go.</p>

      {today.exercises.map((ex) => (
        <ExerciseCard key={ex.exerciseId} ex={ex} mode="readonly" unit={unit} />
      ))}
    </div>
  );
}

function weekdayToday(): string {
  return new Date().toLocaleDateString("en-US", { weekday: "long" });
}

/* ----------------------------- active workout ----------------------------- */

interface WorkoutViewProps {
  today: Today;
  session: Session;
  setSession: (s: Session) => void;
  unit: "KG" | "LB";
  summary: FinishSummary | null;
  setSummary: (s: FinishSummary | null) => void;
  finishing: boolean;
  setFinishing: (b: boolean) => void;
  showFinishConfirm: boolean;
  setShowFinishConfirm: (b: boolean) => void;
  toast: ReturnType<typeof useToast>;
  onStartAnother: () => void;
}

function WorkoutView(props: WorkoutViewProps) {
  const { today, session, unit, summary, finishing, showFinishConfirm } = props;
  const navigate = useNavigate();
  const [showAddExercise, setShowAddExercise] = useState(false);

  if (session.completed) {
    return <CompletedView session={session} summary={summary} unit={unit} onDone={() => navigate("/history")} onStartAnother={props.onStartAnother} />;
  }

  const pct = session.totalSets === 0 ? 0 : Math.round((session.completedSets / session.totalSets) * 100);

  // Merge planned exercises with any exercises added to the session
  const allExercises = useMemo(() => {
    const planned = new Map(today.exercises.map((e) => [e.exerciseId, e]));
    const extraIds = new Set<number>();
    for (const set of session.sets) {
      if (!planned.has(set.exerciseId)) {
        extraIds.add(set.exerciseId);
      }
    }
    if (extraIds.size === 0) return today.exercises;
    const extras: TodayExercise[] = [];
    for (const exId of extraIds) {
      const firstSet = session.sets.find((s) => s.exerciseId === exId);
      extras.push({
        exerciseId: exId,
        name: firstSet?.exerciseName ?? "Exercise",
        muscleGroup: "BACK",
        repMin: 8,
        repMax: 12,
        compound: false,
        sets: session.sets.filter((s) => s.exerciseId === exId).length,
        lastSets: [],
        suggestion: null,
        lastNote: null,
      });
    }
    return [...today.exercises, ...extras];
  }, [today.exercises, session.sets]);

  const sessionExerciseIds = useMemo(() => {
    const ids = new Set<number>();
    for (const set of session.sets) {
      ids.add(set.exerciseId);
    }
    return ids;
  }, [session.sets]);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Day {today.dayNumber} · {weekdayToday()}</p>
          <h1>{today.workoutDayName}</h1>
        </div>
      </header>

      <Card className="progress-card">
        <div className="progress-top">
          <span className="progress-count">
            {session.completedSets} / {session.totalSets} sets
          </span>
          <span className="progress-pct">{pct}%</span>
        </div>
        <SegmentedProgress value={session.completedSets} max={session.totalSets} />
      </Card>

      {allExercises.map((ex) => (
        <ExerciseCard key={ex.exerciseId} {...props} ex={ex} mode="active" unit={unit} />
      ))}

      <Button variant="secondary" className="add-exercise-btn" onClick={() => setShowAddExercise(true)}>
        <span>+</span> Add Exercise
      </Button>

      {showAddExercise && (
        <ExerciseSearchModal
          excludeIds={sessionExerciseIds}
          onSelect={async (exercise) => {
            try {
              const updated = await api.addExerciseToSession(session.id, exercise.id, 3);
              props.setSession(updated);
              setShowAddExercise(false);
              props.toast(`Added ${exercise.name} — 3 sets ready`);
            } catch (e) {
              props.toast(e instanceof ApiError ? e.message : "Couldn't add exercise", "error");
            }
          }}
          onClose={() => setShowAddExercise(false)}
        />
      )}

      <Button size="lg" className="finish-btn" variant="secondary" onClick={() => props.setShowFinishConfirm(true)}>
        <span className="finish-flag">🏁</span> Finish Workout
      </Button>
      <p className="page-hint">Unfinished sets are kept — you can log them later from History.</p>

      <FinishConfirm
        open={showFinishConfirm}
        total={session.totalSets}
        remaining={session.totalSets - session.completedSets}
        busy={finishing}
        onCancel={() => props.setShowFinishConfirm(false)}
        onConfirm={async () => {
          props.setShowFinishConfirm(false);
          await flushAll(props);
          props.setFinishing(true);
          try {
            const s = await api.finishSession(session.id);
            props.setSummary(s);
            props.setSession({ ...session, completed: true, durationMinutes: s.durationMinutes });
            props.toast("Workout complete! 🎉");
          } catch (e) {
            props.toast(e instanceof ApiError ? e.message : "Couldn't finish the workout", "error");
          } finally {
            props.setFinishing(false);
          }
        }}
      />
    </div>
  );
}

/** Saves every set row with unsaved edits before finishing. */
async function flushAll(props: WorkoutViewProps) {
  const dirty = props.session.sets.filter((s) => s.weight != null || s.reps != null);
  await Promise.allSettled(
    dirty.map((s) =>
      api.updateSet(props.session.id, s.id, {
        weight: s.weight,
        reps: s.reps,
        rir: s.rir,
        completed: s.completed,
      }),
    ),
  );
}

/* --------------------------- finish confirmation --------------------------- */

function FinishConfirm({
  open,
  total,
  remaining,
  busy,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  total: number;
  remaining: number;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3 className="modal-title">Finish workout?</h3>
        </div>
        <div className="modal-body">
          {remaining > 0 ? (
            <p>
              You still have <strong>{remaining} sets</strong> left. They'll be kept as unfinished — you can review
              them later from History.
            </p>
          ) : (
            <p>All {total} sets done — great job! Lock it in?</p>
          )}
        </div>
        <div className="modal-foot">
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            Keep going
          </Button>
          <Button onClick={onConfirm} disabled={busy}>
            {busy ? "Finishing…" : "Finish"}
          </Button>
        </div>
      </div>
    </div>
  );
}

/* ----------------------------- exercise card ----------------------------- */

interface ExerciseCardProps {
  ex: TodayExercise;
  mode: "readonly" | "active";
  unit: "KG" | "LB";
  session?: Session;
  setSession?: (s: Session) => void;
  toast?: ReturnType<typeof useToast>;
}

function ExerciseCard({ ex, mode, unit, session, setSession, toast }: ExerciseCardProps) {
  const [noteOpen, setNoteOpen] = useState(false);
  const [noteDraft, setNoteDraft] = useState("");
  const [savingNote, setSavingNote] = useState(false);
  const [timerOn, setTimerOn] = useState<number | null>(null);
  const [timerKey, setTimerKey] = useState(0);

  const note = session?.notes.find((n) => n.exerciseId === ex.exerciseId)?.note ?? ex.lastNote;
  const done = session ? session.sets.filter((s) => s.exerciseId === ex.exerciseId && s.completed).length : 0;
  const target = ex.repMin === ex.repMax ? `${ex.repMin}` : `${ex.repMin}–${ex.repMax}`;
  const lastLine = ex.lastSets.length
    ? ex.lastSets.map((s) => `${toDisplay(s.weight, unit)} × ${s.reps}`).join(" · ")
    : null;

  const toggleNote = () => {
    setNoteOpen((v) => !v);
    setNoteDraft(note ?? "");
  };

  return (
    <Card className={`exercise-card ${mode === "active" && done > 0 ? "has-progress" : ""}`}>
      <div className="exercise-head">
        <div className="exercise-title-row">
          <h3 className="exercise-name">{ex.name}</h3>
          <span className="pill pill-sage">
            {titleCase(ex.muscleGroup)} · {plural(ex.sets, "set")} · {target} reps
          </span>
        </div>
        {mode === "active" && done > 0 && (
          <span className="exercise-done">
            {done}/{ex.sets}
          </span>
        )}
      </div>

      {mode === "active" ? (
        <SetRows
          ex={ex}
          session={session!}
          setSession={setSession!}
          unit={unit}
          toast={toast!}
          onComplete={() => {
            setTimerKey((k) => k + 1);
            setTimerOn(ex.compound ? 150 : 90);
          }}
        />
      ) : (
        <>
          {lastLine && <p className="last-time">Last time: <strong>{lastLine}</strong></p>}
          {ex.suggestion && <SuggestionChip suggestion={ex.suggestion} unit={unit} />}
        </>
      )}

      {mode === "active" && lastLine && <p className="last-time">Last time: <strong>{lastLine}</strong></p>}
      {mode === "active" && ex.suggestion && <SuggestionChip suggestion={ex.suggestion} unit={unit} />}

      {mode === "active" ? (
        <div className="exercise-actions">
          <button className="note-toggle" onClick={toggleNote}>
            <NoteIcon size={15} />
            {note ? "Notes" : "Note"}
            {note && <span className="note-dot" />}
          </button>
          {note && !noteOpen && <span className="note-preview">"{note}"</span>}
        </div>
      ) : (
        note && (
          <p className="last-time note-preview-static">
            <NoteIcon size={13} /> Last note: "{note}"
          </p>
        )
      )}

      {noteOpen && mode === "active" && (
        <div className="note-box">
          <textarea
            className="note-input"
            rows={2}
            value={noteDraft}
            onChange={(e) => setNoteDraft(e.target.value)}
            placeholder="e.g. Felt weak today, shoulder felt weird…"
          />
          <div className="note-actions">
            <Button
              size="sm"
              variant="secondary"
              disabled={savingNote}
              onClick={async () => {
                setSavingNote(true);
                try {
                  const s = await api.saveNote(session!.id, ex.exerciseId, noteDraft);
                  setSession!(s);
                  toast!("Note saved");
                } catch (e) {
                  toast!(e instanceof ApiError ? e.message : "Couldn't save note", "error");
                } finally {
                  setSavingNote(false);
                  setNoteOpen(false);
                }
              }}
            >
              Save note
            </Button>
          </div>
        </div>
      )}

      {timerOn != null && (
        <RestTimer
          key={timerKey}
          initial={timerOn}
          onClose={() => setTimerOn(null)}
          onFinished={() => setTimerOn(0)}
        />
      )}
    </Card>
  );
}

function SuggestionChip({ suggestion, unit }: { suggestion: NonNullable<TodayExercise["suggestion"]>; unit: "KG" | "LB" }) {
  const text =
    suggestion.action === "INCREASE"
      ? `Hit the top of the rep range — try ${toDisplay(suggestion.weight, unit)} ${unit.toLowerCase()} next time.`
      : suggestion.message;
  return (
    <p className={`suggestion suggestion-${suggestion.action.toLowerCase()}`}>
      {suggestion.action === "INCREASE" ? <SparkleIcon size={13} /> : <span className="suggestion-dot" />}
      <span>
        <strong>Next session:</strong> {text}
      </span>
    </p>
  );
}

/* -------------------------------- set rows -------------------------------- */

interface SetRowsProps {
  ex: TodayExercise;
  session: Session;
  setSession: (s: Session) => void;
  unit: "KG" | "LB";
  toast: ReturnType<typeof useToast>;
  onComplete: () => void;
}

function SetRows({ ex, session, setSession, unit, toast, onComplete }: SetRowsProps) {
  const [saving, setSaving] = useState<Set<number>>(new Set());
  const [hint, setHint] = useState<Set<number>>(new Set());

  const rows = session.sets.filter((s) => s.exerciseId === ex.exerciseId);

  const patch = (setId: number, changes: Partial<SetLog>) => {
    setSession({
      ...session,
      sets: session.sets.map((s) => (s.id === setId ? { ...s, ...changes } : s)),
    });
  };

  const persist = async (set: SetLog) => {
    setSaving((cur) => new Set(cur).add(set.id));
    try {
      const updated = await api.updateSet(session.id, set.id, {
        weight: set.weight,
        reps: set.reps,
        rir: set.rir,
        completed: set.completed,
      });
      setSession(updated);
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't save set", "error");
    } finally {
      setSaving((cur) => {
        const next = new Set(cur);
        next.delete(set.id);
        return next;
      });
    }
  };

  const toggle = (set: SetLog) => {
    const hasValues = set.weight != null && set.weight > 0 && set.reps != null && set.reps > 0;
    if (!set.completed && !hasValues) {
      setHint((cur) => new Set(cur).add(set.id));
      setTimeout(() => setHint((cur) => {
        const next = new Set(cur);
        next.delete(set.id);
        return next;
      }), 1500);
      return;
    }
    const completed = !set.completed;
    patch(set.id, { completed });
    void persist({ ...set, completed });
    if (completed) onComplete();
  };

  return (
    <div className="set-rows">
      {rows.map((set) => (
        <div key={set.id} className={`set-row ${set.completed ? "is-complete" : ""} ${hint.has(set.id) ? "needs-values" : ""}`}>
          <span className="set-label">Set {set.setNumber}</span>
          <label className="field field-weight">
            <input
              type="number"
              inputMode="decimal"
              min={0}
              step={0.5}
              value={set.weight == null ? "" : toDisplay(set.weight, unit)}
              placeholder="—"
              onChange={(e) => {
                const v = e.target.value;
                patch(set.id, { weight: v === "" ? null : toKg(parseFloat(v), unit) });
              }}
              onBlur={() => void persist(set)}
            />
            <span className="field-unit">{unit.toLowerCase()}</span>
          </label>
          <label className="field field-reps">
            <input
              type="number"
              inputMode="numeric"
              min={0}
              value={set.reps ?? ""}
              placeholder="—"
              onChange={(e) => {
                const v = e.target.value;
                patch(set.id, { reps: v === "" ? null : Math.max(0, Math.round(parseFloat(v))) });
              }}
              onBlur={() => void persist(set)}
            />
            <span className="field-unit">reps</span>
          </label>
          <label className="field field-rir" title="Reps in reserve (optional)">
            <input
              type="number"
              inputMode="numeric"
              min={0}
              max={5}
              value={set.rir ?? ""}
              placeholder="–"
              onChange={(e) => {
                const v = e.target.value;
                patch(set.id, { rir: v === "" ? null : Math.min(5, Math.max(0, Math.round(parseFloat(v)))) });
              }}
              onBlur={() => void persist(set)}
            />
            <span className="field-unit">rir</span>
          </label>
          <PixelCheckbox
            checked={set.completed}
            disabled={saving.has(set.id)}
            onToggle={() => toggle(set)}
            label={`Complete set ${set.setNumber}`}
          />
        </div>
      ))}
      {hint.size > 0 && <p className="set-hint">Add weight and reps to complete a set ✏️</p>}
    </div>
  );
}

/* -------------------------------- rest timer ------------------------------- */

function RestTimer({ initial, onClose, onFinished }: { initial: number; onClose: () => void; onFinished: () => void }) {
  const [remaining, setRemaining] = useState(initial);
  const [running, setRunning] = useState(true);

  useEffect(() => {
    if (!running || remaining <= 0) return;
    const t = setInterval(() => setRemaining((r) => r - 1), 1000);
    return () => clearInterval(t);
  }, [running, remaining]);

  useEffect(() => {
    if (remaining === 0) onFinished();
  }, [remaining, onFinished]);

  const mm = String(Math.floor(remaining / 60)).padStart(2, "0");
  const ss = String(remaining % 60).padStart(2, "0");
  const over = remaining === 0;

  return (
    <div className={`rest-timer ${over ? "over" : ""}`}>
      <span className="rest-timer-icon">{over ? <StarIcon size={15} /> : <TimerIcon size={15} />}</span>
      <span className="rest-timer-time">
        {over ? "Rest over!" : `${mm}:${ss}`}
      </span>
      {!over && (
        <button className="rest-btn" onClick={() => setRunning((r) => !r)} aria-label={running ? "Pause" : "Resume"}>
          {running ? <PauseIcon size={14} /> : <PlayIcon size={14} />}
        </button>
      )}
      <button className="rest-btn" onClick={() => setRemaining(initial)} aria-label="Reset timer">
        <ResetIcon size={14} />
      </button>
      <button className="rest-btn" onClick={onClose} aria-label="Close timer">
        <XIcon size={14} />
      </button>
    </div>
  );
}

/* ---------------------------- exercise search modal ---------------------------- */

function ExerciseSearchModal({
  excludeIds,
  onSelect,
  onClose,
}: {
  excludeIds: Set<number>;
  onSelect: (exercise: Exercise) => void;
  onClose: () => void;
}) {
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      try {
        setExercises(await api.exercises());
      } catch {
        /* ignore */
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return exercises
      .filter((e) => !excludeIds.has(e.id))
      .filter((e) => !q || e.name.toLowerCase().includes(q) || e.muscleGroup.toLowerCase().includes(q));
  }, [exercises, search, excludeIds]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal exercise-search-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3 className="modal-title">Add Exercise</h3>
        </div>
        <div className="modal-body">
          <input
            className="exercise-search-input"
            type="text"
            placeholder="Search exercises…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            autoFocus
          />
          {loading ? (
            <p className="exercise-search-empty">Loading…</p>
          ) : filtered.length === 0 ? (
            <p className="exercise-search-empty">No exercises found</p>
          ) : (
            <div className="exercise-search-list">
              {filtered.map((ex) => (
                <button
                  key={ex.id}
                  className="exercise-search-item"
                  onClick={() => onSelect(ex)}
                >
                  <span className="exercise-search-name">{ex.name}</span>
                  <span className="exercise-search-group">
                    {titleCase(ex.muscleGroup)}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="modal-foot">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------ completed view ----------------------------- */

function CompletedView({
  session,
  summary,
  unit,
  onDone,
  onStartAnother,
}: {
  session: Session;
  summary: FinishSummary | null;
  unit: "KG" | "LB";
  onDone: () => void;
  onStartAnother: () => void;
}) {
  const [confettiBits] = useState(() => [0, 1, 2, 3, 4]);
  return (
    <div className="page">
      <Card className="complete-card">
        <div className="complete-art" aria-hidden="true">
          {confettiBits.map((i) => (
            <span key={i} className={`confetti c${i}`} />
          ))}
          <span className="complete-star"><StarIcon size={26} /></span>
        </div>
        <h1 className="complete-title">Workout Complete 🎉</h1>
        <p className="complete-sub">
          {session.workoutDayName} · {formatDateLong(session.date)}
        </p>

        <div className="complete-stats">
          <div className="cstat">
            <span className="cstat-label">Duration</span>
            <span className="cstat-value">{session.durationMinutes ?? "—"} min</span>
          </div>
          <div className="cstat">
            <span className="cstat-label">Sets</span>
            <span className="cstat-value">{session.completedSets} / {session.totalSets}</span>
          </div>
          <div className="cstat">
            <span className="cstat-label">Volume</span>
            <span className="cstat-value">{formatVolume(session.volume, unit)}</span>
          </div>
        </div>

        {summary && summary.prs.length > 0 && (
          <div className="pr-list">
            <p className="pr-list-title">
              <SparkleIcon size={14} /> New personal records
            </p>
            {summary.prs.map((pr, i) => (
              <div key={i} className="pr-chip">
                <StarIcon size={13} />
                <span className="pr-text">
                  <strong>{pr.exerciseName ?? "Session"}</strong> — {pr.label}: {pr.value}
                </span>
              </div>
            ))}
          </div>
        )}

        <p className="complete-message">"{summary?.message ?? "Nice session!"}"</p>

        <div className="complete-actions">
          <Button onClick={onDone}>View in History</Button>
          <Button variant="secondary" onClick={onStartAnother}>Start Another Workout</Button>
          <Button variant="ghost" onClick={() => { onDone(); }}>Back to Today</Button>
        </div>
      </Card>
    </div>
  );
}
