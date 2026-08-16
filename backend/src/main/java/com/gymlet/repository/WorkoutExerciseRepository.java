package com.gymlet.repository;

import com.gymlet.domain.WorkoutExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, Long> {

    List<WorkoutExercise> findByWorkoutDayIdOrderBySetOrderAsc(Long workoutDayId);

    /** Whether a given exercise is used in any of the user's workout days. */
    List<WorkoutExercise> findByExerciseIdAndExerciseUserId(Long exerciseId, Long userId);

    /** Ownership is derived from the workout day, which is user-scoped. */
    Optional<WorkoutExercise> findByIdAndWorkoutDayUserId(Long id, Long userId);

    void deleteByWorkoutDayId(Long workoutDayId);

    List<WorkoutExercise> findAllByWorkoutDayIdIn(List<Long> workoutDayIds);
}
