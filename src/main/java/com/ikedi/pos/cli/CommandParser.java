package com.ikedi.pos.cli;

import com.ikedi.pos.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses user terminal input into an argument list.
 *
 * <p>Handles double-quoted strings with spaces, e.g.:
 * <pre>product add "ST-RICE-02" "Delicious Rice 5kg" "Grains & Staples" "SUP-001" 7500 6500 50 10 0</pre>
 * Escaped quotes (\" and \\) inside quoted strings are supported.
 */
public final class CommandParser {

    private CommandParser() {
    }

    public static List<String> parse(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                // If closing an empty quote like "", we still want an empty string token
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0 || (i > 0 && input.charAt(i - 1) == '"')) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (escape) {
            current.append('\\');
        }

        if (inQuotes) {
            throw new ValidationException("Unclosed quotation mark in input");
        }

        if (current.length() > 0 || (!input.isEmpty() && input.charAt(input.length() - 1) == '"')) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
