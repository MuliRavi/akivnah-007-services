package com.akivnah.services.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends queued mail (with attachments) from the Outbox over SMTP; on success it moves to Sent, after 5 failures it is marked FAILED. */
@Component
public class OutboxSender {
    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);
    private static final int MAX_ATTEMPTS = 5;

    private final MailMessageRepository mails;
    private final MailAttachmentRepository attachments;
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public OutboxSender(MailMessageRepository mails, MailAttachmentRepository attachments,
                        ObjectProvider<JavaMailSender> sender,
                        @Value("${akivnah.mail.from:}") String configuredFrom,
                        @Value("${spring.mail.username:}") String smtpUser) {
        this.mails = mails;
        this.attachments = attachments;
        this.sender = sender;
        this.from = !configuredFrom.isBlank() ? configuredFrom : smtpUser;
    }

    @Scheduled(fixedDelayString = "${akivnah.mail.outbox-poll-ms:30000}", initialDelay = 15000)
    public void run() {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) return;
        for (MailMessage m : mails.findByFolderAndSendStatus(MailFolder.OUTBOX, SendStatus.QUEUED)) {
            deliver(s, m);
        }
    }

    private void deliver(JavaMailSender s, MailMessage m) {
        try {
            MimeMessage mime = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, true, "UTF-8");
            if (!from.isBlank()) h.setFrom(from);
            h.setReplyTo(m.getFromAddress());
            h.setTo(m.getExternalTo().split("\\s*,\\s*"));
            h.setSubject(m.getSubject().replaceAll("[\\r\\n]+", " "));
            h.setText(m.getBody(), false);
            for (MailAttachment a : attachments.findByMessageId(m.getId())) {
                h.addAttachment(a.getFilename(), new ByteArrayResource(a.getData()), a.getContentType());
            }
            s.send(mime);
            m.setFolder(MailFolder.SENT);
            m.setSendStatus(null);
            m.setErrorMessage(null);
        } catch (Exception e) {
            m.setAttempts(m.getAttempts() + 1);
            String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            m.setErrorMessage(err.length() > 450 ? err.substring(0, 450) : err);
            if (m.getAttempts() >= MAX_ATTEMPTS) m.setSendStatus(SendStatus.FAILED);
            log.warn("Mail {} could not be sent (attempt {}): {}", m.getId(), m.getAttempts(), err);
        }
        mails.save(m);
    }
}
