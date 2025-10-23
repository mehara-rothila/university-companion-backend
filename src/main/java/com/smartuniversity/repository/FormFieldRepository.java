package com.smartuniversity.repository;

import com.smartuniversity.model.FormField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormFieldRepository extends JpaRepository<FormField, Long> {

    // Find all form fields for a competition, ordered by field order
    List<FormField> findByCompetitionIdOrderByOrderAsc(Long competitionId);

    // Delete all form fields for a competition
    void deleteByCompetitionId(Long competitionId);
}
