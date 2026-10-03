"""SA1 Market accessibility: least-cost road travel time from 1 km origin cells to markets/towns."""
import pickle, time
from common import *
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import dijkstra
from scipy.spatial import cKDTree
import rasterio
from rasterio.transform import rowcol

SPEED = {"motorway": 70, "trunk": 60, "primary": 60, "secondary": 50, "tertiary": 40, "unclassified": 30, "residential": 25, "track": 15}
UNPAVED = {"unpaved", "gravel", "dirt", "ground", "sand", "earth", "compacted", "fine_gravel", "mud", "grass"}
OFFROAD_KMH = 10.0
SNAP_MAX_M = 10000
BETA = 1.5


def to_m(lon, lat):
    t = gpd.GeoSeries(gpd.points_from_xy(lon, lat), crs=4326).to_crs(MCRS)
    return np.c_[t.x.values, t.y.values]


def build_graph():
    cache = os.path.join(HERE, "graph.pkl")
    if os.path.exists(cache):
        return pickle.load(open(cache, "rb"))
    key = {}
    xs, ys = [], []
    src, dst, tmin, dkm = [], [], [], []
    seen_ways = set()
    for name in DISTRICTS:
        for e in elems(name, "roads"):
            if e["id"] in seen_ways:
                continue
            seen_ways.add(e["id"])
            g = e.get("geometry")
            if not g or len(g) < 2:
                continue
            t = e.get("tags", {})
            hw = t.get("highway", "track")
            sp = SPEED.get(hw, 20)
            if t.get("surface", "") in UNPAVED:
                sp = min(sp, 35)
            if t.get("maxspeed", "").replace(" km/h", "").isdigit():
                sp = min(sp, int(t["maxspeed"].replace(" km/h", "")))
            prev = None
            for p in g:
                k = (round(p["lon"], 6), round(p["lat"], 6))
                i = key.get(k)
                if i is None:
                    i = len(key); key[k] = i
                if prev is not None and prev != i:
                    src.append(prev); dst.append(i)
                    tmin.append(sp)  # temp store speed; fix after distances
                prev = i
    n = len(key)
    coords = np.zeros((n, 2))
    for (lo, la), i in key.items():
        coords[i] = (lo, la)
    mc = to_m(coords[:, 0], coords[:, 1])
    src = np.array(src); dst = np.array(dst); sp = np.array(tmin, dtype="float64")
    d = np.hypot(*(mc[src] - mc[dst]).T)  # metres
    minutes = d / 1000.0 / sp * 60.0
    A = coo_matrix((np.r_[minutes, minutes], (np.r_[src, dst], np.r_[dst, src])), shape=(n, n)).tocsr()
    Dm = coo_matrix((np.r_[d, d], (np.r_[src, dst], np.r_[dst, src])), shape=(n, n)).tocsr()
    res = (A, Dm, mc, coords)
    pickle.dump(res, open(cache, "wb"))
    return res


def origin_cells(dist_gdf, pop_arr=None):
    """1 km cell centres inside each district, in metric CRS; with population from WorldPop if available."""
    cells = []
    dm = dist_gdf.to_crs(MCRS)
    for name in DISTRICTS:
        poly = dm.loc[name].geometry
        x0, y0, x1, y1 = poly.bounds
        gx, gy = np.meshgrid(np.arange(x0 + 500, x1, 1000), np.arange(y0 + 500, y1, 1000))
        pts = gpd.GeoSeries(gpd.points_from_xy(gx.ravel(), gy.ravel()), crs=MCRS)
        inside = pts[pts.within(poly)]
        ll = inside.to_crs(4326)
        cells.append(pd.DataFrame(dict(district=name, x=inside.x.values, y=inside.y.values, lon=ll.x.values, lat=ll.y.values)))
    c = pd.concat(cells, ignore_index=True)
    c["cell_id"] = np.arange(len(c))
    return c


def sample_ras(name, lon, lat, scale_sum=False):
    a, tr, nd = read_ras(name)
    r, cc = rowcol(tr, lon, lat)
    r = np.clip(r, 0, a.shape[0] - 1); cc = np.clip(cc, 0, a.shape[1] - 1)
    return a[r, cc]


