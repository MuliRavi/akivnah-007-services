package com.akivnah.services.mail;

import com.akivnah.services.model.Role;
import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.UserRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Receives mail: reads unread messages from one company mailbox over IMAP and files each into the matching portal user's Inbox. */
@Component
@ConditionalOnProperty(name = "akivnah.mail.imap.enabled", havingValue = "true")
public class ImapPoller {
    private static final Logger log = LoggerFactory.getLogger(ImapPoller.class);
    private static final int MAX_PER_POLL = 50;

    private final MailMessageRepository mails;
    private final UserRepository users;
    private final MailService mailService;
    private final String host, username, password, defaultUser;
    private final int port;

    public ImapPoller(MailMessageRepository mails, UserRepository users, MailService mailService,
                      @Value("${akivnah.mail.imap.host}") String host,
                      @Value("${akivnah.mail.imap.port}") int port,
                      @Value("${akivnah.mail.imap.username}") String username,
                      @Value("${akivnah.mail.imap.password}") String password,
                      @Value("${akivnah.mail.imap.default-user}") String defaultUser) {
        this.mails = mails;
        this.users = users;
        this.mailService = mailService;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.defaultUser = defaultUser;
    }

    @Scheduled(fixedDelayString = "${akivnah.mail.imap.poll-ms:60000}", initialDelay = 20000)
    public void poll() {
        if (host.isBlank() || username.isBlank()) return;
        Properties p = new Properties();
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "10000");
        p.put("mail.imaps.timeout", "20000");
        try (Store store = Session.getInstance(p).getStore("imaps")) {
            store.connect(host, port, username, password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            try {
                Message[] unseen = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                int n = 0;
                for (Message msg : unseen) {
                    if (n++ >= MAX_PER_POLL) break;
                    try {
                        file(msg);
                        msg.setFlag(Flags.Flag.SEEN, true);
                    } catch (Exception e) {
                        log.warn("Could not import a message: {}", e.getMessage());
                    }
                }
            } finally {
                inbox.close(false);
            }
        } catch (Exception e) {
            log.warn("IMAP poll failed: {}", e.getMessage());
        }
    }

    private void file(Message msg) throws Exception {
        String[] ids = msg.getHeader("Message-ID");
        Address[] rec = msg.getAllRecipients();
        Address[] fromAddr = msg.getFrom();
        String from = fromAddr != null && fromAddr.length > 0 ? addr(fromAddr[0]) : "unknown";
        String subject = msg.getSubject() == null ? "(no subject)" : msg.getSubject().replaceAll("[\\r\\n]+", " ");
        String externalId = ids != null && ids.length > 0 ? ids[0]
                : "gen-" + Objects.hash(from, subject, msg.getSentDate());

        Set<UserAccount> owners = new LinkedHashSet<>();
        List<String> to = new ArrayList<>();
        if (rec != null) {
            for (Address a : rec) {
                String e = addr(a);
                to.add(e);
                users.findByEmailIgnoreCase(e).filter(u -> u.getRole() != Role.USER).ifPresent(owners::add);
            }
        }
        if (owners.isEmpty()) users.findByUsername(defaultUser).ifPresent(owners::add);

        String body = text(msg);
        List<MailService.Att> atts = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        collect(msg, atts, skipped, new long[]{0});
        if (!skipped.isEmpty()) body += "\n\n[Attachments not imported: " + String.join(", ", skipped) + "]";
        if (body.length() > MailService.MAX_BODY) body = body.substring(0, MailService.MAX_BODY);
        Date sent = msg.getSentDate();
        LocalDateTime when = sent == null ? LocalDateTime.now() : LocalDateTime.ofInstant(sent.toInstant(), ZoneId.systemDefault());

        for (UserAccount owner : owners) {
            if (mails.existsByOwnerAndExternalId(owner, externalId)) continue;
            MailMessage m = new MailMessage();
            m.setOwner(owner);
            m.setFolder(MailFolder.INBOX);
            m.setFromAddress(from.length() > 250 ? from.substring(0, 250) : from);
            String toLine = String.join(", ", to);
            m.setToAddresses(toLine.length() > 1000 ? toLine.substring(0, 1000) : toLine);
            m.setSubject(subject.length() > 200 ? subject.substring(0, 200) : subject);
            m.setBody(body);
            m.setCreatedAt(when);
            m.setExternalId(externalId.length() > 250 ? externalId.substring(0, 250) : externalId);
            m = mails.save(m);
            mailService.attach(m, atts);
        }
    }

    private static String addr(Address a) {
        return a instanceof InternetAddress ia ? ia.getAddress() : a.toString();
    }

    private static String text(Part p) throws Exception {
        if (p.isMimeType("text/plain")) return str(p.getContent());
        if (p.isMimeType("text/html")) return stripHtml(str(p.getContent()));
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part b = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(b.getDisposition())) continue;
                if (b.isMimeType("text/plain")) return str(b.getContent());
                if (b.isMimeType("multipart/*")) {
                    String t = text(b);
                    if (!t.isBlank()) return t;
                } else if (b.isMimeType("text/html") && html == null) {
                    html = stripHtml(str(b.getContent()));
                }
            }
            return html == null ? "" : html;
        }
        return "";
    }

    /** Collects file attachments (size limited; risky file types are skipped). */
    private static void collect(Part p, List<MailService.Att> out, List<String> skipped, long[] total) throws Exception {
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) collect(mp.getBodyPart(i), out, skipped, total);
            return;
        }
        String raw = p.getFileName();
        if (raw == null) return;
        String name = MailService.cleanName(MimeUtility.decodeText(raw));
        if (MailService.isBlocked(name)) { skipped.add(name + " (file type blocked)"); return; }
        if (out.size() >= MailService.MAX_FILES) { skipped.add(name + " (too many files)"); return; }
        try (java.io.InputStream in = p.getInputStream()) {
            byte[] b = in.readNBytes(MailService.MAX_FILE + 1);
            if (b.length > MailService.MAX_FILE || total[0] + b.length > MailService.MAX_TOTAL) { skipped.add(name + " (too large)"); return; }
            total[0] += b.length;
            out.add(new MailService.Att(name, MailService.cleanType(p.getContentType()), b));
        }
    }

    private static String str(Object o) { return o instanceof String s ? s : ""; }

    private static String stripHtml(String h) {
        return h.replaceAll("(?is)<(script|style).*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&amp;", "&").trim();
    }
}
