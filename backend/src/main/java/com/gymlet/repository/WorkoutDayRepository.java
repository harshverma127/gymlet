package com.gymlet.repository;

import com.gymlet.domain.WorkoutDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutDayRepository extends JpaRepository<WorkoutDay, Long> {

    List<WorkoutDay> findAllByUserIdOrderByDayNumberAsc(Long userId);

    Optional<WorkoutDay> findByIdAndUserId(Long id, Long userId);

    Optional<WorkoutDay> findByUserIdAndDayNumber(Long userId, Integer dayNumber);

    /** Days of one plan, in schedule order. */
    List<WorkoutDay> findAllByUserIdAndPlanIdOrderByDayNumberAsc(Long userId, Long planId);

    Optional<WorkoutDay> findByUserIdAndPlanIdAndDayNumber(Long userId, Long planId, Integer dayNumber);

    Optional<WorkoutDay> findByUserIdAndPlanIdAndWeekday(Long userId, Long planId, Integer weekday);

    /**
     * Every row occupying one weekday of a plan, ordered by id.
     *
     * <p>The {@code Optional} finder above throws if historical data ever holds
     * two rows for the same weekday, which is exactly the corruption we repair
     * on boot. Mutations therefore use this list form and collapse duplicates
     * deterministically instead of relying on an at-most-one guarantee that was
     * never enforced for the weekday column.
     */
    List<WorkoutDay> findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(Long userId, Long planId, Integer weekday);

    List<WorkoutDay> findAllByUserIdAndPlanIdAndRestDayFalseOrderByWeekdayAscDayNumberAsc(Long userId, Long planId);

    /** Days not yet attached to any plan — pre-migration rows waiting for backfill. */
    List<WorkoutDay> findByUserIdIsNull();

    List<WorkoutDay> findByUserIdAndPlanIdIsNull(Long userId);

    /** The shared default template (used to bootstrap new accounts). */
    List<WorkoutDay> findByUserIdIsNullOrderByDayNumberAsc();

    /** Rows that have no owner yet — pre-migration legacy plan rows (claimed during migration). */
    List<WorkoutDay> findAllByUserIdIsNull();
}
