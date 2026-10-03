import rasterio, numpy as np, time, sys, os, io, math, requests
from rasterio.windows import from_bounds
from rasterio.transform import from_bounds as tfb
from rasterio.warp import reproject, Resampling
from concurrent.futures import ThreadPoolExecutor
W,S,E,N=28.1,-22.8,31.6,-17.9
RES=0.001
NX=int(round((E-W)/RES)); NY=int(round((N-S)/RES))
DST=tfb(W,S,E,N,NX,NY)
os.makedirs('rasters',exist_ok=True)
def save(name,arr,dtype,nodata):
    with rasterio.open(f'rasters/{name}.tif','w',driver='GTiff',height=NY,width=NX,count=1,dtype=dtype,crs='EPSG:4326',transform=DST,nodata=nodata,compress='deflate') as d:
        d.write(arr.astype(dtype),1)
    print('saved',name,flush=True)
def warp_from(url,resampling,dtype,src_nodata=None,band=1):
    out=np.zeros((NY,NX),dtype=dtype)
    with rasterio.open(url) as ds:
        w=from_bounds(W,S,E,N,transform=ds.transform).round_offsets().round_lengths()
        w=w.intersection(rasterio.windows.Window(0,0,ds.width,ds.height))
        # decimate: read with out_shape proportional to the target resolution
        tw=max(1,int(w.width*abs(ds.transform.a)/RES)); th=max(1,int(w.height*abs(ds.transform.e)/RES))
        a=ds.read(band,window=w,out_shape=(th,tw),resampling=Resampling.nearest if resampling==Resampling.nearest else Resampling.average)
        tr=ds.window_transform(w)*ds.window_transform(w).scale(w.width/tw,w.height/th)
        reproject(a,out,src_transform=tr,src_crs=ds.crs,dst_transform=DST,dst_crs='EPSG:4326',resampling=resampling,src_nodata=src_nodata if src_nodata is not None else ds.nodata,dst_nodata=0)
    return out
def chirps():
    years=[2019,2020,2021,2022,2023]
    def one(y):
        u=f'/vsicurl/https://data.chc.ucsb.edu/products/CHIRPS-2.0/global_annual/tifs/chirps-v2.0.{y}.tif'
        for k in range(4):
            try: return warp_from(u,Resampling.bilinear,'float32',src_nodata=-9999)
            except Exception as e: print('chirps retry',y,e,flush=True); time.sleep(5)
    with ThreadPoolExecutor(5) as ex: arrs=list(ex.map(one,years))
    st=np.stack(arrs); st[st<0]=np.nan
    save('chirps_mean',np.nan_to_num(np.nanmean(st,0),nan=-9999),'float32',-9999)
    cv=np.nanstd(st,0)/np.maximum(np.nanmean(st,0),1)
    save('chirps_cv',np.nan_to_num(cv,nan=-9999),'float32',-9999)
def jrc():
    acc=np.zeros((NY,NX),dtype='uint8')
    for lon,lat in [('20E','10S'),('30E','10S'),('20E','20S'),('30E','20S')]:
        u=f'/vsicurl/https://storage.googleapis.com/global-surface-water/downloads2021/seasonality/seasonality_{lon}_{lat}v1_4_2021.tif'
        try:
            a=warp_from(u,Resampling.max,'uint8',src_nodata=255)
            acc=np.maximum(acc,a)
        except Exception as e: print('jrc skip',lon,lat,str(e)[:100],flush=True)
    save('jrc_seasonality',acc,'uint8',255)
def worldcover():
    acc=np.zeros((NY,NX),dtype='uint8')
    for lat in ['S24','S21']:
        for lon in ['E027','E030']:
            u=f'/vsicurl/https://esa-worldcover.s3.eu-central-1.amazonaws.com/v200/2021/map/ESA_WorldCover_10m_2021_v200_{lat}{lon}_Map.tif'
            try:
                a=warp_from(u,Resampling.nearest,'uint8')
                acc=np.where(a>0,a,acc)
            except Exception as e: print('wc skip',lat,lon,str(e)[:100],flush=True)
    save('worldcover',acc,'uint8',0)
def dem():
    z=10
    def tx(lon): return int((lon+180)/360*2**z)
    def ty(lat): 
        r=math.radians(lat); return int((1-math.asinh(math.tan(r))/math.pi)/2*2**z)
    x0,x1=tx(W),tx(E); y0,y1=ty(N),ty(S)
    mosaic=np.full(((y1-y0+1)*256,(x1-x0+1)*256),np.nan,dtype='float32')
    def get(xy):
        x,y=xy
        for k in range(4):
            try:
                r=requests.get(f'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png',timeout=60)
                from PIL import Image
                im=np.array(Image.open(io.BytesIO(r.content)).convert('RGB')).astype('float32')
                return x,y,(im[:,:,0]*256+im[:,:,1]+im[:,:,2]/256-32768)
            except Exception as e: time.sleep(3)
        return x,y,None
    tiles=[(x,y) for x in range(x0,x1+1) for y in range(y0,y1+1)]
    with ThreadPoolExecutor(8) as ex:
        for x,y,a in ex.map(get,tiles):
            if a is not None: mosaic[(y-y0)*256:(y-y0+1)*256,(x-x0)*256:(x-x0+1)*256]=a
    # mosaic is web mercator; build transform
    def mx(i): return i/2**z*360-180
    def mlat(j): return math.degrees(math.atan(math.sinh(math.pi*(1-2*j/2**z))))
    # approximate: resample via reproject using EPSG:3857 transform
    R=20037508.342789244
    px=2*R/2**z/256
    left=-R+x0*256*px; top=R-y0*256*px
    from rasterio.transform import Affine
    src_tr=Affine(px,0,left,0,-px,top)
    out=np.full((NY,NX),-9999,dtype='float32')
    reproject(mosaic,out,src_transform=src_tr,src_crs='EPSG:3857',dst_transform=DST,dst_crs='EPSG:4326',resampling=Resampling.bilinear,src_nodata=np.nan,dst_nodata=-9999)
    save('dem',out,'float32',-9999)
if __name__=='__main__':
    for f in (dem,jrc,worldcover,chirps):
        t=time.time()
        try: f()
        except Exception as e: print('FAIL',f.__name__,e,flush=True)
        print(f.__name__,'took',round(time.time()-t),'s',flush=True)
    print('DONE',flush=True)
