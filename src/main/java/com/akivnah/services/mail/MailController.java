package com.akivnah.services.mail;

import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Webmail for staff: Inbox, Sent, Outbox, Drafts, Starred and Trash, with attachments and a signature. */
@Controller
@RequestMapping("/mail")
public class MailController {
    private static final Set<String> SLUGS = Set.of("inbox", "sent", "outbox", "drafts", "starred", "trash");

    private final MailService mail;
    private final UserRepository users;

    public MailController(MailService mail, UserRepository users) {
        this.mail = mail;
        this.users = users;
    }

    private UserAccount me(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    @InitBinder
    void binder(org.springframework.web.bind.WebDataBinder b) { b.setAllowedFields("to", "cc", "subject", "body", "draftId"); }

    @ModelAttribute
    void common(Authentication auth, Model model) {
        if (auth == null) return;
        UserAccount u = me(auth);
        model.addAttribute("counts", mail.counts(u));
        model.addAttribute("myEmail", u.getEmail());
        model.addAttribute("externalEnabled", mail.externalEnabled());
    }

    @GetMapping
    public String root() { return "redirect:/mail/folder/inbox"; }

    @GetMapping("/folder/{slug}")
    public String folder(@PathVariable String slug, @RequestParam(required = false) String q,
                         Authentication auth, Model model) {
        String s = slug.toLowerCase(Locale.ROOT);
        if (!SLUGS.contains(s)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        List<MailMessage> list = mail.list(me(auth), s, q);
        model.addAttribute("messages", list);
        model.addAttribute("withFiles", mail.withFiles(list));
        model.addAttribute("active", s);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("title", s.substring(0, 1).toUpperCase() + s.substring(1));
        model.addAttribute("showTo", s.equals("sent") || s.equals("outbox") || s.equals("drafts"));
        return "mail/list";
    }

    @GetMapping("/message/{id}")
    public String view(@PathVariable Long id, Authentication auth, Model model) {
        UserAccount u = me(auth);
        MailMessage m = mail.open(u, id);
        if (m.getFolder() == MailFolder.DRAFTS) return "redirect:/mail/compose/draft/" + id;
        model.addAttribute("m", m);
        model.addAttribute("files", mail.attachmentsOf(m.getId()));
        model.addAttribute("active", m.getFolder().name().toLowerCase(Locale.ROOT));
        model.addAttribute("counts", mail.counts(u)); // the unread badge may have just changed
        return "mail/view";
    }

    @GetMapping("/attachment/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication auth) {
        MailAttachment a = mail.attachment(me(auth), id);
        // always sent as a download, never rendered by the browser
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(a.getData().length)
                .body(a.getData());
    }

    // ---------- compose ----------
    private static String sig(UserAccount u) {
        String s = u.getMailSignature();
        return s == null || s.isBlank() ? "" : "\n\n-- \n" + s.trim();
    }

    @GetMapping("/compose")
    public String compose(@RequestParam(required = false) String to, @RequestParam(required = false) String subject,
                          Authentication auth, Model model) {
        ComposeForm f = new ComposeForm();
        if (to != null) f.setTo(to);
        if (subject != null) f.setSubject(subject);
        f.setBody(sig(me(auth)));
        return composePage(model, f, List.of());
    }

    @GetMapping("/compose/draft/{id}")
    public String editDraft(@PathVariable Long id, Authentication auth, Model model) {
        UserAccount u = me(auth);
        ComposeForm f = mail.draft(u, id);
        model.addAttribute("active", "drafts");
        model.addAttribute("form", f);
        model.addAttribute("files", mail.attachmentsOf(id));
        return "mail/compose";
    }

    @GetMapping("/reply/{id}")
    public String reply(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean all,
                        Authentication auth, Model model) {
        UserAccount u = me(auth);
        ComposeForm f = mail.replyTo(u, id, all);
        f.setBody(sig(u) + f.getBody());
        return composePage(model, f, List.of());
    }

    @GetMapping("/forward/{id}")
    public String forward(@PathVariable Long id, Authentication auth, Model model) {
        UserAccount u = me(auth);
        ComposeForm f = mail.forward(u, id);
        f.setBody(sig(u) + f.getBody());
        return composePage(model, f, List.of());
    }

    private String composePage(Model model, ComposeForm f, List<AttachmentInfo> files) {
        model.addAttribute("form", f);
        model.addAttribute("files", files);
        model.addAttribute("active", "compose");
        return "mail/compose";
    }

    @PostMapping("/send")
    public String send(@ModelAttribute("form") ComposeForm form,
                       @RequestParam(value = "files", required = false) List<MultipartFile> uploads,
                       @RequestParam(value = "removeAtt", required = false) List<Long> removeAtt,
                       Authentication auth, Model model, RedirectAttributes flash) {
        UserAccount u = me(auth);
        try {
            mail.send(u, form, uploads, removeAtt);
        } catch (MailRuleException e) {
            return failed(model, form, u, e.getMessage(), uploads);
        }
        flash.addFlashAttribute("message", mail.externalEnabled() ? "Message sent." : "Message sent to your colleagues.");
        return "redirect:/mail/folder/sent";
    }

    @PostMapping("/draft")
    public String saveDraft(@ModelAttribute("form") ComposeForm form,
                            @RequestParam(value = "files", required = false) List<MultipartFile> uploads,
                            @RequestParam(value = "removeAtt", required = false) List<Long> removeAtt,
                            Authentication auth, Model model, RedirectAttributes flash) {
        UserAccount u = me(auth);
        try {
            mail.saveDraft(u, form, uploads, removeAtt);
        } catch (MailRuleException e) {
            return failed(model, form, u, e.getMessage(), uploads);
        }
        flash.addFlashAttribute("message", "Draft saved.");
        return "redirect:/mail/folder/drafts";
    }

    private String failed(Model model, ComposeForm form, UserAccount u, String error, List<MultipartFile> uploads) {
        boolean hadFiles = uploads != null && uploads.stream().anyMatch(f -> f != null && !f.isEmpty());
        model.addAttribute("error", error + (hadFiles ? " Please attach your files again." : ""));
        model.addAttribute("files", form.getDraftId() == null ? List.<AttachmentInfo>of() : safeFiles(u, form.getDraftId()));
        model.addAttribute("active", "compose");
        return "mail/compose";
    }

    private List<AttachmentInfo> safeFiles(UserAccount u, Long draftId) {
        try {
            mail.get(u, draftId); // ownership check
            return mail.attachmentsOf(draftId);
        } catch (ResponseStatusException e) {
            return List.of();
        }
    }

    // ---------- settings ----------
    @GetMapping("/settings")
    public String settings(Authentication auth, Model model) {
        model.addAttribute("signature", me(auth).getMailSignature() == null ? "" : me(auth).getMailSignature());
        model.addAttribute("active", "settings");
        return "mail/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam(defaultValue = "") String signature, Authentication auth, RedirectAttributes flash) {
        UserAccount u = me(auth);
        String s = signature.replace("\r", "").trim();
        u.setMailSignature(s.length() > 500 ? s.substring(0, 500) : s);
        users.save(u);
        flash.addFlashAttribute("message", "Signature saved. It is added to new messages, replies and forwards.");
        return "redirect:/mail/folder/inbox";
    }

    // ---------- message actions ----------
    @PostMapping("/message/{id}/star")
    public String star(@PathVariable Long id, @RequestParam(defaultValue = "/mail/folder/inbox") String back, Authentication auth) {
        mail.toggleStar(me(auth), id);
        return "redirect:" + safe(back);
    }

    @PostMapping("/message/{id}/unread")
    public String unread(@PathVariable Long id, Authentication auth) {
        mail.setRead(me(auth), id, false);
        return "redirect:/mail/folder/inbox";
    }

    @PostMapping("/message/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        MailMessage m = mail.get(me(auth), id);
        boolean forever = m.getFolder() == MailFolder.TRASH;
        String back = "/mail/folder/" + (forever ? "trash" : m.getFolder().name().toLowerCase(Locale.ROOT));
        mail.delete(me(auth), id);
        flash.addFlashAttribute("message", forever ? "Message deleted permanently." : "Moved to Trash.");
        return "redirect:" + back;
    }

    @PostMapping("/message/{id}/restore")
    public String restore(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        mail.restore(me(auth), id);
        flash.addFlashAttribute("message", "Message restored.");
        return "redirect:/mail/folder/trash";
    }

    @PostMapping("/message/{id}/retry")
    public String retry(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        mail.retry(me(auth), id);
        flash.addFlashAttribute("message", "Queued again. It will be sent within a minute.");
        return "redirect:/mail/folder/outbox";
    }

    @PostMapping("/trash/empty")
    public String emptyTrash(Authentication auth, RedirectAttributes flash) {
        mail.emptyTrash(me(auth));
        flash.addFlashAttribute("message", "Trash emptied.");
        return "redirect:/mail/folder/trash";
    }

    /** Only allow redirects back into the mail section. */
    private static String safe(String back) {
        return back != null && back.startsWith("/mail/") && !back.contains("//") && !back.contains("\\") ? back : "/mail/folder/inbox";
    }
}
