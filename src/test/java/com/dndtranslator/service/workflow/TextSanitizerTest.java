package com.dndtranslator.service.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSanitizerTest {

    private final TextSanitizer sanitizer = new TextSanitizer();

    @Test
    void basicCleanupRemovesInvalidChars() {
        String input = "Armor Class\u0000 ### Hit Points@@";
        String output = sanitizer.sanitizeForTranslation(input);

        assertEquals("Armor Class  Hit Points", output);
    }

    @Test
    void validCharactersArePreserved() {
        String input = "Dexterity (Save) 14, bonus -2!?";
        String output = sanitizer.sanitizeForTranslation(input);

        assertEquals("Dexterity (Save) 14, bonus -2!?", output);
    }

    @Test
    void symbolsFromPdfNoiseAreRemoved() {
        String input = "The fiend� uses ﬁ ligature and ™ symbol";
        String output = sanitizer.sanitizeForTranslation(input);

        assertEquals("The fiend uses ﬁ ligature and  symbol", output);
    }

    @Test
    void blankTextReturnsEmpty() {
        assertEquals("", sanitizer.sanitizeForTranslation("   "));
        assertEquals("", sanitizer.sanitizeForTranslation(null));
    }
}

