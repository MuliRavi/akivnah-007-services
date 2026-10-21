package com.akivnah.services.web;

import com.akivnah.services.model.Employee;
import com.akivnah.services.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/** Printable letters. HR/Admin can open any document; an employee only their own. */
@Controller
@RequestMapping("/documents")
public class DocumentController {
    private final OfferLetterRepository offers;
    private final PayslipRepository payslips;
    private final IncrementLetterRepository increments;

    public DocumentController(OfferLetterRepository offers, PayslipRepository payslips,
                              IncrementLetterRepository increments) {
        this.offers = offers;
        this.payslips = payslips;
        this.increments = increments;
    }

    @GetMapping("/offer/{id}")
    public String offer(@PathVariable Long id, Authentication auth, Model model) {
        var doc = offers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(doc.getEmployee(), auth);
        model.addAttribute("o", doc);
        return "documents/offer-letter";
    }

    @GetMapping("/payslip/{id}")
    public String payslip(@PathVariable Long id, Authentication auth, Model model) {
        var doc = payslips.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(doc.getEmployee(), auth);
        model.addAttribute("p", doc);
        return "documents/payslip";
    }

    @GetMapping("/increment/{id}")
    public String increment(@PathVariable Long id, Authentication auth, Model model) {
        var doc = increments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(doc.getEmployee(), auth);
        model.addAttribute("i", doc);
        return "documents/increment-letter";
    }

    private void authorize(Employee owner, Authentication auth) {
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean mine = owner.getUser() != null && owner.getUser().getUsername().equals(auth.getName());
        if (!staff && !mine) throw new AccessDeniedException("Not your document");
    }
}
