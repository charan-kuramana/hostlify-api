package com.hostlify.module.compliance.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChecklistPhotoRepository extends JpaRepository<ChecklistPhoto, Long> {
    List<ChecklistPhoto> findByChecklistIdOrderByIdAsc(Long checklistId);
    long countByChecklistId(Long checklistId);
}