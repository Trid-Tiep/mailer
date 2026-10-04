package org.Trid;

import com.sun.mail.imap.IMAPFolder;
import jakarta.mail.*;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EmailService {
    private Store store;
    private Folder inbox;
    private final Object lock = new Object();
    public String host, user, pass;
    private String currentFolderName = "INBOX";

    public void connect(String host, int port, String user, String password) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.port", String.valueOf(port));

        Session session = Session.getInstance(props);
        store = session.getStore("imap");
        store.connect(host, user, password);
    }

    public boolean isConnected() {
        synchronized (lock) {
            return store != null && store.isConnected();
        }
    }

    public void disconnect() throws MessagingException {
        synchronized (lock) {
            if (inbox != null && inbox.isOpen()) inbox.close(false);
            if (store != null && store.isConnected()) store.close();
            currentFolderName = "INBOX";
        }
    }

    public List<String> listFolders() throws MessagingException {
        synchronized (lock) {
            if (store == null || !store.isConnected()) {
                throw new MessagingException("Not connected.");
            }
            List<String> result = new ArrayList<>();
            Folder[] folders = store.getDefaultFolder().list("*");
            for (Folder f : folders) {
                if ((f.getType() & Folder.HOLDS_MESSAGES) != 0) {
                    result.add(f.getFullName());
                }
            }
            result.remove("INBOX");
            result.addFirst("INBOX");
            return result;
        }
    }

    public void switchFolder(String folderName) throws MessagingException {
        synchronized (lock) {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
            }
            currentFolderName = folderName;
            inbox = store.getFolder(folderName);
            inbox.open(Folder.READ_WRITE);
        }
    }

    private void ensureFolderOpen() throws MessagingException {
        if (inbox == null || !inbox.isOpen()) {
            inbox = store.getFolder(currentFolderName);
            inbox.open(Folder.READ_WRITE);
        }
    }

    private void refreshFolder() throws MessagingException {
        if (inbox != null && inbox.isOpen()) {
            inbox.close(false);
        }
        inbox = store.getFolder(currentFolderName);
        inbox.open(Folder.READ_WRITE);
    }

    public int getTotalMessageCount() throws MessagingException {
        synchronized (lock) {
            ensureFolderOpen();
            return inbox.getMessageCount();
        }
    }

    public List<EmailModel> fetchBatch(int limit, int offset) throws MessagingException {
        synchronized (lock) {
            ensureFolderOpen();

            int total = inbox.getMessageCount();
            if (total == 0) return new ArrayList<>();

            // offset = how many we've already fetched from the newest.
            int start = Math.max(1, total - offset - limit + 1);
            int end = total - offset;

            if (start > end) return new ArrayList<>();

            Message[] messages = inbox.getMessages(start, end);
            List<EmailModel> list = new ArrayList<>();

            for (int i = messages.length - 1; i >= 0; i--) {
                Message msg = messages[i];
                if (msg.isExpunged()) continue;

                long uid;
                try {
                    uid = ((UIDFolder) inbox).getUID(msg);
                } catch (NoSuchElementException e) {
                    continue;
                }

                EmailModel model = new EmailModel();
                model.setUid(uid);
                model.setSubject(msg.getSubject() != null ? msg.getSubject() : "(No subject)");
                model.setFrom(InternetAddress.toString(msg.getFrom()));
                model.setSentDate(msg.getSentDate());
                model.setSeen(msg.isSet(Flags.Flag.SEEN));
                list.add(model);
            }
            return list;
        }
    }

    public EmailModel fetchFullEmail(long uid) throws Exception {
        synchronized (lock) {
            if (store == null || !store.isConnected()) {
                throw new MessagingException("Connection lost. Please click Fetch.");
            }
            ensureFolderOpen();

            Message msg = ((UIDFolder) inbox).getMessageByUID(uid);
            if (msg == null) return null;

            EmailModel model = new EmailModel();
            model.setUid(uid);
            model.setSubject(msg.getSubject());
            model.setFrom(InternetAddress.toString(msg.getFrom()));
            model.setSentDate(msg.getSentDate());

            Object content = msg.getContent();
            if (content instanceof Multipart) {
                processMultipart((Multipart) content, model);
            } else {
                model.setBody(content.toString());
                String ct = msg.getContentType();
                if (ct != null && ct.toLowerCase().contains("html")) {
                    model.setBodyType("text/html");
                } else {
                    model.setBodyType("text/plain");
                }
            }
            return model;
        }
    }

    private void processMultipart(Multipart mp, EmailModel model) throws Exception {
        String plainText = null;
        String htmlText = null;

        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart part = mp.getBodyPart(i);
            String disposition = part.getDisposition();
            String filename = part.getFileName();

            if (filename != null
                    && disposition != null
                    && (disposition.equalsIgnoreCase(Part.ATTACHMENT)
                    || disposition.equalsIgnoreCase(Part.INLINE))) {
                saveAttachment(part, filename, model);
                continue;
            }

            if (part.isMimeType("text/plain")) {
                plainText = part.getContent().toString();
            } else if (part.isMimeType("text/html")) {
                htmlText = part.getContent().toString();
            } else if (part.getContent() instanceof Multipart) {
                processMultipart((Multipart) part.getContent(), model);
            }
        }

        if (htmlText != null) {
            model.setBody(htmlText);
            model.setBodyType("text/html");
        } else if (plainText != null) {
            model.setBody(plainText);
            model.setBodyType("text/plain");
        }
    }

    public void markAsRead(long uid) throws MessagingException {
        synchronized (lock) {
            ensureFolderOpen();
            Message msg = ((UIDFolder) inbox).getMessageByUID(uid);
            if (msg != null && !msg.isSet(Flags.Flag.SEEN)) {
                msg.setFlag(Flags.Flag.SEEN, true);
            }
        }
    }

    public void markAsUnread(long uid) throws MessagingException {
        synchronized (lock) {
            ensureFolderOpen();
            Message msg = ((UIDFolder) inbox).getMessageByUID(uid);
            if (msg != null && msg.isSet(Flags.Flag.SEEN)) {
                msg.setFlag(Flags.Flag.SEEN, false);
            }
        }
    }

    public void deleteEmail(long uid) throws MessagingException {
        synchronized (lock) {
            ensureFolderOpen();

            boolean alreadyInTrash = isTrashFolder(currentFolderName);
            Message msg = ((UIDFolder) inbox).getMessageByUID(uid);
            if (msg == null) return;

            if (!alreadyInTrash) {
                String trashName = findTrashFolderName();
                if (trashName != null) {
                    Folder trash = store.getFolder(trashName);
                    if (!trash.exists()) trash.create(Folder.HOLDS_MESSAGES);
                    trash.open(Folder.READ_WRITE);
                    try {
                        inbox.copyMessages(new Message[]{msg}, trash);
                    } finally {
                        trash.close(false);
                    }
                }
            }
            try {
                msg.setFlag(Flags.Flag.DELETED, true);
                inbox.expunge();
            } catch (MessageRemovedException ignored) {
            }

            refreshFolder();
        }
    }

    public void moveEmail(long uid, String targetFolderName) throws MessagingException {
        synchronized (lock) {
            ensureFolderOpen();

            if (targetFolderName == null || targetFolderName.isEmpty()) {
                throw new MessagingException("No target folder specified.");
            }
            if (targetFolderName.equalsIgnoreCase(currentFolderName)) {
                return;
            }

            Message msg = ((UIDFolder) inbox).getMessageByUID(uid);
            if (msg == null) return;

            Folder target = store.getFolder(targetFolderName);
            if (!target.exists()) {
                throw new MessagingException("Target folder does not exist: " + targetFolderName);
            }

            target.open(Folder.READ_WRITE);
            try {
                inbox.copyMessages(new Message[]{msg}, target);
            } finally {
                target.close(false);
            }

            try {
                msg.setFlag(Flags.Flag.DELETED, true);
                inbox.expunge();
            } catch (MessageRemovedException ignored) {
                // Already removed by the server.
            }

            refreshFolder();
        }
    }

    private String findTrashFolderName() {
        try {
            Folder[] folders = store.getDefaultFolder().list("*");
            for (Folder f : folders) {
                if (f instanceof IMAPFolder) {
                    String[] attrs = ((IMAPFolder) f).getAttributes();
                    if (attrs != null) {
                        for (String attr : attrs) {
                            if ("\\Trash".equalsIgnoreCase(attr)) {
                                return f.getFullName();
                            }
                        }
                    }
                }
            }
        } catch (MessagingException ignored) {
        }

        String[] candidates;
        String h = host != null ? host.toLowerCase() : "";
        if (h.contains("gmail")) {
            candidates = new String[]{"[Gmail]/Trash", "[Gmail]/Bin", "Trash"};
        } else if (h.contains("outlook") || h.contains("office365")) {
            candidates = new String[]{"Deleted Items", "Trash"};
        } else if (h.contains("yahoo")) {
            candidates = new String[]{"Trash"};
        } else if (h.contains("icloud") || h.contains("me.com")) {
            candidates = new String[]{"Deleted Messages", "Trash"};
        } else {
            candidates = new String[]{"Trash", "Deleted Items", "Deleted Messages"};
        }
        for (String name : candidates) {
            try {
                Folder f = store.getFolder(name);
                if (f.exists()) return name;
            } catch (MessagingException ignored) {}
        }
        return null;
    }

    private boolean isTrashFolder(String folderName) {
        if (folderName == null) return false;
        String lower = folderName.toLowerCase();
        return lower.contains("trash") || lower.contains("deleted");
    }

    private void saveAttachment(BodyPart part, String filename, EmailModel model) throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "email_attachments");
        if (!tempDir.exists()) tempDir.mkdirs();

        String safeFilename = new File(filename).getName();
        String uniqueId = System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        File tempFile = new File(tempDir, uniqueId + "_" + safeFilename);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            part.getDataHandler().writeTo(fos);
        }

        Attachment attachment = new Attachment(safeFilename, tempFile);
        model.addAttachment(attachment);
        tempFile.deleteOnExit();
    }

    public void sendEmail(String to, String cc, String bcc, String subject, String body,
                          List<File> attachments, String inReplyTo, String references) throws Exception {
        String smtpHost = getSmtpHost(host);

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(user));

        if (to != null && !to.trim().isEmpty()) {
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        }
        if (cc != null && !cc.trim().isEmpty()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }
        if (bcc != null && !bcc.trim().isEmpty()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc));
        }

        message.setSubject(subject);

        if (inReplyTo != null && !inReplyTo.isEmpty()) {
            message.setHeader("In-Reply-To", inReplyTo);
        }
        if (references != null && !references.isEmpty()) {
            message.setHeader("References", references);
        }

        if (attachments == null || attachments.isEmpty()) {
            message.setText(body);
        } else {
            message.setContent(buildMultipart(body, attachments));
        }

        Transport.send(message);
    }

    private static Multipart buildMultipart(String body, List<File> attachments)
            throws MessagingException, IOException {
        Multipart multipart = new MimeMultipart();
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(body);
        multipart.addBodyPart(bodyPart);

        for (File file : attachments) {
            MimeBodyPart attachPart = new MimeBodyPart();
            attachPart.attachFile(file);
            attachPart.setFileName(file.getName());
            attachPart.setDisposition(Part.ATTACHMENT);
            multipart.addBodyPart(attachPart);
        }
        return multipart;
    }

    private String getSmtpHost(String imapHost) {
        if (imapHost == null) return "smtp.gmail.com";
        String h = imapHost.toLowerCase();
        if (h.contains("gmail")) return "smtp.gmail.com";
        if (h.contains("outlook") || h.contains("office365")) return "smtp.office365.com";
        if (h.contains("yahoo")) return "smtp.mail.yahoo.com";
        if (h.contains("icloud") || h.contains("me.com")) return "smtp.mail.me.com";
        // Fallback: mirror the IMAP host pattern. Lowercase to keep DNS happy.
        return imapHost.replace("imap", "smtp");
    }
}