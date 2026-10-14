# Run from repository root with python -X utf8 scripts/generate_brand_assets.py
from pathlib import Path
import xml.etree.ElementTree as E, math, shutil, json
A='http://schemas.android.com/apk/res/android'; P='http://schemas.android.com/aapt'; E.register_namespace('android',A); E.register_namespace('aapt',P)
root=Path(__file__).resolve().parents[1]; out=root/'app/src/main/res/drawable'; assets=root/'app/src/main/assets/halo'; assets.mkdir(parents=True,exist_ok=True)
# Preserve supplied paths and gradients; VectorDrawable is the Android static equivalent.
def convert(src,dst,colors=None):
 svg=E.parse(src).getroot(); defs={e.get('id'):e for e in svg.iter() if e.get('id')}; ink=svg.get('style','color:#FAFAF4').split(':')[-1]
 def color(v):
  v=ink if v=='currentColor' else v
  if colors: v=colors.get(v.upper(),v)
  return '#00000000' if v=='none' else v
 vec=E.Element('vector',{f'{{{A}}}width':'240dp',f'{{{A}}}height':'240dp',f'{{{A}}}viewportWidth':'240',f'{{{A}}}viewportHeight':'240'})
 for e in svg.iter():
  tag=e.tag.split('}')[-1]
  if tag not in ['circle','path']: continue
  d=e.get('d')
  if tag=='circle':
   x,y,r=map(float,[e.get('cx'),e.get('cy'),e.get('r')]); d=f'M{x-r},{y}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0'
  if e.get('stroke-dasharray'):
   # Exact dash rhythm of the supplied circular offline arc.
   parts=[]; length=84*math.radians(316); pos=0
   while pos<length:
    a=math.radians(-23)-pos/84; b=math.radians(-23)-min(pos+26,length)/84
    parts.append(f'M{120+84*math.cos(a)},{120+84*math.sin(a)}A84,84 0 0,0 {120+84*math.cos(b)},{120+84*math.sin(b)}'); pos+=48
   d=' '.join(parts)
  p=E.SubElement(vec,'path',{f'{{{A}}}pathData':d})
  for key,target in [('stroke-width','strokeWidth'),('stroke-linecap','strokeLineCap'),('stroke-linejoin','strokeLineJoin')]:
   if e.get(key): p.set(f'{{{A}}}{target}',e.get(key))
  for key,target in [('fill','fillColor'),('stroke','strokeColor')]:
   v=e.get(key,'none')
   p.set(f'{{{A}}}{key}Alpha',str(float(e.get('opacity','1'))*float(e.get(key+'-opacity','1'))))
   if v.startswith('url'):
    g=defs[v[5:-1]]; radial=g.tag.endswith('radialGradient'); item=E.SubElement(p,f'{{{P}}}attr',{'name':'android:'+target}); gr=E.SubElement(item,'gradient',{f'{{{A}}}type':'radial' if radial else 'linear'})
    keys=[('cx','centerX'),('cy','centerY'),('r','gradientRadius')] if radial else [('x1','startX'),('y1','startY'),('x2','endX'),('y2','endY')]
    for a,b in keys: gr.set(f'{{{A}}}{b}',g.get(a))
    for stop in g:
     rgb=color(stop.get('stop-color')); alpha=round(255*float(stop.get('stop-opacity','1'))); rgb=f'#{alpha:02X}'+rgb[1:]
     E.SubElement(gr,'item',{f'{{{A}}}offset':stop.get('offset','0'),f'{{{A}}}color':rgb})
   else:p.set(f'{{{A}}}{target}',color(v))
 E.indent(vec); dst.write_text(E.tostring(vec,encoding='unicode')+'\n')
# Four runtime accent variants; Kids keeps the supplied Mint/Apricot gradient.
t=json.loads((root/'BrandAssets/theme/halo_themes.json').read_text()); states=[]
for src in (root/'BrandAssets/orb/svg').glob('*_dark.svg'): states.append(src.stem.removeprefix('halo_orb_').removesuffix('_dark'))
for family in ['standard','kids']:
 source=root/('BrandAssets/orb' if family=='standard' else 'BrandAssets/kids')
 folder='svg' if family=='standard' else 'orb_svg'; prefix='halo_orb_' if family=='standard' else 'halo_kids_orb_'
 for mode in ['dark','light']:
  for state in states:
   for accent in (t['accents'] if family=='standard' else ['mint']):
    colors={'#0CF2BA':t['accents'][accent]['ring'][0],'#B4E6D6':t['accents'][accent]['ring'][1]} if family=='standard' else None
    convert(source/folder/f'{prefix}{state}_{mode}.svg',out/f'halo_presence_{family}_{state}_{mode}_{accent}.xml',colors)
 for src in (source/'lottie').glob('*.json'):
  shutil.copyfile(src,assets/src.name)
# Micro states never include glyphs/glow.
for src in (root/'BrandAssets/orb/micro').glob('*.svg'): convert(src,out/(src.stem+'.xml'))
for src in (root/'BrandAssets/icons/android').glob('*.xml'): shutil.copyfile(src,out/src.name)
for src in (root/'BrandAssets/launcher').glob('*.xml'):
 if src.name!='ic_launcher.xml': shutil.copyfile(src,out/src.name)
# Asset resolver keeps resource references checked by the Kotlin compiler.
lines=['package com.halo.app.ui.presence','','import com.halo.app.R','','enum class PresenceArtwork(val key: String) {']
lines+=['    '+s.upper()+'("'+s+'"),' for s in states]; lines+=['}','','internal fun PresenceArtwork.staticResource(dark: Boolean, kids: Boolean, accent: Int): Int {','    val variant = if (kids) 8 + (if (dark) 0 else 1) else accent * 2 + (if (dark) 0 else 1)','    return when (this) {']
for state in states:
 ids=[f'R.drawable.halo_presence_standard_{state}_{mode}_{acc}' for acc in t['accents'] for mode in ['dark','light']]+[f'R.drawable.halo_presence_kids_{state}_{mode}_mint' for mode in ['dark','light']]
 lines+=['        PresenceArtwork.'+state.upper()+' -> intArrayOf('+', '.join(ids)+')[variant]']
lines+=['    }','}','','internal fun PresenceArtwork.microResource(): Int = when (this) {']
lines+=['    PresenceArtwork.'+s.upper()+' -> R.drawable.halo_orb_micro_'+s for s in states];lines+=['}']
(root/'app/src/main/java/com/halo/app/ui/presence/PresenceArtwork.kt').write_text('\n'.join(lines)+'\n')
