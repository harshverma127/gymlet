package com.gymlet.web;

import com.gymlet.service.BodyWeightService;
import com.gymlet.service.StatsService;
import com.gymlet.web.dto.Requests;
import com.gymlet.web.dto.StatsDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService statsService;
    private final BodyWeightService bodyWeightService;

    public StatsController(StatsService statsService, BodyWeightService bodyWeightService) {
        this.statsService = statsService;
        this.bodyWeightService = bodyWeightService;
    }

    @GetMapping("/stats/strength")
    public List<StatsDtos.ExerciseStrengthDto> strength() {
        return statsService.strengthProgress();
    }

    @GetMapping("/stats/muscles")
    public List<StatsDtos.MuscleDto> muscles() {
        return statsService.muscleVolume();
    }

    @GetMapping("/stats/prs")
    public StatsDtos.PrSummaryDto prs() {
        return statsService.prSummary();
    }

    @GetMapping("/stats/calendar")
    public List<StatsDtos.CalendarDayDto> calendar(@RequestParam int year, @RequestParam int month) {
        return statsService.calendar(year, month);
    }

    @GetMapping("/bodyweight")
    public List<StatsDtos.BodyWeightDto> bodyWeightList() {
        return bodyWeightService.list();
    }

    @GetMapping("/bodyweight/summary")
    public StatsDtos.BodyWeightSummaryDto bodyWeightSummary() {
        return bodyWeightService.summary();
    }

    @PostMapping("/bodyweight")
    public StatsDtos.BodyWeightDto addBodyWeight(@Valid @RequestBody Requests.BodyWeightRequest req) {
        return bodyWeightService.add(req);
    }

    @DeleteMapping("/bodyweight/{id}")
    public void deleteBodyWeight(@PathVariable Long id) {
        bodyWeightService.delete(id);
    }
}
