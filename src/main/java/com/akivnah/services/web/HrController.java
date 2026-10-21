package com.akivnah.services.web;

import com.akivnah.services.model.*;
import com.akivnah.services.repository.*;
import jakarta.validation.Valid;
import com.akivnah.services.mail.MailAttachmentRepository;
import com.akivnah.services.mail.MailMessageRepository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** HR console: manage employees and generate offer letters, payslips and increment letters. */
@Controller
@RequestMapping("/hr")
public class HrController {
    private final EmployeeRepository employees;
    private final OfferLetterRepository offers;
    private final PayslipRepository payslips;
    private final IncrementLetterRepository increments;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MailMessageRepository mails;
    private final MailAttachmentRepository attachments;
    private final PasswordService passwordService;

    public HrController(EmployeeRepository employees, OfferLetterRepository offers, PayslipRepository payslips,
                        IncrementLetterRepository increments, UserRepository users, PasswordEncoder encoder,
                        MailMessageRepository mails, MailAttachmentRepository attachments,
                        PasswordService passwordService) {
        this.passwordService = passwordService;
        this.mails = mails;
        this.attachments = attachments;
        this.employees = employees;
        this.offers = offers;
        this.payslips = payslips;
        this.increments = increments;
        this.users = users;
        this.encoder = encoder;
    }

    /** Never let a form set ids, owners or reference numbers. */
    @InitBinder
    void binder(WebDataBinder b) { b.setDisallowedFields("id", "employee", "user", "refNo"); }

