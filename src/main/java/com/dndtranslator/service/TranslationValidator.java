package com.dndtranslator.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class TranslationValidator {

    private static final double MIN_LENGTH_RATIO_WARNING = 0.25d;
    private static final double MAX_LENGTH_RATIO_WARNING = 2.5d;
    private static final double MIN_LENGTH_RATIO_BLOCKING = 0.15d;
    private static final double MAX_LENGTH_RATIO_BLOCKING = 3.0d;
    private static final double ENGLISH_RESIDUAL_WARNING = 0.18d;
    private static final double ENGLISH_RESIDUAL_BLOCKING = 0.35d;
    private static final double GARBAGE_SYMBOL_RATIO = 0.35d;

    private static final Pattern REPEATED_CHAR_PATTERN = Pattern.compile("(.)\\1{5,}");
    private static final Pattern REPEATED_WORD_PATTERN = Pattern.compile("(?i)\\b(\\p{L}{2,})\\b(?:\\s+\\1){3,}");
    private static final Pattern MARKDOWN_FENCE_PATTERN = Pattern.compile("```[a-zA-Z0-9_-]*");

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "aqui esta la traduccion",
            "aquí está la traducción",
            "translation:",
            "traduccion:",
            "traducción:",
            "por favor proporcione el texto",
            "por favor, proporcione el texto",
            "no se proporciona texto para traducir",
            "no translation needed",
            "no translations are needed",
            "i'm sorry",
            "im sorry",
            "lo siento",
            "as an ai",
            "como modelo de lenguaje",
            "please provide the text",
            "here is the translation",
            "sure, here's",
            "sure, here is",
            "respuesta:",
            "resultado:"
    );

    private static final Set<String> ENGLISH_STOPWORDS = Set.of(
            "the", "and", "is", "are", "with", "for", "from", "this", "that",
            "you", "your", "here", "please", "translation", "text", "needed",
            "sorry", "provide", "result", "response", "of", "to", "in", "on"
    );

    private static final List<String> HALLUCINATION_MARKERS = List.of(
            "en resumen",
            "si necesitas",
            "puedo ayudarte",
            "como asistente",
            "es importante mencionar",
            "déjame saber",
            "let me know",
            "i can help",
            "i hope this helps"
    );

    public TranslationValidationResult validate(String originalText, String translatedText) {
        List<String> issues = new ArrayList<>();
        boolean blocking = false;
        boolean shouldRetry = false;
        double confidence = 1.0d;

        if (translatedText == null || translatedText.isBlank()) {
            issues.add("BLOCKING: translated text is empty");
            return new TranslationValidationResult(false, true, issues, 0.0d);
        }

        if (containsForbiddenPatterns(translatedText)) {
            issues.add("BLOCKING: forbidden meta/chatbot patterns detected");
            blocking = true;
            shouldRetry = true;
            confidence -= 0.45d;
        }

        if (containsMarkdownFences(translatedText)) {
            issues.add("BLOCKING: markdown fences detected");
            blocking = true;
            shouldRetry = true;
            confidence -= 0.35d;
        }

        if (hasGarbagePatterns(translatedText)) {
            issues.add("BLOCKING: suspicious garbage patterns detected");
            blocking = true;
            shouldRetry = true;
            confidence -= 0.40d;
        }

        double ratio = lengthRatio(originalText, translatedText);
        if (ratio < MIN_LENGTH_RATIO_BLOCKING || ratio > MAX_LENGTH_RATIO_BLOCKING) {
            issues.add(String.format(Locale.ROOT, "BLOCKING: extreme length ratio %.2f", ratio));
            blocking = true;
            shouldRetry = true;
            confidence -= 0.30d;
        } else if (hasSuspiciousLengthRatio(originalText, translatedText)) {
            issues.add(String.format(Locale.ROOT, "WARNING: suspicious length ratio %.2f", ratio));
            confidence -= 0.12d;
        }

        double englishResidual = englishResidualRatio(translatedText);
        if (englishResidual >= ENGLISH_RESIDUAL_BLOCKING) {
            issues.add(String.format(Locale.ROOT, "BLOCKING: too much residual English %.2f", englishResidual));
            blocking = true;
            shouldRetry = true;
            confidence -= 0.25d;
        } else if (englishResidual >= ENGLISH_RESIDUAL_WARNING) {
            issues.add(String.format(Locale.ROOT, "WARNING: residual English %.2f", englishResidual));
            confidence -= 0.08d;
        }

        if (looksHallucinatory(originalText, translatedText)) {
            issues.add("BLOCKING: hallucination heuristic triggered");
            blocking = true;
            shouldRetry = true;
            confidence -= 0.25d;
        }

        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        boolean valid = !blocking;
        return new TranslationValidationResult(valid, shouldRetry, List.copyOf(issues), confidence);
    }

    public boolean containsForbiddenPatterns(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        String comparable = normalizeComparable(text);
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (comparable.contains(normalizeComparable(pattern))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTooMuchResidualEnglish(String text) {
        return englishResidualRatio(text) >= ENGLISH_RESIDUAL_BLOCKING;
    }

    public boolean hasSuspiciousLengthRatio(String originalText, String translatedText) {
        double ratio = lengthRatio(originalText, translatedText);
        return ratio < MIN_LENGTH_RATIO_WARNING || ratio > MAX_LENGTH_RATIO_WARNING;
    }

    public boolean hasGarbagePatterns(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        if (text.indexOf('�') >= 0) {
            return true;
        }

        if (REPEATED_CHAR_PATTERN.matcher(text).find() || REPEATED_WORD_PATTERN.matcher(text).find()) {
            return true;
        }

        long lettersDigits = text.chars().filter(Character::isLetterOrDigit).count();
        long suspiciousSymbols = text.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch) && ",.;:!?¡¿()[]{}'\"-_/".indexOf(ch) < 0)
                .count();

        if (lettersDigits == 0) {
            return true;
        }

        return ((double) suspiciousSymbols / (double) (lettersDigits + suspiciousSymbols)) > GARBAGE_SYMBOL_RATIO;
    }

    public boolean looksHallucinatory(String originalText, String translatedText) {
        if (translatedText == null || translatedText.isBlank()) {
            return false;
        }

        String comparableTranslation = normalizeComparable(translatedText);
        for (String marker : HALLUCINATION_MARKERS) {
            if (comparableTranslation.contains(normalizeComparable(marker))) {
                return true;
            }
        }

        int originalWords = countWords(originalText);
        int translatedWords = countWords(translatedText);
        long sentenceCount = translatedText.chars().filter(ch -> ch == '.' || ch == '!' || ch == '?').count();

        return originalWords > 0
                && originalWords <= 12
                && translatedWords >= originalWords * 3
                && sentenceCount >= 2;
    }

    private boolean containsMarkdownFences(String text) {
        return text != null && MARKDOWN_FENCE_PATTERN.matcher(text).find();
    }

    private double lengthRatio(String originalText, String translatedText) {
        int originalLength = effectiveLength(originalText);
        int translatedLength = effectiveLength(translatedText);
        if (originalLength == 0) {
            return translatedLength == 0 ? 1.0d : 10.0d;
        }
        return (double) translatedLength / (double) originalLength;
    }

    private int effectiveLength(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.replaceAll("\\s+", "").length();
    }

    private double englishResidualRatio(String text) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }

        String[] tokens = normalizeComparable(text).split("[^a-z]+");
        int total = 0;
        int english = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            total++;
            if (ENGLISH_STOPWORDS.contains(token)) {
                english++;
            }
        }

        if (total == 0) {
            return 0.0d;
        }
        return (double) english / (double) total;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private String normalizeComparable(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}

