package org.Trid;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import org.jsoup.Jsoup;

public class AccountPanel extends JPanel {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AccountPanel.class);
    private final String accountName;
    private final EmailService emailService;
    private final DefaultListModel<EmailModel> listModel = new DefaultListModel<>();
    private final JList<EmailModel> emailList = new JList<>(listModel);
    private final List<EmailModel> masterList = new ArrayList<>();
    private final JTextField searchField = new JTextField(15);
    private final javax.swing.Timer searchDebounce =
            new javax.swing.Timer(300, e -> reapplyFilter());
    private String currentQuery = "";
    private final JComboBox<String> folderCombo = new JComboBox<>();
    private final JTextField limitTextField;
    private final JButton fetchButton;
    private final JButton loadOlderButton;
    private final JTabbedPane detailTabPane = new JTabbedPane();
    private final JSplitPane splitPane;
    private final JLabel statusLabel;
    private int currentOffset = 0;
    private int busyOperations = 0;

    public AccountPanel(String host, String user, String password) throws Exception {
        setLayout(new BorderLayout());

        this.accountName = user;
        this.emailService = new EmailService();
        this.emailService.host = host;
        this.emailService.user = user;
        this.emailService.pass = password;

        emailService.connect(host, 993, user, password);

        emailList.setCellRenderer(new EmailCellRenderer());
        JScrollPane listScroll = new JScrollPane(emailList);

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        searchPanel.add(new JLabel("🔍"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        JButton clearSearchBtn = new JButton("✖");
        clearSearchBtn.setMargin(new Insets(0, 4, 0, 4));
        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            reapplyFilter();
        });
        searchPanel.add(clearSearchBtn, BorderLayout.EAST);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(300, 0));

        detailTabPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = detailTabPane.indexAtLocation(e.getX(), e.getY());
                    if (index != -1) detailTabPane.remove(index);
                }
            }
        });
        JScrollPane tabScroll = new JScrollPane(detailTabPane);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, tabScroll);
        splitPane.setDividerLocation(PREFS.getInt("divider." + sanitizeKey(user), 320));
        add(splitPane, BorderLayout.CENTER);

        JToolBar toolbar = new JToolBar();
        toolbar.add(new JLabel("Folder: "));
        toolbar.add(folderCombo);
        toolbar.addSeparator();
        toolbar.add(new JLabel("Count: "));

        int savedCount = PREFS.getInt("count." + sanitizeKey(user), 20);
        limitTextField = new JTextField(String.valueOf(savedCount), 5);
        toolbar.add(limitTextField);

        fetchButton = new JButton("Fetch Latest");
        toolbar.add(fetchButton);

        loadOlderButton = new JButton("Load Older (+Count)");
        loadOlderButton.setEnabled(false);
        toolbar.add(loadOlderButton);

        JButton composeBtn = new JButton("✉ Compose");
        toolbar.add(composeBtn);
        composeBtn.addActionListener(e -> {
            ComposeDialog dialog = new ComposeDialog(
                    SwingUtilities.getWindowAncestor(this), emailService);
            dialog.setVisible(true);
        });

        add(toolbar, BorderLayout.NORTH);

        statusLabel = new JLabel(" Ready. Enter count and press 'Fetch Latest'.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.SOUTH);

        emailList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            EmailModel selected = emailList.getSelectedValue();
            if (selected != null) openEmailInTab(selected.getUid());
        });
        emailList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeShowContextMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowContextMenu(e); }
        });

        fetchButton.addActionListener(e -> {
            currentOffset = 0;
            performFetch(getLimit(), 0, true);
        });
        loadOlderButton.addActionListener(e -> performFetch(getLimit(), currentOffset, false));

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
        });

        SwingUtilities.invokeLater(this::loadFolders);
    }

    public void savePreferences() {
        try {
            PREFS.putInt("divider." + sanitizeKey(accountName), splitPane.getDividerLocation());
            PREFS.putInt("count." + sanitizeKey(accountName), getLimit());
        } catch (Exception ignored) {
            // Preferences are best-effort; never block app close.
        }
    }

    private static String sanitizeKey(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void loadFolders() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return emailService.listFolders();
            }
            @Override
            protected void done() {
                try {
                    List<String> folders = get();
                    folderCombo.removeAllItems();
                    for (String f : folders) folderCombo.addItem(f);
                    folderCombo.setSelectedItem("INBOX");
                    if (folderCombo.getActionListeners().length == 0) {
                        folderCombo.addActionListener(e -> onFolderChanged());
                    }
                } catch (Exception ex) {
                    statusLabel.setText(" Failed to list folders: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onFolderChanged() {
        String selected = (String) folderCombo.getSelectedItem();
        if (selected == null || selected.isEmpty()) return;

        // Close all open email tabs (UIDs are folder-specific)
        for (int i = detailTabPane.getTabCount() - 1; i >= 0; i--) detailTabPane.remove(i);

        listModel.clear();
        masterList.clear();
        currentOffset = 0;

        busyOperations++;
        statusLabel.setText(" Loading folder...");

        int limit = getLimit();
        SwingWorker<List<EmailModel>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<EmailModel> doInBackground() throws Exception {
                emailService.switchFolder(selected);
                return emailService.fetchBatch(limit, 0);
            }
            @Override
            protected void done() {
                boolean customStatus = false;
                try {
                    List<EmailModel> fetched = get();
                    masterList.clear();
                    masterList.addAll(fetched);
                    currentOffset = fetched.size();
                    currentQuery = searchField.getText();
                    reapplyFilter();

                    int total = emailService.getTotalMessageCount();
                    if (currentOffset >= total || total == 0) {
                        loadOlderButton.setEnabled(false);
                        statusLabel.setText(" Folder: " + selected + " — " + fetched.size()
                                + " emails loaded. (No more on server)");
                        customStatus = true;
                    } else {
                        loadOlderButton.setEnabled(true);
                    }
                } catch (Exception ex) {
                    statusLabel.setText(" Failed to switch folder: " + ex.getMessage());
                    customStatus = true;
                } finally {
                    busyOperations = Math.max(0, busyOperations - 1);
                    if (busyOperations == 0 && !customStatus) reapplyFilter();
                }
            }
        };
        worker.execute();
    }

    private int getLimit() {
        try {
            int val = Integer.parseInt(limitTextField.getText().trim());
            return Math.max(1, val);
        } catch (NumberFormatException ex) {
            limitTextField.setText("20");
            return 20;
        }
    }

    private void performFetch(int limit, int offset, boolean clearList) {
        fetchButton.setEnabled(false);
        loadOlderButton.setEnabled(false);
        busyOperations++;
        statusLabel.setText(" Fetching emails...");

        SwingWorker<List<EmailModel>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<EmailModel> doInBackground() throws Exception {
                if (!emailService.isConnected()) {
                    emailService.connect(emailService.host, 993,
                            emailService.user, emailService.pass);
                }
                return emailService.fetchBatch(limit, offset);
            }

            @Override
            protected void done() {
                boolean customStatus = false;
                try {
                    List<EmailModel> fetched = get();
                    if (clearList) {
                        listModel.clear();
                        masterList.clear();
                        currentOffset = 0;
                    }
                    if (fetched.isEmpty()) {
                        statusLabel.setText(clearList ? " No emails found."
                                : " No more older emails.");
                        customStatus = true;
                    } else {
                        masterList.addAll(fetched);
                        currentOffset = offset + fetched.size();
                        currentQuery = searchField.getText();
                        reapplyFilter();

                        int total = emailService.getTotalMessageCount();
                        if (currentOffset >= total) {
                            loadOlderButton.setEnabled(false);
                            statusLabel.setText(" Showing " + masterList.size()
                                    + " loaded emails. (No more on server)");
                            customStatus = true;
                        } else {
                            loadOlderButton.setEnabled(true);
                        }
                    }
                } catch (Exception ex) {
                    statusLabel.setText(" Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Fetch failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    customStatus = true;
                } finally {
                    fetchButton.setEnabled(true);
                    busyOperations = Math.max(0, busyOperations - 1);
                    if (busyOperations == 0 && !customStatus) reapplyFilter();
                }
            }
        };
        worker.execute();
    }

    private void onSearchChanged() {
        currentQuery = searchField.getText();
        searchDebounce.restart();
    }

    private void reapplyFilter() {
        SearchQuery q = SearchQuery.parse(currentQuery);
        listModel.clear();
        for (EmailModel email : masterList) {
            if (q.isEmpty() || q.matches(email)) {
                listModel.addElement(email);
            }
        }
        if (busyOperations > 0) return;

        if (q.isEmpty()) {
            statusLabel.setText(" Showing all " + masterList.size() + " loaded emails.");
        } else {
            statusLabel.setText(" Filter: " + listModel.size() + " / "
                    + masterList.size() + " emails match.");
        }
    }

    private void updateSeenFlagInList(long uid, boolean seen) {
        for (EmailModel m : masterList) {
            if (m.getUid() == uid) {
                m.setSeen(seen);
                emailList.repaint();
                return;
            }
        }
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int index = emailList.locationToIndex(e.getPoint());
        if (index < 0) return;
        Rectangle bounds = emailList.getCellBounds(index, index);
        if (bounds == null || !bounds.contains(e.getPoint())) return;

        emailList.setSelectedIndex(index);
        EmailModel selected = emailList.getModel().getElementAt(index);
        buildContextMenu(selected).show(emailList, e.getX(), e.getY());
    }

    private JPopupMenu buildContextMenu(EmailModel email) {
        long uid = email.getUid();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem replyItem = new JMenuItem("↩ Reply");
        replyItem.addActionListener(e -> withFullEmail(uid, this::openReplyDialog));
        menu.add(replyItem);

        JMenuItem forwardItem = new JMenuItem("↪ Forward");
        forwardItem.addActionListener(e -> withFullEmail(uid, this::openForwardDialog));
        menu.add(forwardItem);

        JMenuItem saveItem = new JMenuItem("💾 Save Email");
        saveItem.addActionListener(e -> withFullEmail(uid, full -> saveEmail(full, this)));
        menu.add(saveItem);

        menu.addSeparator();

        JMenuItem markUnreadItem = new JMenuItem("✉ Mark Unread");
        markUnreadItem.addActionListener(e -> performMarkUnread(uid));
        menu.add(markUnreadItem);

        JMenuItem deleteItem = new JMenuItem("🗑 Delete");
        deleteItem.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Move this email to Trash?", "Delete Email",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.OK_OPTION) performDelete(uid);
        });
        menu.add(deleteItem);

        return menu;
    }

    private void withFullEmail(long uid, Consumer<EmailModel> action) {
        busyOperations++;
        statusLabel.setText(" Loading email…");

        SwingWorker<EmailModel, Void> worker = new SwingWorker<>() {
            @Override
            protected EmailModel doInBackground() throws Exception {
                return emailService.fetchFullEmail(uid);
            }
            @Override
            protected void done() {
                try {
                    EmailModel full = get();
                    if (full != null) action.accept(full);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Failed to load email:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    busyOperations = Math.max(0, busyOperations - 1);
                    if (busyOperations == 0) reapplyFilter();
                }
            }
        };
        worker.execute();
    }

    private void openEmailInTab(long uid) {
        for (int i = 0; i < detailTabPane.getTabCount(); i++) {
            Component c = detailTabPane.getComponentAt(i);
            if (c instanceof JPanel) {
                Long u = (Long) ((JPanel) c).getClientProperty("emailUid");
                if (u != null && u == uid) {
                    detailTabPane.setSelectedIndex(i);
                    return;
                }
            }
        }

        String subject = "Loading…";
        for (EmailModel m : masterList) {
            if (m.getUid() == uid) { subject = m.getSubject(); break; }
        }
        String tabTitle = subject.length() > 20 ? subject.substring(0, 20) + "…" : subject;

        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.putClientProperty("emailUid", uid);
        JLabel loadingLabel = new JLabel("Loading email…", SwingConstants.CENTER);
        loadingLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        placeholder.add(loadingLabel, BorderLayout.CENTER);

        int tabIndex = detailTabPane.getTabCount();
        detailTabPane.addTab(tabTitle, placeholder);
        detailTabPane.setToolTipTextAt(tabIndex, "Double-click to close this email");
        detailTabPane.setSelectedIndex(tabIndex);

        busyOperations++;
        statusLabel.setText(" Loading email…");

        SwingWorker<EmailModel, Void> worker = new SwingWorker<>() {
            @Override
            protected EmailModel doInBackground() throws Exception {
                return emailService.fetchFullEmail(uid);
            }
            @Override
            protected void done() {
                try {
                    EmailModel full = get();
                    if (full == null) {
                        loadingLabel.setText("(Email not found)");
                        return;
                    }

                    if (!full.isSeen()) {
                        SwingWorker<Void, Void> markWorker = new SwingWorker<>() {
                            @Override
                            protected Void doInBackground() throws Exception {
                                emailService.markAsRead(uid);
                                return null;
                            }
                            @Override
                            protected void done() {
                                updateSeenFlagInList(uid, true);
                            }
                        };
                        markWorker.execute();
                    }

                    JPanel detailPanel = buildEmailDetailPanel(full, uid);

                    int idx = -1;
                    for (int i = 0; i < detailTabPane.getTabCount(); i++) {
                        if (detailTabPane.getComponentAt(i) == placeholder) { idx = i; break; }
                    }
                    if (idx != -1) {
                        detailTabPane.setComponentAt(idx, detailPanel);
                        String newTitle = full.getSubject() != null && full.getSubject().length() > 20
                                ? full.getSubject().substring(0, 20) + "…"
                                : (full.getSubject() != null ? full.getSubject() : "(No subject)");
                        detailTabPane.setTitleAt(idx, newTitle);
                    }
                } catch (Exception ex) {
                    loadingLabel.setText("Failed to load: " + ex.getMessage());
                    loadingLabel.setForeground(Color.RED);
                } finally {
                    busyOperations = Math.max(0, busyOperations - 1);
                    if (busyOperations == 0) reapplyFilter();
                }
            }
        };
        worker.execute();
    }

    private JPanel buildEmailDetailPanel(EmailModel full, long uid) {
        JPanel detailPanel = new JPanel(new BorderLayout(10, 10));
        detailPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        detailPanel.putClientProperty("emailUid", uid);

        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JTextArea headerText = new JTextArea();
        headerText.setEditable(false);
        headerText.setBackground(detailPanel.getBackground());
        headerText.setText("From: " + full.getFrom()
                + "\nDate: " + DateFormatter.format(full.getSentDate())
                + "\nSubject: " + full.getSubject());
        headerPanel.add(headerText, BorderLayout.CENTER);
        headerPanel.add(buildButtonPanel(full, uid), BorderLayout.EAST);

        detailPanel.add(headerPanel, BorderLayout.NORTH);

        JEditorPane bodyArea = new JEditorPane();
        bodyArea.setEditable(false);
        bodyArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        bodyArea.setFont(new Font("SansSerif", Font.PLAIN, 14));

        String body = full.getBody() != null ? full.getBody() : "(No body)";
        String htmlContent;
        if ("text/html".equals(full.getBodyType())) {
            htmlContent = body;
        } else {
            htmlContent = "<html><pre>" + convertUrlsToHtml(body) + "</pre></html>";
        }
        bodyArea.setContentType("text/html");
        bodyArea.setText(htmlContent);

        bodyArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(detailPanel,
                            "Could not open link: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

        if (!full.getAttachments().isEmpty()) {
            JPanel attachmentsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            attachmentsPanel.setBorder(BorderFactory.createTitledBorder("Attachments"));
            for (Attachment att : full.getAttachments()) {
                JButton openBtn = new JButton(att.toString());
                openBtn.addActionListener(e -> {
                    try {
                        File file = att.getTempFile();
                        if (file.exists()) Desktop.getDesktop().open(file);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(detailPanel,
                                "Could not open attachment: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
                attachmentsPanel.add(openBtn);
            }
            centerPanel.add(attachmentsPanel, BorderLayout.SOUTH);
        }

        detailPanel.add(centerPanel, BorderLayout.CENTER);
        return detailPanel;
    }

    private JPanel buildButtonPanel(EmailModel full, long uid) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton replyButton = new JButton("↩ Reply");
        JButton forwardButton = new JButton("↪ Forward");
        JButton saveButton = new JButton("💾 Save Email");
        JButton markUnreadButton = new JButton("✉ Mark Unread");
        JButton moveButton = new JButton("📁 Move");
        JButton deleteButton = new JButton("🗑 Delete");

        replyButton.addActionListener(e -> openReplyDialog(full));
        forwardButton.addActionListener(e -> openForwardDialog(full));
        saveButton.addActionListener(e -> saveEmail(full, this));
        markUnreadButton.addActionListener(e -> performMarkUnread(uid));
        moveButton.addActionListener(e -> openMoveDialog(uid, full));
        deleteButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Move this email to Trash?", "Delete Email",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.OK_OPTION) performDelete(uid);
        });

        panel.add(replyButton);
        panel.add(forwardButton);
        panel.add(saveButton);
        panel.add(markUnreadButton);
        panel.add(moveButton);
        panel.add(deleteButton);
        return panel;
    }

    private void performMarkUnread(long uid) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                emailService.markAsUnread(uid);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    updateSeenFlagInList(uid, false);
                    statusLabel.setText(" Marked as unread.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Failed to mark as unread:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performDelete(long uid) {
        statusLabel.setText(" Deleting email...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                emailService.deleteEmail(uid);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    removeEmailFromUI(uid);
                    statusLabel.setText(" Email moved to Trash.");
                } catch (Exception ex) {
                    statusLabel.setText(" Delete failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Failed to delete email:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performMove(long uid, String targetFolder) {
        statusLabel.setText(" Moving email to " + targetFolder + "...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                emailService.moveEmail(uid, targetFolder);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    removeEmailFromUI(uid);
                    statusLabel.setText(" Email moved to " + targetFolder + ".");
                } catch (Exception ex) {
                    statusLabel.setText(" Move failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Failed to move email:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void removeEmailFromUI(long uid) {
        for (int i = 0; i < masterList.size(); i++) {
            if (masterList.get(i).getUid() == uid) {
                masterList.remove(i);
                break;
            }
        }
        reapplyFilter();

        for (int i = detailTabPane.getTabCount() - 1; i >= 0; i--) {
            Component c = detailTabPane.getComponentAt(i);
            if (c instanceof JPanel) {
                Long u = (Long) ((JPanel) c).getClientProperty("emailUid");
                if (u != null && u == uid) {
                    detailTabPane.remove(i);
                    break;
                }
            }
        }
    }

    private void openReplyDialog(EmailModel email) {
        String to = email.getFrom() != null ? email.getFrom() : "";
        String originalSubject = email.getSubject() != null ? email.getSubject() : "";
        String subject = originalSubject.toLowerCase().startsWith("re:")
                ? originalSubject : "Re: " + originalSubject;

        StringBuilder body = new StringBuilder();
        body.append("\n\n");
        body.append("On ").append(DateFormatter.format(email.getSentDate()))
                .append(", ").append(email.getFrom()).append(" wrote:\n");
        for (String line : extractReadableBody(email).split("\n")) {
            body.append("> ").append(line).append("\n");
        }

        ComposeDialog dialog = new ComposeDialog(
                SwingUtilities.getWindowAncestor(this), emailService,
                to, "", "", subject, body.toString(), null, null, null);
        dialog.setVisible(true);
    }

    private void openForwardDialog(EmailModel email) {
        String originalSubject = email.getSubject() != null ? email.getSubject() : "";
        String lower = originalSubject.toLowerCase();
        String subject = (lower.startsWith("fwd:") || lower.startsWith("fw:"))
                ? originalSubject : "Fwd: " + originalSubject;

        String body = "\n\n"
                + "---------- Forwarded message ----------\n"
                + "From: " + email.getFrom() + "\n"
                + "Date: " + DateFormatter.format(email.getSentDate()) + "\n"
                + "Subject: " + email.getSubject() + "\n\n"
                + extractReadableBody(email);

        List<File> forwardAttachments = new ArrayList<>();
        for (Attachment att : email.getAttachments()) {
            if (att.getTempFile() != null && att.getTempFile().exists()) {
                forwardAttachments.add(att.getTempFile());
            }
        }

        ComposeDialog dialog = new ComposeDialog(
                SwingUtilities.getWindowAncestor(this), emailService,
                "", "", "", subject, body, forwardAttachments, null, null);
        dialog.setVisible(true);
    }

    private void openMoveDialog(long uid, EmailModel email) {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return emailService.listFolders();
            }
            @Override
            protected void done() {
                try {
                    List<String> folders = get();
                    String current = (String) folderCombo.getSelectedItem();
                    folders.removeIf(f -> f.equalsIgnoreCase(current));

                    if (folders.isEmpty()) {
                        JOptionPane.showMessageDialog(AccountPanel.this,
                                "No other folders available.",
                                "Move Email", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    Object choice = JOptionPane.showInputDialog(
                            AccountPanel.this,
                            "Move \"" + email.getSubject() + "\" to:",
                            "Move Email", JOptionPane.QUESTION_MESSAGE,
                            null, folders.toArray(), folders.getFirst());

                    if (choice == null) return;
                    performMove(uid, choice.toString());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Failed to list folders:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void saveEmail(EmailModel email, Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select folder to save email");
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File selectedDir = chooser.getSelectedFile();
        if (selectedDir == null || !selectedDir.exists()) return;

        String subject = email.getSubject();
        if (subject == null || subject.trim().isEmpty()) subject = "Untitled";
        String safeName = subject.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeName.length() > 200) safeName = safeName.substring(0, 200);

        File file = new File(selectedDir, safeName + ".txt");
        int counter = 1;
        while (file.exists()) {
            file = new File(selectedDir, safeName + " (" + counter + ").txt");
            counter++;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("From: " + email.getFrom());
            writer.println("Date: " + DateFormatter.format(email.getSentDate()));
            writer.println("Subject: " + email.getSubject());
            writer.println();
            writer.println("--- Message Body ---");
            writer.println();

            String body = email.getBody() != null ? email.getBody() : "(No body)";
            if ("text/html".equals(email.getBodyType())) {
                writer.println(htmlToPlainText(body));
            } else {
                writer.println(body);
            }
            writer.flush();

            JOptionPane.showMessageDialog(parent,
                    "Email saved successfully to:\n" + file.getAbsolutePath(),
                    "Save Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Error saving email: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String convertUrlsToHtml(String text) {
        if (text == null) return "";
        String urlPattern = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)"
                + "(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+"
                + "(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)"
                + "|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))";
        Pattern p = Pattern.compile(urlPattern);
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = m.group();
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
            m.appendReplacement(sb, "<a href=\"" + url + "\">" + m.group() + "</a>");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String extractReadableBody(EmailModel email) {
        String body = email.getBody() != null ? email.getBody() : "(No body)";
        if ("text/html".equals(email.getBodyType())) return htmlToPlainText(body);
        return body;
    }

    private String htmlToPlainText(String html) {
        if (html == null || html.isEmpty()) return "";
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        StringBuilder sb = new StringBuilder();
        buildText(doc.body(), sb);
        return sb.toString()
                .replaceAll("[ \\t\\xA0]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void buildText(org.jsoup.nodes.Element el, StringBuilder sb) {
        String tag = el.tagName().toLowerCase();
        if (tag.equals("br")) { sb.append('\n'); return; }
        if (tag.equals("hr")) { sb.append("\n---\n"); return; }

        boolean block = isBlockTag(tag);
        if (block && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');

        for (org.jsoup.nodes.Node child : el.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode) {
                sb.append(((org.jsoup.nodes.TextNode) child).getWholeText());
            } else if (child instanceof org.jsoup.nodes.Element) {
                buildText((org.jsoup.nodes.Element) child, sb);
            }
        }

        if (block && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
    }

    private boolean isBlockTag(String tag) {
        return switch (tag) {
            case "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
                 "li", "tr", "ul", "ol", "blockquote", "pre",
                 "section", "article", "header", "footer" -> true;
            default -> false;
        };
    }

    public void disconnect() {
        try { emailService.disconnect(); } catch (Exception ignored) {}
    }

    public String getAccountName() {
        return accountName;
    }

    private static class EmailCellRenderer extends DefaultListCellRenderer {
        private static final Color UNREAD_BG = new Color(0xE8F0FE);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof EmailModel m) {
                String dateStr = DateFormatter.format(m.getSentDate());
                boolean unread = !m.isSeen();

                if (unread && !isSelected) {
                    setBackground(UNREAD_BG);
                } else if (!isSelected) {
                    setBackground(list.getBackground());
                }
                setOpaque(true);

                String subjectOpen  = unread ? "<b>" : "";
                String subjectClose = unread ? "</b>" : "";
                String senderOpen   = unread ? "<b>" : "";
                String senderClose  = unread ? "</b>" : "";

                setText("<html>"
                        + subjectOpen + escapeHtml(m.getSubject()) + subjectClose + "<br>"
                        + "<font color='#666666' size='-1'>"
                        + senderOpen + escapeHtml(m.getFrom()) + senderClose
                        + "</font><br>"
                        + "<font color='#888888' size='-2'>" + dateStr + "</font>"
                        + "</html>");
            }
            return this;
        }

        private static String escapeHtml(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}