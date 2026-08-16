package com.gymlet.repository;

import com.gymlet.domain.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    Optional<WorkoutSession> findFirstByDate(LocalDate date);

    Optional<WorkoutSession> findFirstByDateAndCompletedTrue(LocalDate date);

    List<WorkoutSession> findAllByOrderByDateDescIdDesc();

    List<WorkoutSession> findByCompletedTrueOrderByDateAsc();

    List<WorkoutSession> findByCompletedTrueOrderByDateDesc();

    List<WorkoutSession> findByCompletedTrueAndDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    List<WorkoutSession> findByWorkoutDayIdAndCompletedTrueOrderByDateDesc(Long workoutDayId);
}
