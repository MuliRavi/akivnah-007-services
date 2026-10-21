package com.akivnah.services.web;

import com.akivnah.services.model.Employee;
import com.akivnah.services.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Employee self-service portal: each employee sees only their own documents. */
@Controller
public class PortalController {
    private final EmployeeRepository employees;
    private final OfferLetterRepository offers;
    private final PayslipRepository payslips;
    private final IncrementLetterRepository increments;

    public PortalController(EmployeeRepository employees, OfferLetterRepository offers,
                            PayslipRepository payslips, IncrementLetterRepository increments) {
        this.employees = employees;
        this.offers = offers;
        this.payslips = payslips;
        this.increments = increments;
    }

    @GetMapping("/portal")
    public String portal(Authentication auth, Model model) {
        Employee e = employees.findByUserUsername(auth.getName()).orElse(null);
        model.addAttribute("employee", e);
        if (e != null) {
            model.addAttribute("offers", offers.findByEmployeeIdOrderByIssueDateDesc(e.getId()));
            model.addAttribute("payslips", payslips.findByEmployeeIdOrderByPayYearDescPayMonthDesc(e.getId()));
            model.addAttribute("increments", increments.findByEmployeeIdOrderByEffectiveDateDesc(e.getId()));
        }
        return "portal";
    }
}
