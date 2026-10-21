package com.akivnah.services.repository;

import com.akivnah.services.model.IncrementLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncrementLetterRepository extends JpaRepository<IncrementLetter, Long> {
    List<IncrementLetter> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);
}
