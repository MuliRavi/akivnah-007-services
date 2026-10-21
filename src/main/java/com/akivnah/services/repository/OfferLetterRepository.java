package com.akivnah.services.repository;

import com.akivnah.services.model.OfferLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OfferLetterRepository extends JpaRepository<OfferLetter, Long> {
    List<OfferLetter> findByEmployeeIdOrderByIssueDateDesc(Long employeeId);
}
