package org.Trid;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ComposeDialog extends JDialog {
    private final EmailService emailService;
    private final JTextField toField = new JTextField(30);
    private final JTextField ccField = new JTextField(30);
    private final JTextField bccField = new JTextField(30);
    private final JTextField subjectField = new JTextField(30);
    private final JTextArea bodyArea = new JTextArea(12, 40);
    private final DefaultListModel<File> attachmentModel = new DefaultListModel<>();
    private final JList<File> attachmentList = new JList<>(attachmentModel);
    private final JButton sendButton = new JButton("Send");
    private final String inReplyTo;
    private final String references;

    public ComposeDialog(Window owner, EmailService emailService) {
        this(owner, emailService, "", "", "", "", "", null, null, null);
    }

    public ComposeDialog(Window owner, EmailService emailService,
                         String prefillTo, String prefillCc, String prefillBcc,
                         String prefillSubject, String prefillBody,
                         List<File> prefillAttachments,
                         String inReplyTo, String references) {
        super(owner, "Compose Email", ModalityType.APPLICATION_MODAL);
        this.emailService = emailService;
        this.inReplyTo = inReplyTo;
        this.references = references;

        toField.setText(prefillTo != null ? prefillTo : "");
        ccField.setText(prefillCc != null ? prefillCc : "");
        bccField.setText(prefillBcc != null ? prefillBcc : "");
        subjectField.setText(prefillSubject != null ? prefillSubject : "");
        bodyArea.setText(prefillBody != null ? prefillBody : "");

        if (prefillAttachments != null) {
            for (File f : prefillAttachments) {
                if (f != null && f.exists()) {
                    attachmentModel.addElement(f);
                }
            }
        }

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("To:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        fieldsPanel.add(toField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("Cc:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        fieldsPanel.add(ccField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("Bcc:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        fieldsPanel.add(bccField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        fieldsPanel.add(subjectField, gbc);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createTitledBorder("Message"));

        JPanel attachmentsPanel = new JPanel(new BorderLayout(5, 5));
        attachmentsPanel.setBorder(BorderFactory.createTitledBorder("Attachments"));

        attachmentList.setVisibleRowCount(4);
        JScrollPane attachScroll = new JScrollPane(attachmentList);
        attachmentsPanel.add(attachScroll, BorderLayout.CENTER);

        JPanel attachButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addAttachBtn = new JButton("➕ Add");
        JButton removeAttachBtn = new JButton("➖ Remove");
        attachButtonsPanel.add(addAttachBtn);
        attachButtonsPanel.add(removeAttachBtn);
        attachmentsPanel.add(attachButtonsPanel, BorderLayout.SOUTH);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        bottomPanel.add(cancelButton);
        bottomPanel.add(sendButton);

        setLayout(new BorderLayout(10, 10));
        add(fieldsPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(bodyScroll, BorderLayout.CENTER);
        centerPanel.add(attachmentsPanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        addAttachBtn.addActionListener(e -> addAttachment());
        removeAttachBtn.addActionListener(e -> removeAttachment());
        cancelButton.addActionListener(e -> dispose());
        sendButton.addActionListener(e -> performSend());

        pack();
        setLocationRelativeTo(owner);
    }

    private void addAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Choose files to attach");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                attachmentModel.addElement(f);
            }
        }
    }

    private void removeAttachment() {
        int[] indices = attachmentList.getSelectedIndices();
        // Remove from the end to avoid index shifting
        for (int i = indices.length - 1; i >= 0; i--) {
            attachmentModel.remove(indices[i]);
        }
    }

    private void performSend() {
        String to = toField.getText().trim();
        String cc = ccField.getText().trim();
        String bcc = bccField.getText().trim();
        String subject = subjectField.getText().trim();
        String body = bodyArea.getText();

        if (to.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter at least one recipient in the To field.",
                    "Missing Recipient", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (subject.isEmpty()) {
            subject = "(No subject)";
        }

        List<File> attachments = new ArrayList<>();
        for (int i = 0; i < attachmentModel.size(); i++) {
            attachments.add(attachmentModel.get(i));
        }

        sendButton.setEnabled(false);
        final String finalSubject = subject;
        final String finalCc = cc;
        final String finalBcc = bcc;
        final String finalInReplyTo = inReplyTo;
        final String finalReferences = references;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                emailService.sendEmail(to, finalCc, finalBcc, finalSubject, body,
                        attachments, finalInReplyTo, finalReferences);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ComposeDialog.this,
                            "Email sent successfully.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ComposeDialog.this,
                            "Failed to send email:\n" + ex.getMessage(),
                            "Send Error", JOptionPane.ERROR_MESSAGE);
                    sendButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}