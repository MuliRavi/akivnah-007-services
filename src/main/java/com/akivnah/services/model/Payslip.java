package com.akivnah.services.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

@Entity
public class Payslip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Employee employee;
    @NotNull @Min(2000) @Max(2100)
    private Integer payYear;
    @NotNull @Min(1) @Max(12)
    private Integer payMonth;
    @NotNull @Min(0) @Max(31)
    private Integer workingDays;
    @NotNull @Min(0) @Max(31)
    private Integer daysPaid;
    @NotNull @DecimalMin("0.00") private BigDecimal basic = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") private BigDecimal hra = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") private BigDecimal specialAllowance = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") private BigDecimal providentFund = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") private BigDecimal professionalTax = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") private BigDecimal incomeTax = BigDecimal.ZERO;

    public BigDecimal getGrossEarnings() { return basic.add(hra).add(specialAllowance); }
    public BigDecimal getTotalDeductions() { return providentFund.add(professionalTax).add(incomeTax); }
    public BigDecimal getNetPay() { return getGrossEarnings().subtract(getTotalDeductions()); }
    public String getPeriodLabel() {
        return Month.of(payMonth).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + payYear;
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee v) { this.employee = v; }
    public Integer getPayYear() { return payYear; }
    public void setPayYear(Integer v) { this.payYear = v; }
    public Integer getPayMonth() { return payMonth; }
    public void setPayMonth(Integer v) { this.payMonth = v; }
    public Integer getWorkingDays() { return workingDays; }
    public void setWorkingDays(Integer v) { this.workingDays = v; }
    public Integer getDaysPaid() { return daysPaid; }
    public void setDaysPaid(Integer v) { this.daysPaid = v; }
    public BigDecimal getBasic() { return basic; }
    public void setBasic(BigDecimal v) { this.basic = v; }
    public BigDecimal getHra() { return hra; }
    public void setHra(BigDecimal v) { this.hra = v; }
    public BigDecimal getSpecialAllowance() { return specialAllowance; }
    public void setSpecialAllowance(BigDecimal v) { this.specialAllowance = v; }
    public BigDecimal getProvidentFund() { return providentFund; }
    public void setProvidentFund(BigDecimal v) { this.providentFund = v; }
    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal v) { this.professionalTax = v; }
    public BigDecimal getIncomeTax() { return incomeTax; }
    public void setIncomeTax(BigDecimal v) { this.incomeTax = v; }
}
