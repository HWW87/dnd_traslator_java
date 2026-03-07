from pypdf import PdfReader
from pathlib import Path
import re
p = Path(r"C:\Users\Mariano\Documents\7th Sea - Novus Ordo Mundi one page_translated_layout.pdf")
print('exists', p.exists())
reader = PdfReader(str(p))
text = '\n'.join((pg.extract_text() or '') for pg in reader.pages)
print('pages', len(reader.pages))
print('chars', len(text))
print('question_marks', text.count('?'))
print('contains_cool_char', '酷' in text)
print('sample_lines_with_question_mark')
for ln in text.splitlines():
    if '?' in ln:
        print(ln[:220])
print('english_hits', len(re.findall(r"\\b(the|and|with|for|from|this|that|is|are|was)\\b", text.lower())))
print('preview')
print(text[:1600])
