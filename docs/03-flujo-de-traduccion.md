# Flujo de traduccion

## Flujo end-to-end

1. Usuario selecciona PDF en `TranslatorUI`.
2. `TranslationTaskManager.start(...)` crea `Task<TranslationResult>`.
3. `TranslationCoordinatorService.execute(...)` coordina extraccion, traduccion y reconstruccion.
4. Se genera `<archivo>_translated_layout.pdf`.

## Paso 1: validacion de entrada

`TranslationCoordinatorService` valida:

- `request` no nulo
- archivo existente y legible
- idioma objetivo (si falta, usa `Spanish`)

## Paso 2: extraccion inicial (texto embebido)

`PdfExtractorService`:

- usa `PDFTextStripper` con `setSortByPosition(true)`
- construye `Paragraph` con coordenadas
- detecta maquetacion de columnas por pagina (`columnCount`, `splitX`)
- genera `layoutInfo` (`Map<Integer, PageMeta>`)

## Paso 3: decision OCR fallback

`OcrDecisionService.shouldUseOcrFallback(...)` evalua:

- densidad minima de caracteres por pagina
- ratio de caracteres ruidosos
- ratio/cantidad de caracteres sospechosos

Si la calidad embebida es baja o no hay parrafos, se activa OCR.

## Paso 4: OCR embebido (si aplica)

`PdfToParagraphService`:

- renderiza cada pagina a imagen (`300 DPI`)
- ejecuta OCR por pagina en paralelo
- compara OCR normal vs preprocesado binario y elige mejor score
- escala coordenadas OCR a unidades PDF (`72/300`)
- detecta columnas y arma `PageMeta` por pagina

## Paso 5: traduccion paralela

`ParagraphTranslationExecutor`:

- usa un pool fijo (`workers`, por defecto nucleos disponibles)
- respeta `pause/stop` desde `TranslationEventListener`
- por cada parrafo aplica:
  - `TextSanitizer.sanitizeForTranslation(...)`
  - `GlossaryService.applyBeforeTranslation(...)`
  - `TranslatorGateway.translate(...)`
  - `GlossaryService.applyAfterTranslation(...)`
- notifica progreso con `TranslationProgress`

## Paso 6: llamada a Ollama

`TranslatorService.translate(...)`:

- detecta modelo (`/api/tags`): primario `gemma3:1b`, fallback `llama3.2:1b-instruct`
- segmenta texto largo en bloques de ~1000 palabras
- traduce cada segmento via `POST /api/generate`
- limpia ruido comun de respuesta
- guarda resultado en cache SQLite (`translations.db`)

## Paso 7: reconstruccion PDF

`PdfRebuilderService.rebuild(...)`:

- crea documento de salida con PDFBox
- escribe texto traducido respetando coordenadas y columnas
- calcula ancho maximo por columna para wrap
- selecciona fuente CJK cuando existe
- reemplaza glifos no soportados por `?`

## Paso 8: resultado y cierre

- `TranslationResult` incluye:
  - `outputPdfPath`
  - `paragraphCount`
  - `usedOcrFallback`
- UI muestra logs de fin y archivo de salida.

## Controles de ejecucion

Desde la UI:

- Pausar/Reanudar: alterna bandera en `TranslationTaskManager`
- Detener: cancela `Task` y propaga `CancellationException`
- Salir: invoca `TranslationCoordinatorService.shutdown()`
