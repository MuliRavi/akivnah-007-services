package com.akivnah.services.mail;

/** A user-correctable problem (unknown recipient, empty message...). The message is shown on the compose page. */
public class MailRuleException extends RuntimeException {
    public MailRuleException(String message) { super(message); }
}
