package org.Trid;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SearchQuery {
    private final List<String> fromTerms = new ArrayList<>();
    private final List<String> subjectTerms = new ArrayList<>();
    private final List<String> plainTerms = new ArrayList<>();
    private Date beforeDate = null;
    private Date afterDate = null;

    public static SearchQuery parse(String input) {
        SearchQuery q = new SearchQuery();
        if (input == null || input.trim().isEmpty()) return q;

        List<String> tokens = tokenize(input);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setLenient(false);

        for (String token : tokens) {
            String lower = token.toLowerCase();
            if (lower.startsWith("from:")) {
                q.fromTerms.add(token.substring(5).toLowerCase());
            } else if (lower.startsWith("subject:")) {
                q.subjectTerms.add(token.substring(8).toLowerCase());
            } else if (lower.startsWith("before:")) {
                try { q.beforeDate = sdf.parse(token.substring(7)); } catch (ParseException ignored) {}
            } else if (lower.startsWith("after:")) {
                try { q.afterDate = sdf.parse(token.substring(6)); } catch (ParseException ignored) {}
            } else {
                q.plainTerms.add(lower);
            }
        }
        return q;
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    public boolean isEmpty() {
        return fromTerms.isEmpty() && subjectTerms.isEmpty()
                && plainTerms.isEmpty() && beforeDate == null && afterDate == null;
    }

    public boolean matches(EmailModel email) {
        String subject = email.getSubject() != null ? email.getSubject().toLowerCase() : "";
        String from = email.getFrom() != null ? email.getFrom().toLowerCase() : "";

        for (String term : plainTerms) {
            if (!subject.contains(term) && !from.contains(term)) return false;
        }
        for (String term : fromTerms) {
            if (!from.contains(term)) return false;
        }
        for (String term : subjectTerms) {
            if (!subject.contains(term)) return false;
        }
        if (beforeDate != null) {
            if (email.getSentDate() == null || !email.getSentDate().before(beforeDate)) return false;
        }
        if (afterDate != null) {
            return email.getSentDate() != null && email.getSentDate().after(afterDate);
        }
        return true;
    }
}