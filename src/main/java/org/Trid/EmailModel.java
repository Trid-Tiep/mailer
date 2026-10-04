package org.Trid;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EmailModel {
    private long uid;
    private String subject;
    private String from;
    private Date sentDate;
    private String body;
    private String bodyType;
    private final List<Attachment> attachments = new ArrayList<>();
    private boolean seen = false;

    public boolean isSeen() { return seen; }

    public void setSeen(boolean seen) { this.seen = seen; }

    public long getUid() {return uid;}

    public void setUid(long uid) { this.uid = uid; }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public Date getSentDate() {
        return sentDate;
    }

    public void setSentDate(Date sentDate) {
        this.sentDate = sentDate;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) { this.bodyType = bodyType; }

    public List<Attachment> getAttachments() { return attachments; }

    public void addAttachment(Attachment attachment) { this.attachments.add(attachment); }

    @Override
    public String toString() {
        return subject + " (" + from + ")";
    }
}