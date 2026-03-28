package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TranslationOutputSanitizerTest {

    private final TranslationOutputSanitizer sanitizer = new TranslationOutputSanitizer();

    @Test
    void removesAquiEstaLaTraduccion() {
        String sanitized = sanitizer.sanitize("Aquí está la traducción:\nHola mundo");

        assertEquals("Hola mundo", sanitized);
    }

    @Test
    void removesMarkdownFences() {
        String sanitized = sanitizer.sanitize("```spanish\nHola mundo\n```");

        assertEquals("Hola mundo", sanitized);
    }

    @Test
    void removesPleaseProvideTextMetaOutput() {
        String sanitized = sanitizer.sanitize("Por favor proporcione el texto");

        assertEquals("", sanitized);
    }

    @Test
    void keepsUsefulText() {
        String sanitized = sanitizer.sanitize("Clase de Armadura 15");

        assertEquals("Clase de Armadura 15", sanitized);
    }

    @Test
    void normalizesWhitespace() {
        String sanitized = sanitizer.sanitize("  Resultado:   Hola    mundo  \n\n\n  line 2   ");

        assertEquals("Hola mundo\n\nline 2", sanitized);
    }

    @Test
    void removesLeadingMetaLinesBeforeUsefulContent() {
        String sanitized = sanitizer.sanitize("Output:\nAnswer:\nClase de Armadura 18");

        assertEquals("Clase de Armadura 18", sanitized);
    }

    @Test
    void removesEnglishAssistantPrefaceVariants() {
        String sanitized = sanitizer.removeAssistantPrefaces("Here's the translated text: Damage Reduction 5");

        assertEquals("Damage Reduction 5", sanitized);
    }

    @Test
    void stripsRepeatedLeadingMetaLinesButKeepsUsefulBody() {
        String sanitized = sanitizer.sanitize("Output:\nTranslation:\nAnswer:\nClase de Armadura 18\nCon escudo");

        assertEquals("Clase de Armadura 18\nCon escudo", sanitized);
    }

    @Test
    void doesNotCutUsefulTextWhenMetaPhraseAppearsInMiddle() {
        String sanitized = sanitizer.sanitize("La frase here is the translation aparece en contexto y debe conservarse");

        assertEquals("La frase here is the translation aparece en contexto y debe conservarse", sanitized);
    }
}

