package com.gymlet.web;

import com.gymlet.service.StructureService;
import com.gymlet.web.dto.Requests;
import com.gymlet.web.dto.WorkoutDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkoutController {

    private final StructureService structureService;

    public WorkoutController(StructureService structureService) {
        this.structureService = structureService;
    }

    @GetMapping("/workout-days")
    public List<WorkoutDtos.WorkoutDayDto> workoutDays() {
        return structureService.getWorkoutDays();
    }

    @GetMapping("/workout-days/{id}")
    public WorkoutDtos.WorkoutDayDto workoutDay(@PathVariable Long id) {
        return structureService.getWorkoutDay(id);
    }

    @GetMapping("/today")
    public WorkoutDtos.TodayDto today() {
        return structureService.getToday();
    }

    @GetMapping("/exercises")
    public List<WorkoutDtos.ExerciseDto> exercises() {
        return structureService.getExercises();
    }

    @PostMapping("/exercises")
    public WorkoutDtos.ExerciseDto createExercise(@Valid @RequestBody Requests.ExerciseRequest req) {
        return structureService.createExercise(req);
    }

    @PutMapping("/exercises/{id}")
    public WorkoutDtos.ExerciseDto updateExercise(@PathVariable Long id,
                                                  @Valid @RequestBody Requests.ExerciseRequest req) {
        return structureService.updateExercise(id, req);
    }

    @DeleteMapping("/exercises/{id}")
    public void deleteExercise(@PathVariable Long id) {
        structureService.deleteExercise(id);
    }

    @PostMapping("/workout-days/{id}/exercises")
    public WorkoutDtos.WorkoutDayDto addExerciseToDay(@PathVariable Long id,
                                                      @Valid @RequestBody Requests.AddExerciseToDayRequest req) {
        return structureService.addExerciseToDay(id, req);
    }

    @PutMapping("/workout-exercises/{id}")
    public WorkoutDtos.WorkoutDayDto updateExerciseInDay(@PathVariable Long id,
                                                         @Valid @RequestBody Requests.UpdateExerciseInDayRequest req) {
        return structureService.updateExerciseInDay(id, req);
    }

    @DeleteMapping("/workout-exercises/{id}")
    public WorkoutDtos.WorkoutDayDto removeExerciseFromDay(@PathVariable Long id) {
        return structureService.removeExerciseFromDay(id);
    }
}
