package com.gymlet.web;

import com.gymlet.service.SessionService;
import com.gymlet.web.dto.WorkoutDtos;
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
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Starts today's workout (one click). Creates the session + all set rows, pre-filled from last time. */
    @PostMapping
    public WorkoutDtos.SessionDto startToday() {
        return sessionService.startToday();
    }

    @GetMapping
    public List<WorkoutDtos.HistoryItemDto> history() {
        return sessionService.getHistory();
    }

    @GetMapping("/{id}")
    public WorkoutDtos.SessionDto getSession(@PathVariable Long id) {
        return sessionService.getSession(id);
    }

    @PutMapping("/{sessionId}/sets/{setId}")
    public WorkoutDtos.SessionDto updateSet(@PathVariable Long sessionId,
                                            @PathVariable Long setId,
                                            @RequestBody WorkoutDtos.SetUpdateRequest req) {
        return sessionService.updateSet(sessionId, setId, req);
    }

    @PostMapping("/{sessionId}/notes/{exerciseId}")
    public WorkoutDtos.SessionDto upsertNote(@PathVariable Long sessionId,
                                             @PathVariable Long exerciseId,
                                             @RequestBody WorkoutDtos.NoteRequest req) {
        return sessionService.upsertNote(sessionId, exerciseId, req);
    }

    @PostMapping("/{id}/finish")
    public WorkoutDtos.FinishSummaryDto finish(@PathVariable Long id,
                                               @RequestBody(required = false) WorkoutDtos.FinishRequest req) {
        return sessionService.finish(id, req == null ? new WorkoutDtos.FinishRequest(null) : req);
    }

    @DeleteMapping("/{id}")
    public void deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
    }
}
