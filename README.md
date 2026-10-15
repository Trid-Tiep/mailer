# Mailer

A lightweight, cross-platform desktop email client built with **Java Swing** and **Jakarta Mail**. It connects to any IMAP/SMTP email provider (Gmail, Outlook, Yahoo, iCloud, and more) and lets you manage multiple accounts side by side in a single window — no browser required.

---

## Features

### Multi-Account Support
- Add and switch between multiple email accounts using tabs.
- Each account maintains its own connection, folder state, search query, and open email tabs.
- Per-account preferences (divider position, fetch count) are remembered between sessions.

### Reading Mail
- **IMAP-based** — works with Gmail, Outlook, Yahoo, iCloud, and any standard IMAP server.
- Folder navigation: browse INBOX, Sent, Drafts, Trash, and any other message-holding folder.
- User-controlled batch fetching: choose how many emails to load, then click **Load Older** to fetch more.
- **HTML rendering** with clickable links; plain-text emails are converted to readable HTML with URLs auto-linked.
- **Attachments** are downloaded to a temp folder and openable with a single click.
- **Unread indicators** — unread emails are shown with a bold subject and a pale-blue background.
- Auto-mark-as-read when you open an email; a **Mark Unread** button lets you reverse it.

### Managing Mail
- **Search & filter** with keyword syntax:
  - `invoice` — matches subject or sender
  - `from:google` — filter by sender
  - `subject:"security alert"` — filter by subject (quotes allow spaces)
  - `before:2024-01-01` / `after:2025-06-01` — filter by date
  - Keywords can be combined: `from:amazon after:2025-01-01`
- **Delete** — moves emails to the provider's Trash folder (hard-deletes if already in Trash).
- **Move to folder** — pick any folder from a dropdown to file the email elsewhere.
- **Save to disk** — export an email as a readable `.txt` file (HTML stripped of markup, dates in a friendly format). Duplicate filenames are auto-numbered.

### Composing & Replying
- **Compose** new emails with To / Cc / Bcc (comma-separated, multiple recipients per field).
- **Attachments** — add and remove files via a file chooser.
- **Reply** — pre-fills recipient, subject (`Re:`), and quoted body.
- **Forward** — pre-fills subject (`Fwd:`), forwarded header block, and carries over original attachments.
- Sending uses SMTP with STARTTLS on port 587; credentials are shared with the IMAP connection.

### Usability
- **Right-click context menu** on any email: Reply, Forward, Save, Mark Unread, Delete.
- **Double-click a tab header** to close that email.
- **Status bar** with clear feedback for every operation (fetching, loading, sending, deleting…).
- **Window geometry** and split-pane dividers are saved per account via `java.util.prefs`.
- Friendly date formatting: `Today 14:30` / `Yesterday 09:12` / `Sep 3, 14:30` / `Sep 3, 2024`.

---

## Requirements

### To run the built application
- Windows 10/11 (the packaged `.exe` bundles its own JRE — nothing else needed).

### To build from source
- **JDK 21 or later** (the project uses modern switch expressions and `List.getFirst()`).
- **Maven 3.8+**.
- (Optional, for MSI installer) **WiX Toolset v3** on the `PATH`.

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/<your-repo>.git
cd <your-repo>
```

### 2. Generate an App Password

Most providers block plain passwords over IMAP. Create a one-time **App Password**:

- **Gmail** — Google Account → Security → 2-Step Verification → App Passwords → generate one for "Mail".
- **Outlook / Microsoft 365** — Security → Advanced options → App passwords.
- **Yahoo** — Account Security → Generate app password.
- **iCloud** — Apple ID → Sign-In and Security → App-Specific Passwords.

Copy the 16-character password; you'll paste it into the app.

### 3. Build and run

```bash
mvn clean package
java -jar target/email-client-<version>.jar
```

Or run directly from your IDE by executing `org.Trid.EmailClient#main`.

### 4. Log in

The login dialog appears on startup:

1. Pick your provider (Gmail, Outlook, Yahoo, iCloud, or Other).
2. The email domain and IMAP host are auto-filled. For **Other**, enter them manually.
3. Type your username (before the `@`) and paste your App Password.
4. Click **OK** — the account appears as a tab.

To add another account, click **Add Account** in the top toolbar.