def destinations():
    rows = []
    seen = set()
    for name in DISTRICTS:
        pl = points(name, "places")
        for _, r in pl.iterrows():
            if r.osm_id in seen: continue
            seen.add(r.osm_id)
            if r.place in ("town", "city"):
                rows.append(dict(osm_id=r.osm_id, name=r["name"] or r.place, lon=r.lon, lat=r.lat, kind=r.place, weight=3 if r.place == "city" else 2))
        mk = points(name, "markets")
        for _, r in mk.iterrows():
            if r.osm_id in seen: continue
            seen.add(r.osm_id)
            nm = (r["name"] or "").lower()
            if r.amenity == "marketplace":
                rows.append(dict(osm_id=r.osm_id, name=r["name"] or "Marketplace", lon=r.lon, lat=r.lat, kind="marketplace", weight=3))
            elif r.industrial == "slaughterhouse" or r.amenity == "slaughterhouse" or any(k in nm for k in ("abattoir", "slaughter", "auction", "sale pen", "cold storage")):
                rows.append(dict(osm_id=r.osm_id, name=r["name"] or "Abattoir", lon=r.lon, lat=r.lat, kind="abattoir/auction", weight=3))
            elif any(k in nm for k in ("growth point", "business cent")):
                rows.append(dict(osm_id=r.osm_id, name=r["name"], lon=r.lon, lat=r.lat, kind="growth point/business centre", weight=2 if "growth point" in nm else 1))
            elif r.shop == "butcher":
                rows.append(dict(osm_id=r.osm_id, name=r["name"] or "Butcher", lon=r.lon, lat=r.lat, kind="butcher", weight=1))
    d = pd.DataFrame(rows)
    # de-duplicate butchers/other points within 1 km of a higher-weight destination or each other
    m = to_m(d.lon.values, d.lat.values)
    d["x"], d["y"] = m[:, 0], m[:, 1]
    d = d.sort_values("weight", ascending=False).reset_index(drop=True)
    keep = []
    tree_pts = []
    for i, r in d.iterrows():
        if all(np.hypot(r.x - px, r.y - py) > 1000 for px, py in tree_pts):
            keep.append(i); tree_pts.append((r.x, r.y))
    d = d.loc[keep].reset_index(drop=True)
    d["dest_id"] = np.arange(len(d))
    return d


