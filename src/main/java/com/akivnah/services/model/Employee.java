package com.akivnah.services.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,20}$", message = "2-20 letters, numbers, dash or underscore")
    @Column(nullable = false, unique = true, length = 20)
    private String employeeCode;
    @NotBlank @Size(max = 100)
    private String fullName;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(max = 100)
    private String designation;
    @NotBlank @Size(max = 100)
    private String department;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfJoining;
    @NotNull @DecimalMin("0.00")
    private BigDecimal annualCtc;
    @Size(max = 20)
    private String panNumber;
    @Size(max = 40)
    private String bankAccount;
    @OneToOne
    private UserAccount user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String v) { this.employeeCode = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { this.designation = v; }
    public String getDepartment() { return department; }
    public void setDepartment(String v) { this.department = v; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate v) { this.dateOfJoining = v; }
    public BigDecimal getAnnualCtc() { return annualCtc; }
    public void setAnnualCtc(BigDecimal v) { this.annualCtc = v; }
    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String v) { this.panNumber = v; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String v) { this.bankAccount = v; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount v) { this.user = v; }
}
