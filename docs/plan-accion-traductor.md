# Plan de accion y ejecucion - D&D Translator

## Objetivo
Restaurar el flujo end-to-end para que la aplicacion pueda:
1. Extraer texto del PDF.
2. Traducir contenido con Ollama.
3. Reconstruir un PDF traducido con layout razonable.

## Plan ejecutado
- [x] Corregir flujo principal en `TranslatorUI`.
- [x] Reactivar reconstruccion de PDF (`rebuild`).
- [x] Unificar estrategia de extraccion con fallback OCR.
- [x] Agregar validaciones de entrada y control de estado (pausa/detener).
- [x] Eliminar ruta hardcodeada de Tesseract.
- [x] Corregir seleccion dinamica de modelo en `TranslatorService`.
- [x] Verificar compilacion del proyecto.

## Cambios implementados

### 1) `src/main/java/com/dndtranslator/TranslatorUI.java`
- Se agrego validacion de archivo antes de iniciar proceso (`exists` y `canRead`).
- Se reinician flags de control al comenzar cada ejecucion (`paused`, `stopped`).
- Se usa `PdfExtractorService` como estrategia principal de extraccion.
- Si no hay texto embebido, se activa fallback OCR con `PdfToParagraphService`.
- Se corrige el bucle de pausa para permitir salida limpia si el usuario detiene.
- Se mantiene progreso con `updateProgress` por parrafo.
- Se reactiva `rebuilder.rebuild(...)` pasando `List<Paragraph>` + `layoutInfo`.
- Se mejora el ciclo de vida del hilo (`daemon`, nombre de worker, `unbind` previo del progress bar).

### 2) `src/main/java/com/dndtranslator/service/PdfToParagraphService.java`
- Se migro de OCR externo (`ProcessBuilder` + ejecutable `tesseract`) a OCR embebido con `Tess4J`.
- El OCR ya no depende de invocar un binario externo durante la ejecucion.
- Se mantiene paralelismo por pagina para aprovechar CPU multinucleo.
- Se mantiene ordenamiento por columnas (`columna -> y -> x`) para doble columna.
- Configuracion OCR:
  - `DND_TESSDATA_PATH` para ubicar carpeta `tessdata`.
  - `DND_OCR_LANG` (default `spa+eng`).
  - `DND_OCR_THREADS` para cantidad de hilos OCR.

### 3) `src/main/java/com/dndtranslator/service/TranslatorService.java`
- Se corrige uso de modelo en request a Ollama:
  - Antes: siempre `PRIMARY_MODEL`.
  - Ahora: usa el modelo detectado (`model`) y permite fallback real.
- Se quitan trazas de depuracion innecesarias por consola.
- Se simplifica parseo de respuesta JSON.

### 4) `src/main/java/com/dndtranslator/service/PdfRebuilderService.java`
- Se corrigio el error de render de PDF por glifos no soportados (ej: `U+9177`, caracter chino `酷`).
- Se agrego sanitizacion previa del texto (`sanitizeForFont`) antes de medir/escribir con PDFBox.
- Si un caracter no esta soportado por la fuente activa, se reemplaza por `?` para evitar que el proceso falle.
- Se agrego cache de soporte de glifos por codepoint para reducir costo de validaciones repetidas.
- Se agrego validacion explicita de carga de `/fonts/NotoSans-Regular.ttf` para dar error claro si falta el recurso.

### 5) `src/main/java/com/dndtranslator/service/PdfRebuilderService.java` (mejora CJK)
- Se agrego seleccion de fuente CJK al reconstruir PDF para cubrir glifos de chino/japones/coreano.
- Prioridad actual de carga de fuente:
  1. `/fonts/NotoSansCJKsc-Regular.otf` en recursos,
  2. variable de entorno `DND_CJK_FONT_PATH` (ruta a `.ttf` o `.otf`),
  3. autodeteccion en `C:/Windows/Fonts` por nombres CJK conocidos,
  4. fallback seguro con reemplazo por `?` si no hay cobertura.
- Se evita cargar `.ttc` para reducir warnings de parseo de FontBox y priorizar rutas estables (`.ttf`/`.otf`).
- Si se encuentra una fuente valida, se usa como principal durante la escritura del PDF traducido.
- Si no encuentra fallback CJK, mantiene comportamiento seguro y reemplaza glifos no soportados por `?`.

### 6) Orden de lectura column-aware (exactitud prioritaria)
- `src/main/java/com/dndtranslator/service/PdfExtractorService.java`:
  - Se evita fusionar fragmentos entre columnas al construir parrafos.
  - Se ordena resultado por `pagina -> columna -> y -> x` para preservar lectura natural en doble columna.
  - Se mantiene `layoutInfo` con `columnCount` por pagina para reconstruccion posterior.
