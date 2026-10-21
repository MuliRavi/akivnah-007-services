package com.akivnah.services.web;

import com.akivnah.services.mail.MailService;
import com.akivnah.services.model.ContactMessage;
import com.akivnah.services.repository.ContactMessageRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public contact form; admins read the messages at /admin/messages. */
@Controller
public class ContactController {
    private final ContactMessageRepository messages;
    private final MailService mail;

    public ContactController(ContactMessageRepository messages, MailService mail) {
        this.messages = messages;
        this.mail = mail;
    }

    @ModelAttribute("serviceOptions")
    public List<String> serviceOptions() {
        return List.of("Custom software", "Web & mobile apps", "Cloud & DevOps", "Data & AI", "UI/UX design", "Support");
    }

    @GetMapping("/contact")
    public String form(Model model) {
        model.addAttribute("form", new ContactForm());
        return "contact";
    }

    @PostMapping("/contact")
    public String submit(@Valid @ModelAttribute("form") ContactForm form, BindingResult result) {
        if (form.getWebsite() != null && !form.getWebsite().isBlank()) {
            return "redirect:/contact?sent"; // bot: pretend success, store nothing
        }
        if (result.hasErrors()) return "contact";
        ContactMessage m = new ContactMessage();
        m.setName(form.getName().trim());
        m.setEmail(form.getEmail().trim());
        m.setService(form.getService());
        m.setMessage(form.getMessage().trim());
        messages.save(m);
        mail.notifyAdmins(m.getEmail(), m.getName(), m.getService(), m.getMessage()); // also lands in the admin Inbox
        return "redirect:/contact?sent";
    }

    @GetMapping("/admin/messages")
    public String list(Model model) {
        model.addAttribute("messages", messages.findAllByOrderByCreatedAtDesc());
        return "admin/messages";
    }
}
