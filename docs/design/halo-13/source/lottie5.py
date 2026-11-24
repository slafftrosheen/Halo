import json,os,math
exec(open('lhelp.py').read())
def ladder(c):
    cols=[c['m'],c['m'],c['l'],c['l'],c['sky'],c['a']]
    L=[layer(f'Rung {i}',[gr('r',[rc([0,0],[150,22],8),fl(K([(0,hx(c['fg'],1)),(10+i*12,hx(c['fg'],1)),(16+i*12,hx(cols[i]))]),K([(0,12),(10+i*12,12),(16+i*12,90)]))])],0,90,p=[128,200-i*30]) for i in range(6)]
    hand=layer('You',[gr('d',[el([0,0],[16,16]),fl(hx(c['m']))])],0,90,p=K([(0,[38,200,0])]+[(10+i*12,[38,200-i*30,0]) for i in range(6)]))
    return comp('Help ladder',90,[hand]+L)
def flip(c):
    card=layer('Card',[gr('c',[rc([0,0],[110,140],14),fl(K([(0,hx(c['l'])),(22,hx(c['l'])),(23,hx(c['m']))]))])],0,90,
        p=K([(0,[128,118,0]),(46,[128,118,0]),(66,[200,210,0])]),s=K([(0,[100,100,100]),(14,[100,100,100]),(22,[0,100,100]),(30,[100,100,100]),(46,[100,100,100]),(66,[30,30,100])]))
    slots=[layer(f'Day {i}',[gr('s',[rc([0,0],[34,40],8),st(hx(c['fg']),2,35)])],0,90,p=[48+i*38,210]) for i in range(5)]
    return comp('Review card · see it again in 4 days',90,[card]+slots)
def exam(c):
    days=[layer(f'Day {i}',[gr('d',[rc([0,0],[22,22],6),fl(K([(0,hx(c['fg'],1)),(8+i*5,hx(c['fg'],1)),(12+i*5,hx(c['m'] if i<9 else c['a']))]),K([(0,14),(8+i*5,14),(12+i*5,100)]))])],0,90,p=[50+(i%5)*39,90+(i//5)*40]) for i in range(10)]
    flag=layer('Exam',[gr('f',[sh([P(0,30),P(0,-14),P(22,-6),P(0,2)]),st(hx(c['a']),4)])],0,90,p=[128,200],s=K([(0,[0,0,100]),(58,[0,0,100]),(66,[115,115,100]),(72,[100,100,100])]))
    return comp('Exam plan',90,[flag]+days)
def adventure(c):
    stops=[(56,190,c['m']),(100,110,c['l']),(160,160,c['a']),(206,80,c['sun'])]
    S=[layer(f'Stop {i}',[gr('s',[el([0,0],[30,30]),fl(hx(col))])],0,90,p=[x,y],s=K([(0,[100,100,100]),(14+i*18,[100,100,100]),(19+i*18,[125,125,100]),(24+i*18,[100,100,100])])) for i,(x,y,col) in enumerate(stops)]
    path=layer('Path',[gr('p',[sh([P(56,190),P(100,110),P(160,160),P(206,80)]),st(hx(c['fg']),3,40)],tr(p=[-128,-128]))],0,90,p=[128,128])
    fox=layer('Buddy',[gr('f',[el([0,0],[18,18]),fl(hx(c['fg']))])],0,90,p=K([(0,[56,166,0])]+[(14+i*18,[x,y-24,0]) for i,(x,y,_) in enumerate(stops)]))
    return comp('Kids adventure · no points',90,[fox]+S+[path])
def handback(c):
    ph=layer('Phone',[gr('p',[rc([0,0],[70,120],14),fl(hx(c['card'])),st(hx(c['fg']),3,70)])],0,90,p=K([(0,[70,128,0]),(40,[186,128,0])]),r=None) if False else layer('Phone',[gr('p',[rc([0,0],[70,120],14),fl(hx(c['card'])),st(hx(c['fg']),3,70)])],0,90,p=K([(0,[70,128,0]),(40,[186,128,0])]))
    lock=layer('Lock',[gr('b',[rc([0,8],[30,24],6),fl(hx(c['m']))]),gr('a',[sh([P(-9,-2),P(-9,-12),P(9,-12),P(9,-2)]),st(hx(c['m']),4)],tr(p=K([(0,[0,-6]),(46,[0,-6]),(56,[0,0])])))],0,90,p=[186,128],o=K([(0,0),(40,0),(46,100)]))
    return comp('Hand back to a grown-up',90,[lock,ph])
M={'help_ladder':ladder,'card_flip':flip,'exam_plan':exam,'kids_adventure':adventure,'hand_back':handback}
os.makedirs('out/raw',exist_ok=True)
for k,f in M.items():
    for v in ('dark','light'): json.dump(f(PAL[v]),open(f'out/raw/halo13_motion_{k}_{v}.json','w'),separators=(',',':'))
print('ok')
