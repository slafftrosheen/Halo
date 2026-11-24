import json,os,math
exec(open('lhelp.py').read())
def wave(x0,y0,w,amp,n=10): return [P(x0+w*i/(n-1), y0+amp*math.sin(i*1.3)) for i in range(n)]
def sheet_next(c):
    # sheet 1 fills with ink, slides left & shrinks to a thumbnail, sheet 2 slides in, ink keeps going
    s1=layer('Sheet 1',[gr('ink',[sh(wave(-50,-20,100,12)),st(hx(c['l']),5),tm(K([(0,0),(30,100)]))]),gr('ink2',[sh(wave(-50,10,100,10)),st(hx(c['l']),5),tm(K([(0,0),(14,0),(34,100)]))]),gr('p',[rc([0,0],[130,160],14),fl(hx(c['card'])),st(hx(c['fg']),2,30)])],0,90,
        p=K([(0,[128,128,0]),(40,[128,128,0]),(56,[48,196,0])]),s=K([(0,[100,100,100]),(40,[100,100,100]),(56,[34,34,100])]))
    s2=layer('Sheet 2',[gr('ink',[sh(wave(-50,-20,100,12)),st(hx(c['m']),5),tm(K([(0,0),(58,0),(84,100)]))]),gr('p',[rc([0,0],[130,160],14),fl(hx(c['card'])),st(hx(c['m']),3,90)])],0,90,
        p=K([(0,[330,128,0]),(42,[330,128,0]),(58,[128,128,0])]))
    badge=layer('2',[gr('b',[el([0,0],[22,22]),fl(hx(c['m']))])],0,90,p=[190,56],s=K([(0,[0,0,100]),(56,[0,0,100]),(64,[120,120,100]),(70,[100,100,100])]))
    return comp('Keep drawing · next sheet',90,[badge,s2,s1])
def tidy(c):
    cols=[c['m'],c['l'],c['a'],c['sky'],c['m'],c['l']]
    start=[(60,70),(180,60),(110,130),(200,170),(70,190),(150,200)]
    end=[(70,80),(186,80),(70,128),(186,128),(70,176),(186,176)]
    L=[layer(f'Card {i}',[gr('c',[rc([0,0],[56,36],8),fl(hx(cols[i]),80)])],0,90,p=K([(0,[*start[i],0]),(20+i*3,[*start[i],0]),(46+i*3,[*end[i],0])]),r=None) if False else
       layer(f'Card {i}',[gr('c',[rc([0,0],[56,36],8),fl(hx(cols[i]),80)])],0,90,p=K([(0,[*start[i],0]),(20+i*3,[*start[i],0]),(46+i*3,[*end[i],0])])) for i in range(6)]
    g=[layer(f'Group {j}',[gr('g',[rc([0,0],[100,150],14),st(hx(c['fg']),2.5,60)])],0,90,p=[[70,128],[186,128]][j],o=K([(0,0),(56,0),(66,100)])) for j in range(2)]
    return comp('Tidy up',90,g+L)
def face(c):
    top=layer('Their side',[gr('b',[rc([0,0],[150,40],12),fl(hx(c['l']),85)])],0,90,p=[128,70],r=None) if False else layer('Their side',[gr('b',[rc([0,0],[150,40],12),fl(hx(c['l']),85)])],0,90,p=[128,72],o=K([(0,0),(42,0),(52,100)]),s=K([(0,[90,90,100]),(42,[90,90,100]),(52,[100,100,100])]))
    top['ks']['r']=S(180)
    mine=layer('Your words',[gr('b',[rc([0,0],[150,40],12),fl(hx(c['m']),85)])],0,90,p=K([(0,[128,230,0]),(10,[128,184,0])]),o=K([(0,0),(10,100)]))
    line=layer('Fold',[gr('l',[sh([P(-100,0),P(100,0)]),st(hx(c['fg']),2,40)])],0,90,p=[128,128])
    dots=[layer(f'Dot {i}',[gr('d',[el([0,0],[10,10]),fl(hx(c['fg']))])],0,90,p=[108+i*20,128],o=K([(0,0),(14+i*4,0),(20+i*4,100),(40,100),(44,0)])) for i in range(3)]
    return comp('Face to face',90,[top,mine,line]+dots)
def scan_edges(c):
    page=layer('Page',[gr('p',[rc([0,0],[120,156],4),fl(hx(c['card'])),st(hx(c['fg']),1.5,30)])],0,90,p=[128,128])
    page['ks']['r']=K([(0,-8),(1,-8)])
    corners=[]
    for i,(sx,sy,ex,ey) in enumerate([(40,40,68,52),(216,40,188,52),(216,216,188,204),(40,216,68,204)]):
        corners.append(layer(f'Corner {i}',[gr('c',[sh([P(-12,0),P(0,0),P(0,12)]),st(hx(c['m']),5)],tr(r=90*i))],0,90,p=K([(0,[sx,sy,0]),(18,[sx,sy,0]),(42,[ex,ey,0])])))
    flash=layer('Done',[gr('f',[rc([0,0],[124,160],6),st(hx(c['m']),4)])],0,90,p=[128,128],o=K([(0,0),(44,0),(50,100),(80,100),(90,0)]))
    flash['ks']['r']=S(-8)
    return comp('Scan · find the edges',90,[flash]+corners+[page])
def join(c):
    a=layer('Circle A',[gr('a',[el([0,0],[90,90]),fl(hx(c['m']),70)])],0,90,p=K([(0,[70,128,0]),(30,[104,128,0])]),o=K([(0,100),(50,100),(56,0)]))
    b=layer('Circle B',[gr('b',[el([0,0],[90,90]),fl(hx(c['l']),70)])],0,90,p=K([(0,[186,128,0]),(30,[152,128,0])]),o=K([(0,100),(50,100),(56,0)]))
    w=layer('Joined',[gr('w',[el([-24,0],[90,90]),el([24,0],[90,90]),fl(hx(c['m']))])],0,90,p=[128,128],o=K([(0,0),(50,0),(56,100)]),s=K([(0,[100,100,100]),(56,[100,100,100]),(62,[106,106,100]),(68,[100,100,100])]))
    return comp('Join shapes',90,[w,a,b])
M={'sheet_next':sheet_next,'tidy_up':tidy,'face_to_face':face,'scan_edges':scan_edges,'join_shapes':join}
os.makedirs('out/raw',exist_ok=True)
for k,f in M.items():
    for v in ('dark','light'): json.dump(f(PAL[v]),open(f'out/raw/halo13_motion_{k}_{v}.json','w'),separators=(',',':'))
print('ok')