    @ModelAttribute("months")
    List<String> months() {
        return IntStream.rangeClosed(1, 12)
                .mapToObj(m -> Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH)).toList();
    }

    private Employee employee(Long id) {
        return employees.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // ---------- employees ----------
	/*
	 * @GetMapping("/employees") public String list(Model model) {
	 * model.addAttribute("employees", employees.findAllByOrderByEmployeeCodeAsc());
	 * model.addAttribute("employee", new Employee()); return "hr/employees"; }
	 */
    @GetMapping("/employees")
    public String list(
            @RequestParam(defaultValue = "") String employeeId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        addEmployeesToModel(model, employeeId, page);
        model.addAttribute("employee", new Employee());

        return "hr/employees";
    }

    private void addEmployeesToModel(
            Model model,
            String employeeId,
            int page) {

        int safePage = Math.max(page, 0);

        Pageable pageable = PageRequest.of(
                safePage,
                10,
                Sort.by("employeeCode").ascending()
        );

        Page<Employee> employeesPage =
                employees.findByEmployeeCodeContainingIgnoreCase(
                        employeeId == null ? "" : employeeId.trim(),
                        pageable
                );

        model.addAttribute("employees", employeesPage.getContent());
        model.addAttribute("employeesPage", employeesPage);
        model.addAttribute("employeeId",
                employeeId == null ? "" : employeeId);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", employeesPage.getTotalPages());
    }

	/** One transaction: the portal login and the employee record are saved together, or neither is (H2). */
    @PostMapping("/employees")
    @Transactional
    public String create(@Valid @ModelAttribute("employee") Employee e, BindingResult result,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "EMPLOYEE") String role,
                         Authentication auth, Model model) {
        Role newRole = "HR".equalsIgnoreCase(role) ? Role.HR : Role.EMPLOYEE;
        boolean roleDenied = newRole == Role.HR && !isAdmin(auth);
        if (e.getEmployeeCode() != null) e.setEmployeeCode(e.getEmployeeCode().trim());
        if (e.getEmail() != null) e.setEmail(e.getEmail().trim());
        if (e.getEmployeeCode() != null && (employees.existsByEmployeeCode(e.getEmployeeCode()) || users.existsByUsername(e.getEmployeeCode()))) {
            result.rejectValue("employeeCode", "dup", "Employee ID already exists");
        }
        if (e.getEmail() != null && users.findByEmailIgnoreCase(e.getEmail()).isPresent()) {
            result.rejectValue("email", "dup", "Email already registered");
        }
        boolean badPassword = password.length() < 8;
        if (result.hasErrors() || badPassword || roleDenied) {
            //model.addAttribute("employees", employees.findAllByOrderByEmployeeCodeAsc());
        	 // Load employees with pagination
        	addEmployeesToModel(model, "", 0);
            model.addAttribute("passwordError", badPassword ? "Initial password must be at least 8 characters" : null);
            model.addAttribute("roleError", roleDenied ? "Only an administrator can create an HR user" : null);
            return "hr/employees";
        }
        UserAccount u = new UserAccount();
        u.setUsername(e.getEmployeeCode());
        u.setEmail(e.getEmail());
        u.setPassword(encoder.encode(password));
        u.setMustChangePassword(true);   // the employee picks their own password at first sign-in
        u.setRole(newRole);
        users.save(u);
        e.setUser(u);
        employees.save(e);
        return "redirect:/hr/employees/" + e.getId() + "?created";
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ---------- edit ----------
    @GetMapping("/employees/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("employee", employee(id));
        model.addAttribute("employeeId", id);
        return "hr/employee-edit";
    }

    @PostMapping("/employees/{id}/edit")
    @Transactional
    public String editSave(@PathVariable Long id, @Valid @ModelAttribute("employee") Employee form,
                           BindingResult result, Model model) {
        Employee e = employee(id);
        UserAccount u = e.getUser();
        String email = form.getEmail() == null ? null : form.getEmail().trim();
        if (email != null && !result.hasFieldErrors("email")
                && users.findByEmailIgnoreCase(email).filter(o -> u == null || !o.getId().equals(u.getId())).isPresent()) {
            result.rejectValue("email", "dup", "Email already registered");
        }
        if (result.hasErrors()) {
            model.addAttribute("employeeId", id);
            return "hr/employee-edit";
        }
        // the Employee ID (= portal username) never changes
        e.setFullName(form.getFullName());
        e.setEmail(email);
        e.setDesignation(form.getDesignation());
        e.setDepartment(form.getDepartment());
        e.setDateOfJoining(form.getDateOfJoining());
        e.setAnnualCtc(form.getAnnualCtc());
        e.setPanNumber(form.getPanNumber());
        e.setBankAccount(form.getBankAccount());
        employees.save(e);
        if (u != null) { u.setEmail(email); users.save(u); }
        return "redirect:/hr/employees/" + id + "?updated";
    }

    // ---------- reset password (HR / admin) ----------
    @PostMapping("/employees/{id}/reset-password")
    @Transactional
    public String resetPassword(@PathVariable Long id, Authentication auth,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        Employee e = employee(id);
        UserAccount u = e.getUser();
        if (u == null || u.getRole() == Role.ADMIN || (u.getRole() == Role.HR && !isAdmin(auth))
                || u.getUsername().equals(auth.getName())) {
            return "redirect:/hr/employees/" + id + "?noreset";
        }
        String temp = passwordService.generateTemporaryPassword();
        passwordService.setPassword(u, temp, true);   // signs the employee out everywhere; they must choose a new password
        flash.addFlashAttribute("tempPassword", temp);
        return "redirect:/hr/employees/" + id;
    }

    // ---------- delete ----------
    @PostMapping("/employees/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, Authentication auth) {
        Employee e = employee(id);
        UserAccount u = e.getUser();
        if (u != null && (u.getUsername().equals(auth.getName())
                || u.getRole() == Role.ADMIN
                || (u.getRole() == Role.HR && !isAdmin(auth)))) {
            return "redirect:/hr/employees/" + id + "?nodelete";
        }
        offers.deleteAll(offers.findByEmployeeIdOrderByIssueDateDesc(id));
        payslips.deleteAll(payslips.findByEmployeeIdOrderByPayYearDescPayMonthDesc(id));
        increments.deleteAll(increments.findByEmployeeIdOrderByEffectiveDateDesc(id));
        employees.delete(e);
        employees.flush();
        if (u != null) {            // also remove the portal login and its mailbox
            attachments.deleteAllOwnedBy(u);
            mails.deleteAllOwnedBy(u);
            users.delete(u);
        }
        return "redirect:/hr/employees?deleted";
    }

    @GetMapping("/employees/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("employee", employee(id));
        model.addAttribute("offers", offers.findByEmployeeIdOrderByIssueDateDesc(id));
        model.addAttribute("payslips", payslips.findByEmployeeIdOrderByPayYearDescPayMonthDesc(id));
        model.addAttribute("increments", increments.findByEmployeeIdOrderByEffectiveDateDesc(id));
        return "hr/employee";
    }

    // ---------- offer letters ----------
    @GetMapping("/employees/{id}/offers/new")
    public String offerForm(@PathVariable Long id, Model model) {
        Employee e = employee(id);
        OfferLetter o = new OfferLetter();
        o.setIssueDate(LocalDate.now());
        o.setJoiningDate(e.getDateOfJoining());
        o.setDesignation(e.getDesignation());
        o.setAnnualCtc(e.getAnnualCtc());
        o.setProbationMonths(6);
        o.setNoticeDays(30);
        model.addAttribute("employee", e);
        model.addAttribute("offer", o);
        return "hr/offer-form";
    }

    @PostMapping("/employees/{id}/offers")
    public String offerSave(@PathVariable Long id, @Valid @ModelAttribute("offer") OfferLetter o,
                            BindingResult result, Model model) {
        Employee e = employee(id);
        if (result.hasErrors()) {
            model.addAttribute("employee", e);
            return "hr/offer-form";
        }
        o.setEmployee(e);
        offers.save(o);
        o.setRefNo("AKV/OFR/%d/%04d".formatted(o.getIssueDate().getYear(), o.getId()));
        offers.save(o);
        return "redirect:/hr/employees/" + id;
    }

    // ---------- payslips ----------
    @GetMapping("/employees/{id}/payslips/new")
    public String payslipForm(@PathVariable Long id, Model model) {
        Payslip p = new Payslip();
        LocalDate now = LocalDate.now();
        p.setPayYear(now.getYear());
        p.setPayMonth(now.getMonthValue());
        p.setWorkingDays(now.lengthOfMonth());
        p.setDaysPaid(now.lengthOfMonth());
        model.addAttribute("employee", employee(id));
        model.addAttribute("payslip", p);
        return "hr/payslip-form";
    }

    @PostMapping("/employees/{id}/payslips")
    public String payslipSave(@PathVariable Long id, @Valid @ModelAttribute("payslip") Payslip p,
                              BindingResult result, Model model) {
        Employee e = employee(id);
        if (p.getPayYear() != null && p.getPayMonth() != null
                && payslips.existsByEmployeeIdAndPayYearAndPayMonth(id, p.getPayYear(), p.getPayMonth())) {
            result.rejectValue("payMonth", "dup", "A payslip already exists for this month");
        }
        if (p.getDaysPaid() != null && p.getWorkingDays() != null && p.getDaysPaid() > p.getWorkingDays()) {
            result.rejectValue("daysPaid", "range", "Days paid cannot exceed working days");
        }
        if (result.hasErrors()) {
            model.addAttribute("employee", e);
            return "hr/payslip-form";
        }
        p.setEmployee(e);
        payslips.save(p);
        return "redirect:/hr/employees/" + id;
    }

    // ---------- increment letters ----------
    @GetMapping("/employees/{id}/increments/new")
    public String incrementForm(@PathVariable Long id, Model model) {
        Employee e = employee(id);
        IncrementLetter i = new IncrementLetter();
        i.setIssueDate(LocalDate.now());
        i.setEffectiveDate(LocalDate.now());
        i.setOldCtc(e.getAnnualCtc());
        model.addAttribute("employee", e);
        model.addAttribute("increment", i);
        return "hr/increment-form";
    }

    @PostMapping("/employees/{id}/increments")
    public String incrementSave(@PathVariable Long id, @Valid @ModelAttribute("increment") IncrementLetter i,
                                BindingResult result, Model model) {
        Employee e = employee(id);
        if (result.hasErrors()) {
            model.addAttribute("employee", e);
            return "hr/increment-form";
        }
        i.setEmployee(e);
        increments.save(i);
        i.setRefNo("AKV/INC/%d/%04d".formatted(i.getIssueDate().getYear(), i.getId()));
        increments.save(i);
        e.setAnnualCtc(i.getNewCtc());
        if (i.getNewDesignation() != null && !i.getNewDesignation().isBlank()) {
            e.setDesignation(i.getNewDesignation());
        }
        employees.save(e);
        return "redirect:/hr/employees/" + id;
    }
}
