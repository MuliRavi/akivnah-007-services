package com.akivnah.services.mail;

import com.akivnah.services.model.UserAccount;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One copy of a mail in one user's mailbox (the sender's Sent copy and each recipient's Inbox copy are separate rows). */
@Entity
public class MailMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private UserAccount owner;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MailFolder folder = MailFolder.INBOX;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private MailFolder previousFolder;
    private String fromAddress;
    @Column(length = 1000) private String toAddresses = "";
    @Column(length = 1000) private String ccAddresses = "";
    @Column(length = 1000) private String externalTo = "";
    private String subject = "";
    @Column(length = 20000) private String body = "";
    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean readFlag;
    private boolean starred;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private SendStatus sendStatus;
    @Column(length = 500) private String errorMessage;
    private int attempts;
    private String externalId;

    public String getSnippet() {
        if (body == null) return "";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }

    public Long getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public void setOwner(UserAccount v) { this.owner = v; }
    public MailFolder getFolder() { return folder; }
    public void setFolder(MailFolder v) { this.folder = v; }
    public MailFolder getPreviousFolder() { return previousFolder; }
    public void setPreviousFolder(MailFolder v) { this.previousFolder = v; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String v) { this.fromAddress = v; }
    public String getToAddresses() { return toAddresses; }
    public void setToAddresses(String v) { this.toAddresses = v; }
    public String getCcAddresses() { return ccAddresses; }
    public void setCcAddresses(String v) { this.ccAddresses = v; }
    public String getExternalTo() { return externalTo; }
    public void setExternalTo(String v) { this.externalTo = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }
    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean v) { this.readFlag = v; }
    public boolean isStarred() { return starred; }
    public void setStarred(boolean v) { this.starred = v; }
    public SendStatus getSendStatus() { return sendStatus; }
    public void setSendStatus(SendStatus v) { this.sendStatus = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { this.attempts = v; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String v) { this.externalId = v; }
}
