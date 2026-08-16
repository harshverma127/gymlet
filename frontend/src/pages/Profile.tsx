import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import type { Exercise, MuscleGroup, Unit, WorkoutDay, WorkoutExercise } from "../types";
import { useProfile } from "../state";
import { DAY_NAMES } from "../lib/format";
import { useToast } from "../components/Toast";
import { Button, Card, Modal, Skeleton } from "../components/ui";
import {
  ChevronDownIcon,
  DownloadIcon,
  MinusIcon,
  NoteIcon,
  PlusIcon,
  SparkleIcon,
  TrashIcon,
} from "../components/Icons";
import { titleCase } from "../components/Charts";

const MUSCLE_GROUPS: MuscleGroup[] = [
  "CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "FOREARMS", "LEGS", "ABS",
];

export function ProfilePage() {
  const { profile, saveProfile, reloadProfile } = useProfile();
  const toast = useToast();
  const [days, setDays] = useState<WorkoutDay[] | null>(null);
  const [exercises, setExercises] = useState<Exercise[] | null>(null);
  const [expandedDay, setExpandedDay] = useState<number | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);
  const [busy, setBusy] = useState(false);

  const loadStructure = useCallback(async () => {
    try {
      const [d, e] = await Promise.all([api.workoutDays(), api.exercises()]);
      setDays(d);
      setExercises(e);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Couldn't load structure", "error");
    }
  }, [toast]);

  useEffect(() => {
    void loadStructure();
  }, [loadStructure]);

  if (!profile) {
    return (
      <div className="page">
        <Skeleton lines={4} />
      </div>
    );
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Settings</p>
          <h1>Profile</h1>
        </div>
      </header>

      <Card className="section-card">
        <h2 className="section-title">You</h2>
        <div className="form-grid">
          <label className="field">
            <span className="field-label">Name</span>
            <input
              type="text"
              value={profile.name}
              maxLength={40}
              onChange={(e) => void saveProfile({ ...profile, name: e.target.value }).catch((err: unknown) =>
                toast(err instanceof ApiError ? err.message : "Couldn't save", "error"),
              )}
            />
          </label>

          <div className="field">
            <span className="field-label">Units</span>
            <div className="seg-toggle">
              {(["KG", "LB"] as Unit[]).map((u) => (
                <button
                  key={u}
                  className={`seg-btn ${profile.unit === u ? "on" : ""}`}
                  onClick={() => void saveProfile({ ...profile, unit: u })}
                >
                  {u}
                </button>
              ))}
            </div>
          </div>

          <label className="field">
            <span className="field-label">Week starts on</span>
            <select
              value={profile.startDay}
              onChange={(e) => void saveProfile({ ...profile, startDay: Number(e.target.value) })}
            >
              {DAY_NAMES.map((d, i) => (
                <option key={d} value={i + 1}>
                  {d}
                </option>
              ))}
            </select>
          </label>
        </div>
        <p className="field-note">Weights are stored in kg and shown in your unit.</p>
      </Card>

      <Card className="section-card">
        <div className="section-title-row">
          <h2 className="section-title">Workout split</h2>
          <span className="section-sub">Tap a day to edit it</span>
        </div>
        {days === null ? (
          <Skeleton lines={5} />
        ) : (
          <div className="split-list">
            {days.map((day) => (
              <div key={day.id} className="split-day">
                <button
                  className="split-day-head"
                  onClick={() => setExpandedDay(expandedDay === day.id ? null : day.id)}
                >
                  <span className="split-day-num">D{day.dayNumber}</span>
                  <span className="split-day-name">{day.name}</span>
                  <span className="split-day-count">{day.exercises.length} exercises</span>
                  <ChevronDownIcon size={15} className={expandedDay === day.id ? "rotated" : ""} />
                </button>
                {expandedDay === day.id && (
                  <div className="split-day-body">
                    {day.exercises.map((we, idx) => (
                      <WorkoutExerciseRow
                        key={we.id}
                        we={we}
                        first={idx === 0}
                        last={idx === day.exercises.length - 1}
                        onChange={() => void loadStructure()}
                        toast={toast}
                      />
                    ))}
                    {exercises && (
                      <AddExerciseRow
                        dayId={day.id}
                        exercises={exercises}
                        present={new Set(day.exercises.map((e) => e.exerciseId))}
                        onChange={() => void loadStructure()}
                        toast={toast}
                      />
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card className="section-card">
        <div className="section-title-row">
          <h2 className="section-title">Exercise library</h2>
          <NoteIcon size={16} />
        </div>
        {exercises === null ? (
          <Skeleton lines={3} />
        ) : (
          <ExerciseLibrary exercises={exercises} onChange={loadStructure} toast={toast} />
        )}
      </Card>

      <Card className="section-card">
        <h2 className="section-title">Data</h2>
        <div className="data-actions">
          <Button variant="secondary" onClick={() => window.open("/api/export", "_blank")}>
            <DownloadIcon size={15} /> Export data (JSON)
          </Button>
          <Button
            variant="secondary"
            onClick={async () => {
              try {
                await api.removeDemo();
                toast("Sample data removed");
                await reloadProfile();
              } catch (e) {
                toast(e instanceof ApiError ? e.message : "Couldn't remove demo data", "error");
              }
            }}
          >
            <SparkleIcon size={15} /> Remove sample data
          </Button>
          <Button variant="danger" onClick={() => setConfirmReset(true)}>
            <TrashIcon size={15} /> Reset all data
          </Button>
        </div>
        <p className="field-note">
          Export downloads everything as JSON. Reset wipes workout history and bodyweight (the split stays).
        </p>
      </Card>

      <Modal
        open={confirmReset}
        onClose={() => setConfirmReset(false)}
        title="Reset all data?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmReset(false)} disabled={busy}>
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                try {
                  await api.resetData();
                  toast("All workout data reset");
                  setConfirmReset(false);
                  await reloadProfile();
                } catch (e) {
                  toast(e instanceof ApiError ? e.message : "Couldn't reset", "error");
                } finally {
                  setBusy(false);
                }
              }}
            >
              {busy ? "Resetting…" : "Reset everything"}
            </Button>
          </>
        }
      >
        <p>This permanently deletes all workouts, sets, notes, and bodyweight entries. The workout split and profile are kept.</p>
      </Modal>
    </div>
  );
}

/* ---------------------------- structure rows ---------------------------- */

function WorkoutExerciseRow({
  we,
  first,
  last,
  onChange,
  toast,
}: {
  we: WorkoutExercise;
  first: boolean;
  last: boolean;
  onChange: () => void;
  toast: ReturnType<typeof useToast>;
}) {
  const [sets, setSets] = useState(we.sets);
  const [saving, setSaving] = useState(false);

  const apply = async (updates: { sets?: number; setOrder?: number }) => {
    setSaving(true);
    try {
      await api.updateExerciseInDay(we.id, updates.sets ?? sets, updates.setOrder);
      if (updates.sets != null) setSets(updates.sets);
      onChange();
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't update", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="split-ex-row">
      <span className="split-ex-order">{we.setOrder + 1}.</span>
      <span className="split-ex-name">{we.name}</span>
      <span className="split-ex-reps">
        {we.repMin}–{we.repMax} reps
      </span>
      <div className="split-ex-controls">
        <button
          className="icon-btn icon-btn-sm"
          disabled={saving || first}
          onClick={() => void apply({ setOrder: we.setOrder - 1 })}
          aria-label="Move up"
          title="Move up"
        >
          ↑
        </button>
        <button
          className="icon-btn icon-btn-sm"
          disabled={saving || last}
          onClick={() => void apply({ setOrder: we.setOrder + 1 })}
          aria-label="Move down"
          title="Move down"
        >
          ↓
        </button>
        <div className="stepper">
          <button
            className="stepper-btn"
            disabled={saving || sets <= 1}
            onClick={() => void apply({ sets: sets - 1 })}
            aria-label="Fewer sets"
          >
            <MinusIcon size={12} />
          </button>
          <span className="stepper-value">{sets}</span>
          <button
            className="stepper-btn"
            disabled={saving}
            onClick={() => void apply({ sets: sets + 1 })}
            aria-label="More sets"
          >
            <PlusIcon size={12} />
          </button>
        </div>
        <button
          className="icon-btn icon-btn-sm danger"
          disabled={saving}
          onClick={async () => {
            try {
              await api.removeExerciseFromDay(we.id);
              onChange();
            } catch (e) {
              toast(e instanceof ApiError ? e.message : "Couldn't remove", "error");
            }
          }}
          aria-label={`Remove ${we.name}`}
          title="Remove from this day"
        >
          <TrashIcon size={13} />
        </button>
      </div>
    </div>
  );
}

function AddExerciseRow({
  dayId,
  exercises,
  present,
  onChange,
  toast,
}: {
  dayId: number;
  exercises: Exercise[];
  present: Set<number>;
  onChange: () => void;
  toast: ReturnType<typeof useToast>;
}) {
  const [open, setOpen] = useState(false);
  const [exerciseId, setExerciseId] = useState<number | "">("");
  const [sets, setSets] = useState(3);
  const [saving, setSaving] = useState(false);

  const available = exercises.filter((e) => !present.has(e.id));

  const add = async () => {
    if (!exerciseId) {
      toast("Pick an exercise", "error");
      return;
    }
    setSaving(true);
    try {
      await api.addExerciseToDay(dayId, Number(exerciseId), sets);
      toast("Added to workout");
      setOpen(false);
      setExerciseId("");
      onChange();
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't add", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="add-ex-row">
      {open ? (
        <>
          <select value={exerciseId} onChange={(e) => setExerciseId(Number(e.target.value) || "")}>
            <option value="">Choose exercise…</option>
            {available.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name} ({titleCase(e.muscleGroup)})
              </option>
            ))}
          </select>
          <div className="stepper">
            <button className="stepper-btn" disabled={sets <= 1} onClick={() => setSets(sets - 1)}>
              <MinusIcon size={12} />
            </button>
            <span className="stepper-value">{sets} sets</span>
            <button className="stepper-btn" onClick={() => setSets(sets + 1)}>
              <PlusIcon size={12} />
            </button>
          </div>
          <Button size="sm" onClick={() => void add()} disabled={saving}>
            Add
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </>
      ) : (
        <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
          <PlusIcon size={14} /> Add exercise
        </Button>
      )}
    </div>
  );
}

/* ----------------------------- exercise library ----------------------------- */

function ExerciseLibrary({
  exercises,
  onChange,
  toast,
}: {
  exercises: Exercise[];
  onChange: () => void;
  toast: ReturnType<typeof useToast>;
}) {
  const [editing, setEditing] = useState<Exercise | "new" | null>(null);

  return (
    <>
      <div className="library-head">
        <span className="library-count">{exercises.length} exercises</span>
        <Button size="sm" variant="secondary" onClick={() => setEditing("new")}>
          <PlusIcon size={14} /> New exercise
        </Button>
      </div>
      <div className="library-list">
        {exercises.map((ex) => (
          <div key={ex.id} className="library-row">
            <span className="library-name">{ex.name}</span>
            <span className="pill pill-sage">{titleCase(ex.muscleGroup)}</span>
            <span className="library-reps">
              {ex.repMin}–{ex.repMax} reps
            </span>
            {ex.compound && <span className="pill pill-blue">compound</span>}
            <button className="icon-btn icon-btn-sm" onClick={() => setEditing(ex)} aria-label={`Edit ${ex.name}`}>
              ✏️
            </button>
          </div>
        ))}
      </div>

      {editing && (
        <ExerciseForm
          exercise={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onChange={onChange}
          toast={toast}
        />
      )}
    </>
  );
}

function ExerciseForm({
  exercise,
  onClose,
  onChange,
  toast,
}: {
  exercise: Exercise | null;
  onClose: () => void;
  onChange: () => void;
  toast: ReturnType<typeof useToast>;
}) {
  const [name, setName] = useState(exercise?.name ?? "");
  const [muscleGroup, setMuscleGroup] = useState<MuscleGroup>(exercise?.muscleGroup ?? "CHEST");
  const [repMin, setRepMin] = useState(exercise?.repMin ?? 8);
  const [repMax, setRepMax] = useState(exercise?.repMax ?? 12);
  const [compound, setCompound] = useState(exercise?.compound ?? false);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const save = async () => {
    if (!name.trim()) {
      toast("Give the exercise a name", "error");
      return;
    }
    if (repMax < repMin) {
      toast("Rep max must be at least rep min", "error");
      return;
    }
    setSaving(true);
    try {
      const body = { name: name.trim(), muscleGroup, repMin, repMax, compound };
      if (exercise) {
        await api.updateExercise(exercise.id, body);
      } else {
        await api.createExercise(body);
      }
      toast(exercise ? "Exercise updated" : "Exercise created");
      onChange();
      onClose();
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Couldn't save exercise", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={exercise ? `Edit ${exercise.name}` : "New exercise"}
      footer={
        <>
          {exercise && (
            <Button variant="danger" onClick={() => setConfirmDelete(true)}>
              <TrashIcon size={14} /> Delete
            </Button>
          )}
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={() => void save()} disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </Button>
        </>
      }
    >
      <div className="form-grid">
        <label className="field field-full">
          <span className="field-label">Name</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} />
        </label>
        <label className="field">
          <span className="field-label">Muscle group</span>
          <select value={muscleGroup} onChange={(e) => setMuscleGroup(e.target.value as MuscleGroup)}>
            {MUSCLE_GROUPS.map((m) => (
              <option key={m} value={m}>
                {titleCase(m)}
              </option>
            ))}
          </select>
        </label>
        <div className="field">
          <span className="field-label">Target reps</span>
          <div className="rep-range">
            <input type="number" min={1} value={repMin} onChange={(e) => setRepMin(Math.max(1, Math.round(Number(e.target.value) || 1)))} />
            <span>–</span>
            <input type="number" min={1} value={repMax} onChange={(e) => setRepMax(Math.max(1, Math.round(Number(e.target.value) || 1)))} />
          </div>
        </div>
        <label className="field-check">
          <input type="checkbox" checked={compound} onChange={(e) => setCompound(e.target.checked)} />
          <span>Compound movement (longer rest timer)</span>
        </label>
      </div>

      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title={`Delete ${exercise?.name}?`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                if (!exercise) return;
                try {
                  await api.deleteExercise(exercise.id);
                  toast("Exercise deleted");
                  setConfirmDelete(false);
                  onChange();
                  onClose();
                } catch (e) {
                  toast(e instanceof ApiError ? e.message : "Couldn't delete exercise", "error");
                  setConfirmDelete(false);
                }
              }}
            >
              Delete
            </Button>
          </>
        }
      >
        <p>This deletes the exercise from the library. It must be removed from all workouts first.</p>
      </Modal>
    </Modal>
  );
}
