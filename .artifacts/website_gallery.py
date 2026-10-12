from pathlib import Path
p=Path('index.html')
s=p.read_text(encoding='utf-8')
marker='''        <p class="asset-note reveal">'''
start=s.index(marker)
end=s.index('        </p>',start)+len('        </p>')
extra='''

        <header class="section-head reveal">
          <h3>See Halo in action.</h3>
          <p>Real screens from the same alpha build: phone assistance, local PDF tools, guided setup and model management.</p>
        </header>
        <div class="screen-gallery screen-gallery--product">
'''
for name,title,alt,caption in [
('assistant-action','Phone assistance','Halo showing a phone battery request and its actual result.','A phone request and the outcome reported by Halo.'),
('canvas-pdf','PDF tools','Halo Canvas with a sample PDF and page tools.','Open a local PDF and choose pages to extract into your workspace.'),
('onboarding','Guided setup','Halo guided setup reopened from Settings.','Revisit setup without clearing your saved workspace.'),
('models','Local models','Halo local model management screen.','Inspect installed models and choose what runs on your phone.')]:
 extra+=f'''          <figure class="screen-card shot-reveal">
            <img loading="lazy" src="assets/screens/{name}.png" width="1080" height="2400" alt="{alt}" />
            <figcaption><b>{title}</b><span>{caption}</span></figcaption>
          </figure>
'''
extra+='        </div>'
s=s[:end]+extra+s[end:]
p.write_text(s,encoding='utf-8')
