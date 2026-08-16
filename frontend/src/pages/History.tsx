import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api/client";
import type { HistoryItem } from "../types";
import { useProfile } from "../state";
import { formatDate, formatDuration, weekdayLabel } from "../lib/format";
import { formatVolume } from "../lib/units";
import { Card, EmptyState, Skeleton } from "../components/ui";
import { CalendarIcon, FlagIcon } from "../components/Icons";

export function HistoryPage() {
  const { unit } = useProfile();
  const [items, setItems] = useState<HistoryItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setItems(await api.history());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const anyDemo = items?.some((i) => i.demo) ?? false;

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Logbook</p>
          <h1>Workout History</h1>
        </div>
      </header>

      {anyDemo && (
        <div className="demo-banner">
          <span className="demo-banner-icon">🧪</span>
          <span>
            Showing <strong>sample history</strong> so the analytics look alive. Remove it anytime in Profile.
          </span>
        </div>
      )}

      {error ? (
        <Card className="card-error">
          <p>{error}</p>
        </Card>
      ) : items === null ? (
        <Skeleton lines={5} />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState
            icon={<FlagIcon size={30} />}
            title="No workouts yet"
            text="Head to Today and start your first session — it'll show up here."
          />
        </Card>
      ) : (
        <div className="history-list">
          {items.map((item) => (
            <Link key={item.id} to={`/history/${item.id}`} className="history-item-link">
              <Card className="history-item">
                <div className="history-item-top">
                  <span className="history-date">
                    <CalendarIcon size={15} />
                    <strong>{formatDate(item.date)}</strong>
                    <span className="history-weekday">{weekdayLabel(item.date)}</span>
                  </span>
                  {item.demo && <span className="pill pill-peach">sample</span>}
                </div>
                <h3 className="history-name">{item.workoutDayName}</h3>
                <div className="history-stats">
                  <span>{item.setsCompleted} sets</span>
                  <span>·</span>
                  <span>{formatDuration(item.durationMinutes)}</span>
                  <span>·</span>
                  <span>{item.exercisesCompleted} exercises</span>
                  <span>·</span>
                  <span>{formatVolume(item.volume, unit)}</span>
                </div>
                {!item.completed && <span className="pill pill-peach incomplete-pill">in progress</span>}
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
