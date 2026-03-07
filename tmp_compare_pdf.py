from pypdf import PdfReader
from pathlib import Path
def extract(path):
    r = PdfReader(str(path))
    txt = "\n".join((p.extract_text() or "") for p in r.pages)
    return len(r.pages), len(txt), txt
orig = Path(r"C:\Users\Mariano\Documents\7th Sea - Novus Ordo Mundi one page.pdf")
tran = Path(r"C:\Users\Mariano\Documents\7th Sea - Novus Ordo Mundi one page_translated_layout.pdf")
op, oc, ot = extract(orig)
tp, tc, tt = extract(tran)
print("orig_pages", op)
print("orig_chars", oc)
print("tran_pages", tp)
print("tran_chars", tc)
print("orig_preview_start")
print(ot[:1000])
print("orig_preview_end")
print("tran_preview_start")
print(tt[:1000])
print("tran_preview_end")
