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

    public BodyWeightService(BodyWeightLogRepository bodyWeightRepository) {
        this.bodyWeightRepository = bodyWeightRepository;
    }

    @Transactional(readOnly = true)
    public List<StatsDtos.BodyWeightDto> list() {
        return bodyWeightRepository.findAllByOrderByDateAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatsDtos.BodyWeightSummaryDto summary() {
        List<BodyWeightLog> logs = bodyWeightRepository.findAllByOrderByDateAsc();
        if (logs.isEmpty()) {
            return new StatsDtos.BodyWeightSummaryDto(null, null, null, List.of());
        }
        BodyWeightLog current = logs.get(logs.size() - 1);
        LocalDate from = LocalDate.now().minusDays(6);
        List<BodyWeightLog> week = bodyWeightRepository.findByDateBetweenOrderByDateAsc(from, LocalDate.now());
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
        BodyWeightLog log = bodyWeightRepository.findAllByOrderByDateAsc().stream()
                .filter(l -> l.getDate().equals(date))
                .findFirst()
                .orElseGet(BodyWeightLog::new);
        log.setDate(date);
        log.setWeightKg(req.weightKg());
        log.setDemo(false);
        return toDto(bodyWeightRepository.save(log));
    }

    @Transactional
    public void delete(Long id) {
        BodyWeightLog log = bodyWeightRepository.findById(id)
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
