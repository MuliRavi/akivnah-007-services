package com.akivnah.services.web;

import com.akivnah.services.model.ServiceItem;
import com.akivnah.services.repository.ServiceItemRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/services")
public class AdminServiceController {
    private final ServiceItemRepository repo;

    public AdminServiceController(ServiceItemRepository repo) { this.repo = repo; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("service", new ServiceItem());
        model.addAttribute("items", repo.findAll());
        return "admin/services";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("service", repo.findById(id).orElseThrow());
        model.addAttribute("items", repo.findAll());
        return "admin/services";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("service") ServiceItem service, BindingResult result,
                       Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("items", repo.findAll());
            return "admin/services";
        }
        repo.save(service);
        flash.addFlashAttribute("message", "Service saved.");
        return "redirect:/admin/services";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        repo.deleteById(id);
        flash.addFlashAttribute("message", "Service deleted.");
        return "redirect:/admin/services";
    }
}
