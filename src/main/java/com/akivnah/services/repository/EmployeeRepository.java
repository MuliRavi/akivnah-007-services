package com.akivnah.services.repository;

import com.akivnah.services.model.Employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUserUsername(String username);
    boolean existsByEmployeeCode(String employeeCode);
    List<Employee> findAllByOrderByEmployeeCodeAsc();
	Page<Employee> findByEmployeeCodeContainingIgnoreCase(String trim, Pageable pageable);
}
