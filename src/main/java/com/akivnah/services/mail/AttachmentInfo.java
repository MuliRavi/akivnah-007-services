package com.akivnah.services.mail;

/** Attachment details without the file bytes, for lists. */
public class AttachmentInfo {
    private final Long id;
    private final String filename;
    private final long size;

    public AttachmentInfo(Long id, String filename, long size) {
        this.id = id;
        this.filename = filename;
        this.size = size;
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public long getSize() { return size; }
    public String getSizeLabel() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1024.0 / 1024.0);
    }
}
