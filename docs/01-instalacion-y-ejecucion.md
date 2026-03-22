# Instalacion y ejecucion
## Requisitos
- Java 21 (el `pom.xml` compila con `release 21`).
- Maven 3.9+.
- Sistema operativo Windows (el proyecto contiene autodeteccion de fuentes y rutas orientadas a Windows).
- Ollama en ejecucion para traducir.
- Modelos Ollama disponibles (por defecto):
  - `gemma3:1b` (primario)
  - `llama3.2:1b-instruct` (fallback)
## OCR (solo si hace falta fallback)
El OCR esta implementado con Tess4J embebido. No requiere invocar `tesseract.exe`, pero si necesita acceso a `tessdata`.
Variables soportadas:
- `DND_TESSDATA_PATH`: ruta a carpeta `tessdata`.
- `DND_OCR_LANG`: idiomas OCR (default `spa+eng`).
- `DND_OCR_THREADS`: cantidad de hilos OCR.
Busquedas automaticas de `tessdata`:
- `DND_TESSDATA_PATH`
- `TESSDATA_PREFIX`
- `./tessdata`
- `src/main/resources/tessdata`
- rutas comunes en `Program Files` y `LOCALAPPDATA`
## Fuentes para reconstruccion PDF
La reconstruccion usa por defecto `src/main/resources/fonts/NotoSans-Regular.ttf`.
Para cobertura CJK (chino/japones/coreano):
- recurso opcional: `/fonts/NotoSansCJKsc-Regular.otf`
- variable `DND_CJK_FONT_PATH`
- autodeteccion en `C:/Windows/Fonts`
Si no hay glifo soportado, se reemplaza por `?` para evitar fallos de escritura PDF.
## Variables de entorno utiles
- `DND_MAX_THREADS`: hilos de traduccion.
- `DND_OCR_THREADS`: hilos OCR.
- `DND_OCR_LANG`: idioma OCR.
- `DND_TESSDATA_PATH`: carpeta `tessdata`.
- `DND_CJK_FONT_PATH`: fuente CJK (`.ttf`/`.otf`).
Ejemplo en PowerShell:
```powershell
$env:DND_MAX_THREADS="6"
$env:DND_OCR_THREADS="6"
$env:DND_OCR_LANG="spa+eng"
$env:DND_TESSDATA_PATH="C:\Tesseract-OCR\tessdata"
$env:DND_CJK_FONT_PATH="C:\Windows\Fonts\msyh.ttf"
```
## Compilar y probar
Desde la raiz del proyecto:
```powershell
mvn clean compile
mvn test
```
## Ejecutar la app JavaFX
```powershell
mvn javafx:run
```
La clase principal configurada en Maven es `com.dndtranslator.TranslatorUI`.
## Flujo de uso rapido
1. Abrir app.
2. Seleccionar PDF.
3. Pulsar Traducir.
4. Revisar logs de progreso (extraccion, OCR fallback si aplica, traduccion, reconstruccion).
5. Buscar archivo de salida: `<archivo_original>_translated_layout.pdf`.
## Salidas generadas
- PDF traducido junto al original con sufijo `_translated_layout.pdf`.
- Base SQLite local `translations.db` para cache de traducciones.
