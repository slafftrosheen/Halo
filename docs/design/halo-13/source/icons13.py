import json,os
def rr(x,y,w,h,r): return f"M{x+r} {y}H{x+w-r}A{r} {r} 0 0 1 {x+w} {y+r}V{y+h-r}A{r} {r} 0 0 1 {x+w-r} {y+h}H{x+r}A{r} {r} 0 0 1 {x} {y+h-r}V{y+r}A{r} {r} 0 0 1 {x+r} {y}Z"
def c(cx,cy,r): return f"M{cx-r} {cy}A{r} {r} 0 1 0 {cx+r} {cy}A{r} {r} 0 1 0 {cx-r} {cy}Z"
IC={
'level_simple':[rr(4.5,4.5,15,15,3.5),"M9 12H15"],
'level_standard':[rr(4,4,7,7,2),rr(13,4,7,7,2),rr(4,13,7,7,2),rr(13,13,7,7,2)],
'level_pro':["M4 7H7M11 7H20","M4 12H13M17 12H20","M4 17H5M9 17H20",c(9,7,2),c(15,12,2),c(7,17,2)],
'plain_words':["M5 5H19A2 2 0 0 1 21 7V15A2 2 0 0 1 19 17H11L7 20.5V17H5A2 2 0 0 1 3 15V7A2 2 0 0 1 5 5Z","M7.5 9.5H16.5","M7.5 12.5H13"],
'explain_this':["M4 6H15","M4 10H10","M4 14H8",c(15.5,14.5,3.5),"M18 17L20.5 19.5"],
'adapts':[rr(3.5,3.5,8,8,2),c(16.5,16.5,4),"M14 5.5H17A2 2 0 0 1 19 7.5V10","M17 8.5L19 10.5L21 8.5","M10 18.5H7A2 2 0 0 1 5 16.5V14","M3 15.5L5 13.5L7 15.5"],
'one_action':[rr(3,10,18,8,4),"M12 3V6","M7.5 4.5L9 6.5","M16.5 4.5L15 6.5"],
'why':[c(12,12,9),"M9.6 9.5A2.5 2.5 0 1 1 12.6 11.9C12 12.1 12 12.6 12 13.3","M12 16.6V16.7"],
'pin_layout':["M9 4H15L14 10L17 13H7L10 10L9 4Z","M12 13V20"],
'reset_layout':["M5.1 8.5A7.5 7.5 0 1 1 4.5 12","M4.5 4.5V8.5H8.5"],
'wallpaper':[rr(3,4,18,16,2.5),"M3.5 16L9 11L13 15L15.5 12.5L20.5 17",c(16,8.5,1.6)],
'sky_day':["M3 18H21","M7 18A5 5 0 0 1 17 18","M12 8V6","M6.3 10.3L5 9","M17.7 10.3L19 9"],
'sky_night':["M15.5 4A7.5 7.5 0 1 0 20 14.5A6 6 0 0 1 15.5 4Z","M6 4V7","M4.5 5.5H7.5"],
'season':["M5 19C5 10 10 5 19 5C19 14 14 19 5 19Z","M5 19L13 11"],
'weather_mood':["M7 19H17A3.5 3.5 0 0 0 17 12A5 5 0 0 0 7.4 11.2A3.9 3.9 0 0 0 7 19Z","M17 3.5V5","M21 7L19.9 8.1","M13 7L14.1 8.1"],
'look':["M12 3A9 9 0 1 0 12 21C13.4 21 13.9 20 13.3 18.9C12.6 17.6 13.4 16 15 16H17A4 4 0 0 0 21 12C21 7 17 3 12 3Z",c(7.5,11.5,1),c(9.5,7.5,1),c(14.5,7.5,1),c(17,11,1)],
'text_size':["M3 18L7.5 7L12 18","M4.6 14H10.4","M13.5 18L16.75 10.5L20 18","M14.7 15.5H18.8"],
'contrast':[c(12,12,8.5),"M12 3.5V20.5","M12 8H18.6","M12 12H20.5","M12 16H18.6"],
'calm_motion':["M3 10C5 7 7 7 9 10S13 13 15 10S19 7 21 10","M3 16C5 14.5 7 14.5 9 16S13 17.5 15 16S19 14.5 21 16"],
'tint':["M12 3.5C12 3.5 6 10 6 14A6 6 0 0 0 18 14C18 10 12 3.5 12 3.5Z","M9 14.5A3 3 0 0 0 12 17.5"],
'helper':[c(12,12,8.5),c(12,12,3.5),"M6 6L9.5 9.5","M18 6L14.5 9.5","M6 18L9.5 14.5","M18 18L14.5 14.5"],
'show_more':[rr(4,4,16,16,4),"M12 8.5V15.5","M8.5 12H15.5"],
'show_less':[rr(4,4,16,16,4),"M8.5 12H15.5"],
'tip':["M12 3L13.6 10.4L21 12L13.6 13.6L12 21L10.4 13.6L3 12L10.4 10.4Z"],
'anchor':[c(12,5,2),"M12 7V20","M5 13A7 7 0 0 0 19 13","M8.5 10H15.5"],
}
A='stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"'
inner={k:''.join(f'<path d="{d}" {A}/>' for d in v) for k,v in IC.items()}
os.makedirs('out/svg',exist_ok=True);os.makedirs('out/drawable',exist_ok=True)
for k,v in IC.items():
    open(f'out/svg/halo_ic_{k}.svg','w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none">{inner[k]}</svg>')
    ps=''.join(f'\n  <path android:pathData="{d}" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.75" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillAlpha="1.0" android:strokeAlpha="1.0" />' for d in v)
    open(f'out/drawable/halo_ic_{k}.xml','w').write(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24.0" android:viewportHeight="24.0">{ps}\n</vector>\n')
open('icons13.js','w').write('Object.assign(window.IC,'+json.dumps(inner)+');')
print(len(IC))
