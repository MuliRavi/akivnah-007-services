package com.akivnah.services.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
public class OfferLetter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Employee employee;
    private String refNo;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issueDate;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate joiningDate;
    @NotBlank @Size(max = 100)
    private String designation;
    @NotNull @DecimalMin("0.00")
    private BigDecimal annualCtc;
    @NotNull @Min(0) @Max(36)
    private Integer probationMonths;
    @NotNull @Min(0) @Max(365)
    private Integer noticeDays;
    @NotBlank @Size(max = 100)
    private String signatoryName;
    @NotBlank @Size(max = 100)
    private String signatoryTitle;

    public BigDecimal getMonthlyCtc() {
        return annualCtc == null ? BigDecimal.ZERO : annualCtc.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee v) { this.employee = v; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String v) { this.refNo = v; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate v) { this.issueDate = v; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate v) { this.joiningDate = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { this.designation = v; }
    public BigDecimal getAnnualCtc() { return annualCtc; }
    public void setAnnualCtc(BigDecimal v) { this.annualCtc = v; }
    public Integer getProbationMonths() { return probationMonths; }
    public void setProbationMonths(Integer v) { this.probationMonths = v; }
    public Integer getNoticeDays() { return noticeDays; }
    public void setNoticeDays(Integer v) { this.noticeDays = v; }
    public String getSignatoryName() { return signatoryName; }
    public void setSignatoryName(String v) { this.signatoryName = v; }
    public String getSignatoryTitle() { return signatoryTitle; }
    public void setSignatoryTitle(String v) { this.signatoryTitle = v; }
}
