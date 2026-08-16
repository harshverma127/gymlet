package com.gymlet.service;

import com.gymlet.domain.BodyWeightLog;
import com.gymlet.repository.BodyWeightLogRepository;
import com.gymlet.web.dto.Requests;
import com.gymlet.web.dto.StatsDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class BodyWeightService {

    private final BodyWeightLogRepository bodyWeightRepository;
    private final UserContext userContext;

    public BodyWeightService(BodyWeightLogRepository bodyWeightRepository, UserContext userContext) {
        this.bodyWeightRepository = bodyWeightRepository;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<StatsDtos.BodyWeightDto> list() {
        return bodyWeightRepository.findAllByUserIdOrderByDateAsc(userContext.getUserId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatsDtos.BodyWeightSummaryDto summary() {
        List<BodyWeightLog> logs = bodyWeightRepository.findAllByUserIdOrderByDateAsc(userContext.getUserId());
        if (logs.isEmpty()) {
            return new StatsDtos.BodyWeightSummaryDto(null, null, null, List.of());
        }
        BodyWeightLog current = logs.get(logs.size() - 1);
        LocalDate from = LocalDate.now().minusDays(6);
        List<BodyWeightLog> week = bodyWeightRepository
                .findByUserIdAndDateBetweenOrderByDateAsc(userContext.getUserId(), from, LocalDate.now());
        double weeklyAverage = week.stream().mapToDouble(BodyWeightLog::getWeightKg).average().orElse(current.getWeightKg());
        BodyWeightLog weekStart = week.isEmpty() ? null : week.get(0);
        Double change = weekStart == null || weekStart.getDate().equals(current.getDate())
                ? null
                : round1(current.getWeightKg() - weekStart.getWeightKg());
        List<StatsDtos.BodyWeightDto> history = logs.size() > 12
                ? logs.subList(logs.size() - 12, logs.size()).stream().map(this::toDto).toList()
                : logs.stream().map(this::toDto).toList();
        return new StatsDtos.BodyWeightSummaryDto(round1(current.getWeightKg()), round1(weeklyAverage),
                change, history);
    }

    @Transactional
    public StatsDtos.BodyWeightDto add(Requests.BodyWeightRequest req) {
        LocalDate date = LocalDate.parse(req.date());
        if (req.weightKg() == null || req.weightKg() <= 0 || req.weightKg() > 500) {
            throw new IllegalArgumentException("Enter a valid weight");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date can't be in the future");
        }
        BodyWeightLog log = bodyWeightRepository
                .findByUserIdAndDate(userContext.getUserId(), date)
                .orElseGet(BodyWeightLog::new);
        log.setUserId(userContext.getUserId());
        log.setDate(date);
        log.setWeightKg(req.weightKg());
        log.setDemo(false);
        return toDto(bodyWeightRepository.save(log));
    }

    @Transactional
    public void delete(Long id) {
        BodyWeightLog log = bodyWeightRepository.findByIdAndUserId(id, userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Body weight entry not found"));
        bodyWeightRepository.delete(log);
    }

    private StatsDtos.BodyWeightDto toDto(BodyWeightLog log) {
        return new StatsDtos.BodyWeightDto(log.getId(), log.getDate().toString(), round1(log.getWeightKg()));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
