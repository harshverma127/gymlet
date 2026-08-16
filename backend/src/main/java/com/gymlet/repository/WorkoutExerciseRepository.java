package com.gymlet.repository;

import com.gymlet.domain.WorkoutExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, Long> {

    List<WorkoutExercise> findByWorkoutDayIdOrderBySetOrderAsc(Long workoutDayId);

    List<WorkoutExercise> findByExerciseId(Long exerciseId);

    void deleteByWorkoutDayId(Long workoutDayId);
}
