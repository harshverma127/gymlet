package com.gymlet.repository;

import com.gymlet.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<Exercise> findByIdAndUserId(Long id, Long userId);

    /** The shared default exercise library (used to bootstrap new accounts). */
    List<Exercise> findAllByUserIdIsNull();

    long countByUserIdIsNull();
}
