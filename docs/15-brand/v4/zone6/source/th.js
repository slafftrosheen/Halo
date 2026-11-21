/* Real halo themes from app/.../ui/theme/BrandAppearance.kt */
const TONES={dark:{NATURAL:['#060330','#110E45','#1A1760','#FAFAF4','#B7B9D3'],WARM:['#160E1C','#22182A','#2E2238','#FBF6F0','#C9BCC4'],COOL:['#06142A','#0E2038','#172C48','#F4F8FC','#B3C2D4'],PAPER:['#191813','#23221C','#302F27','#F8F5E9','#C4C0AE'],SLATE:['#11191C','#1A2529','#29363B','#F0F6F5','#B7C9CB']},
 light:{NATURAL:['#FAFAF4','#FFFFFF','#EFF0E8','#060330','#4A4F63'],WARM:['#FFF5EC','#FFFFFF','#F7E9DD','#1A0F14','#5E4F55'],COOL:['#F3F6FA','#FFFFFF','#E5ECF4','#06142A','#46566B'],PAPER:['#F7F4E9','#FFFDF5','#ECE8D9','#26271F','#585C4D'],SLATE:['#EDF2F1','#F9FCFB','#DCE6E5','#172B31','#4C6268']}};
const ACC={MINT:['#0CF2BA','#047A5E','#060330'],BLUE:['#8EC5FF','#1F5FBF','#06142A'],LILAC:['#C9B8FF','#6446D6','#160E3A'],CORAL:['#FF9EB3','#B8325A','#2A0A14'],SAGE:['#BED5B1','#42643E','#182C20'],ROSE:['#E5BDC9','#885066','#38202A']};
const ANAME={MINT:'Mint',BLUE:'Blue',LILAC:'Lilac',CORAL:'Coral',SAGE:'Sage',ROSE:'Rose'};
const TNAME={NATURAL:'Natural',WARM:'Warm',COOL:'Cool',PAPER:'Paper',SLATE:'Slate'};
function themeVars(dark,accent,tone){const t=TONES[dark?'dark':'light'][tone],a=ACC[accent];
 return `--ink:${t[0]};--s1:${t[1]};--s2:${t[2]};--cream:${t[3]};--t2:${t[4]};--t3:${t[4]};--mint:${dark?a[0]:a[1]};--acc:${a[0]};--onacc:${dark?a[2]:'#FFFFFF'};--line:${dark?'rgba(250,250,244,.10)':'rgba(6,3,48,.10)'}`}
function themed(html,dark,accent,tone){const a=ACC[accent];
 return html.replaceAll('#0CF2BA',a[0]).replaceAll('#B4E6D6',dark?a[0]:a[1]).replace('class="phone','style="'+themeVars(dark,accent,tone)+'" class="th '+(dark?'':'lt ')+'phone')}
/* launcher icon (adaptive 108 grid, safe zone 66) */
function appIcon(kind,accent,size=96,shape='squircle'){
 const a=ACC[accent]; let bg,ring,glow;
 if(kind=='dark'){bg='#060330';ring=a[0];glow=a[0]}
 if(kind=='light'){bg='#FAFAF4';ring=a[1];glow=a[0]}
 if(kind=='tone'){bg=TONES.dark.PAPER[0];ring=a[0];glow=a[0]}
 if(kind=='mono'){bg='#2C3A36';ring='#CDE8DD';glow=null}
 const r=shape=='circle'?'50%':shape=='squircle'?'30%':'22%';
 const g='ag'+(gid++);
 return `<div class="appi" style="width:${size}px;height:${size}px;border-radius:${r};background:${bg}"><svg viewBox="0 0 108 108" width="${size}" height="${size}">
 ${glow?`<defs><radialGradient id="${g}" cx="54" cy="54" r="46" gradientUnits="userSpaceOnUse"><stop stop-color="${glow}" stop-opacity="${kind=='light'?.28:.34}"/><stop offset="1" stop-color="${glow}" stop-opacity="0"/></radialGradient></defs><circle cx="54" cy="54" r="46" fill="url(#${g})"/>`:''}
 <path d="M75.61 46.13A23 23 0 1 1 61.87 32.39" stroke="${ring}" stroke-width="9" stroke-linecap="round" fill="none"/></svg></div>`}
/* in-app icon styles */
function icon(name,style,size=24){
 const svg=`<svg viewBox="0 0 24 24" fill="none" style="width:${size}px;height:${size}px;position:relative">${IC[name]}</svg>`;
 if(style=='line') return `<span class="ist line">${svg}</span>`;
 if(style=='spot') return `<span class="ist spot"><i></i>${svg}</span>`;
 if(style=='tile') return `<span class="ist tl">${svg}</span>`;
}
