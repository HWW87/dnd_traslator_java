# Guía de Próximas Fases: Phase 3, 4, 5+

Basado en la implementación completa de Phases 0, 1 y 2.

---

## Phase 3: Checkpointing and Resume Support

### Objetivo
Permitir que traducciones interrumpidas se reanuden desde donde pararon.

### Archivos a Crear/Modificar

#### 1. Crear CheckpointRepository
**Archivo:** `src/main/java/com/dndtranslator/infrastructure/CheckpointRepository.java`

```java
public interface CheckpointRepository {
    void save(Checkpoint checkpoint) throws IOException;
    Optional<Checkpoint> getLatest(String jobId) throws IOException;
    List<Checkpoint> getAll(String jobId) throws IOException;
    void delete(String jobId) throws IOException;
}

public class SqliteCheckpointRepository implements CheckpointRepository {
    // Implementar CRUD en translations.db
    // Crear tabla: checkpoints (job_id, page_number, last_unit_id, timestamp, data)
}
```

#### 2. Crear JobRepository
**Archivo:** `src/main/java/com/dndtranslator/infrastructure/JobRepository.java`

```java
public interface JobRepository {
    void save(TranslationJob job) throws IOException;
    Optional<TranslationJob> getById(String jobId) throws IOException;
    List<TranslationJob> getAll() throws IOException;
}
```

#### 3. Adaptar TranslationCoordinatorService
**Archivo:** `src/main/java/com/dndtranslator/service/workflow/TranslationCoordinatorService.java`

Cambios:
- Crear o recuperar `TranslationJob` al inicio
- Crear `TranslationUnit` por párrafo
- Crear `Checkpoint` después de cada página completada
- Reanudar desde último checkpoint si existe

```java
public TranslationResult execute(TranslationRequest request, TranslationEventListener listener) {
    // 1. Crear job
    TranslationJob job = createOrResumeJob(request);
    
    // 2. Si reanudando, saltar a último checkpoint
    if (shouldResume(job)) {
        resumeFromCheckpoint(job);
    }
    
    // 3. Traducir párrafos como TranslationUnits
    for (Paragraph para : paragraphs) {
        TranslationUnit unit = createUnit(para);
        translateUnit(unit);
        job.recordUnitCompleted(unit.isTranslated(), unit.isSkipped());
    }
    
    // 4. Crear checkpoint
    createCheckpoint(job);
    
    // 5. Reconstruir PDF
    pdfRebuilderGateway.rebuild(...);
}
```

### Acceptance Criteria
- ✅ Job persiste en SQLite
- ✅ Checkpoint se crea después de cada página
- ✅ Resume mantiene párrafos ya traducidos
- ✅ Prueba de interrupción y resume

### Tests a Crear
- `CheckpointRepositoryTest.java` - Persistencia
- `JobRepositoryTest.java` - CRUD de jobs
- `TranslationCoordinatorResumeTest.java` - Resume end-to-end

---

## Phase 4: Translation Quality by Content Type

### Objetivo
Mejorar calidad usando prompts especializados por tipo de contenido.

### Archivos a Crear/Modificar

#### 1. Extend PromptBuilder Variants
**Archivo:** `src/main/java/com/dndtranslator/service/PromptBuilder.java`

Agregar métodos:
```java
public String buildNarrativePrompt(String text, String targetLanguage) { }
public String buildStructuredPrompt(String text, String targetLanguage) { }
public String buildMapLabelPrompt(String text, String targetLanguage) { }
public String buildLegalPrompt(String text, String targetLanguage) { }
public String buildRetryPrompt(TranslationUnit unit, String previousAttempt) { }
```

#### 2. Create ContentAwarePromptRouter
**Archivo:** `src/main/java/com/dndtranslator/service/ContentAwarePromptRouter.java`

```java
public class ContentAwarePromptRouter {
    public String selectPrompt(TranslationUnit unit, String targetLanguage) {
        return switch (unit.getUnitType()) {
            case PARAGRAPH -> promptBuilder.buildNarrativePrompt(...);
            case INDEX_LINE -> promptBuilder.buildStructuredPrompt(...);
            case MAP_LABEL -> promptBuilder.buildMapLabelPrompt(...);
            case LEGAL_TEXT -> promptBuilder.buildLegalPrompt(...);
            default -> promptBuilder.buildNarrativePrompt(...);
        };
    }
}
```

#### 3. Enhance TranslationRetryPolicy
**Archivo:** `src/main/java/com/dndtranslator/service/TranslationRetryPolicy.java`

