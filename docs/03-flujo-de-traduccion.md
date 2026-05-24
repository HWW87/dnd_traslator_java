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

## Paso 5: preparacion de unidades

`TranslationCoordinatorService` ejecuta una etapa explicita de preparacion:

- convierte `Paragraph` -> `TranslationUnit` (`ParagraphToUnitConverter`)
- enriquece metadata de unidad (`deterministic_unit_id`, `page_type`, `retry_context`)
- calcula cursor de resume por `unitId` cuando existe checkpoint

## Paso 6: traduccion unit-first (secuencial)

`TranslationCoordinatorService` + `TranslatorService`:

- procesa unidades pendientes en orden deterministico
- respeta `pause/stop` desde `TranslationEventListener`
- por cada unidad aplica:
  - `TextSanitizer.sanitizeForTranslation(...)`
  - `GlossaryService.applyBeforeTranslation(...)`
  - `UnitTranslatorGateway.translate(...)` (ruta canonica)
  - `GlossaryService.applyAfterTranslation(...)`
- notifica progreso con `TranslationProgress`

## Paso 7: llamada al provider

`TranslatorService.translate(...)`:

- obtiene provider ya resuelto desde wiring runtime
- detecta modelo disponible y aplica retry policy
- segmenta texto largo en bloques de ~1000 palabras
- traduce cada segmento via contrato `TranslationProvider`
- limpia ruido comun de respuesta
- guarda resultado en cache SQLite (`translations.db`)

`TranslatorService.translateUnit(...)`:

- usa prompts unit-aware (`UnitType`, `page_type`, `retry_context`)
- conserva compatibilidad con el flujo legacy por texto

## Paso 8: reconstruccion PDF

`PdfRebuilderService.rebuild(...)`:

- crea documento de salida con PDFBox
- escribe texto traducido respetando coordenadas y columnas
- calcula ancho maximo por columna para wrap
- selecciona fuente CJK cuando existe
- reemplaza glifos no soportados por `?`

## Paso 9: checkpoint y resume

- `CheckpointSnapshot` persiste cursor por unidad (`currentUnitId`, `lastCompletedUnitId`)
- resume salta a la siguiente unidad realmente pendiente
- mantiene campos legacy para compatibilidad (`paragraph_count`)

## Paso 10: resultado y cierre

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
