package com.smartuniversity.repository;

import com.smartuniversity.model.StudySpaceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StudySpaceReportRepository extends JpaRepository<StudySpaceReport, Long> {
    List<StudySpaceReport> findBySpaceIdAndReportedAtAfter(Long spaceId, LocalDateTime time);
}