Mejoras:
- Escalar modelo en reintento
- Cambiar prompt estrategia
- Reducir chunk size

### Tests a Crear
- `PromptBuilderVariantsTest.java` - Variantes de prompt
- `ContentAwarePromptRouterTest.java` - Routing por tipo

---

## Phase 5: Strengthen Output Hygiene

### Objetivo
Reducir leakage de asistente, mejorar validación.

### Archivos a Modificar

#### 1. Improve TranslationOutputSanitizer
**Archivo:** `src/main/java/com/dndtranslator/service/TranslationOutputSanitizer.java`

Enhancements:
```java
// Detectar more assistant patterns
private static final List<String> ASSISTANT_PREFIXES = List.of(
    "As an AI", "I'm an AI", "Based on", "According to",
    "The translation", "Here's the", "I've translated",
    "Note:", "Please note", "Important:", "Disclaimer:",
    // Spanish
    "Como IA", "Soy una IA", "Según", "La traducción",
    "Aquí está", "He traducido", "Nota:", "Por favor",
    "Importante:", "Disclaimer:"
);

// Detectar markdown fences
private static final Pattern MARKDOWN_FENCE = Pattern.compile("```[^`]*```|~~~[^~]*~~~");

// Detectar response wrappers
private static final Pattern RESPONSE_WRAPPER = Pattern.compile(
    "^\\[?(response|respuesta|translation|traducción)\\]?[:\\s]*(.*?)$",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
);
```

#### 2. Enhance TranslationValidator
**Archivo:** `src/main/java/com/dndtranslator/service/TranslationValidator.java`

Enhancements:
```java
// Content-type aware validation
public ValidationResult validate(TranslationUnit unit) {
    return switch (unit.getUnitType()) {
        case INDEX_LINE -> validateStructured(unit);
        case MAP_LABEL -> validateCompact(unit);
        case LEGAL_TEXT -> validateLegal(unit);
        default -> validateGeneric(unit);
    };
}

// Stricto para structured
private ValidationResult validateStructured(TranslationUnit unit) {
    // Penalizar expansiones, longitud excesiva
    if (unit.getTranslatedText().length() > unit.getSourceText().length() * 1.5f) {
        return ValidationResult.FAILED("Expansion excesiva (structured)");
    }
}

// Strict para legal
private ValidationResult validateLegal(TranslationUnit unit) {
    // Penalizar paraphrasing
    if (similarityScore < 0.8f) {
        return ValidationResult.FAILED("Paraphrasing detectado (legal)");
    }
}
```

#### 3. Create ValidationPolicy
**Archivo:** `src/main/java/com/dndtranslator/service/ValidationPolicy.java`

```java
public class ValidationPolicy {
    private final Map<UnitType, ValidationRules> rulesByType = new HashMap<>();
    
    public ValidationPolicy() {
        rulesByType.put(UnitType.PARAGRAPH, new ValidationRules(
            maxExpansionRatio: 2.0f,
            requireMinLength: true,
            detectHallucination: true
        ));
        
        rulesByType.put(UnitType.INDEX_LINE, new ValidationRules(
            maxExpansionRatio: 1.2f,
            requireMinLength: false,
            detectHallucination: true,
            preserveNumbering: true
        ));
        
        // ... más tipos
    }
}
```

### Tests a Crear
- `TranslationOutputSanitizerEdgeCasesTest.java` - Cases extremos
- `TranslationValidatorByTypeTest.java` - Validación por tipo
- `ValidationPolicyTest.java` - Políticas

---

## Phase 6: Structured Page Layout Improvements

### Objetivo
Mejorar calidad visual, especialmente índices, tablas, mapas.

### Archivos a Modificar

#### 1. Strengthen TableOrIndexLayoutStrategy
**Archivo:** `src/main/java/com/dndtranslator/service/TableOrIndexLayoutStrategy.java`

```java
@Override
public void renderPage(PageRenderContext context) {
    // 1. Detectar estructura de índice
    List<LayoutBox> indexBoxes = detectIndexStructure(context.getParagraphs());
    
    // 2. Preservar numeración y alineación
    for (LayoutBox box : indexBoxes) {
        preserveNumberingFormat(box);
        respectColumnAlignment(box);
    }
    
    // 3. Evitar wrapping excesivo
    applyTightWrappingPolicy(context);
}
```

#### 2. Enhance TitleOrCoverLayoutStrategy
**Archivo:** `src/main/java/com/dndtranslator/service/TitleOrCoverLayoutStrategy.java`

```java
@Override
public void renderPage(PageRenderContext context) {
    // 1. Detectar texto sobre fondo visual
    boolean hasBackgroundImage = hasSignificantVisual(context);
    
    if (hasBackgroundImage) {
        // 2. Suprimir texto largo
        suppressLongParagraphs(context);
        
        // 3. Renderizar solo títulos/labels importantes
        renderOnlyEssentialText(context);
    }
}
```

#### 3. Enhance MapPageLayoutStrategy
**Archivo:** `src/main/java/com/dndtranslator/service/MapPageLayoutStrategy.java`

```java
// Mejor handling de múltiples labels
// Preservar leyenda
// Respetar bounds visuales
```

### Tests a Crear
- `TableOrIndexLayoutStrategyTest.java` - Layout de índices
- `TitleOrCoverLayoutStrategyTest.java` - Portadas
- `MapPageLayoutStrategyTest.java` - Mapas

---

## Phase 7: Translation Cache Evolution

### Objetivo
Hacer caching más explícito, seguro y versionable.

### Cambios

#### 1. Enhance TranslationCacheKey
- Incluir `translationStrategyVersion`
- Incluir `sanitizerVersion`
- Incluir `validatorVersion`

#### 2. Add Cache Invalidation
```java
public class CacheInvalidationStrategy {
    public boolean shouldInvalidate(CacheEntry entry) {
        String currentVersion = SystemConstants.TRANSLATION_STRATEGY_VERSION;
        return !entry.strategyVersion().equals(currentVersion);
    }
}
```

#### 3. Add Cache Metadata
```java
public record CacheMetadata(
    LocalDateTime createdAt,
    String providerId,
    String strategyVersion,
    String sanitizerVersion,
    String validatorVersion,
    double confidenceScore
)
```

---

## Phase 8: Observability and Diagnostics

### Objetivo
Facilitar debugging y tuning.

### Nuevos Componentes

#### 1. Structured Logging
```java
logger.info("Translation page={} type={} strategy={} model={} retries={}",
    pageNumber, pageType, strategyName, modelId, retryCount);
```

#### 2. Quality Metrics
```java
public class QualityMetrics {
    int invalidSegmentCount;
    int retryCount;
    int residualEnglishWarnings;
    int sanitizerRemovals;
    Map<PageType, Integer> pageTypeDistribution;
    double overflowFrequency;
}
```

#### 3. Debug Mode
```bash
export DND_DEBUG_MODE=true
# Escribe analysis data, estrategy decisions, rejected outputs
```

---

## Phase 9: Test Strategy Expansion

### Objetivo
Stronger regression prevention.

### Componentes Nuevos

#### 1. Regression Test Corpus
- `tests/regression/cover-*.pdf` - Portadas
- `tests/regression/index-*.pdf` - Índices
- `tests/regression/map-*.pdf` - Mapas
- etc.

#### 2. Snapshot Testing
```java
@Test
public void testCoverPageRegressions() {
    PDF original = load("cover-dnd.pdf");
    PDF translated = translatePdf(original);
    
    assertSnapshotEquals(translated, "snapshots/cover-dnd.translated");
}
```

#### 3. Golden Files
Comparar outputs contra golden files en `tests/golden/`

---

## Resumen de Roadmap Completo

```
Phase 0: ✅ COMPLETO
Phase 1: ✅ COMPLETADO  
Phase 2: ✅ COMPLETADO
Phase 3: ⬜ TODO - Checkpointing
Phase 4: ⬜ TODO - Quality by Content
Phase 5: ⬜ TODO - Output Hygiene
Phase 6: ⬜ TODO - Layout Improvements
Phase 7: ⬜ TODO - Cache Evolution
Phase 8: ⬜ TODO - Observability
Phase 9: ⬜ TODO - Tests
Phase 10: ⬜ FUTURO - Advanced Options
```

---

## Command Reference

```bash
# Compilar
mvn clean compile

# Tests
mvn test

# Tests específicos
mvn test -Dtest=TranslationUnitTest

# Build con verificación
mvn clean verify

# Ejecutar aplicación
mvn javafx:run

# Generar reportes
mvn site

# Limpieza
mvn clean
```

---

## Notas Importantes

1. **Mantener Compatibilidad**: Cada fase extiende sin romper
2. **Tests Primero**: Escribir tests antes de implementar features
3. **Documentar**: Actualizar docs/ con cada cambio
4. **Commits Granulares**: Un cambio = un commit
5. **Code Reviews**: Revisar PR antes de mergear

---

Escrito: 2026-05-03  
Para Copilot: Usa este documento como guía para las próximas fases.

