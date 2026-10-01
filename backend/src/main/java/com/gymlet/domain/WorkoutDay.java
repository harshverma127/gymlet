package com.gymlet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "workout_day", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workout_day_user_plan_day", columnNames = {"user_id", "plan_id", "day_number"})
})
public class WorkoutDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner. Null rows are the shared default template copied into each new account. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    /** Position within the plan: 1..5 training slots, 6 = custom-workout pseudo day. Unique per user+plan. */
    @Column(nullable = false)
    private Integer dayNumber;

    /** The plan this day belongs to. Null only for the shared default template and pre-migration rows (backfilled on boot). */
    @Column(name = "plan_id")
    private Long planId;

    /** Calendar weekday this row represents in the plan schedule: 1 = Monday … 7 = Sunday (ISO). */
    @Column(name = "weekday")
    private Integer weekday;

    /**
     * When true, this weekday is a rest day (no workout template).
     *
     * The explicit DDL default matters on PostgreSQL: {@code workout_day} is an
     * existing, populated table, and Hibernate's {@code ddl-auto=update} cannot
     * add a plain NOT NULL column to non-empty data (it logs a warning and keeps
     * going, which is how {@code column wd1_0.rest_day does not exist} reached
     * production). With the default inline, the ALTER succeeds during JPA init —
     * before the web server accepts requests and before any migration runner.
     */
    @Column(name = "rest_day", nullable = false, columnDefinition = "boolean default false")
    private boolean restDay = false;

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public Integer getWeekday() {
        return weekday;
    }

    public void setWeekday(Integer weekday) {
        this.weekday = weekday;
    }

    public boolean isRestDay() {
        return restDay;
    }

    public void setRestDay(boolean restDay) {
        this.restDay = restDay;
    }
}
