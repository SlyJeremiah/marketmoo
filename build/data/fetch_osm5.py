import geopandas as gpd, time, json, os, math, sys
from concurrent.futures import ThreadPoolExecutor
from ovp import op
s=gpd.read_file('study_districts.geojson').set_index('shapeName')
M=0.45
def bbox(n):
    x0,y0,x1,y1=s.loc[n].geometry.bounds; return x0-M,y0-M,x1+M,y1+M
ROADS='way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|track)$"]({b});out tags geom qt;'
def boxes(n,st):
    x0,y0,x1,y1=bbox(n); out=[]
    for i in range(math.ceil((x1-x0)/st)):
        for j in range(math.ceil((y1-y0)/st)):
            out.append((i,j,(y0+j*st,x0+i*st,min(y0+(j+1)*st,y1),min(x0+(i+1)*st,x1))))
    return out
def one(args):
    n,k,t,st,i,j,b=args
    bs=f"{b[0]:.3f},{b[1]:.3f},{b[2]:.3f},{b[3]:.3f}"
    for att in range(8):
        try:
            d=op('[out:json][timeout:90];'+t.replace('{b}',bs),f'tiles/{n}_{k}_s{st}_{i}_{j}.json',tries=2,timeout=100)
            return d['elements']
        except Exception as e:
            time.sleep(10)
    print('GIVEUP',n,k,i,j,flush=True); return None
def run_tiled(n,k,t,st):
    f=f'raw/{n}_{k}.json'
    if os.path.exists(f) and os.path.getsize(f)>50: print(n,k,'cached',flush=True); return
    jobs=[(n,k,t,st,i,j,b) for i,j,b in boxes(n,st)]
    els={}; bad=0
    with ThreadPoolExecutor(3) as ex:
        for r in ex.map(one,jobs):
            if r is None: bad+=1; continue
            for e in r: els[e['id']]=e
    json.dump({'elements':list(els.values())},open(f,'w')); print(n,k,len(els),'missing tiles',bad,len(jobs),flush=True)
def run_one(n,k,t):
    f=f'raw/{n}_{k}.json'
    if os.path.exists(f) and os.path.getsize(f)>50: print(n,k,'cached',flush=True); return
    x0,y0,x1,y1=bbox(n)
    for att in range(6):
        try:
            d=op('[out:json][timeout:120];'+t.replace('{b}',f"{y0:.3f},{x0:.3f},{y1:.3f},{x1:.3f}"),f'{n}_{k}.json',tries=3,timeout=150); print(n,k,len(d['elements']),flush=True); return
        except Exception as e: time.sleep(15)
    print('GIVEUP',n,k,flush=True)
N='Mhondoro-Ngezi'
run_tiled(N,'roads',ROADS,0.25)
run_one(N,'places','node["place"~"^(city|town|village|hamlet)$"]({b});out;')
run_one(N,'vets','(nwr["amenity"="veterinary"]({b});nwr["name"~"veterinary|dip tank|dip-tank|agritex|animal health",i]({b});nwr["shop"="agrarian"]({b}););out tags center;')
run_one(N,'water_pts','(nwr["man_made"~"water_well|water_tower|borehole|water_works"]({b});nwr["amenity"="drinking_water"]({b});nwr["natural"="spring"]({b}););out tags center;')
run_one(N,'water_poly','(nwr["natural"="water"]({b});nwr["landuse"="reservoir"]({b}););out tags center;')
for n in ['Mhondoro-Ngezi','Gwanda','Beitbridge']:
    run_one(n,'rivers_main','way["waterway"="river"]({b});out tags geom qt;')
print('DONE',flush=True)
