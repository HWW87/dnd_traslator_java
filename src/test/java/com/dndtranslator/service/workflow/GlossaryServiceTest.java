package com.dndtranslator.service.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlossaryServiceTest {

    @Test
    void appliesPlaceholdersAndRestoresConfiguredTerms() {
        GlossaryService service = new GlossaryService(List.of(
                new GlossaryEntry("Armor Class", "Clase de Armadura", false),
                new GlossaryEntry("Dungeon Master", "Dungeon Master", true)
        ));

        GlossaryService.GlossaryApplication app = service.applyBeforeTranslation("Armor Class and Dungeon Master");

        assertTrue(app.text().contains("DNDTERM0X"));
        assertTrue(app.text().contains("DNDTERM1X"));

        String restored = service.applyAfterTranslation(app.text(), app);
        assertEquals("Clase de Armadura and Dungeon Master", restored);
    }

    @Test
    void doesNotReplacePartialMatchesInsideWords() {
        GlossaryService service = new GlossaryService(List.of(
                new GlossaryEntry("initiative", "iniciativa", false)
        ));

        GlossaryService.GlossaryApplication app = service.applyBeforeTranslation("reinitiative should stay, initiative should map");

        assertTrue(app.text().contains("reinitiative"));
        assertTrue(app.text().contains("DNDTERM0X"));

        String restored = service.applyAfterTranslation(app.text(), app);
        assertEquals("reinitiative should stay, iniciativa should map", restored);
    }

    @Test
    void loadsDefaultGlossaryFromResources() {
        GlossaryService service = new GlossaryService();

        List<GlossaryEntry> entries = service.entries();

        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().anyMatch(e -> e.sourceTerm().equals("Armor Class")));
    }
}

