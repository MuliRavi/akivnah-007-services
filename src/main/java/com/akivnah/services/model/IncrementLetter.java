package com.akivnah.services.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
public class IncrementLetter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Employee employee;
    private String refNo;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issueDate;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;
    @NotNull @DecimalMin("0.00")
    private BigDecimal oldCtc;
    @NotNull @DecimalMin("0.00")
    private BigDecimal newCtc;
    @Size(max = 100)
    private String newDesignation;
    @Size(max = 500) @Column(length = 500)
    private String remarks;
    @NotBlank @Size(max = 100)
    private String signatoryName;
    @NotBlank @Size(max = 100)
    private String signatoryTitle;

    public BigDecimal getIncrementAmount() { return newCtc.subtract(oldCtc); }
    public BigDecimal getIncrementPercent() {
        if (oldCtc.signum() == 0) return BigDecimal.ZERO;
        return getIncrementAmount().multiply(BigDecimal.valueOf(100)).divide(oldCtc, 2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee v) { this.employee = v; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String v) { this.refNo = v; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate v) { this.issueDate = v; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate v) { this.effectiveDate = v; }
    public BigDecimal getOldCtc() { return oldCtc; }
    public void setOldCtc(BigDecimal v) { this.oldCtc = v; }
    public BigDecimal getNewCtc() { return newCtc; }
    public void setNewCtc(BigDecimal v) { this.newCtc = v; }
    public String getNewDesignation() { return newDesignation; }
    public void setNewDesignation(String v) { this.newDesignation = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public String getSignatoryName() { return signatoryName; }
    public void setSignatoryName(String v) { this.signatoryName = v; }
    public String getSignatoryTitle() { return signatoryTitle; }
    public void setSignatoryTitle(String v) { this.signatoryTitle = v; }
}