- `src/main/java/com/dndtranslator/service/PdfToParagraphService.java`:
  - Se agrego deteccion de split de columnas para bloques OCR.
  - Se ordenan bloques OCR por columna antes de traducir.
- `src/main/java/com/dndtranslator/service/PdfRebuilderService.java`:
  - Se ajusta wrap con `maxWidth` calculado por columna real (no ancho fijo global).
  - Se mejora estabilidad visual al no invadir la columna adyacente en paginas de dos columnas.

### 7) Paralelismo maximo para CPU (6 nucleos y escalable)
- `src/main/java/com/dndtranslator/TranslatorUI.java`:
  - La traduccion de parrafos ahora se ejecuta en paralelo usando `Runtime.getRuntime().availableProcessors()`.
  - Mantiene control de `pausa/detener` y progreso incremental durante ejecucion concurrente.
- `src/main/java/com/dndtranslator/service/TranslatorService.java`:
  - El pool interno de traduccion pasa de fijo (`4`) a dinamico por nucleos disponibles.
  - Se permite override con variable de entorno `DND_MAX_THREADS`.
- `src/main/java/com/dndtranslator/service/PdfToParagraphService.java`:
  - OCR paralelo por pagina (despues del render de imagen) para usar todos los nucleos.
  - Se permite override con variable de entorno `DND_OCR_THREADS`.

## Resultado esperado
- Para PDFs con texto embebido: extrae, traduce y reconstruye el PDF.
- Para PDFs escaneados: intenta OCR y luego traduce/reconstruye.
- La salida se guarda como `*_translated_layout.pdf` en la misma ruta del original.

## Riesgos y pendientes
- La fuente base actual (`NotoSans-Regular.ttf`) no cubre todos los alfabetos CJK.
- Con el fix, el proceso no se detiene, pero caracteres no soportados se reemplazan por `?`.
- Si se requiere cobertura total CJK, queda pendiente incorporar una fuente CJK y fallback por fuente.
- Si Ollama no esta corriendo o no hay modelo compatible, devolvera error de traduccion por segmento.
- Si Tesseract no esta instalado ni en PATH, el fallback OCR fallara.
- La fidelidad del layout depende de la calidad de coordenadas extraidas (especialmente en OCR).
- Con fallback CJK disponible en el sistema, deberia bajar notablemente la aparicion de `?` por glifos CJK.
- La deteccion de columnas es heuristica; en maquetaciones complejas (sidebars, tablas) puede requerir refinamiento adicional.

## Configuracion minima recomendada

### Ollama
- Tener `ollama serve` en ejecucion.
- Tener descargado al menos uno de estos modelos:
  - `gemma3:1b`
  - `llama3.2:1b-instruct`

### Tesseract (opcional, para OCR)
- Instalar Tesseract y dejarlo en PATH, o
- Definir variable de entorno `TESSERACT_PATH` con ruta completa al ejecutable.

### Tesseract CLI (legado)
- Esta opcion queda solo como referencia historica.
- El flujo OCR actual del proyecto usa `Tess4J` embebido y prioriza `DND_TESSDATA_PATH`.

### OCR embebido (Tess4J)
- Definir `DND_TESSDATA_PATH` apuntando a una carpeta que contenga al menos `spa.traineddata` o `eng.traineddata`.
- Opcional: definir `DND_OCR_LANG` (por ejemplo `spa+eng`).
- Ya no es necesario `TESSERACT_PATH` para el flujo principal OCR.

### Fuente CJK (opcional pero recomendada)
- Si tu documento contiene CJK y no hay fuente embebida, podes fijar una ruta explicita:
  - `DND_CJK_FONT_PATH=C:\\ruta\\a\\fuente-cjk.ttf`
- Si no la definis, el sistema intenta autodeteccion en `C:/Windows/Fonts`.
- Si no encuentra ninguna fuente compatible, los glifos no cubiertos se reemplazan por `?`.

### Rendimiento / Paralelismo
- Por defecto la app usa todos los nucleos detectados del sistema.
- Si se desea ajustar manualmente:
  - `DND_MAX_THREADS` para traduccion.
  - `DND_OCR_THREADS` para OCR.
- En tu caso (6 nucleos), podes fijar ambos en `6` para uso pleno de CPU.

## Validacion aplicada
Se ejecuta compilacion Maven para confirmar que los cambios no rompen build.

Comando ejecutado para validar build:

```powershell
mvn -f "D:\Desarrollos para PC\dnd_traslator_java\pom.xml" -q -DskipTests compile
```

Resultado:
- Compilacion completada sin errores de Maven.
- Se observaron solo advertencias del runtime de Maven/Jansi (no bloqueantes).
- Queda una advertencia de inspeccion en IDE por `Thread.sleep` durante pausa, usada para mantener control simple de reanudacion/detencion.
