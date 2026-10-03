import os, json, math
os.environ["PROJ_LIB"] = "C:/Users/HomePC/AppData/Roaming/Python/Python311/site-packages/rasterio/proj_data"
os.environ["PROJ_DATA"] = os.environ["PROJ_LIB"]
import numpy as np, geopandas as gpd, pandas as pd
from shapely.geometry import Point, LineString, Polygon
import rasterio

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
RAS = os.path.join(HERE, "rasters")
OUT = os.path.join(HERE, "..", "results")
os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.join(OUT, "maps"), exist_ok=True)
DISTRICTS = ["Mhondoro-Ngezi", "Gwanda", "Beitbridge"]
# metric CRS: transverse Mercator centred on 30E (spans the three districts with low distortion)
MCRS = "+proj=tmerc +lat_0=0 +lon_0=30 +k=0.9996 +x_0=500000 +y_0=10000000 +datum=WGS84 +units=m +no_defs"


def districts():
    g = gpd.read_file(os.path.join(HERE, "study_districts.geojson"))
    return g.set_index("shapeName")


def elems(name, layer):
    p = os.path.join(RAW, f"{name}_{layer}.json")
    if not os.path.exists(p):
        return []
    return json.load(open(p, encoding="utf8"))["elements"]


def points(name, layer):
    rows = []
    for e in elems(name, layer):
        lat = e.get("lat") or (e.get("center") or {}).get("lat")
        lon = e.get("lon") or (e.get("center") or {}).get("lon")
        if lat is None:
            continue
        t = e.get("tags", {})
        rows.append(dict(osm_id=f"{e['type'][0]}{e['id']}", name=t.get("name", ""), tags=json.dumps(t), lon=lon, lat=lat,
                         place=t.get("place", ""), amenity=t.get("amenity", ""), shop=t.get("shop", ""),
                         industrial=t.get("industrial", ""), natural=t.get("natural", ""), man_made=t.get("man_made", ""),
                         water=t.get("water", ""), landuse=t.get("landuse", "")))
    if not rows:
        return gpd.GeoDataFrame(columns=["osm_id", "name", "geometry"], geometry=[], crs=4326)
    df = pd.DataFrame(rows)
    return gpd.GeoDataFrame(df, geometry=gpd.points_from_xy(df.lon, df.lat), crs=4326)


def lines(name, layer, tagkeys=("highway", "surface", "waterway", "name")):
    geoms, recs = [], []
    for e in elems(name, layer):
        g = e.get("geometry")
        if not g or len(g) < 2:
            continue
        geoms.append(LineString([(p["lon"], p["lat"]) for p in g]))
        t = e.get("tags", {})
        recs.append({k: t.get(k, "") for k in tagkeys})
    return gpd.GeoDataFrame(recs, geometry=geoms, crs=4326)


def read_ras(name):
    with rasterio.open(os.path.join(RAS, name + ".tif")) as d:
        return d.read(1), d.transform, d.nodata
