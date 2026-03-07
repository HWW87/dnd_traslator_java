from pypdf import PdfReader
from pathlib import Path
import re, json
p = Path(r"C:\Users\Mariano\Documents\7th Sea - Novus Ordo Mundi one page_translated_layout.pdf")
reader = PdfReader(str(p))
text = "\n".join((pg.extract_text() or "") for pg in reader.pages)
payload = {
    "exists": p.exists(),
    "pages": len(reader.pages),
    "chars": len(text),
    "question_marks": text.count("?"),
    "has_cjk_char": ("酷" in text),
    "english_hits": len(re.findall(r"\\b(the|and|with|for|from|this|that|is|are|was)\\b", text.lower())),
    "sample": text[:1200]
}
out = Path(r"D:\Desarrollos para PC\dnd_traslator_java\tmp_verify_output_metrics.json")
out.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")
print(out)
