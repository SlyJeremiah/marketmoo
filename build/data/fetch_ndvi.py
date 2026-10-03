import sys; sys.argv=['x']
import fetch_rasters as fr, numpy as np, requests, rasterio, time, collections
from rasterio.windows import from_bounds
from rasterio.warp import reproject, Resampling, transform_bounds
bb=[fr.W,fr.S,fr.E,fr.N]
def search(dt):
    items=[];url='https://earth-search.aws.element84.com/v1/search'
    body={'collections':['sentinel-2-l2a'],'bbox':bb,'datetime':dt,'limit':200,'query':{'eo:cloud_cover':{'lt':8}}}
    r=requests.post(url,json=body,timeout=90).json()
    items+=r['features']
    return items
items=search('2025-08-20T00:00:00Z/2025-10-10T23:59:59Z')
print('items',len(items),flush=True)
best={}
for it in items:
    t=it['properties'].get('grid:code') or it['id'].split('_')[1]
    cc=it['properties']['eo:cloud_cover']
    if t not in best or cc<best[t][0]: best[t]=(cc,it)
print('tiles',len(best),sorted(best),flush=True)
ndvi_acc=np.full((fr.NY,fr.NX),np.nan,dtype='float32')
def read_band(href,tb,shape):
    with rasterio.open('/vsicurl/'+href) as ds:
        l,b,r,t=transform_bounds('EPSG:4326',ds.crs,*bb)
        w=from_bounds(l,b,r,t,transform=ds.transform).round_offsets().round_lengths()
        w=w.intersection(rasterio.windows.Window(0,0,ds.width,ds.height))
        th=max(1,int(w.height*abs(ds.transform.e)/100)); tw=max(1,int(w.width*ds.transform.a/100))
        a=ds.read(1,window=w,out_shape=(th,tw),resampling=Resampling.nearest).astype('float32')
        tr=ds.window_transform(w); tr=tr*tr.scale(w.width/tw,w.height/th)
        return a,tr,ds.crs
for t,(cc,it) in best.items():
    try:
        red,tr,crs=read_band(it['assets']['red']['href'],None,None)
        nir,_,_=read_band(it['assets']['nir']['href'],None,None)
        scl,_,_=read_band(it['assets']['scl']['href'],None,None)
        n=(nir-red)/np.maximum(nir+red,1)
        bad=(red==0)|np.isin(scl,[0,1,3,8,9,10,11])|(scl==0)
        n[bad]=np.nan; n=np.nan_to_num(n,nan=-9)
        out=np.full((fr.NY,fr.NX),-9,dtype='float32')
        reproject(n,out,src_transform=tr,src_crs=crs,dst_transform=fr.DST,dst_crs='EPSG:4326',resampling=Resampling.nearest,src_nodata=-9,dst_nodata=-9)
        m=out>-9
        ndvi_acc=np.where(m & np.isnan(ndvi_acc),out,ndvi_acc)
        print('ok',t,cc,int(m.sum()),flush=True)
    except Exception as e: print('fail',t,str(e)[:120],flush=True)
fr.save('ndvi_dry2025',np.nan_to_num(ndvi_acc,nan=-9),'float32',-9)
print('DONE',flush=True)
