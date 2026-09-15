import { useCallback, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api/client";
import type { PlanList, PlanSummary, WorkoutDay } from "../types";
import { DAY_NAMES } from "../lib/format";
import { useToast } from "./Toast";
import { Button, Card, Modal, Pill, Skeleton } from "./ui";
import { CalendarIcon, CheckIcon, ChevronDownIcon, EditIcon, PlusIcon, SparkleIcon } from "./Icons";

interface PlanSwitcherProps {
  onChanged?: () => Promise<void> | void;
}

export function PlanSwitcher({ onChanged }: PlanSwitcherProps) {
  const toast = useToast();
  const [data, setData] = useState<PlanList | null>(null);
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await api.plans());
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't load plans", "error");
    }
  }, [toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const activate = async (plan: PlanSummary) => {
    if (plan.active || plan.archived) return;
    setBusy(true);
    try {
      await api.activatePlan(plan.id);
      await load();
      await onChanged?.();
      toast(`${plan.name} is now active`);
      setOpen(false);
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't switch plans", "error");
    } finally {
      setBusy(false);
    }
  };

  if (!data) return <div className="plan-switcher-skeleton"><Skeleton lines={2} /></div>;

  return (
    <div className="plan-switcher">
      <button className="plan-switcher-trigger" type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        <span className="plan-switcher-mark"><SparkleIcon size={16} /></span>
        <span className="plan-switcher-copy">
          <span className="eyebrow">Active plan</span>
          <strong>{data.active?.name ?? "Choose a plan"}</strong>
        </span>
        <ChevronDownIcon size={16} className={open ? "rotated" : ""} />
      </button>
      {open && (
        <div className="plan-switcher-menu">
          {data.plans.map((plan) => (
            <button
              key={plan.id}
              type="button"
              className={`plan-option ${plan.active ? "is-active" : ""} ${plan.archived ? "is-archived" : ""}`}
              disabled={busy || plan.archived}
              onClick={() => void activate(plan)}
            >
              <span>
                <strong>{plan.name}</strong>
                <small>{plan.archived ? "Archived" : plan.active ? "Currently active" : "Available to use"}</small>
              </span>
              {plan.active && <CheckIcon size={16} />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function PlanManagement({ onChanged }: PlanSwitcherProps) {
  const toast = useToast();
  const [data, setData] = useState<PlanList | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [copyingId, setCopyingId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<PlanSummary | null>(null);
  const [savingEdit, setSavingEdit] = useState(false);
  const [draft, setDraft] = useState({ name: "", description: "", goal: "" });

  const load = useCallback(async () => {
    try {
      setData(await api.plans());
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't load plans", "error");
    }
  }, [toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const activate = async (plan: PlanSummary) => {
    setBusyId(plan.id);
    try {
      await api.activatePlan(plan.id);
      await load();
      await onChanged?.();
      toast(`${plan.name} is now active`);
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't use this plan", "error");
    } finally {
      setBusyId(null);
    }
  };

  const archive = async (plan: PlanSummary) => {
    setBusyId(plan.id);
    try {
      await api.archivePlan(plan.id);
      await load();
      await onChanged?.();
      toast(`${plan.name} was archived`);
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't archive this plan", "error");
    } finally {
      setBusyId(null);
    }
  };

  const copy = async (plan: PlanSummary) => {
    setCopyingId(plan.id);
    try {
      const copied = await api.copyPlan(plan.id);
      await load();
      toast(`${copied.name} created — your current plan stays active`);
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't copy this plan", "error");
    } finally {
      setCopyingId(null);
    }
  };

  const create = async () => {
    if (!draft.name.trim()) return;
    setCreating(true);
    try {
      await api.createPlan({
        name: draft.name.trim(),
        description: draft.description.trim() || undefined,
        goal: draft.goal.trim() || undefined,
      });
      setDraft({ name: "", description: "", goal: "" });
      setCreateOpen(false);
      await load();
      toast("Plan created — it is ready to customize");
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't create plan", "error");
    } finally {
      setCreating(false);
    }
  };

  const beginEdit = (plan: PlanSummary) => {
    setEditing(plan);
    setDraft({ name: plan.name, description: plan.description ?? "", goal: plan.goal ?? "" });
  };

  const saveEdit = async () => {
    if (!editing || !draft.name.trim()) return;
    setSavingEdit(true);
    try {
      await api.updatePlan(editing.id, {
        name: draft.name.trim(),
        description: draft.description.trim(),
        goal: draft.goal.trim(),
      });
      setEditing(null);
      await load();
      toast("Plan details saved");
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't save plan details", "error");
    } finally {
      setSavingEdit(false);
    }
  };

  return (
    <Card className="section-card plans-card">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">Your training worlds</p>
          <h2 className="section-title">Workout plans</h2>
        </div>
        <Button size="sm" onClick={() => setCreateOpen(true)}><PlusIcon size={14} /> New plan</Button>
      </div>
      {data === null ? <Skeleton lines={4} /> : (
        <div className="plan-list">
          {data.plans.length === 0 ? (
            <div className="plan-empty"><SparkleIcon size={22} /><strong>Build your first plan</strong><span>Give your week a shape that feels good.</span></div>
          ) : data.plans.map((plan) => (
            <div key={plan.id} className={`plan-management-row ${plan.active ? "is-active" : ""} ${plan.archived ? "is-archived" : ""}`}>
              <div className="plan-management-icon"><CalendarIcon size={17} /></div>
              <div className="plan-management-copy">
                <div className="plan-name-line"><strong>{plan.name}</strong>{plan.active && <Pill tone="sage">Active</Pill>}{plan.archived && <Pill tone="neutral">Archived</Pill>}</div>
                <span>{plan.description || plan.goal || `${plan.historicalSessionCount} logged ${plan.historicalSessionCount === 1 ? "session" : "sessions"}`}</span>
              </div>
              {!plan.archived && <div className="plan-row-actions"><Button size="sm" variant="ghost" aria-label={`Edit ${plan.name}`} title="Edit plan" onClick={() => beginEdit(plan)}><EditIcon size={14} /></Button><Button size="sm" variant="ghost" disabled={copyingId === plan.id} onClick={() => void copy(plan)}>{copyingId === plan.id ? "Copying…" : "Copy"}</Button>{!plan.active && <Button size="sm" variant="secondary" disabled={busyId === plan.id} onClick={() => void activate(plan)}>Use</Button>}{plan.active && <Button size="sm" variant="ghost" disabled={busyId === plan.id} onClick={() => void archive(plan)}>Archive</Button>}</div>}
            </div>
          ))}
        </div>
      )}
      <p className="field-note plans-note">Plans keep their own workout structure. Your completed sessions stay attached to the day you trained.</p>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create a workout plan" footer={
        <><Button variant="ghost" onClick={() => setCreateOpen(false)} disabled={creating}>Cancel</Button><Button onClick={() => void create()} disabled={creating || !draft.name.trim()}>{creating ? "Creating…" : "Create plan"}</Button></>
      }>
        <div className="plan-form">
          <label className="field"><span className="field-label">Plan name</span><input autoFocus maxLength={80} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="e.g. Spring strength" /></label>
          <label className="field"><span className="field-label">Description <span className="field-optional">optional</span></span><textarea rows={2} maxLength={2000} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} placeholder="What is this plan for?" /></label>
          <label className="field"><span className="field-label">Goal <span className="field-optional">optional</span></span><input maxLength={200} value={draft.goal} onChange={(e) => setDraft({ ...draft, goal: e.target.value })} placeholder="e.g. Build consistent strength" /></label>
        </div>
      </Modal>
      <Modal open={editing != null} onClose={() => setEditing(null)} title="Edit plan details" footer={
        <><Button variant="ghost" onClick={() => setEditing(null)} disabled={savingEdit}>Cancel</Button><Button onClick={() => void saveEdit()} disabled={savingEdit || !draft.name.trim()}>{savingEdit ? "Saving…" : "Save details"}</Button></>
      }>
        <div className="plan-form">
          <label className="field"><span className="field-label">Plan name</span><input autoFocus maxLength={80} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} /></label>
          <label className="field"><span className="field-label">Description <span className="field-optional">optional</span></span><textarea rows={2} maxLength={2000} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} /></label>
          <label className="field"><span className="field-label">Goal <span className="field-optional">optional</span></span><input maxLength={200} value={draft.goal} onChange={(e) => setDraft({ ...draft, goal: e.target.value })} /></label>
        </div>
      </Modal>
    </Card>
  );
}

export function WeeklySchedule({ onChanged }: PlanSwitcherProps) {
  const toast = useToast();
  const [data, setData] = useState<PlanList | null>(null);
  const [days, setDays] = useState<WorkoutDay[] | null>(null);
  const [selected, setSelected] = useState<Record<number, string>>({});
  const [saving, setSaving] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [plans, workoutDays] = await Promise.all([api.plans(), api.workoutDays()]);
      setData(plans);
      setDays(workoutDays);
      const next: Record<number, string> = {};
      workoutDays.forEach((day) => { if (day.weekday != null) next[day.weekday] = String(day.id); });
      setSelected(next);
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't load your schedule", "error");
    }
  }, [toast]);

  useEffect(() => { void load(); }, [load]);

  const today = new Date().getDay() === 0 ? 7 : new Date().getDay();
  const options = useMemo(() => (days ?? []).filter((day) => day.dayNumber !== 6 && !day.restDay), [days]);

  const save = async (weekday: number, value: string) => {
    if (!data?.active?.id || saving != null) return;
    setSaving(weekday);
    try {
      if (value === "rest") {
        await api.setScheduleRest(data.active.id, weekday);
      } else {
        await api.assignScheduleDay(data.active.id, weekday, Number(value));
      }
      await load();
      await onChanged?.();
      toast(value === "rest" ? `${DAY_NAMES[weekday - 1]} is a rest day` : "Schedule updated");
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "Couldn't update schedule", "error");
    } finally {
      setSaving(null);
    }
  };

  return (
    <Card className="section-card schedule-card">
      <div className="section-title-row">
        <div><p className="eyebrow">Seven-day rhythm</p><h2 className="section-title">Weekly schedule</h2></div>
        <CalendarIcon size={20} />
      </div>
      <p className="schedule-intro">Shape the week around the plan you are using now. Changes never rewrite your workout history.</p>
      {!data || !days ? <Skeleton lines={7} /> : (
        <div className="schedule-grid">
          {DAY_NAMES.map((dayName, index) => {
            const weekday = index + 1;
            const value = selected[weekday] ?? "rest";
            const assigned = options.find((day) => String(day.id) === value);
            return (
              <div key={dayName} className={`schedule-day ${weekday === today ? "is-today" : ""} ${value === "rest" ? "is-rest" : ""}`}>
                <div className="schedule-day-top"><span className="schedule-weekday">{dayName.slice(0, 3)}</span>{weekday === today && <Pill tone="peach">Today</Pill>}</div>
                <div className="schedule-status"><span className="schedule-dot" />{assigned ? assigned.name : "Rest day"}</div>
                <select aria-label={`${dayName} workout`} value={value} disabled={saving === weekday} onChange={(e) => void save(weekday, e.target.value)}>
                  <option value="rest">Rest day</option>
                  {options.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
                </select>
                {saving === weekday && <span className="schedule-saving">Saving…</span>}
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );
}
