# Pruebas

## Suite actual

La cobertura automatizada esta centrada en `src/test/java/com/dndtranslator/service/workflow`.

Archivos relevantes:

- `GlossaryServiceTest`
- `OcrDecisionServiceTest`
- `TextSanitizerTest`
- `TranslationCoordinatorServiceTest`
- `TranslationCoordinatorServiceMockitoTest`

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

## Ejecutar pruebas

```powershell
mvn test
```

## Ejecutar una clase puntual

```powershell
mvn -Dtest=TranslationCoordinatorServiceTest test
mvn -Dtest=GlossaryServiceTest test
```

## Recomendaciones para nuevas pruebas

- Agregar pruebas de `PdfExtractorService` con PDFs de 1 y 2 columnas.
- Agregar pruebas de `PdfRebuilderService` para wrap por columna.
- Cubrir casos de concurrencia en `ParagraphTranslationExecutor`.
- Simular respuestas de Ollama con timeouts/reintentos en `TranslatorService`.
