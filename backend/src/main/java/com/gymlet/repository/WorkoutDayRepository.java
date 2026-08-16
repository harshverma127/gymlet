package com.gymlet.repository;

import com.gymlet.domain.WorkoutDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkoutDayRepository extends JpaRepository<WorkoutDay, Long> {

    Optional<WorkoutDay> findByDayNumber(Integer dayNumber);
}
