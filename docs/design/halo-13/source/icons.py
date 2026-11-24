import json,os
def rr(x,y,w,h,r): return f"M{x+r} {y}H{x+w-r}A{r} {r} 0 0 1 {x+w} {y+r}V{y+h-r}A{r} {r} 0 0 1 {x+w-r} {y+h}H{x+r}A{r} {r} 0 0 1 {x} {y+h-r}V{y+r}A{r} {r} 0 0 1 {x+r} {y}Z"
def c(cx,cy,r): return f"M{cx-r} {cy}A{r} {r} 0 1 0 {cx+r} {cy}A{r} {r} 0 1 0 {cx-r} {cy}Z"
IC={
'src_news':[rr(3,4.5,15,15,2),"M18 8.5H20A1 1 0 0 1 21 9.5V17A2.5 2.5 0 0 1 18.5 19.5","M6.5 8.5H14.5","M6.5 12H14.5","M6.5 15.5H11"],
'src_official':["M3 9.5L12 4L21 9.5","M5 9.5V17","M9.5 9.5V17","M14.5 9.5V17","M19 9.5V17","M3 20H21"],
'src_science':["M9.5 3.5V9L4.5 18A1.5 1.5 0 0 0 5.8 20.5H18.2A1.5 1.5 0 0 0 19.5 18L14.5 9V3.5","M8 3.5H16","M7 15H17"],
'src_community':["M4 6A2 2 0 0 1 6 4H14A2 2 0 0 1 16 6V11A2 2 0 0 1 14 13H9L6 15.5V13H6A2 2 0 0 1 4 11Z","M16 9H18A2 2 0 0 1 20 11V16A2 2 0 0 1 18 18V20L15.5 18H12A2 2 0 0 1 10 16V15.5"],
'src_own':["M4 11.5L12 5L20 11.5","M6 10V19.5H18V10","M10 19.5V14.5H14V19.5"],
'src_book':["M5 4.5H17A2 2 0 0 1 19 6.5V20.5H7A2 2 0 0 1 5 18.5Z","M5 18.5A2 2 0 0 1 7 16.5H19","M9 8.5H15"],
'kid_safe':["M12 3L19 6V11.5C19 15.8 16 19.3 12 21C8 19.3 5 15.8 5 11.5V6Z",c(10,11,0.6),c(14,11,0.6),"M9.5 14.5A3 3 0 0 0 14.5 14.5"],
'fresh':[c(12,12,8.5),"M12 7.5V12H15.5","M3.5 4.5L5.5 6.5"],
'paused_src':[c(12,12,8.5),"M10 9V15","M14 9V15"],
'add_link':["M10 14L14 10","M8.5 11.5L6.5 13.5A3 3 0 0 0 10.5 17.5L12.5 15.5","M11.5 8.5L13.5 6.5A3 3 0 0 1 17.5 10.5L15.5 12.5","M19 16V22","M16 19H22"],
'your_mix':[c(12,12,8.5),"M12 3.5V12L18.5 17.5","M12 12L3.7 10"],
'timeline':["M7 4V20","M10 6H19A1 1 0 0 1 20 7V9A1 1 0 0 1 19 10H10","M10 13H16A1 1 0 0 1 17 14V16A1 1 0 0 1 16 17H10",c(7,8,1.2),c(7,15,1.2)],
'week_view':[rr(3,4.5,18,16,2.5),"M3 9H21","M8 4.5V20.5","M13 4.5V20.5","M8 2.5V5","M16 2.5V5"],
'month_view':[rr(3,4.5,18,16,2.5),"M3 9H21",c(7.5,13,0.6),c(12,13,0.6),c(16.5,13,0.6),c(7.5,17,0.6),c(12,17,0.6)],
'drag_block':[rr(4,8,12,7,2),"M18 5V20","M16 7L18 5L20 7","M16 18L18 20L20 18"],
'find_time':[rr(3,4.5,13,13,2),"M3 9H16",c(17,17,3.5),"M19.5 19.5L21.5 21.5","M7 12.5H9"],
'leave_by':["M6 4H18V17H6Z","M6 12H18",c(8.5,19.5,1.2),c(15.5,19.5,1.2),"M9 7H15","M20 8V11"],
'too_much':["M12 4V20","M7 20H17","M5 8H19","M5 8L2.5 14A2.5 2.5 0 0 0 7.5 14Z","M19 8L16.5 13A2.5 2.5 0 0 0 21.5 13Z"],
'someday':["M4 13L6.5 5.5A2 2 0 0 1 8.4 4H15.6A2 2 0 0 1 17.5 5.5L20 13V18A2 2 0 0 1 18 20H6A2 2 0 0 1 4 18Z","M4 13H8.5L10 15.5H14L15.5 13H20"],
'repeat':["M4 11V9.5A3.5 3.5 0 0 1 7.5 6H19","M16 3L19 6L16 9","M20 13V14.5A3.5 3.5 0 0 1 16.5 18H5","M8 21L5 18L8 15"],
'family_lane':[rr(3,4,18,16,2.5),"M9 4V20","M15 4V20",c(6,7.5,0.8),c(12,7.5,0.8),c(18,7.5,0.8),"M4.5 11H7.5","M10.5 14H13.5","M16.5 12H19.5"],
}
A='stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"'
inner={k:''.join(f'<path d="{d}" {A}/>' for d in v) for k,v in IC.items()}
os.makedirs('out/svg',exist_ok=True);os.makedirs('out/drawable',exist_ok=True)
for k,v in IC.items():
    open(f'out/svg/halo_ic_{k}.svg','w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none">{inner[k]}</svg>')
    ps=''.join(f'\n  <path android:pathData="{d}" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.75" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillAlpha="1.0" android:strokeAlpha="1.0" />' for d in v)
    open(f'out/drawable/halo_ic_{k}.xml','w').write(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24.0" android:viewportHeight="24.0">{ps}\n</vector>\n')
open('/tmp/w13/src/icons13c.js','w').write('Object.assign(window.IC,'+json.dumps(inner)+');')
print(len(IC))
