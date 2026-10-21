package com.akivnah.services.web;

import com.akivnah.services.repository.ServiceItemRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Public website pages plus the logged-in dashboard. */
@Controller
public class PageController {
    private final ServiceItemRepository services;

    public PageController(ServiceItemRepository services) { this.services = services; }

    @GetMapping("/")          public String home()      { return "index"; }
    @GetMapping("/about")     public String about()     { return "about"; }
    @GetMapping("/services")  public String services()  { return "services"; }
    @GetMapping("/work")      public String work()      { return "work"; }
    @GetMapping("/careers")   public String careers()   { return "careers"; }
    @GetMapping("/insights")  public String insights()  { return "insights"; }

    @GetMapping("/catalog")
    public String catalog(Model model) {
        model.addAttribute("services", services.findByActiveTrueOrderByNameAsc());
        return "catalog";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        model.addAttribute("username", auth.getName());
        model.addAttribute("roles", auth.getAuthorities());
        model.addAttribute("services", services.findByActiveTrueOrderByNameAsc());
        return "dashboard";
    }
}
