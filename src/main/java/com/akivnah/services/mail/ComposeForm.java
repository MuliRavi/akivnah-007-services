package com.akivnah.services.mail;

public class ComposeForm {
    private String to = "";
    private String cc = "";
    private String subject = "";
    private String body = "";
    private Long draftId;

    public String getTo() { return to; }
    public void setTo(String v) { this.to = v; }
    public String getCc() { return cc; }
    public void setCc(String v) { this.cc = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }
    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }
    public Long getDraftId() { return draftId; }
    public void setDraftId(Long v) { this.draftId = v; }
}
