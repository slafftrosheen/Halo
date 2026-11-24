import json,os
def hx(h,a=1): h=h.lstrip('#'); return [int(h[i:i+2],16)/255 for i in (0,2,4)]+[a]
E_IN={"x":[0.33],"y":[1]}; E_OUT={"x":[0.67],"y":[0]}
def S(v): return {"a":0,"k":v}
def K(fr):
    ks=[]
    for i,(t,v) in enumerate(fr):
        k={"t":t,"s":v if isinstance(v,list) else [v]}
        if i<len(fr)-1: k["i"]=E_IN;k["o"]=E_OUT
        ks.append(k)
    return {"a":1,"k":ks}
def V(x): return x if isinstance(x,dict) else S(x)
def tr(p=[0,0],s=[100,100],o=100,r=0): return {"ty":"tr","p":V(p),"a":S([0,0]),"s":V(s),"r":V(r),"o":V(o),"sk":S(0),"sa":S(0)}
def rc(p,s,r): return {"ty":"rc","d":1,"p":V(p),"s":V(s),"r":V(r)}
def el(p,s): return {"ty":"el","d":1,"p":V(p),"s":V(s)}
def sh(v,closed=False,i=None,o=None):
    n=len(v); return {"ty":"sh","ks":S({"v":v,"i":i or [[0,0]]*n,"o":o or [[0,0]]*n,"c":closed})}
def fl(c,o=100): return {"ty":"fl","c":V(c),"o":V(o),"r":1}
def st(c,w=4,o=100): return {"ty":"st","c":V(c),"o":V(o),"w":S(w),"lc":2,"lj":2,"ml":4}
def tm(e,s=S(0)): return {"ty":"tm","s":s,"e":V(e),"o":S(0),"m":1}
def gr(nm,items,t=None): return {"ty":"gr","nm":nm,"it":items+[t or tr()]}
def layer(nm,shapes,ind,op,p=[128,128],o=100,s=[100,100]):
    return {"ddd":0,"ind":ind,"ty":4,"nm":nm,"sr":1,"ks":{"o":V(o),"r":S(0),"p":V(p if isinstance(p,dict) else p+[0]) if not isinstance(p,dict) else p,"a":S([0,0,0]),"s":V(s if isinstance(s,dict) else s+[100])},"ao":0,"shapes":shapes,"ip":0,"op":op,"st":0,"bm":0}
def comp(nm,op,layers):
    for i,l in enumerate(layers): l["ind"]=i+1
    return {"v":"5.12.2","fr":30,"ip":0,"op":op,"w":256,"h":256,"nm":nm,"ddd":0,"assets":[],"layers":layers}
def P(x,y): return [x,y]
PAL={'dark':dict(fg='#FAFAF4',m='#0CF2BA',l='#C9B8FF',a='#FF9F6B',sun='#FFE08A',sky='#9EC9FF',night='#1b1760',hill='#100c45',card='#1b1760'),
     'light':dict(fg='#060330',m='#0AA983',l='#7B61D9',a='#E0763F',sun='#F5B82E',sky='#9EC9FF',night='#2a2470',hill='#E9E6DA',card='#FFFFFF')}
def level_shift(c):
    cell={'pro':44,'std':71,'one':152}
    L=[]
    for r in range(3):
        for q in range(3):
            pro=[(q-1)*54,(r-1)*54]; qr=0 if r<2 else 1; qc=0 if q<2 else 1
            std=[(qc-.5)*81,(qr-.5)*81]
            pos=K([(0,[0,0]),(20,[0,0]),(32,std),(50,std),(62,pro),(78,pro),(90,[0,0])])
            siz=K([(0,[152,152]),(20,[152,152]),(32,[71,71]),(50,[71,71]),(62,[44,44]),(78,[44,44]),(90,[152,152])])
            rad=K([(0,26),(20,26),(32,16),(50,16),(62,11),(78,11),(90,26)])
            L.append(gr(f'tile {r}{q}',[rc(pos,siz,rad),st(hx(c['m']),4)]))
    dot=gr('focus dot',[el([0,0],K([(0,[18,18]),(20,[18,18]),(30,[0,0]),(80,[0,0]),(90,[18,18])])),fl(hx(c['m']))])
    return comp('Level shift · Simple → Standard → Pro',90,[layer('dot',[dot],1,90),layer('Tiles',L,2,90)])
def page_assembles(c):
    frame=layer('Screen',[gr('frame',[rc([0,0],[132,212],22),st(hx(c['fg']),4,40)])],1,75)
    blocks=[([0,-62],[104,34]),([-27,-14],[50,48]),([27,-14],[50,48]),([0,42],[104,46])]
    Ls=[]
    for i,(p,s) in enumerate(blocks):
        t0=6+i*9
        col=hx(c['m']) if i==0 else hx(c['fg'])
        g=gr('block',[rc([0,0],s,10),fl(col,22 if i==0 else 12),st(col,3,70 if i==0 else 35)])
        Ls.append(layer(f'Block {i+1}',[g],0,75,p=K([(0,[128+p[0],128+p[1]+40,0]),(t0,[128+p[0],128+p[1]+40,0]),(t0+14,[128+p[0],128+p[1],0])]),o=K([(0,0),(t0,0),(t0+10,100)])))
    spark=layer('Made for you',[gr('spark',[sh([P(0,-12),P(3,-3),P(12,0),P(3,3),P(0,12),P(-3,3),P(-12,0),P(-3,-3)],True),fl(hx(c['l']))])],0,75,p=[178,58],s=K([(0,[0,0,100]),(48,[0,0,100]),(56,[120,120,100]),(62,[100,100,100])]))
    return comp('Page assembles · made for you',75,[spark]+Ls+[frame])
