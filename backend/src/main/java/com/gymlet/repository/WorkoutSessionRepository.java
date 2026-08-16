package com.gymlet.repository;

import com.gymlet.domain.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    Optional<WorkoutSession> findFirstByUserIdAndDate(Long userId, LocalDate date);

    Optional<WorkoutSession> findFirstByUserIdAndDateAndCompletedTrue(Long userId, LocalDate date);

    List<WorkoutSession> findAllByUserIdOrderByDateDescIdDesc(Long userId);

    List<WorkoutSession> findByUserIdAndCompletedTrueOrderByDateAsc(Long userId);

    List<WorkoutSession> findByUserIdAndCompletedTrueOrderByDateDesc(Long userId);

    List<WorkoutSession> findByUserIdAndCompletedTrueAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    List<WorkoutSession> findByUserIdAndWorkoutDayIdAndCompletedTrueOrderByDateDesc(Long userId, Long workoutDayId);

    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    List<WorkoutSession> findAllByUserId(Long userId);

    /** Rows that have no owner yet — pre-migration legacy sessions (claimed during migration). */
    List<WorkoutSession> findAllByUserIdIsNull();
}
