from pypdf import PdfReader
from pathlib import Path
import json, re
p = Path(r"C:\Users\Mariano\Documents\7th Sea - Novus Ordo Mundi one page_translated_layout.pdf")
reader = PdfReader(str(p))
text = '\n'.join((pg.extract_text() or '') for pg in reader.pages)
lines_q = [ln for ln in text.splitlines() if '?' in ln]
payload = {
  'exists': p.exists(),
  'pages': len(reader.pages),
  'chars': len(text),
  'question_marks': text.count('?'),
  'contains_cool_char': ('酷' in text),
  'english_hits': len(re.findall(r"\\b(the|and|with|for|from|this|that|is|are|was)\\b", text.lower())),
  'sample_lines_with_question_mark': lines_q[:5],
}
out = Path(r"D:\Desarrollos para PC\dnd_traslator_java\tmp_pdf_review_metrics.json")
out.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding='utf-8')
print(str(out))
