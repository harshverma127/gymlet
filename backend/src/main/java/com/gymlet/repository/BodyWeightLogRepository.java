package com.gymlet.repository;

import com.gymlet.domain.BodyWeightLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyWeightLogRepository extends JpaRepository<BodyWeightLog, Long> {

    List<BodyWeightLog> findAllByUserIdOrderByDateAsc(Long userId);

    Optional<BodyWeightLog> findByUserIdAndDate(Long userId, LocalDate date);

    List<BodyWeightLog> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    List<BodyWeightLog> findByUserIdAndDateAfterOrderByDateAsc(Long userId, LocalDate from);

    Optional<BodyWeightLog> findByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);

    /** Rows that have no owner yet — pre-migration legacy entries (claimed during migration). */
    List<BodyWeightLog> findAllByUserIdIsNull();
}
