import img2pdf,glob,io
from pypdf import PdfReader,PdfWriter
P='/mnt/session/uploads/previous-session'
lay=img2pdf.get_layout_fun((img2pdf.in_to_pt(25),img2pdf.in_to_pt(15.625)))
def imgpdf(files):
  return PdfReader(io.BytesIO(img2pdf.convert(files,layout_fun=img2pdf.get_layout_fun((1800,1125)))))
w=PdfWriter()
cov=imgpdf(sorted(glob.glob('/tmp/w13/book/out/cover/*.jpg')))
f2=imgpdf(sorted(glob.glob('/tmp/w13/book/out/front2/*.jpg')))
c30=imgpdf(sorted(glob.glob('/tmp/w13/book/out/ch30/*.jpg')))
v7=PdfReader(P+'/halo-brand-book-v7.0.pdf')
add=PdfReader('/tmp/w/halo-complete-9.5-11.0/halo-additions-9.5-11.0.pdf')
b12=PdfReader('/tmp/w/h12/docs/design/halo-12/book/halo-12-book.pdf')
w.add_page(cov.pages[0])
for i in range(1,347): w.add_page(v7.pages[i])
w.add_page(f2.pages[0]);w.add_page(f2.pages[1]);w.add_page(f2.pages[2])
for i in range(42,57):
  pg=add.pages[i];pg.scale_by(1.25);w.add_page(pg)
w.add_page(f2.pages[3])
for i in range(2,50): w.add_page(b12.pages[i])
for pg in c30.pages: w.add_page(pg)
for pg in w.pages:
  mb=pg.mediabox
  if (round(float(mb.width)),round(float(mb.height)))!=(1800,1125): print('size',mb)
w.add_metadata({'/Title':'halo brand book v8.0 — Made for everyone','/Author':'halo design'})
w.write('/mnt/session/outputs/halo-brand-book-v8.0.pdf');print(len(w.pages))
