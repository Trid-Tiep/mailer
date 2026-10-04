package org.Trid;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EmailClient extends JFrame {
    private static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userNodeForPackage(EmailClient.class);

    private final JTabbedPane accountTabs;
    private final List<AccountPanel> accountPanels = new ArrayList<>();

    public EmailClient() {
        setTitle("Multi-Account Email Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        restoreWindowGeometry();

        accountTabs = new JTabbedPane();
        add(accountTabs, BorderLayout.CENTER);

        JToolBar toolbar = new JToolBar();
        JButton addAccountButton = new JButton("Add Account");
        toolbar.add(addAccountButton);
        add(toolbar, BorderLayout.NORTH);

        addAccountButton.addActionListener(e -> addNewAccount());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveWindowGeometry();

                for (AccountPanel ap : accountPanels) {
                    ap.savePreferences();
                }

                for (AccountPanel ap : accountPanels) {
                    ap.disconnect();
                }

                dispose();
                System.exit(0);
            }
        });

        SwingUtilities.invokeLater(this::addNewAccount);
    }

    private void restoreWindowGeometry() {
        int w = PREFS.getInt("window.width", 1000);
        int h = PREFS.getInt("window.height", 700);
        int x = PREFS.getInt("window.x", -1);
        int y = PREFS.getInt("window.y", -1);

        setSize(w, h);
        if (x >= 0 && y >= 0) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }
        if (PREFS.getBoolean("window.maximized", false)) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    private void saveWindowGeometry() {
        boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
        PREFS.putBoolean("window.maximized", maximized);

        if (!maximized) {
            PREFS.putInt("window.width", getWidth());
            PREFS.putInt("window.height", getHeight());
            PREFS.putInt("window.x", getX());
            PREFS.putInt("window.y", getY());
        }
    }

    private void addNewAccount() {
        AccountPanel panel = showLoginDialogWithReturn();
        if (panel != null) {
            accountPanels.add(panel);
            String title = panel.getAccountName();
            accountTabs.addTab(title, panel);
            accountTabs.setSelectedIndex(accountTabs.getTabCount() - 1);
        }
    }

    private AccountPanel showLoginDialogWithReturn() {
        String[] providers = {"Gmail", "Outlook", "Yahoo", "iCloud", "Other"};
        java.util.Map<String, String[]> providerData = new java.util.HashMap<>();
        providerData.put("Gmail", new String[]{"imap.gmail.com", "gmail.com"});
        providerData.put("Outlook", new String[]{"outlook.office365.com", "outlook.com"});
        providerData.put("Yahoo", new String[]{"imap.mail.yahoo.com", "yahoo.com"});
        providerData.put("iCloud", new String[]{"imap.mail.me.com", "icloud.com"});
        providerData.put("Other", new String[]{"", ""});

        JComboBox<String> providerCombo = new JComboBox<>(providers);
        JTextField hostField = new JTextField(20);
        hostField.setEditable(false);
        JTextField usernameField = new JTextField(12);
        JTextField domainField = new JTextField(12);
        domainField.setEditable(false);
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Provider:"), gbc);
        gbc.gridx = 1;
        panel.add(providerCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Email:"), gbc);
        JPanel emailPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        emailPanel.add(usernameField);
        emailPanel.add(new JLabel(" @ "));
        emailPanel.add(domainField);
        gbc.gridx = 1;
        panel.add(emailPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("IMAP Host:"), gbc);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("App Password:"), gbc);
        gbc.gridx = 1;
        panel.add(passField, gbc);

        providerCombo.addActionListener(e -> {
            String selected = (String) providerCombo.getSelectedItem();
            if (selected == null) return;
            String[] data = providerData.get(selected);
            if (data == null) return;
            String host = data[0];
            String domain = data[1];
            hostField.setText(host);
            hostField.setEditable("Other".equals(selected));
            if (!"Other".equals(selected)) {
                domainField.setText(domain);
                domainField.setEditable(false);
            } else {
                domainField.setText("");
                domainField.setEditable(true);
            }
            usernameField.requestFocusInWindow();
        });
        providerCombo.setSelectedItem("Gmail");

        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Add Email Account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option != JOptionPane.OK_OPTION) {
            return null;
        }

        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        String domain = domainField.getText().trim();
        String pass = new String(passField.getPassword()).trim();

        String email = username + "@" + domain;
        if (host.isEmpty() || username.isEmpty() || domain.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "All fields are required.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return showLoginDialogWithReturn();
        }

        try {
            return new AccountPanel(host, email, pass);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to connect: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return showLoginDialogWithReturn();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EmailClient().setVisible(true));
    }
}