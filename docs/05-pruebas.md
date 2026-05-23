# Pruebas

## Suite actual

La cobertura automatizada esta centrada en `src/test/java/com/dndtranslator/service/workflow`.

Archivos relevantes:

- `GlossaryServiceTest`
- `OcrDecisionServiceTest`
- `TextSanitizerTest`
- `TranslationCoordinatorServiceTest`
- `TranslationCoordinatorServiceMockitoTest`
- `TranslationCoordinatorResumeTest`
- `TranslationCoordinatorRuntimeWiringTest`
- `TranslatorServiceUnitCanonicalTest`
- `TranslatorServiceProviderTest`
- `TranslationProviderFactoryTest`

## Que valida cada bloque

- Glosario:
  - placeholders y restauracion de terminos
  - no reemplazar coincidencias parciales dentro de palabras
  - carga de glosario por defecto desde recursos
- OCR decision:
  - casos de texto limpio/noisy/sospechoso
  - umbrales minimos por pagina
- Sanitizacion:
  - remocion de caracteres invalidos
  - preservacion de puntuacion valida
- Coordinador (integracion ligera):
  - camino normal sin OCR
  - camino con OCR fallback
  - cancelacion
  - propagacion de errores del traductor
- Coordinador con Mockito:
  - interacciones con gateways
  - uso de OCR segun decision
- Resume/checkpoint unit-aware:
  - restauracion por `unitId`
  - cursor por `currentUnitId`/`lastCompletedUnitId`
  - salto a siguiente unidad pendiente real
- Wiring runtime/provider:
  - resolucion de provider centralizada en `TranslationCoordinatorRuntimeWiring`
  - politica default en `TranslationProviderFactory`
- Ruta canonica por unidad:
  - `TranslatorService.translateUnit(...)`
  - prompts unit-aware y retry context

## Ejecutar pruebas

```powershell
.\mvnw.cmd test
```

## Ejecutar una clase puntual

```powershell
.\mvnw.cmd "-Dtest=TranslationCoordinatorServiceTest" test
.\mvnw.cmd "-Dtest=TranslationCoordinatorResumeTest,TranslationCoordinatorRuntimeWiringTest" test
.\mvnw.cmd "-Dtest=TranslatorServiceUnitCanonicalTest,TranslatorServiceProviderTest,TranslationProviderFactoryTest" test
```

## Recomendaciones para nuevas pruebas

- Agregar pruebas de `PdfExtractorService` con PDFs de 1 y 2 columnas.
- Agregar pruebas de `PdfRebuilderService` para wrap por columna.
- Cubrir casos de concurrencia en `ParagraphTranslationExecutor`.
- Simular respuestas de Ollama con timeouts/reintentos en `TranslatorService`.

## Regression Corpus (Phase 9)

Se agrego un corpus liviano para detectar regresiones en `PageAnalyzer` y `PageTypeClassifier`:

- `src/test/resources/regression/page-corpus/v1/manifest.json`
- `src/test/resources/regression/page-corpus/v1/cases/*.json`
- `src/test/java/com/dndtranslator/service/regression/PageClassificationRegressionTest.java`

Casos incluidos:

- `cover-basic`
- `index-basic`
- `table-basic`
- `map-basic`
- `mixed-basic`
- `text-heavy-basic`

Ejecucion puntual:

```powershell
mvn -Dtest=PageClassificationRegressionTest test
```

