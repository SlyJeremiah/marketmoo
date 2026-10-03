"""SA2 Vet and service accessibility: travel-time coverage and 2SFCA."""
from common import *
from sa1_market import build_graph, to_m, origin_cells
from scipy.sparse.csgraph import dijkstra
from scipy.spatial import cKDTree

T0 = 60
T1 = 30
OFFROAD_KMH = 10.0


def service_points():
    """Service points from OSM plus a clearly labelled proxy: District Veterinary Services office assumed at the district's main town."""
    rows, seen = [], set()
    for name in DISTRICTS:
        for _, r in points(name, "vets").iterrows():
            if r.osm_id in seen: continue
            seen.add(r.osm_id)
            nm = (r["name"] or "").lower()
            tags = json.loads(r.tags)
            if r.amenity == "veterinary" or "veterinary" in nm or "animal health" in nm:
                kind, w = "vet (OSM)", 1.0
            elif "agritex" in nm:
                kind, w = "AGRITEX office (OSM)", 0.5
            elif "dip" in nm:
                kind, w = "dip tank (OSM)", 0.3
            elif r.shop == "agrarian":
                kind, w = "agro-dealer (OSM)", 0.3
            else:
                continue
            rows.append(dict(osm_id=r.osm_id, name=r["name"] or kind, lon=r.lon, lat=r.lat, kind=kind, weight=w, source="OpenStreetMap"))
    up = os.path.join(HERE, "user_supplied", "dip_tanks.geojson")
    if os.path.exists(up):
        g = gpd.read_file(up).to_crs(4326)
        for i, r in g.iterrows():
            c = r.geometry.centroid
            rows.append(dict(osm_id=f"user:{i}", name=str(r.get("name", "Dip tank")), lon=c.x, lat=c.y, kind="dip tank (Zimbabwe Geoportal)", weight=0.3, source="Zimbabwe Geoportal (user supplied)"))
    osm_n = len(rows)
    # proxy offices at the main town of each district
    dist = districts()
    for name in DISTRICTS:
        poly = dist.loc[name].geometry
        best = None
        want = {"Gwanda": "Gwanda", "Beitbridge": "Beitbridge", "Mhondoro-Ngezi": "Mhondoro"}[name]
        for _, r in points(name, "places").iterrows():
            if r["name"] == want and poly.contains(r.geometry):
                best = r; break
        if best is None:
            for _, r in points(name, "places").iterrows():
                if r.place in ("town", "city") and poly.contains(r.geometry):
                    best = r; break
        if best is None:
            cands = [r for _, r in points(name, "places").iterrows() if poly.contains(r.geometry)]
            if cands:
                cands.sort(key=lambda r: {"village": 0, "hamlet": 1}.get(r.place, 2)); best = cands[0]
        if best is None:
            c = poly.centroid; rows.append(dict(osm_id="proxy", name=f"{name} district centre (proxy)", lon=c.x, lat=c.y, kind="DVS office (proxy)", weight=1.0, source="ASSUMED at district centre"))
        else:
            rows.append(dict(osm_id="proxy:" + best.osm_id, name=f"DVS district office, {best['name']} (proxy)", lon=best.lon, lat=best.lat, kind="DVS office (proxy)", weight=1.0, source="ASSUMED at main town"))
    return pd.DataFrame(rows), osm_n


def run():
    A, Dm, mc, coords = build_graph()
    tree = cKDTree(mc)
    cells = pd.read_pickle(os.path.join(HERE, "sa1_cells.pkl"))
    sp, osm_n = service_points()
    m = to_m(sp.lon.values, sp.lat.values)
    sp["x"], sp["y"] = m[:, 0], m[:, 1]
    sd, si = tree.query(m)
    od, oi = tree.query(np.c_[cells.x, cells.y])
    acc = od / 1000.0 / OFFROAD_KMH * 60.0
    sacc = sd / 1000.0 / OFFROAD_KMH * 60.0
    n = len(sp)
    T = np.full((len(cells), n), np.inf, dtype="float32")
    for j in range(n):
        dm = dijkstra(A, directed=False, indices=[int(si[j])], limit=T0 + 60)[0]
        T[:, j] = dm[oi] + acc + sacc[j]
    demand = cells["pop"].values.astype("float64")
    W = sp.weight.values
    out = {}
    for t0, tag in [(T0, "60"), (T1, "30")]:
        inside = T <= t0
        R = np.zeros(n)
        for j in range(n):
            dj = demand[inside[:, j]].sum()
            R[j] = W[j] / dj if dj > 0 else 0
        Ai = (inside * R[None, :]).sum(1)
        cells[f"sfca{tag}"] = Ai * 1000  # service units per 1000 people
    vet_cols = [j for j in range(n) if sp.kind.iloc[j].startswith(("vet", "DVS"))]
    cells["t_vet"] = np.nanmin(np.where(np.isfinite(T[:, vet_cols]), T[:, vet_cols], np.nan), axis=1) if vet_cols else np.nan
    cells["t_any"] = np.nanmin(np.where(np.isfinite(T), T, np.nan), axis=1)
    near = np.argmin(np.where(np.isfinite(T), T, 1e9), axis=1)
    cells["nearest_service"] = [sp.name.iloc[i] if np.isfinite(T[r, i]) else "" for r, i in enumerate(near)]
    cells["cover"] = pd.cut(cells.t_any, [-1, 30, 60, 1e9], labels=["Covered (<=30 min)", "Partly (30-60 min)", "Underserved (>60 min)"]).astype(str)
    cells.loc[cells.t_any.isna(), "cover"] = "Underserved (>60 min)"
    cells.to_pickle(os.path.join(HERE, "sa2_cells.pkl"))
    sp.to_pickle(os.path.join(HERE, "sa2_points.pkl"))
    rows = []
    for name in DISTRICTS:
        c = cells[cells.district == name]
        pop = c["pop"].values
        tot = pop.sum()
        pct = lambda mask: float(pop[mask].sum() / tot * 100) if tot > 0 else float((mask).mean() * 100)
        ta = c.t_any.fillna(999).values
        rows.append(dict(district=name, service_points_in_district=int(sum(1 for _, r in sp.iterrows() if districts().loc[name].geometry.contains(Point(r.lon, r.lat)))),
                         median_min_to_service=float(np.nanmedian(c.t_any)), pct_pop_le30=pct(ta <= 30), pct_pop_le60=pct(ta <= 60), pct_pop_gt60=pct(ta > 60),
                         pct_cells_gt60=float((ta > 60).mean() * 100), sfca60_median=float(np.nanmedian(c.sfca60))))
    st = pd.DataFrame(rows)
    st.to_csv(os.path.join(OUT, "sa2_district_stats.csv"), index=False)
    sp.drop(columns=["x", "y"]).to_csv(os.path.join(OUT, "sa2_service_points_used.csv"), index=False)
    print(sp[["name", "kind", "source"]].to_string()); print(st.round(1).to_string()); print("OSM-derived service points:", osm_n)


if __name__ == "__main__":
    run()