def run():
    dist = districts()
    A, Dm, mc, coords = build_graph()
    tree = cKDTree(mc)
    cells = origin_cells(dist)
    dest = destinations()
    print("graph nodes", A.shape[0], "edges", A.nnz // 2, "cells", len(cells), "destinations", len(dest), dest.kind.value_counts().to_dict(), flush=True)
    # snap
    od, oi = tree.query(np.c_[cells.x, cells.y])
    dd, di = tree.query(np.c_[dest.x, dest.y])
    ok_d = dd <= SNAP_MAX_M
    dest = dest[ok_d].reset_index(drop=True); di = di[ok_d]; dd = dd[ok_d]
    cells["snap_m"] = od
    access_min = od / 1000.0 / OFFROAD_KMH * 60.0
    dest_access = dd / 1000.0 / OFFROAD_KMH * 60.0
    T = np.full((len(cells), len(dest)), np.inf, dtype="float32")
    t0 = time.time()
    uniq = {}
    for j, node in enumerate(di):
        uniq.setdefault(int(node), []).append(j)
    nodes = list(uniq)
    for s in range(0, len(nodes), 20):
        chunk = nodes[s:s + 20]
        dm = dijkstra(A, directed=False, indices=chunk, limit=600)
        for r, node in enumerate(chunk):
            for j in uniq[node]:
                T[:, j] = dm[r, oi] + access_min + dest_access[j]
    print("dijkstra done", round(time.time() - t0), "s", flush=True)
    T[np.isinf(T)] = np.nan
    T[:, :] = np.where(cells.snap_m.values[:, None] > SNAP_MAX_M, np.nan, T)
    order = np.argsort(np.nan_to_num(T, nan=1e9), axis=1)[:, :3]
    cells["t1"] = np.take_along_axis(T, order[:, :1], 1)[:, 0]
    cells["t2"] = np.take_along_axis(T, order[:, 1:2], 1)[:, 0]
    cells["t3"] = np.take_along_axis(T, order[:, 2:3], 1)[:, 0]
    for k in range(3):
        cells[f"m{k + 1}"] = [dest.name.iloc[i] if np.isfinite(T[r, i]) else "" for r, i in enumerate(order[:, k])]
        cells[f"m{k + 1}_kind"] = [dest.kind.iloc[i] if np.isfinite(T[r, i]) else "" for r, i in enumerate(order[:, k])]
    major = np.where(dest.weight.values >= 2)[0]
    Tm = T[:, major]
    cells["t_major"] = np.nanmin(np.where(np.isnan(Tm), np.inf, Tm), axis=1)
    cells.loc[np.isinf(cells.t_major), "t_major"] = np.nan
    am = np.argmin(np.where(np.isnan(Tm), np.inf, Tm), axis=1)
    cells["m_major"] = [dest.name.iloc[major[i]] if np.isfinite(cells.t_major.iloc[r]) else "" for r, i in enumerate(am)]
    # gravity market access index (hours, floor 0.25 h)
    w = dest.weight.values[None, :].astype("float32")
    th = np.maximum(T / 60.0, 0.25)
    mai = np.nansum(w * np.power(th, -BETA), axis=1)
    mai = np.where(np.isnan(T).all(axis=1), np.nan, mai)
    cells["mai_raw"] = mai
    cells["mai"] = pd.Series(mai).rank(pct=True).values * 100
    # population
    pp = os.path.join(RAS, "worldpop_mean_ppp.tif")
    if os.path.exists(pp):
        cells["pop"] = sample_ras("worldpop_mean_ppp", cells.lon.values, cells.lat.values) * 100  # 1 km cell = 100 x 100 m px approx
    else:
        cells["pop"] = 1.0
    cells.to_pickle(os.path.join(HERE, "sa1_cells.pkl"))
    dest.to_pickle(os.path.join(HERE, "sa1_dest.pkl"))
    # town-pair plausibility checks
    pairs = []
    tnode = {}
    for nm in ("Gwanda", "Beitbridge"):
        pl = points(nm, "places"); pl = pl[(pl["name"] == nm) & (pl.place == "town")]
        if len(pl):
            xy = to_m(pl.lon.values[:1], pl.lat.values[:1])[0]
            tnode[nm] = int(tree.query(xy)[1])
    for a, b in [("Gwanda", "Beitbridge"), ("Beitbridge", "Gwanda")]:
        if a in tnode and b in tnode:
            d1 = dijkstra(A, directed=False, indices=[tnode[a]])[0, tnode[b]]
            d2 = dijkstra(Dm, directed=False, indices=[tnode[a]])[0, tnode[b]] / 1000
            pairs.append((a, b, round(d1, 0), round(d2, 0)))
    print("pairs", pairs)
    # per-district stats
    rows = []
    for name in DISTRICTS:
        c = cells[cells.district == name]
        pop = c["pop"].values
        wmean = lambda v: float(np.nansum(v * pop) / np.nansum(pop[~np.isnan(v)])) if np.nansum(pop) > 0 else float(np.nanmean(v))
        share = lambda lim: float(np.nansum(pop[(c.t_major.values <= lim)]) / np.nansum(pop) * 100) if np.nansum(pop) > 0 else np.nan
        rows.append(dict(district=name, cells=len(c), pop=float(np.nansum(pop)), median_min=float(np.nanmedian(c.t_major)), popw_mean_min=wmean(c.t_major.values), median_local_min=float(np.nanmedian(c.t1)),
                         p90_min=float(np.nanpercentile(c.t_major.dropna(), 90)), pct_pop_le30=share(30), pct_pop_le60=share(60), pct_pop_le120=share(120),
                         pct_cells_gt120=float((c.t_major > 120).mean() * 100), mai_median=float(np.nanmedian(c.mai_raw))))
    st = pd.DataFrame(rows)
    st.to_csv(os.path.join(OUT, "sa1_district_stats.csv"), index=False)
    print(st.round(1).to_string())
    json.dump(pairs, open(os.path.join(OUT, "sa1_town_pairs.json"), "w"))
    return cells, dest


if __name__ == "__main__":
    run()
