import sys; sys.argv=['x']
import fetch_rasters as fr
from rasterio.warp import Resampling
import numpy as np
a=fr.warp_from('/vsicurl/https://data.worldpop.org/GIS/Population/Global_2000_2020/2020/ZWE/zwe_ppp_2020.tif',Resampling.average,'float32',src_nodata=-99999)
a[a<0]=0
# average over decimation gives mean per cell; scale to people per cell is approximate: convert mean(people per 100m px) * (cell/100m)^2
fr.save('worldpop_mean_ppp',a,'float32',-9999)
print('DONE')
