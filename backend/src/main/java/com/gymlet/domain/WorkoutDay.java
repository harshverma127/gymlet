package com.gymlet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "workout_day", uniqueConstraints = @UniqueConstraint(name = "uk_workout_day_user_day", columnNames = {"user_id", "day_number"}))
public class WorkoutDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner. Null rows are the shared default template copied into each new account. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    /** 1..5 within the weekly split (unique per user). */
    @Column(nullable = false)
    private Integer dayNumber;

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
}
