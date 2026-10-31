package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.FaceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, Long> {
    List<FaceTemplate> findByUserIdOrderBySampleIndex(Long userId);
    void deleteByUserId(Long userId);
}
