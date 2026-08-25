import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import type { Session, SetLog, SessionNote } from "../types";
import { useProfile } from "../state";
import { formatDateLong, formatDuration } from "../lib/format";
import { formatVolume, toDisplay } from "../lib/units";
import { useToast } from "../components/Toast";
import { Button, Card, Modal, Skeleton } from "../components/ui";
import {
  ArrowLeftIcon,
  CheckIcon,
  NoteIcon,
  TrashIcon,
  EditIcon,
  XIcon,
  SaveIcon,
} from "../components/Icons";

/** Deep-clone the sets/notes for editing so originals are untouched. */
function cloneSets(sets: SetLog[]): EditSet[] {
  return sets.map((s) => ({
    ...s,
    weight: s.weight,
    reps: s.reps,
    rir: s.rir,
  }));
}

function cloneNotes(notes: SessionNote[]): EditNote[] {
  return notes.map((n) => ({ ...n, note: n.note }));
}

interface EditSet {
  id: number;
  exerciseId: number;
  exerciseName: string;
  setNumber: number;
  weight: number | null;
  reps: number | null;
  rir: number | null;
  completed: boolean;
}

interface EditNote {
  exerciseId: number;
  note: string;
}

export function HistoryDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { unit } = useProfile();
  const toast = useToast();
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Edit mode state
  const [editing, setEditing] = useState(false);
  const [editSets, setEditSets] = useState<EditSet[]>([]);
  const [editNotes, setEditNotes] = useState<EditNote[]>([]);
  const [editError, setEditError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [pendingNavigation, setPendingNavigation] = useState<(() => void) | null>(null);
  const isDirty = useRef(false);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setSession(await api.session(Number(id)));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong");
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  // Unsaved changes guard (browser tab close / navigate)
  useEffect(() => {
    if (!editing) return;
    const handler = (e: BeforeUnloadEvent) => {
      if (isDirty.current) {
        e.preventDefault();
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [editing]);

  const groups = useMemo(() => {
    if (!session) return [];
    const map = new Map<number, typeof session.sets>();
    for (const set of session.sets) {
      const arr = map.get(set.exerciseId) ?? [];
      arr.push(set);
      map.set(set.exerciseId, arr);
    }
    return [...map.entries()].map(([exerciseId, sets]) => ({
      exerciseId,
      name: sets[0].exerciseName,
      sets,
    }));
  }, [session]);

  // Group edit sets by exercise
  const editGroups = useMemo(() => {
    if (!editing) return [];
    const map = new Map<number, EditSet[]>();
    for (const s of editSets) {
      const arr = map.get(s.exerciseId) ?? [];
      arr.push(s);
      map.set(s.exerciseId, arr);
    }
    return [...map.entries()].map(([exerciseId, sets]) => ({
      exerciseId,
      name: sets[0].exerciseName,
      sets,
    }));
  }, [editing, editSets]);

  const notesByExercise = useMemo(() => {
    const source = editing ? editNotes : session?.notes ?? [];
    return new Map(source.map((n) => [n.exerciseId, n.note]));
  }, [editing, editNotes, session]);

  // --- Edit handlers ---

  function enterEditMode() {
    if (!session) return;
    setEditSets(cloneSets(session.sets));
    setEditNotes(cloneNotes(session.notes));
    setEditError(null);
    isDirty.current = false;
    setEditing(true);
  }

  function cancelEdit() {
    isDirty.current = false;
    setEditing(false);
    setEditError(null);
  }

  function handleEditChange(
    setId: number,
    field: "weight" | "reps" | "rir",
    value: string
  ) {
    isDirty.current = true;
    setEditSets((prev) =>
      prev.map((s) => {
        if (s.id !== setId) return s;
        if (value === "") return { ...s, [field]: null };
        const num = Number(value);
        return { ...s, [field]: isNaN(num) ? value : num };
      }) as EditSet[]
    );
  }

  function handleNoteChange(exerciseId: number, value: string) {
    isDirty.current = true;
    setEditNotes((prev) => {
      const existing = prev.find((n) => n.exerciseId === exerciseId);
      if (existing) {
        return prev.map((n) =>
          n.exerciseId === exerciseId ? { ...n, note: value } : n
        );
      }
      return [...prev, { exerciseId, note: value }];
    });
  }

  async function saveEdit() {
    if (!session) return;
    setEditError(null);

    // Validate
    for (const s of editSets) {
      if (s.completed) {
        if (s.weight == null || s.weight <= 0) {
          setEditError(
            `${s.exerciseName} Set ${s.setNumber}: weight must be positive for completed sets`
          );
          return;
        }
        if (s.reps == null || s.reps <= 0) {
          setEditError(
            `${s.exerciseName} Set ${s.setNumber}: reps must be positive for completed sets`
          );
          return;
        }
      }
      if (s.weight != null && s.weight < 0) {
        setEditError(
          `${s.exerciseName} Set ${s.setNumber}: weight cannot be negative`
        );
        return;
      }
      if (s.reps != null && s.reps < 0) {
        setEditError(
          `${s.exerciseName} Set ${s.setNumber}: reps cannot be negative`
        );
        return;
      }
      if (s.rir != null && s.rir < 0) {
        setEditError(
          `${s.exerciseName} Set ${s.setNumber}: RIR cannot be negative`
        );
        return;
      }
    }

    setSaving(true);
    try {
      // Update all sets
      await Promise.all(
        editSets.map((s) =>
          api.updateSet(session.id, s.id, {
            weight: s.weight,
            reps: s.reps,
            rir: s.rir,
            completed: s.completed,
          })
        )
      );

      // Update all notes
      for (const n of editNotes) {
        await api.saveNote(session.id, n.exerciseId, n.note);
      }

      // Reload session to reflect changes
      await load();
      isDirty.current = false;
      setEditing(false);
      toast("Workout updated successfully.");
    } catch (e) {
      setEditError(e instanceof ApiError ? e.message : "Couldn't save changes");
    } finally {
      setSaving(false);
    }
  }

  function handleNavigateAway(callback: () => void) {
    if (editing && isDirty.current) {
      setPendingNavigation(() => callback);
      setConfirmDiscard(true);
    } else {
      callback();
    }
  }

  // --- Render ---

  if (error) {
    return (
      <div className="page">
        <Card className="card-error">
          <p>{error}</p>
          <Button variant="secondary" onClick={() => navigate("/history")}>
            <ArrowLeftIcon size={16} /> Back to History
          </Button>
        </Card>
      </div>
    );
  }

  if (!session) {
    return (
      <div className="page">
        <Skeleton lines={6} />
      </div>
    );
  }

  return (
    <div className="page">
      <button
        className="back-link"
        onClick={() =>
          handleNavigateAway(() => navigate(-1))
        }
      >
        <ArrowLeftIcon size={15} /> Back
      </button>

      <header className="page-head">
        <div>
          <p className="eyebrow">
            {formatDateLong(session.date)}
            {session.demo && (
              <span className="pill pill-peach pill-inline">sample</span>
            )}
          </p>
          <h1>
            {session.workoutDayName}
            {session.workoutDayName === "Custom Workout" && (
              <span className="pill pill-blue pill-inline">custom</span>
            )}
          </h1>
        </div>
      </header>

      <Card className="detail-stats">
        <div className="cstat">
          <span className="cstat-label">Duration</span>
          <span className="cstat-value">
            {formatDuration(session.durationMinutes)}
          </span>
        </div>
        <div className="cstat">
          <span className="cstat-label">Sets completed</span>
          <span className="cstat-value">
            {session.completedSets} / {session.totalSets}
          </span>
        </div>
        <div className="cstat">
          <span className="cstat-label">Volume</span>
          <span className="cstat-value">
            {formatVolume(session.volume, unit)}
          </span>
        </div>
      </Card>

      {/* Edit / Save / Cancel buttons */}
      {session.completed && !editing && (
        <Button variant="primary" className="edit-workout-btn" onClick={enterEditMode}>
          <EditIcon size={15} /> Edit Workout
        </Button>
      )}
      {editing && (
        <div className="edit-actions">
          <Button
            variant="primary"
            onClick={() => void saveEdit()}
            disabled={saving}
          >
            <SaveIcon size={15} /> {saving ? "Saving…" : "Save Changes"}
          </Button>
          <Button
            variant="ghost"
            onClick={cancelEdit}
            disabled={saving}
          >
            <XIcon size={15} /> Cancel
          </Button>
        </div>
      )}
      {editError && (
        <Card className="card-error">
          <p>{editError}</p>
        </Card>
      )}

      {/* Exercise groups */}
      {(editing ? editGroups : groups).map((g) => {
        const done = g.sets.filter((s) => s.completed).length;
        const note = notesByExercise.get(g.exerciseId);
        return (
          <Card key={g.exerciseId} className="detail-exercise">
            <div className="exercise-title-row">
              <h3 className="exercise-name">{g.name}</h3>
              <span className="exercise-done">
                {done}/{g.sets.length}
              </span>
            </div>
            <div className="detail-sets">
              {g.sets.map((s) => {
                if (editing) {
                  const es = s as EditSet;
                  return (
                    <div key={es.id} className="edit-set-row">
                      <span className="detail-set-label">
                        Set {es.setNumber}
                      </span>
                      <div className="edit-set-fields">
                        <div className="edit-field">
                          <label className="edit-field-label">kg</label>
                          <input
                            className="edit-input"
                            type="number"
                            step="0.5"
                            min="0"
                            value={es.weight ?? ""}
                            onChange={(e) =>
                              handleEditChange(es.id, "weight", e.target.value)
                            }
                            inputMode="decimal"
                          />
                        </div>
                        <div className="edit-field">
                          <label className="edit-field-label">reps</label>
                          <input
                            className="edit-input"
                            type="number"
                            step="1"
                            min="0"
                            value={es.reps ?? ""}
                            onChange={(e) =>
                              handleEditChange(es.id, "reps", e.target.value)
                            }
                            inputMode="numeric"
                          />
                        </div>
                        <div className="edit-field">
                          <label className="edit-field-label">RIR</label>
                          <input
                            className="edit-input"
                            type="number"
                            step="1"
                            min="0"
                            value={es.rir ?? ""}
                            onChange={(e) =>
                              handleEditChange(es.id, "rir", e.target.value)
                            }
                            inputMode="numeric"
                          />
                        </div>
                      </div>
                    </div>
                  );
                }
                // Read-only view
                return (
                  <div
                    key={s.id}
                    className={`detail-set ${s.completed ? "is-complete" : ""}`}
                  >
                    <span className="detail-set-label">
                      Set {s.setNumber}
                    </span>
                    <span className="detail-set-value">
                      {s.completed && s.weight != null ? (
                        <>
                          <CheckIcon size={13} />
                          <strong>
                            {toDisplay(s.weight, unit)} × {s.reps}
                          </strong>
                          {s.rir != null && (
                            <span className="detail-rir">RIR {s.rir}</span>
                          )}
                        </>
                      ) : (
                        <span className="detail-skipped">skipped</span>
                      )}
                    </span>
                  </div>
                );
              })}
            </div>
            {editing ? (
              <div className="edit-note-field">
                <label className="edit-field-label">
                  <NoteIcon size={13} /> Notes
                </label>
                <textarea
                  className="edit-note-input"
                  placeholder="Add notes for this exercise…"
                  value={note ?? ""}
                  onChange={(e) =>
                    handleNoteChange(g.exerciseId, e.target.value)
                  }
                  rows={2}
                />
              </div>
            ) : (
              note && (
                <p className="detail-note">
                  <NoteIcon size={13} /> {note}
                </p>
              )
            )}
          </Card>
        );
      })}

      {/* Delete button (only when NOT editing) */}
      {!editing && (
        <Button
          variant="danger"
          className="delete-session-btn"
          onClick={() => setConfirmDelete(true)}
        >
          <TrashIcon size={15} /> Delete workout
        </Button>
      )}

      {/* Delete confirmation modal */}
      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Delete this workout?"
        footer={
          <>
            <Button
              variant="ghost"
              onClick={() => setConfirmDelete(false)}
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={deleting}
              onClick={async () => {
                setDeleting(true);
                try {
                  await api.deleteSession(session.id);
                  toast("Workout deleted");
                  navigate("/history");
                } catch (e) {
                  toast(
                    e instanceof ApiError ? e.message : "Couldn't delete",
                    "error"
                  );
                  setDeleting(false);
                  setConfirmDelete(false);
                }
              }}
            >
              {deleting ? "Deleting…" : "Delete"}
            </Button>
          </>
        }
      >
        <p>This removes the workout and all its sets. This can't be undone.</p>
      </Modal>

      {/* Discard unsaved changes modal */}
      <Modal
        open={confirmDiscard}
        onClose={() => {
          setConfirmDiscard(false);
          setPendingNavigation(null);
        }}
        title="Unsaved changes"
        footer={
          <>
            <Button
              variant="ghost"
              onClick={() => {
                setConfirmDiscard(false);
                setPendingNavigation(null);
              }}
            >
              Stay
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                isDirty.current = false;
                setEditing(false);
                setConfirmDiscard(false);
                pendingNavigation?.();
                setPendingNavigation(null);
              }}
            >
              Discard
            </Button>
          </>
        }
      >
        <p>You have unsaved changes. Leave without saving?</p>
      </Modal>
    </div>
  );
}
