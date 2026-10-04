package org.Trid;

import java.io.File;

public class Attachment {
    private final String filename;
    private final File tempFile;

    public Attachment(String filename, File tempFile) {
        this.filename = filename;
        this.tempFile = tempFile;
    }

    public File getTempFile() { return tempFile; }

    @Override
    public String toString() {
        return "📎 " + filename;
    }
}