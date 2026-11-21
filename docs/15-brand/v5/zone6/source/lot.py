# halo Zone 6 "Calm Dock, alive" — Lottie set authored as code (shape layers only).
import json, os
OUT='/tmp/z6/lottie'; os.makedirs(OUT, exist_ok=True)
FR=30
TH={'dark':dict(mint='#0CF2BA',mint2='#B4E6D6',ap='#FF9F6B',bad='#FF8FA3',ink='#FAFAF4',surf='#1A1760',glass='#120F4A',line='#3A3672'),
    'light':dict(mint='#047A5E',mint2='#0CF2BA',ap='#C25E1F',bad='#A82948',ink='#060330',surf='#EEF1F0',glass='#FFFFFF',line='#C9CCD4')}
def hx(h,a=1):h=h.lstrip('#');return [int(h[i:i+2],16)/255 for i in (0,2,4)]+[a]
def P(v):return {'a':0,'k':v}
SPR=({'x':[0.34],'y':[1.56]},{'x':[0.64],'y':[1]})
EM=({'x':[0.2],'y':[0]},{'x':[0],'y':[1]})
SIN=({'x':[0.45],'y':[0]},{'x':[0.55],'y':[1]})
def A(keys,ease=EM):
    """keys: [(t, value)], value list or scalar"""
    k=[]
    for i,(t,v) in enumerate(keys):
        v=v if isinstance(v,list) else [v]
        e={'t':t,'s':v}
        if i<len(keys)-1:
            n=len(v); e['o']={'x':ease[0]['x']*n,'y':ease[0]['y']*n}; e['i']={'x':ease[1]['x']*n,'y':ease[1]['y']*n}
        k.append(e)
    return {'a':1,'k':k}
def tr(p=[0,0],s=[100,100],o=100,r=0):
    return {'ty':'tr','p':p if isinstance(p,dict) else P(p),'a':P([0,0]),'s':s if isinstance(s,dict) else P(s),'r':r if isinstance(r,dict) else P(r),'o':o if isinstance(o,dict) else P(o),'sk':P(0),'sa':P(0)}
def rect(w,h,r): return {'ty':'rc','d':1,'s':w if isinstance(w,dict) else P([w,h]),'p':P([0,0]),'r':r if isinstance(r,dict) else P(r)}
def ell(d): return {'ty':'el','d':1,'s':P([d,d]),'p':P([0,0])}
def fill(c,o=100): return {'ty':'fl','c':P(hx(c)),'o':o if isinstance(o,dict) else P(o),'r':1}
def stroke(c,w,o=100): return {'ty':'st','c':P(hx(c)),'o':o if isinstance(o,dict) else P(o),'w':w if isinstance(w,dict) else P(w),'lc':2,'lj':2,'ml':4}
def trim(s,e,o): return {'ty':'tm','s':s if isinstance(s,dict) else P(s),'e':e if isinstance(e,dict) else P(e),'o':o if isinstance(o,dict) else P(o),'m':1}
def path(pts,closed=False):
    return {'ty':'sh','ks':P({'i':[[0,0]]*len(pts),'o':[[0,0]]*len(pts),'v':pts,'c':closed})}
def grp(items,t=None,nm='g'): return {'ty':'gr','nm':nm,'it':items+[t or tr()]}
def layer(i,shapes,op,p=[0,0],o=100,s=[100,100],nm='l'):
    ks={'o':o if isinstance(o,dict) else P(o),'r':P(0),'p':p if isinstance(p,dict) else P(p+[0]),'a':P([0,0,0]),'s':s if isinstance(s,dict) else P(s+[100])}
    return {'ddd':0,'ind':i,'ty':4,'nm':nm,'sr':1,'ks':ks,'ao':0,'shapes':shapes,'ip':0,'op':op,'st':0,'bm':0}
def comp(nm,w,h,op,layers,markers=None):
    d={'v':'5.7.4','fr':FR,'ip':0,'op':op,'w':w,'h':h,'nm':nm,'ddd':0,'assets':[],'layers':layers}
    if markers: d['markers']=[{'tm':t,'cm':c,'dr':dr} for t,c,dr in markers]
    return d
def ring(d,c1,w,extra=None):
    # canonical open ring: gap ~44deg at 1-2 o'clock
    return grp([ell(d),trim(0,87.8,67),stroke(c1,w)]+(extra or []),nm='ring')
def save(nm,d): json.dump(d,open(f'{OUT}/{nm}.json','w'),separators=(',',':'))

