import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { CalendarDay } from "../types";
import { todayISO } from "../lib/format";
import { ArrowLeftIcon, ArrowRightIcon } from "./Icons";

const WEEKDAYS = ["M", "T", "W", "T", "F", "S", "S"];

export function MonthCalendar() {
  const navigate = useNavigate();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1); // 1-based
  const [days, setDays] = useState<CalendarDay[] | null>(null);

  useEffect(() => {
    let alive = true;
    setDays(null);
    api
      .calendar(year, month)
      .then((d) => alive && setDays(d))
      .catch(() => alive && setDays([]));
    return () => {
      alive = false;
    };
  }, [year, month]);

  const shift = (delta: number) => {
    const d = new Date(year, month - 1 + delta, 1);
    setYear(d.getFullYear());
    setMonth(d.getMonth() + 1);
  };

  const firstDow = days && days.length > 0 ? new Date(days[0].date + "T00:00:00").getDay() : 0;
  const mondayFirst = (firstDow + 6) % 7;
  const today = todayISO();

  return (
    <div className="calendar">
      <div className="calendar-head">
        <button className="icon-btn" onClick={() => shift(-1)} aria-label="Previous month">
          <ArrowLeftIcon size={16} />
        </button>
        <span className="calendar-title">
          {new Date(year, month - 1, 1).toLocaleDateString("en-US", { month: "long", year: "numeric" })}
        </span>
        <button className="icon-btn" onClick={() => shift(1)} aria-label="Next month">
          <ArrowRightIcon size={16} />
        </button>
      </div>

      <div className="calendar-grid">
        {WEEKDAYS.map((w, i) => (
          <span key={i} className="cal-dow">
            {w}
          </span>
        ))}
        {days === null ? (
          <div className="cal-loading" />
        ) : (
          <>
            {Array.from({ length: mondayFirst }, (_, i) => (
              <span key={`pad-${i}`} className="cal-cell cal-pad" />
            ))}
            {days.map((d) => {
              const isToday = d.date === today;
              const clickable = d.status === "WORKOUT" && d.sessionId != null;
              return (
                <button
                  key={d.date}
                  className={`cal-cell cal-${d.status.toLowerCase()} ${isToday ? "cal-today" : ""} ${clickable ? "cal-click" : ""}`}
                  onClick={clickable ? () => navigate(`/history/${d.sessionId}`) : undefined}
                  title={
                    d.status === "WORKOUT"
                      ? `${d.date} — ${d.workoutDayName ?? "Workout"}`
                      : d.status === "MISSED"
                        ? `${d.date} — missed workout`
                        : d.status === "REST"
                          ? `${d.date} — rest day`
                          : d.date
                  }
                >
                  {d.status === "REST" && <span className="cal-rest-dot" />}
                </button>
              );
            })}
          </>
        )}
      </div>

      <div className="calendar-legend">
        <span className="leg"><i className="leg-sw leg-workout" /> Workout</span>
        <span className="leg"><i className="leg-sw leg-missed" /> Missed</span>
        <span className="leg"><i className="leg-sw leg-rest" /> Rest</span>
      </div>
    </div>
  );
}
