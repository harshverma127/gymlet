package com.gymlet.repository;

import com.gymlet.domain.BodyWeightLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BodyWeightLogRepository extends JpaRepository<BodyWeightLog, Long> {

    List<BodyWeightLog> findAllByOrderByDateAsc();

    List<BodyWeightLog> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    List<BodyWeightLog> findByDateAfterOrderByDateAsc(LocalDate from);
}
