package com.akivnah.services.repository;

import com.akivnah.services.model.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByEmployeeIdOrderByPayYearDescPayMonthDesc(Long employeeId);
    boolean existsByEmployeeIdAndPayYearAndPayMonth(Long employeeId, Integer payYear, Integer payMonth);
}
