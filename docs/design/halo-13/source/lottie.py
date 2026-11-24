import json,os,math
exec(open('lhelp.py').read())
def arc(r,a0,a1,n=16): return [P(r*math.cos(math.radians(a0+(a1-a0)*i/(n-1))),r*math.sin(math.radians(a0+(a1-a0)*i/(n-1)))) for i in range(n)]
def refresh(c):
    arrows=layer('Arrows',[gr('a1',[sh(arc(44,200,330)),st(hx(c['fg']),7,70)]),gr('h1',[sh([P(30,-44),P(40,-32),P(26,-26)]),st(hx(c['fg']),7,70)]),gr('a2',[sh(arc(44,20,150)),st(hx(c['fg']),7,70)]),gr('h2',[sh([P(-30,44),P(-40,32),P(-26,26)]),st(hx(c['fg']),7,70)])],0,75,o=K([(0,100),(40,100),(52,0)]))
    arrows['ks']['r']=K([(0,0),(40,360)])
    dot=layer('Up to date',[gr('d',[el([0,0],[60,60]),fl(K([(0,hx(c['fg'],1)),(44,hx(c['fg'],1)),(54,hx(c['m']))]),K([(0,10),(44,10),(54,100)]))]),gr('t',[sh([P(-12,0),P(-3,9),P(13,-8)]),st(hx('#04211a' if c['fg']=='#FAFAF4' else '#FFFFFF'),6),tm(K([(0,0),(52,0),(64,100)]))])],0,75,s=K([(0,[100,100,100]),(52,[100,100,100]),(58,[112,112,100]),(64,[100,100,100])]))
    return comp('Source refresh · up to date',75,[dot,arrows])
def add_src(c):
    rows=[layer(f'Row {i}',[gr('r',[rc([0,0],[150,26],8),fl(hx(c['fg']),10)]),gr('d',[el([-60,0],[12,12]),fl(hx([c['m'],c['l'],c['a']][i]))])],0,75,p=K([(0,[128,92+i*34,0]),(26,[128,92+i*34,0]),(40,[128,126+i*34,0])])) for i in range(3)]
    new=layer('New source',[gr('r',[rc([0,0],[150,26],8),fl(hx(c['m']),25),st(hx(c['m']),3)]),gr('d',[el([-60,0],[12,12]),fl(hx(c['m']))])],0,75,p=K([(0,[128,20,0]),(14,[128,20,0]),(40,[128,92,0])]),o=K([(0,0),(14,0),(22,100)]),s=K([(0,[80,80,100]),(40,[100,100,100]),(46,[104,104,100]),(52,[100,100,100])]))
    return comp('Add a source',75,[new]+rows)
def drag(c):
    lines=[layer(f'Hour {i}',[gr('h',[sh([P(-80,0),P(90,0)]),st(hx(c['fg']),2,25)])],0,90,p=[128,60+i*34]) for i in range(5)]
    fixed=layer('Event',[gr('e',[rc([0,0],[150,30],8),fl(hx(c['sky']),70)])],0,90,p=[138,77])
    blk=layer('Task block',[gr('b',[rc([0,0],[150,30],8),fl(hx(c['m']),85)])],0,90,p=K([(0,[160,210,0]),(16,[160,210,0]),(44,[138,145,0])]),s=K([(0,[100,100,100]),(14,[104,104,100]),(44,[104,104,100]),(52,[100,100,100])]),o=K([(0,60),(14,100)]))
    slot=layer('Free slot',[gr('s',[rc([0,0],[150,30],8),st(hx(c['m']),3,80)])],0,90,p=[138,145],o=K([(0,0),(10,100),(44,100),(50,0)]))
    return comp('Drag a task into your day',90,[blk,slot,fixed]+lines)
def find(c):
    cols=[layer(f'Day {d}',[gr('c',[rc([0,0],[26,150],7),fl(hx(c['fg']),8)])],0,90,p=[52+d*38,128]) for d in range(5)]
    busy=[layer(f'Busy {i}',[gr('b',[rc([0,0],[26,h],6),fl(hx(c['sky']),55)])],0,90,p=[52+d*38,y]) for i,(d,y,h) in enumerate([(0,80,30),(1,120,50),(2,90,24),(3,150,40),(4,100,30),(2,170,30)])]
    picks=[layer(f'Option {i}',[gr('o',[rc([0,0],[26,30],6),fl(hx(c['m'])),st(hx(c['m']),3)])],0,90,p=[52+d*38,y],s=K([(0,[0,0,100]),(40+i*8,[0,0,100]),(50+i*8,[110,110,100]),(56+i*8,[100,100,100])])) for i,(d,y) in enumerate([(1,170),(3,90),(4,170)])]
    scan=layer('Scan',[gr('s',[rc([0,0],[30,160],8),st(hx(c['l']),3)])],0,90,p=K([(0,[52,128,0]),(36,[204,128,0])]),o=K([(0,0),(4,100),(34,100),(40,0)]))
    return comp('Find a time',90,picks+[scan]+busy+cols)
def leave(c):
    track=layer('Track',[gr('t',[rc([0,0],[180,14],7),fl(hx(c['fg']),12)])],0,90,p=[128,150])
    fill=layer('Countdown',[gr('f',[rc([90,0],[180,14],7),fl(K([(0,hx(c['m'])),(60,hx(c['m'])),(70,hx(c['a']))]))],tr(p=[0,0],s=K([(0,[0,100]),(70,[100,100])])))],0,90,p=[38,150])
    bus=layer('Bus',[gr('b',[rc([0,0],[44,30],8),fl(hx(c['fg']),90)]),gr('w',[rc([0,-4],[34,10],3),fl(hx(c['sky']))]),gr('wh',[el([-12,16],[9,9]),el([12,16],[9,9]),fl(hx(c['fg']))])],0,90,p=K([(0,[40,118,0]),(70,[216,118,0])]))
    pin=layer('Place',[gr('p',[el([0,0],[16,16]),fl(hx(c['a']))])],0,90,p=[222,92],s=K([(0,[100,100,100]),(70,[100,100,100]),(78,[140,140,100]),(86,[100,100,100])]))
    return comp('Leave by',90,[pin,bus,fill,track])
M={'source_refresh':refresh,'add_source':add_src,'timeline_drag':drag,'find_time':find,'leave_by':leave}
os.makedirs('out/raw',exist_ok=True)
for k,f in M.items():
    for v in ('dark','light'): json.dump(f(PAL[v]),open(f'out/raw/halo13_motion_{k}_{v}.json','w'),separators=(',',':'))
print('ok')