def explain_bloom(c):
    lines=[layer(f'Line {i}',[gr('l',[sh([P(-70,0),P(w,0)]),st(hx(c['fg']),8,30)])],0,60,p=[128,150+i*22]) for i,w in enumerate([70,40,60])]
    under=layer('Word underline',[gr('u',[sh([P(-14,0),P(40,0)]),st(hx(c['l']),8),tm(K([(0,0),(10,0),(24,100)]))])],0,60,p=[128,150])
    bub=gr('bubble',[sh([P(-62,-26),P(62,-26),P(62,20),P(-8,20),P(-18,32),P(-20,20),P(-62,20)],True),fl(hx(c['l']),95)])
    txt=gr('words',[sh([P(-46,-8),P(40,-8)]),sh([P(-46,6),P(14,6)]),st(hx('#1d1240' if c['fg']=='#FAFAF4' else '#FFFFFF'),6,90)])
    bl=layer('Plain words bubble',[txt,bub],0,60,p=[140,104],s=K([(0,[0,0,100]),(22,[0,0,100]),(34,[106,106,100]),(40,[100,100,100])]),o=K([(0,0),(22,0),(28,100)]))
    return comp('Explain this · plain words',60,[bl,under]+lines)
def sky_cycle(c):
    win=K([(0,hx(c['sky'])),(40,hx(c['sky'])),(60,hx(c['a'])),(78,hx(c['night'])),(108,hx(c['night'])),(120,hx(c['sky']))])
    bg=layer('Sky',[gr('w',[rc([0,0],[176,176],34),fl(win)])],0,120)
    sun=layer('Sun',[gr('s',[el([0,0],[34,34]),fl(hx(c['sun']))])],0,120,p=K([(0,[68,180,0]),(30,[128,72,0]),(62,[192,186,0]),(100,[192,186,0]),(101,[64,186,0]),(120,[68,180,0])]))
    moon=layer('Moon',[gr('m',[sh([P(0,-15),P(0,15)],False,None,None),el([0,0],[26,26]),fl(hx('#FAFAF4'))]),gr('star',[el([-34,14],[5,5]),el([22,-22],[4,4]),fl(hx('#FAFAF4'),80)])],0,120,p=[158,92],o=K([(0,0),(66,0),(80,100),(104,100),(116,0)]))
    hills=layer('Hills',[gr('h',[sh([P(-88,30),P(-40,8),P(10,26),P(52,4),P(88,22),P(88,58),P(-88,58)],True),fl(hx(c['hill']))]),gr('rim',[rc([0,0],[176,176],34),st(hx(c['fg']),4,25)])],0,120,p=[128,158])
    return comp('Sky follows the day',120,[hills,moon,sun,bg])
def tint(c):
    wp=layer('Wallpaper',[gr('hill',[sh([P(-34,10),P(-6,-8),P(34,8),P(34,40),P(-34,40)],True),fl(hx(c['m']),80)]),gr('sky',[rc([0,0],[68,100],14),fl(hx(c['l']),70)])],0,75,p=[64,128])
    drop=layer('Drop',[gr('d',[sh([P(0,-12),P(8,2),P(0,12),P(-8,2)],True,[[0,0],[0,-6],[5,0],[0,6]],[[0,0],[0,6],[-5,0],[0,-6]]),fl(hx(c['m']))])],0,75,p=K([(0,[64,118,0]),(10,[64,118,0]),(30,[128,70,0]),(46,[190,150,0])]),o=K([(0,0),(10,100),(44,100),(48,0)]))
    btn=K([(0,hx(c['fg'],1)),(46,hx(c['fg'],1)),(54,hx(c['m']))])
    card=layer('Card',[gr('btn',[rc([0,30],[64,22],11),fl(btn,K([(0,15),(46,15),(54,100)]))]),gr('lines',[sh([P(-26,-26),P(22,-26)]),sh([P(-26,-12),P(8,-12)]),st(hx(c['fg']),6,40)]),gr('card',[rc([0,0],[84,104],16),fl(hx(c['card'])),st(hx(c['fg']),3,30)])],0,75,p=[190,128])
    ok=layer('Contrast check',[gr('ok',[sh([P(-9,0),P(-3,6),P(9,-6)]),st(hx(c['m']),5),tm(K([(0,0),(56,0),(66,100)]))]),gr('ring',[el([0,0],[30,30]),fl(hx(c['card'])),st(hx(c['m']),3)])],0,75,p=[222,84],s=K([(0,[0,0,100]),(54,[0,0,100]),(60,[100,100,100])]))
    return comp('Colours from your wallpaper · contrast checked',75,[ok,drop,card,wp])
M={'level_shift':level_shift,'page_assembles':page_assembles,'explain_bloom':explain_bloom,'sky_cycle':sky_cycle,'tint_from_wallpaper':tint}
os.makedirs('out/raw',exist_ok=True)
for k,f in M.items():
    for v in ('dark','light'):
        json.dump(f(PAL[v]),open(f'out/raw/halo13_motion_{k}_{v}.json','w'),separators=(',',':'))
print(os.listdir('out/raw'))
