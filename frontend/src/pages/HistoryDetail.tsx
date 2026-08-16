import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import type { Session } from "../types";
import { useProfile } from "../state";
import { formatDateLong, formatDuration } from "../lib/format";
import { formatVolume, toDisplay } from "../lib/units";
import { useToast } from "../components/Toast";
import { Button, Card, Modal, Skeleton } from "../components/ui";
import { ArrowLeftIcon, CheckIcon, NoteIcon, TrashIcon } from "../components/Icons";

export function HistoryDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { unit } = useProfile();
  const toast = useToast();
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

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

  const notesByExercise = new Map(session.notes.map((n) => [n.exerciseId, n.note]));

  return (
    <div className="page">
      <button className="back-link" onClick={() => navigate(-1)}>
        <ArrowLeftIcon size={15} /> Back
      </button>

      <header className="page-head">
        <div>
          <p className="eyebrow">
            {formatDateLong(session.date)}
            {session.demo && <span className="pill pill-peach pill-inline">sample</span>}
          </p>
          <h1>{session.workoutDayName}</h1>
        </div>
      </header>

      <Card className="detail-stats">
        <div className="cstat">
          <span className="cstat-label">Duration</span>
          <span className="cstat-value">{formatDuration(session.durationMinutes)}</span>
        </div>
        <div className="cstat">
          <span className="cstat-label">Sets completed</span>
          <span className="cstat-value">
            {session.completedSets} / {session.totalSets}
          </span>
        </div>
        <div className="cstat">
          <span className="cstat-label">Volume</span>
          <span className="cstat-value">{formatVolume(session.volume, unit)}</span>
        </div>
      </Card>

      {groups.map((g) => {
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
              {g.sets.map((s) => (
                <div key={s.id} className={`detail-set ${s.completed ? "is-complete" : ""}`}>
                  <span className="detail-set-label">Set {s.setNumber}</span>
                  <span className="detail-set-value">
                    {s.completed && s.weight != null ? (
                      <>
                        <CheckIcon size={13} />
                        <strong>
                          {toDisplay(s.weight, unit)} × {s.reps}
                        </strong>
                        {s.rir != null && <span className="detail-rir">RIR {s.rir}</span>}
                      </>
                    ) : (
                      <span className="detail-skipped">skipped</span>
                    )}
                  </span>
                </div>
              ))}
            </div>
            {note && (
              <p className="detail-note">
                <NoteIcon size={13} /> {note}
              </p>
            )}
          </Card>
        );
      })}

      <Button variant="danger" className="delete-session-btn" onClick={() => setConfirmDelete(true)}>
        <TrashIcon size={15} /> Delete workout
      </Button>

      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Delete this workout?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)} disabled={deleting}>
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
                  toast(e instanceof ApiError ? e.message : "Couldn't delete", "error");
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
    </div>
  );
}
