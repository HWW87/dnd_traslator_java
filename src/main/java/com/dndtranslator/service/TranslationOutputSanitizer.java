package com.dndtranslator.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TranslationOutputSanitizer {

    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "aqui esta la traduccion",
            "aquí está la traducción",
            "translation:",
            "traduccion:",
            "traducción:",
            "por favor, proporcione el texto",
            "por favor proporcione el texto",
            "no se proporciona texto para traducir",
            "no translation needed",
            "no translations are needed",
            "i'm sorry",
            "im sorry",
            "lo siento",
            "as an ai",
            "como modelo de lenguaje",
            "here is the translation",
            "please provide the text",
            "no text was provided"
    );

    private static final List<String> ASSISTANT_PREFACES = List.of(
            "texto traducido:",
            "version en español:",
            "versión en español:",
            "resultado:",
            "respuesta:",
            "traduccion:",
            "traducción:",
            "sure, here's",
            "sure, here is",
            "here is the translation:",
            "aqui esta la traduccion:",
            "aquí está la traducción:",
            "translated text:"
    );

    public String sanitize(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "";
        }

        String sanitized = stripMarkdownFences(rawOutput);
        sanitized = removeAssistantPrefaces(sanitized);
        sanitized = removeForbiddenPhrases(sanitized);
        sanitized = normalizeWhitespace(sanitized);

        return isAlmostEmpty(sanitized) ? "" : sanitized;
    }

    public String removeForbiddenPhrases(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        List<String> keptLines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                keptLines.add("");
                continue;
            }

            String normalizedLine = normalizeComparable(trimmed);
            boolean dropLine = false;
            for (String phrase : FORBIDDEN_PHRASES) {
                String normalizedPhrase = normalizeComparable(phrase);
                if (normalizedLine.equals(normalizedPhrase)
                        || normalizedLine.startsWith(normalizedPhrase + ":")
                        || normalizedLine.startsWith(normalizedPhrase + " -")
                        || (normalizedLine.contains(normalizedPhrase) && normalizedLine.length() <= normalizedPhrase.length() + 24)) {
                    dropLine = true;
                    break;
                }
            }

            if (!dropLine) {
                keptLines.add(trimmed);
            }
        }

        String result = String.join("\n", keptLines);
        for (String phrase : FORBIDDEN_PHRASES) {
            result = stripLeadingPhrase(result, phrase);
        }
        return result;
    }

    public String stripMarkdownFences(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text
                .replaceAll("(?im)^```[a-z0-9_-]*\\s*$", "")
                .replace("```", "");
    }

    public String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\t\\x0B\\f ]+", " ");
        normalized = normalized.replaceAll(" *\\n *", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");

        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\\n", -1)) {
            lines.add(line.trim());
        }

        return String.join("\n", lines).trim();
    }

    public String removeAssistantPrefaces(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String sanitized = text.trim();
        boolean changed;
        do {
            changed = false;
            String normalized = normalizeComparable(sanitized);
            for (String preface : ASSISTANT_PREFACES) {
                String normalizedPreface = normalizeComparable(preface);
                if (normalized.startsWith(normalizedPreface)) {
                    sanitized = sanitized.substring(Math.min(sanitized.length(), preface.length())).trim();
                    changed = true;
                    break;
                }
            }
        } while (changed && !sanitized.isBlank());

        return sanitized;
    }

    private String stripLeadingPhrase(String text, String phrase) {
        String result = text == null ? "" : text.trim();
        String normalizedPhrase = normalizeComparable(phrase);

        while (!result.isBlank()) {
            String normalized = normalizeComparable(result);
            if (!normalized.startsWith(normalizedPhrase)) {
                break;
            }
            int cutIndex = Math.min(result.length(), phrase.length());
            result = result.substring(cutIndex).replaceFirst("^[\\s:;,.!¿?\\-]+", "").trim();
        }
        return result;
    }

    private boolean isAlmostEmpty(String text) {
        if (text == null) {
            return true;
        }
        long meaningfulChars = text.chars().filter(Character::isLetterOrDigit).count();
        return meaningfulChars < 2;
    }

    private String normalizeComparable(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }
}

