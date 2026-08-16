package com.gymlet.repository;

import com.gymlet.domain.WorkoutDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutDayRepository extends JpaRepository<WorkoutDay, Long> {

    List<WorkoutDay> findAllByUserIdOrderByDayNumberAsc(Long userId);

    Optional<WorkoutDay> findByIdAndUserId(Long id, Long userId);

    Optional<WorkoutDay> findByUserIdAndDayNumber(Long userId, Integer dayNumber);

    /** The shared default template (used to bootstrap new accounts). */
    List<WorkoutDay> findByUserIdIsNullOrderByDayNumberAsc();

    /** Rows that have no owner yet — pre-migration legacy plan rows (claimed during migration). */
    List<WorkoutDay> findAllByUserIdIsNull();
}
