package com.smartuniversity.repository;

import com.smartuniversity.model.StudySpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudySpaceRepository extends JpaRepository<StudySpace, Long> {
}