for th,C in TH.items():
    # 1 dock_spring: 4 buttons spring out of the ring (zone 390x197; ring at 195,155; slots y=84)
    op=36; L=[]
    xs=[56,148,240,332]
    for k,x in enumerate(xs):
        d=k*1.2
        L.append(layer(k+1,[grp([rect(84,52,16),fill(C['surf'] if k else C['mint']),stroke(C['line'],1,0 if k==0 else 100)])],op,
            p=A([(0+d,[195,155,0]),(18+d,[x,84,0])],SPR),s=A([(0+d,[30,30,100]),(18+d,[100,100,100])],SPR),o=A([(0+d,0),(6+d,100)]),nm=f'button{k+1}'))
    L.append(layer(9,[ring(44,C['mint'],7)],op,p=[195,155],nm='ring'))
    save(f'halo_z6_dock_spring_{th}',comp('dock_spring',390,197,op,L,[(0,'out',18),(18,'settled',18)]))
    # 2 island_lift: dock rect -> floating island + edge stroke
    op=30
    sz=A([(0,[390,240]),(16,[370,107])],SPR); pos=A([(0,[195,120,0]),(16,[195,57.5,0])],SPR); rr=A([(0,30),(16,26)],SPR)
    L=[layer(1,[grp([{'ty':'rc','d':1,'s':sz,'p':P([0,0]),'r':rr},fill(C['glass']),stroke(C['mint'],1.5,A([(4,0),(16,70)]))])],op,p=pos,nm='island')]
    save(f'halo_z6_island_lift_{th}',comp('island_lift',390,197,op,L,[(0,'lift',16)]))
    # 3 places_open: 6 tiles spring from ring (canvas 374x560; ring at 187,600 offscreen-bottom)
    op=40; L=[]
    for k in range(6):
        cx=94+(k%2)*186; cy=80+(k//2)*130; d=k*1.5
        L.append(layer(k+1,[grp([rect(170,112,20),fill(C['surf']),stroke(C['mint'] if k==0 else C['line'],1.5 if k==0 else 1)]),
            grp([rect(40,40,13),fill(C['mint'],18)],tr([-55,-22]))],op,
            p=A([(d,[187,560,0]),(20+d,[cx,cy,0])],SPR),s=A([(d,[20,20,100]),(20+d,[100,100,100])],SPR),o=A([(d,0),(6+d,100)]),nm=f'tile{k+1}'))
    save(f'halo_z6_places_open_{th}',comp('places_open',374,460,op,L,[(0,'open',28)]))
    # 4 need_glow: apricot island edge breathes (loop 2.4s)
    op=72
    L=[layer(1,[grp([rect(370,107,26),stroke(C['ap'],2,A([(0,90),(36,40),(72,90)],SIN))]),grp([rect(378,115,30),stroke(C['ap'],6,A([(0,22),(36,6),(72,22)],SIN))])],op,p=[195,60],nm='need')]
    save(f'halo_z6_need_glow_{th}',comp('need_glow',390,120,op,L))
    # 5 receipt_bloom: check disc + bloom ring x2 then still
    op=60
    chk=grp([path([[-9,0],[-3,6],[9,-7]]),trim(0,A([(4,0),(14,100)]),0),stroke(C['glass'] if th=='dark' else '#FFFFFF',4)],nm='check')
    L=[layer(1,[chk],op,p=[48,48]),
       layer(2,[grp([ell(36),fill(C['mint'])])],op,p=[48,48],s=A([(0,[60,60,100]),(10,[100,100,100])],SPR)),
       layer(3,[grp([ell(48),stroke(C['mint'],2)])],op,p=[48,48],s=A([(6,[70,70,100]),(30,[180,180,100])]),o=A([(6,90),(30,0)])),
       layer(4,[grp([ell(48),stroke(C['mint'],2)])],op,p=[48,48],s=A([(26,[70,70,100]),(50,[180,180,100])]),o=A([(0,0),(25,0),(26,90),(50,0)]))]
    save(f'halo_z6_receipt_bloom_{th}',comp('receipt_bloom',96,96,op,L,[(0,'bloom',50)]))
    # 6 listen_wave: 7 bars loop 1s
    op=30; L=[]
    for k in range(7):
        ph=[0,4,9,2,7,3,8][k]
        def hk(t):return 26 if ((t+ph)//15)%2 else 8
        keys=[(0,[4,8 if ph%2 else 22]),(15,[4,22 if ph%2 else 8]),(30,[4,8 if ph%2 else 22])]
        L.append(layer(k+1,[grp([{'ty':'rc','d':1,'s':A(keys,SIN),'p':P([0,0]),'r':P(2)},fill(C['mint'])])],op,p=[12+k*16,20],nm=f'bar{k+1}'))
    save(f'halo_z6_listen_wave_{th}',comp('listen_wave',120,40,op,L))
    # 7 widget_refresh: open ring one lap then settles
    op=36
    L=[layer(1,[grp([ell(30),trim(0,87.8,A([(0,67),(30,427)],EM)),stroke(C['mint'],4)])],op,p=[24,24],nm='refresh')]
    save(f'halo_z6_widget_refresh_{th}',comp('widget_refresh',48,48,op,L,[(0,'lap',30)]))
    # 8 error_soften: soft red island edge fades in once (no shake)
    op=24
    L=[layer(1,[grp([rect(370,107,26),stroke(C['bad'],2,A([(0,0),(12,80)])),fill(C['bad'],A([(0,0),(12,8)]))])],op,p=[195,60],nm='error')]
    save(f'halo_z6_error_soften_{th}',comp('error_soften',390,120,op,L))
print(sorted(os.listdir(OUT)))
