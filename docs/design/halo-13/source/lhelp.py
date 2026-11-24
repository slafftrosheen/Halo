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
