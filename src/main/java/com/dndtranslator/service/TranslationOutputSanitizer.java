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
            "traducción al español",
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
            "here's the translated text",
            "below is the translation",
            "please provide the text",
            "no text was provided",
            "spanish translation",
            "output:",
            "answer:"
    );

    private static final List<String> ASSISTANT_PREFACES = List.of(
            "texto traducido:",
            "version en español:",
            "versión en español:",
            "resultado:",
            "respuesta:",
            "traduccion:",
            "traducción:",
            "traducción al español:",
            "sure, here's",
            "sure, here is",
            "here is the translation:",
            "here's the translated text:",
            "below is the translation:",
            "aqui esta la traduccion:",
            "aquí está la traducción:",
            "translated text:",
            "spanish translation:"
    );

    public String sanitize(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "";
        }

        String sanitized = stripMarkdownFences(rawOutput);
        sanitized = removeLeadingMetaLines(sanitized);
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
                        || (normalizedLine.contains(normalizedPhrase)
                        && normalizedLine.length() <= normalizedPhrase.length() + 24)) {
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
            result = stripLeadingPhraseLoosely(result, phrase);
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
            for (String preface : ASSISTANT_PREFACES) {
                int cutIndex = findLoosePrefixCutIndex(sanitized, preface);
                if (cutIndex >= 0) {
                    sanitized = sanitized.substring(cutIndex).replaceFirst("^[\\s:;,.!¿?\\-]+", "").trim();
                    changed = true;
                    break;
                }
            }
        } while (changed && !sanitized.isBlank());

        return sanitized;
    }

    private String removeLeadingMetaLines(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\\r?\\n")));
        int start = 0;

        while (start < lines.size()) {
            String trimmed = lines.get(start).trim();
            if (trimmed.isEmpty()) {
                start++;
                continue;
            }

            String normalized = normalizeComparable(trimmed);
            boolean meta = false;

            for (String phrase : FORBIDDEN_PHRASES) {
                String normalizedPhrase = normalizeComparable(phrase);
                if (normalized.equals(normalizedPhrase)
                        || normalized.startsWith(normalizedPhrase + ":")
                        || normalized.startsWith(normalizedPhrase + " -")) {
                    meta = true;
                    break;
                }
            }

            if (!meta) {
                break;
            }
            start++;
        }

        return String.join("\n", lines.subList(start, lines.size()));
    }

    private String stripLeadingPhraseLoosely(String text, String phrase) {
        String result = text == null ? "" : text.trim();

        while (!result.isBlank()) {
            int cutIndex = findLoosePrefixCutIndex(result, phrase);
            if (cutIndex < 0) {
                break;
            }
            result = result.substring(cutIndex).replaceFirst("^[\\s:;,.!¿?\\-]+", "").trim();
        }

        return result;
    }

    private int findLoosePrefixCutIndex(String text, String phrase) {
        if (text == null || text.isBlank() || phrase == null || phrase.isBlank()) {
            return -1;
        }

        String normalizedText = normalizeComparable(text);
        String normalizedPhrase = normalizeComparable(phrase);

        if (!normalizedText.startsWith(normalizedPhrase)) {
            return -1;
        }

        int consumed = 0;
        int matched = 0;
        String original = text.trim();

        while (consumed < original.length() && matched < normalizedPhrase.length()) {
            char c = original.charAt(consumed);
            String normalizedChar = normalizeComparable(String.valueOf(c));

            if (!normalizedChar.isEmpty()) {
                matched += normalizedChar.length();
            }
            consumed++;
        }

        return Math.min(consumed, original.length());
    }

    private boolean isAlmostEmpty(String text) {
        if (text == null) {
            return true;
        }
        long meaningfulChars = text.chars().filter(Character::isLetterOrDigit).count();
        return meaningfulChars < 2;
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}