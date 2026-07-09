package com.smartuniversity.service;

import com.smartuniversity.model.StudySpace;
import com.smartuniversity.model.StudySpaceReport;
import com.smartuniversity.repository.StudySpaceReportRepository;
import com.smartuniversity.repository.StudySpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StudySpaceService {

    @Autowired
    private StudySpaceRepository studySpaceRepository;

    @Autowired
    private StudySpaceReportRepository studySpaceReportRepository;

    @Transactional
    public List<StudySpace> getAllSpaces() {
        List<StudySpace> spaces = studySpaceRepository.findAll();
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);

        for (StudySpace space : spaces) {
            List<StudySpaceReport> recentReports = studySpaceReportRepository.findBySpaceIdAndReportedAtAfter(space.getId(), twoHoursAgo);

            if (!recentReports.isEmpty()) {
                // Determine majority vote
                Map<String, Long> votes = recentReports.stream()
                        .collect(Collectors.groupingBy(StudySpaceReport::getStatus, Collectors.counting()));

                String consensus = votes.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("EMPTY");

                if (!space.getStatus().equals(consensus)) {
                    space.setStatus(consensus);
                    studySpaceRepository.save(space);
                }
            }
        }
        return spaces;
    }

    @Transactional
    public StudySpace createSpace(StudySpace space) {
        return studySpaceRepository.save(space);
    }

    @Transactional
    public void deleteSpace(Long id) {
        studySpaceRepository.deleteById(id);
    }

    @Transactional
    public StudySpaceReport submitReport(Long spaceId, Long userId, String status) {
        StudySpaceReport report = new StudySpaceReport(spaceId, userId, status);
        StudySpaceReport saved = studySpaceReportRepository.save(report);

        // Recalculate and update the status of the space immediately
        StudySpace space = studySpaceRepository.findById(spaceId).orElse(null);
        if (space != null) {
            LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
            List<StudySpaceReport> recentReports = studySpaceReportRepository.findBySpaceIdAndReportedAtAfter(spaceId, twoHoursAgo);
            
            Map<String, Long> votes = recentReports.stream()
                    .collect(Collectors.groupingBy(StudySpaceReport::getStatus, Collectors.counting()));

            String consensus = votes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(status);

            space.setStatus(consensus);
            studySpaceRepository.save(space);
        }

        return saved;
    }
}
