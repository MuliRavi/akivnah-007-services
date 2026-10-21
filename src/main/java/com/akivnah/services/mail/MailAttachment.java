package com.akivnah.services.mail;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A file attached to one mailbox copy of a message. Deleted by the database when the message is deleted. */
@Entity
public class MailAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MailMessage message;
    @Column(nullable = false, length = 200) private String filename;
    @Column(length = 120) private String contentType;
    private long size;
    @Lob @Column(nullable = false)
    private byte[] data;

    public Long getId() { return id; }
    public MailMessage getMessage() { return message; }
    public void setMessage(MailMessage v) { this.message = v; }
    public String getFilename() { return filename; }
    public void setFilename(String v) { this.filename = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getSize() { return size; }
    public void setSize(long v) { this.size = v; }
    public byte[] getData() { return data; }
    public void setData(byte[] v) { this.data = v; }
}
