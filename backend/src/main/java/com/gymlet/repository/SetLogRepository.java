package com.gymlet.repository;

import com.gymlet.domain.SetLog;
import com.gymlet.domain.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SetLogRepository extends JpaRepository<SetLog, Long> {

    List<SetLog> findBySession(WorkoutSession session);

    List<SetLog> findBySessionOrderBySetNumberAsc(WorkoutSession session);

    List<SetLog> findBySessionIdAndExerciseIdOrderBySetNumberAsc(Long sessionId, Long exerciseId);

    List<SetLog> findBySessionOrderByExerciseIdAscSetNumberAsc(WorkoutSession session);

    List<SetLog> findBySessionIn(List<WorkoutSession> sessions);

    void deleteBySession(WorkoutSession session);
}
