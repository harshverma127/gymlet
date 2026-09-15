package com.gymlet.repository;

import com.gymlet.domain.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    /** All non-archived plans, newest first. */
    List<WorkoutPlan> findAllByUserIdAndArchivedFalseOrderByCreatedAtDesc(Long userId);

    /** Everything, including archived — shown collapsed in the UI. */
    List<WorkoutPlan> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WorkoutPlan> findByIdAndUserId(Long id, Long userId);

    /** Uniqueness check for plan names per user. */
    Optional<WorkoutPlan> findByUserIdAndNameIgnoreCase(Long userId, String name);
}
