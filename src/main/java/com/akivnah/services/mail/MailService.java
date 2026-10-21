package com.akivnah.services.mail;

import com.akivnah.services.model.Role;
import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MailService {
    public static final int MAX_BODY = 20000;
    private static final Pattern ANGLE = Pattern.compile("<([^>]+)>");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final PageRequest PAGE = PageRequest.of(0, 100);

    public record Recipients(List<UserAccount> internal, List<String> external, List<String> unknown) {
        public boolean isEmpty() { return internal.isEmpty() && external.isEmpty(); }
        public List<String> addresses() {
            List<String> all = new ArrayList<>();
            internal.forEach(u -> all.add(u.getEmail()));
            all.addAll(external);
            return all;
        }
    }

    public record Att(String name, String type, byte[] data) {}

    public static final int MAX_FILE = 5 * 1024 * 1024;
    public static final int MAX_TOTAL = 10 * 1024 * 1024;
    public static final int MAX_FILES = 10;
    private static final Set<String> BLOCKED = Set.of("exe", "bat", "cmd", "com", "scr", "js", "vbs", "msi", "jar", "ps1", "sh", "dll", "lnk");
    private static final Pattern MIME = Pattern.compile("^[a-z0-9.+-]+/[a-z0-9.+-]+$");

    public record Counts(long unread, long outbox, long drafts, long trash) {}

    private final MailMessageRepository mails;
    private final UserRepository users;
    private final ObjectProvider<JavaMailSender> sender;
    private final MailAttachmentRepository attachments;

    public MailService(MailMessageRepository mails, UserRepository users, ObjectProvider<JavaMailSender> sender,
                       MailAttachmentRepository attachments) {
        this.attachments = attachments;
        this.mails = mails;
        this.users = users;
        this.sender = sender;
    }

    /** True when SMTP is configured, so mail to outside addresses can be sent. */
    public boolean externalEnabled() { return sender.getIfAvailable() != null; }

    public static MailFolder folderFromSlug(String slug) {
        return switch (slug.toLowerCase(Locale.ROOT)) {
            case "inbox" -> MailFolder.INBOX;
            case "sent" -> MailFolder.SENT;
            case "outbox" -> MailFolder.OUTBOX;
            case "drafts" -> MailFolder.DRAFTS;
            case "trash" -> MailFolder.TRASH;
            default -> null;
        };
    }

    // ---------- recipients ----------
    public Recipients resolve(String raw) {
        Map<Long, UserAccount> internal = new LinkedHashMap<>();
        Set<String> external = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split("[,;\\n]+")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                Matcher m = ANGLE.matcher(t);
                if (m.find()) t = m.group(1).trim();
                Optional<UserAccount> u = t.contains("@") ? users.findByEmailIgnoreCase(t) : users.findByUsername(t);
                u = u.filter(x -> x.getRole() != Role.USER); // only staff accounts have a mailbox
                if (u.isPresent()) internal.putIfAbsent(u.get().getId(), u.get());
                else if (EMAIL.matcher(t).matches()) external.add(t.toLowerCase(Locale.ROOT));
                else unknown.add(t);
            }
        }
        return new Recipients(new ArrayList<>(internal.values()), new ArrayList<>(external), unknown);
    }

    // ---------- sending ----------
    @Transactional
    public void send(UserAccount me, ComposeForm f, List<MultipartFile> files, List<Long> removeIds) {
        Recipients to = resolve(f.getTo());
        Recipients cc = resolve(f.getCc());
        List<String> unknown = new ArrayList<>(to.unknown());
        unknown.addAll(cc.unknown());
        if (!unknown.isEmpty()) throw new MailRuleException("Unknown recipient: " + String.join(", ", unknown)
                + ". Use a colleague's Employee ID or email, or a full outside email address.");
        if (to.isEmpty() && cc.isEmpty()) throw new MailRuleException("Add at least one recipient.");
        String body = f.getBody() == null ? "" : f.getBody();
        if (body.length() > MAX_BODY) throw new MailRuleException("Message is too long (max " + MAX_BODY + " characters).");

        Set<String> external = new LinkedHashSet<>(to.external());
        external.addAll(cc.external());
        if (!external.isEmpty() && !externalEnabled()) {
            throw new MailRuleException("Sending to outside addresses is not set up yet (" + String.join(", ", external)
                    + "). Ask an admin to configure SMTP, or send only to colleagues.");
        }

        // attachments: those kept from a draft plus new uploads
        MailMessage draft = null;
        if (f.getDraftId() != null) {
            draft = mails.findById(f.getDraftId())
                    .filter(d -> d.getOwner().getId().equals(me.getId()) && d.getFolder() == MailFolder.DRAFTS).orElse(null);
        }
        List<Att> atts = new ArrayList<>();
        long bytes = 0;
        List<MailAttachment> draftFiles = new ArrayList<>();
        if (draft != null) {
            draftFiles = attachments.findByMessageId(draft.getId());
            for (MailAttachment a : draftFiles) {
                if (removeIds != null && removeIds.contains(a.getId())) continue;
                atts.add(new Att(a.getFilename(), a.getContentType(), a.getData()));
                bytes += a.getSize();
            }
        }
        atts.addAll(readUploads(files, bytes, atts.size()));

        String subject = cleanSubject(f.getSubject());
        String toLine = String.join(", ", to.addresses());
        String ccLine = String.join(", ", cc.addresses());

        Map<Long, UserAccount> internal = new LinkedHashMap<>();
        to.internal().forEach(u -> internal.putIfAbsent(u.getId(), u));
        cc.internal().forEach(u -> internal.putIfAbsent(u.getId(), u));
        for (UserAccount r : internal.values()) {
            MailMessage in = build(r, MailFolder.INBOX, me.getEmail(), toLine, ccLine, subject, body);
            attach(mails.save(in), atts);
        }

        MailMessage own = build(me, external.isEmpty() ? MailFolder.SENT : MailFolder.OUTBOX,
                me.getEmail(), toLine, ccLine, subject, body);
        own.setReadFlag(true);
        if (!external.isEmpty()) {
            own.setSendStatus(SendStatus.QUEUED);
            own.setExternalTo(String.join(", ", external));
        }
        attach(mails.save(own), atts);

        if (draft != null) {
            attachments.deleteAll(draftFiles);
            mails.delete(draft);
        }
    }

    private MailMessage build(UserAccount owner, MailFolder folder, String from, String to, String cc,
                              String subject, String body) {
        MailMessage m = new MailMessage();
        m.setOwner(owner);
        m.setFolder(folder);
        m.setFromAddress(from);
        m.setToAddresses(to);
        m.setCcAddresses(cc);
        m.setSubject(subject);
        m.setBody(body);
        m.setCreatedAt(LocalDateTime.now());
        m.setReadFlag(false);
        return m;
    }

    static String cleanSubject(String s) {
        String t = s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
        if (t.isEmpty()) t = "(no subject)";
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    // ---------- drafts ----------
    @Transactional
    public MailMessage saveDraft(UserAccount me, ComposeForm f, List<MultipartFile> files, List<Long> removeIds) {
        MailMessage d = null;
        if (f.getDraftId() != null) {
            d = mails.findById(f.getDraftId())
                     .filter(x -> x.getOwner().getId().equals(me.getId()) && x.getFolder() == MailFolder.DRAFTS)
                     .orElse(null);
        }
        if (d == null) {
            d = new MailMessage();
            d.setOwner(me);
            d.setFolder(MailFolder.DRAFTS);
            d.setReadFlag(true);
        }
        d.setFromAddress(me.getEmail());
        d.setToAddresses(trim(f.getTo(), 1000));
        d.setCcAddresses(trim(f.getCc(), 1000));
        d.setSubject(trim(f.getSubject() == null ? "" : f.getSubject().replaceAll("[\\r\\n]+", " "), 200));
        d.setBody(trim(f.getBody(), MAX_BODY));
        d.setCreatedAt(LocalDateTime.now());
        d = mails.save(d);
        if (removeIds != null && !removeIds.isEmpty()) attachments.deleteByIds(removeIds, d.getId());
        attach(d, readUploads(files, attachments.totalSize(d.getId()), (int) attachments.countByMessageId(d.getId())));
        return d;
    }

    // ---------- attachments ----------
    public static boolean isBlocked(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 && BLOCKED.contains(name.substring(i + 1).toLowerCase(Locale.ROOT));
    }

    public static String cleanName(String n) {
        String s = n == null ? "" : n.replace('\\', '/');
        s = s.substring(s.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").trim();
        if (s.isEmpty()) s = "file";
        return s.length() > 150 ? s.substring(s.length() - 150) : s;
    }

    public static String cleanType(String t) {
        if (t == null) return "application/octet-stream";
        String s = t.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return MIME.matcher(s).matches() ? s : "application/octet-stream";
    }

    private List<Att> readUploads(List<MultipartFile> files, long bytesSoFar, int countSoFar) {
        List<Att> out = new ArrayList<>();
        if (files == null) return out;
        long total = bytesSoFar;
        int count = countSoFar;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            String name = cleanName(f.getOriginalFilename());
            if (isBlocked(name)) throw new MailRuleException("\"" + name + "\" is a file type that cannot be attached for safety.");
            if (f.getSize() > MAX_FILE) throw new MailRuleException("\"" + name + "\" is larger than 5 MB.");
            count++;
            total += f.getSize();
            if (count > MAX_FILES) throw new MailRuleException("You can attach at most " + MAX_FILES + " files.");
            if (total > MAX_TOTAL) throw new MailRuleException("Attachments together are larger than 10 MB.");
            try {
                out.add(new Att(name, cleanType(f.getContentType()), f.getBytes()));
            } catch (IOException e) {
                throw new MailRuleException("Could not read \"" + name + "\".");
            }
        }
        return out;
    }

    public void attach(MailMessage m, List<Att> list) {
        for (Att a : list) {
            MailAttachment x = new MailAttachment();
            x.setMessage(m);
            x.setFilename(a.name());
            x.setContentType(a.type());
            x.setSize(a.data().length);
            x.setData(a.data());
            attachments.save(x);
        }
    }

    /** Call only after the message was loaded through get()/open(), which check ownership. */
    public List<AttachmentInfo> attachmentsOf(Long messageId) { return attachments.infos(messageId); }

    public Set<Long> withFiles(List<MailMessage> list) {
        if (list.isEmpty()) return Set.of();
        return new HashSet<>(attachments.idsWithFiles(list.stream().map(MailMessage::getId).toList()));
    }

    @Transactional(readOnly = true)
    public MailAttachment attachment(UserAccount me, Long id) {
        MailAttachment a = attachments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!a.getMessage().getOwner().getId().equals(me.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return a;
    }

    // ---------- website enquiries ----------
    /** Puts a website contact-form enquiry into every admin's Inbox. Never throws, so the form always succeeds. */
    public void notifyAdmins(String visitorEmail, String visitorName, String service, String message) {
        try {
            String subject = cleanSubject("Website enquiry: " + service + " from " + visitorName);
            String body = "Name: " + visitorName + "\nEmail: " + visitorEmail + "\nInterested in: " + service + "\n\n" + message;
            for (UserAccount admin : users.findByRole(Role.ADMIN)) {
                mails.save(build(admin, MailFolder.INBOX, visitorEmail, admin.getEmail(), "", subject, body));
            }
        } catch (RuntimeException ignored) {
            // the enquiry is already saved in the contact messages list
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---------- reading ----------
    @Transactional(readOnly = true)
    public List<MailMessage> list(UserAccount me, String slug, String q) {
        if ("starred".equalsIgnoreCase(slug)) return mails.starred(me, MailFolder.TRASH, PAGE);
        MailFolder f = folderFromSlug(slug);
        if (f == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
            return mails.search(me, f, like, PAGE);
        }
        return mails.inFolder(me, f, PAGE);
    }

    /** Loads a message and checks it belongs to the user. */
    public MailMessage get(UserAccount me, Long id) {
        return mails.findById(id).filter(m -> m.getOwner().getId().equals(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public MailMessage open(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        if (!m.isReadFlag()) { m.setReadFlag(true); mails.save(m); }
        return m;
    }

    @Transactional(readOnly = true)
    public Counts counts(UserAccount me) {
        return new Counts(mails.countByOwnerAndFolderAndReadFlagFalse(me, MailFolder.INBOX),
                mails.countByOwnerAndFolder(me, MailFolder.OUTBOX),
                mails.countByOwnerAndFolder(me, MailFolder.DRAFTS),
                mails.countByOwnerAndFolder(me, MailFolder.TRASH));
    }

    // ---------- actions ----------
    @Transactional
    public void toggleStar(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        m.setStarred(!m.isStarred());
        mails.save(m);
    }

    @Transactional
    public void setRead(UserAccount me, Long id, boolean read) {
        MailMessage m = get(me, id);
        m.setReadFlag(read);
        mails.save(m);
    }

    /** First delete moves to Trash (and cancels a queued send); deleting from Trash removes it for good. */
    @Transactional
    public void delete(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        if (m.getFolder() == MailFolder.TRASH) { mails.delete(m); return; }
        m.setPreviousFolder(m.getFolder());
        m.setFolder(MailFolder.TRASH);
        m.setSendStatus(null);
        mails.save(m);
    }

    @Transactional
    public void restore(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        if (m.getFolder() != MailFolder.TRASH) return;
        MailFolder prev = m.getPreviousFolder();
        m.setFolder(prev == null ? MailFolder.INBOX : prev == MailFolder.OUTBOX ? MailFolder.DRAFTS : prev);
        m.setPreviousFolder(null);
        mails.save(m);
    }

    @Transactional
    public void emptyTrash(UserAccount me) { mails.deleteAllInFolder(me, MailFolder.TRASH); }

    @Transactional
    public void retry(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        if (m.getFolder() != MailFolder.OUTBOX) return;
        m.setSendStatus(SendStatus.QUEUED);
        m.setAttempts(0);
        m.setErrorMessage(null);
        mails.save(m);
    }

    // ---------- reply / forward ----------
    public ComposeForm replyTo(UserAccount me, Long id, boolean all) {
        MailMessage m = get(me, id);
        ComposeForm f = new ComposeForm();
        boolean mine = me.getEmail().equalsIgnoreCase(m.getFromAddress());
        f.setTo(mine ? m.getToAddresses() : m.getFromAddress());
        if (all) {
            Set<String> others = new LinkedHashSet<>();
            for (String a : (m.getToAddresses() + "," + m.getCcAddresses()).split(",")) {
                String t = a.trim();
                if (!t.isEmpty() && !t.equalsIgnoreCase(me.getEmail()) && !t.equalsIgnoreCase(m.getFromAddress())) others.add(t);
            }
            f.setCc(String.join(", ", others));
        }
        f.setSubject(prefix("Re: ", m.getSubject()));
        f.setBody("\n\n" + quoteHeader(m) + "\n" + quote(m.getBody()));
        return f;
    }

    public ComposeForm forward(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        ComposeForm f = new ComposeForm();
        f.setSubject(prefix("Fwd: ", m.getSubject()));
        f.setBody("\n\n---------- Forwarded message ----------\nFrom: " + m.getFromAddress()
                + "\nTo: " + m.getToAddresses() + "\nDate: " + fmt(m.getCreatedAt())
                + "\nSubject: " + m.getSubject() + "\n\n" + m.getBody());
        return f;
    }

    public ComposeForm draft(UserAccount me, Long id) {
        MailMessage m = get(me, id);
        if (m.getFolder() != MailFolder.DRAFTS) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ComposeForm f = new ComposeForm();
        f.setTo(m.getToAddresses());
        f.setCc(m.getCcAddresses());
        f.setSubject(m.getSubject());
        f.setBody(m.getBody());
        f.setDraftId(m.getId());
        return f;
    }

    private static String prefix(String p, String subject) {
        String s = subject == null ? "" : subject;
        return s.regionMatches(true, 0, p, 0, p.length()) ? s : p + s;
    }
    private static String fmt(LocalDateTime t) { return t.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")); }
    private static String quoteHeader(MailMessage m) { return "On " + fmt(m.getCreatedAt()) + ", " + m.getFromAddress() + " wrote:"; }
    private static String quote(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : (body == null ? "" : body).split("\\R", -1)) sb.append("> ").append(line).append('\n');
        return sb.toString();
    }
}
