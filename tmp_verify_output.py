from pypdf import PdfReader
from pathlib import Path
import re
p = Path(r"C:\Users\Mariano\Documents\7th Sea - Novus Ordo Mundi one page_translated_layout.pdf")
reader = PdfReader(str(p))
text = "\n".join((pg.extract_text() or "") for pg in reader.pages)
print("exists", p.exists())
print("pages", len(reader.pages))
print("chars", len(text))
print("question_marks", text.count("?"))
print("has_cjk_char_酷", "酷" in text)
print("english_hits", len(re.findall(r"\\b(the|and|with|for|from|this|that|is|are|was)\\b", text.lower())))
print("preview_start")
print(text[:1800])
print("preview_end")
