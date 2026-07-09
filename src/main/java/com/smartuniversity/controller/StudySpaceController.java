package com.smartuniversity.controller;

import com.smartuniversity.model.StudySpace;
import com.smartuniversity.model.StudySpaceReport;
import com.smartuniversity.service.StudySpaceService;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StudySpaceController {

    @Autowired
    private StudySpaceService studySpaceService;

    @Autowired
    private AuthUtils authUtils;

    // Public / authenticated student endpoints
    @GetMapping("/study-spaces")
    public ResponseEntity<List<StudySpace>> getAllSpaces() {
        return ResponseEntity.ok(studySpaceService.getAllSpaces());
    }

    @PostMapping("/study-spaces/{id}/vote")
    public ResponseEntity<?> submitReport(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        String status = request.get("status");
        if (status == null || (!status.equals("EMPTY") && !status.equals("MODERATE") && !status.equals("CROWDED"))) {
            return ResponseEntity.badRequest().body("Invalid occupancy status");
        }

        StudySpaceReport report = studySpaceService.submitReport(id, userId, status);
        return ResponseEntity.ok(report);
    }

    // Admin endpoints (Automatically protected by SecurityConfig due to "/api/admin/**" pattern)
    @PostMapping("/admin/study-spaces")
    public ResponseEntity<?> createSpace(@RequestBody StudySpace space) {
        if (space.getName() == null || space.getName().isEmpty() ||
            space.getBuilding() == null || space.getBuilding().isEmpty() ||
            space.getFloor() == null || space.getRoom() == null ||
            space.getCapacity() == null || space.getDefaultNoiseLevel() == null) {
            return ResponseEntity.badRequest().body("Missing required study space fields");
        }

        StudySpace saved = studySpaceService.createSpace(space);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/admin/study-spaces/{id}")
    public ResponseEntity<?> deleteSpace(@PathVariable Long id) {
        studySpaceService.deleteSpace(id);
        return ResponseEntity.ok().body("Study space deleted successfully");
    }
}
