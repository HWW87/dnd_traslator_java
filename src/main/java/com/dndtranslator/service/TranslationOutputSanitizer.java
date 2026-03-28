package com.dndtranslator.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
                if (isMetaLine(normalizedLine, normalizedPhrase)) {
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
                if (isMetaLine(normalized, normalizedPhrase)) {
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

        List<String> phraseTokens = tokenizeComparableWords(phrase);
        if (phraseTokens.isEmpty()) {
            return -1;
        }

        List<TokenSpan> textTokens = tokenizeWithSpans(text);
        if (textTokens.size() < phraseTokens.size()) {
            return -1;
        }

        for (int i = 0; i < phraseTokens.size(); i++) {
            if (!Objects.equals(textTokens.get(i).normalized(), phraseTokens.get(i))) {
                return -1;
            }
        }

        int cutIndex = textTokens.get(phraseTokens.size() - 1).endExclusive();
        while (cutIndex < text.length()) {
            int codePoint = text.codePointAt(cutIndex);
            if (Character.isLetterOrDigit(codePoint)) {
                break;
            }
            cutIndex += Character.charCount(codePoint);
        }

        return cutIndex;
    }

    private boolean isMetaLine(String normalizedLine, String normalizedPhrase) {
        return normalizedLine.equals(normalizedPhrase)
                || normalizedLine.startsWith(normalizedPhrase + ":")
                || normalizedLine.startsWith(normalizedPhrase + " -");
    }

    private List<String> tokenizeComparableWords(String value) {
        String normalized = normalizeComparable(value);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private List<TokenSpan> tokenizeWithSpans(String text) {
        List<TokenSpan> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (!Character.isLetterOrDigit(codePoint)) {
                index += Character.charCount(codePoint);
                continue;
            }

            int start = index;
            StringBuilder rawToken = new StringBuilder();
            while (index < text.length()) {
                int innerCodePoint = text.codePointAt(index);
                if (!Character.isLetterOrDigit(innerCodePoint)) {
                    break;
                }
                rawToken.appendCodePoint(innerCodePoint);
                index += Character.charCount(innerCodePoint);
            }

            String normalizedToken = normalizeComparable(rawToken.toString());
            if (!normalizedToken.isBlank()) {
                tokens.add(new TokenSpan(normalizedToken, start, index));
            }
        }

        return tokens;
    }

    private record TokenSpan(String normalized, int startInclusive, int endExclusive) {
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