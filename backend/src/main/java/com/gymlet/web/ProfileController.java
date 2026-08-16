package com.gymlet.web;

import com.gymlet.service.ProfileService;
import com.gymlet.web.dto.Requests;
import com.gymlet.web.dto.StatsDtos;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public StatsDtos.ProfileDto profile() {
        return profileService.getProfile();
    }

    @PutMapping("/profile")
    public StatsDtos.ProfileDto updateProfile(@Valid @RequestBody Requests.ProfileRequest req) {
        return profileService.updateProfile(req);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        String json = profileService.exportJson();
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("gymlet-export-" + LocalDate.now() + ".json", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/data/reset")
    public Map<String, String> reset() {
        profileService.resetData();
        return Map.of("ok", "true");
    }

    @PostMapping("/demo/remove")
    public Map<String, String> removeDemo() {
        profileService.removeDemoData();
        return Map.of("ok", "true");
    }
}