---

## Usage Tips

| Action | How |
| :--- | :--- |
| Fetch newest N emails | Set the **Count** field, click **Fetch Latest** |
| Load earlier emails | Click **Load Older (+Count)** |
| Switch folders | Use the **Folder** dropdown |
| Search | Type in the 🔍 box; supports `from:`, `subject:`, `before:`, `after:` |
| Reply / Forward / Save | Click the corresponding button in an open email, or right-click the row |
| Mark unread | Button in the email detail, or via right-click |
| Delete | Moves to Trash; use the right-click menu or the button in the detail view |
| Move to folder | **Move** button in the detail view |
| Close an email tab | Double-click the tab header |

---

## Building a Distributable

The project uses the [`maven-easypackage-plugin`](https://github.com/janix520/maven-easypackage-plugin) which wraps JDK's `jpackage`.

### Portable app image

With `<type>app-image</type>` (the default in `pom.xml`):

```bash
mvn clean package
```

The output appears at `target/EmailClient/`:

```
EmailClient/
├── EmailClient.exe     ← double-click this
├── app/                ← your JAR + dependencies
└── runtime/            ← bundled JRE
```
### Windows MSI installer

Change the plugin config to:

```xml
<type>msi</type>
```

Then `mvn clean package` produces a single `.msi` that installs to `Program Files`, adds a Start Menu entry, and registers an uninstaller. Requires WiX v3 on the `PATH`.

### Application icon

Place a multi-resolution `.ico` file at `src/main/resources/icon.ico` and reference it in `pom.xml`:

```xml
<icon>${project.basedir}/src/main/resources/icon.ico</icon>
```

Convert a square PNG to ICO with [icoconvert.com](https://icoconvert.com) or ImageMagick:

```bash
magick convert icon.png -define icon:auto-resize=256,128,64,48,32,16 icon.ico
```

---

## Project Structure

```
src/main/java/org/Trid/
├── EmailClient.java         — main window, account tabs, login dialog
├── AccountPanel.java        — one account's list + detail tabs + actions
├── ComposeDialog.java       — compose / reply / forward dialog
├── EmailService.java        — IMAP + SMTP logic (Jakarta Mail)
├── EmailModel.java          — email data holder
├── Attachment.java          — attachment data holder
├── SearchQuery.java         — keyword parser & matcher
└── DateFormatter.java       — human-friendly date formatting
```

---

## Technical Notes

- **Protocols:** IMAP over SSL on port 993 (receive), SMTP with STARTTLS on port 587 (send).
- **Libraries:**
  - [Jakarta Mail 2.0.x](https://eclipse-ee4j.github.io/mail/) — IMAP & SMTP.
  - [jsoup](https://jsoup.org/) — HTML → plain-text conversion for saved emails and quoted replies.
- **Folder discovery** uses the IMAP `\Trash` special-use attribute where available, so it works with localized folder names (e.g. Gmail's `[Gmail]/Thùng rác`).
- **Gmail workaround:** On Gmail, `copyMessages` acts as a *move*, so the client tolerates `MessageRemovedException` during delete/move and refreshes the folder count afterward to keep pagination accurate.
- **Preferences** are stored via `java.util.prefs` — per OS user, per account. To reset, delete the relevant node:
  - Windows: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`
  - Linux / macOS: `~/.java/.userPrefs/`

---

## Known Limitations

- Bodies are rendered as HTML but CSS is not fully applied — some emails may look different from the web client.
- Attachments are saved to the system temp folder and cleaned up on JVM exit; they are not stored permanently.
- Reply threading relies on `In-Reply-To` / `References` headers, which are only set for replies composed within the same session (the original `Message-ID` is not currently persisted).
- No OAuth login — the app uses App Passwords. This is deliberate: it keeps the app self-contained with no browser flow.
- POP3 is not supported.

---

## Contributing

Issues and pull requests are welcome. Please open an issue first to discuss any substantial change.

---

## License

Commercial use, or any form of monetary gain from this project is strictly prohibited without explicit permission from the author. Redistributes can be created in form of forks with author's approval.

---

## Acknowledgements

Built with [Jakarta Mail](https://eclipse-ee4j.github.io/mail/), [jsoup](https://jsoup.org/), and Java Swing.
