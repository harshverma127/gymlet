package com.gymlet.repository;

import com.gymlet.domain.ExerciseNote;
import com.gymlet.domain.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseNoteRepository extends JpaRepository<ExerciseNote, Long> {

    List<ExerciseNote> findBySession(WorkoutSession session);

    Optional<ExerciseNote> findBySessionAndExerciseId(WorkoutSession session, Long exerciseId);

    void deleteBySession(WorkoutSession session);
}
