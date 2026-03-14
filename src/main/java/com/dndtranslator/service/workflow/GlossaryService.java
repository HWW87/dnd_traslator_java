package com.dndtranslator.service.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlossaryService {

    private static final String DEFAULT_RESOURCE_PATH = "/glossary/dnd-glossary.json";
    private static final Pattern WORD_BOUNDARY_TEMPLATE = Pattern.compile("%s", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final List<GlossaryRule> rules;

    public GlossaryService() {
        this(loadFromResource(DEFAULT_RESOURCE_PATH));
    }

    public GlossaryService(List<GlossaryEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");

        List<GlossaryRule> loadedRules = new ArrayList<>();
        for (GlossaryEntry entry : entries) {
            String source = entry.sourceTerm().trim();
            String output = entry.outputTerm().trim();
            Pattern pattern = buildWholeWordPattern(source);
            loadedRules.add(new GlossaryRule(source, output, pattern));
        }

        loadedRules.sort(Comparator.comparingInt((GlossaryRule r) -> r.source().length()).reversed());
        this.rules = Collections.unmodifiableList(loadedRules);
    }

    public GlossaryApplication applyBeforeTranslation(String text) {
        if (text == null || text.isBlank() || rules.isEmpty()) {
            return new GlossaryApplication(text == null ? "" : text, Map.of());
        }

        String result = text;
        Map<String, String> placeholders = new LinkedHashMap<>();
        int placeholderCounter = 0;

        for (GlossaryRule rule : rules) {
            String placeholder = "DNDTERM" + placeholderCounter + "X";
            Matcher matcher = rule.pattern().matcher(result);
            StringBuffer buffer = new StringBuffer();
            boolean found = false;

            while (matcher.find()) {
                found = true;
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(placeholder));
            }

            if (found) {
                matcher.appendTail(buffer);
                result = buffer.toString();
                placeholders.put(placeholder, rule.output());
                placeholderCounter++;
            }
        }

        return new GlossaryApplication(result, placeholders);
    }

    public String applyAfterTranslation(String translatedText, GlossaryApplication application) {
        if (translatedText == null || translatedText.isBlank()) {
            return translatedText == null ? "" : translatedText;
        }
        if (application == null || application.placeholders().isEmpty()) {
            return translatedText;
        }

        String result = translatedText;
        for (Map.Entry<String, String> replacement : application.placeholders().entrySet()) {
            result = result.replace(replacement.getKey(), replacement.getValue());
        }
        return result;
    }

    public List<GlossaryEntry> entries() {
        List<GlossaryEntry> entries = new ArrayList<>();
        for (GlossaryRule rule : rules) {
            entries.add(new GlossaryEntry(rule.source(), rule.output(), false));
        }
        return entries;
    }

    private static Pattern buildWholeWordPattern(String term) {
        String escaped = Pattern.quote(term);
        String regex = "(?<![\\p{L}\\p{N}])" + escaped + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(regex, WORD_BOUNDARY_TEMPLATE.flags());
    }

    private static List<GlossaryEntry> loadFromResource(String resourcePath) {
        try (InputStream stream = GlossaryService.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Glossary resource not found: " + resourcePath);
            }

            String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(raw);
            List<GlossaryEntry> entries = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String source = obj.getString("source");
                String target = obj.optString("target", source);
                boolean preserve = obj.optBoolean("preserve", false);
                entries.add(new GlossaryEntry(source, target, preserve));
            }

            return entries;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load glossary resource", ex);
        }
    }

    public record GlossaryApplication(String text, Map<String, String> placeholders) {
    }

    private record GlossaryRule(String source, String output, Pattern pattern) {
    }
}

