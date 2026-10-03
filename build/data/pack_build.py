"""Build per-district SQLite packs from analysis outputs, measure sizes, write data_counts.json and example_card.json."""
import sqlite3, gzip, shutil
from common import *

PK = os.path.join(OUT, "packs")
os.makedirs(PK, exist_ok=True)


def counts():
    c = dict(road_ways=0, places=0, destinations=0, osm_service_points=0, water_pts=0, water_poly=0, rivers=0)
    seen = set()
    for n in DISTRICTS:
        for e in elems(n, "roads"):
            if e["id"] not in seen: seen.add(e["id"])
    c["road_ways"] = len(seen)
    for key, layer in [("places", "places"), ("water_pts", "water_pts"), ("water_poly", "water_poly")]:
        s = set()
        for n in DISTRICTS:
            for e in elems(n, layer): s.add(e["id"])
        c[key] = len(s)
    c["rivers"] = sum(v["rivers_main"] for v in [{"rivers_main": len(elems(n, "rivers_main")) or sum(1 for e in elems(n, "rivers") if e.get("tags", {}).get("waterway") == "river")} for n in DISTRICTS])
    d = pd.read_pickle(os.path.join(HERE, "sa1_dest.pkl")); c["destinations"] = len(d)
    sp = pd.read_pickle(os.path.join(HERE, "sa2_points.pkl"))
    c["osm_service_points"] = int((~sp.source.str.startswith("ASSUMED")).sum())
    bd = {}
    for n in DISTRICTS:
        bd[n] = {k: len(elems(n, k)) for k in ("roads", "places", "markets", "water_pts", "water_poly", "rivers_main", "vets")}
        if bd[n]["rivers_main"] == 0:
            bd[n]["rivers_main"] = sum(1 for e in elems(n, "rivers") if e.get("tags", {}).get("waterway") == "river")
    c["by_district"] = bd
    json.dump(c, open(os.path.join(OUT, "data_counts.json"), "w"))
    return c


def build():
    cells = pd.read_pickle(os.path.join(HERE, "sa4_cells.pkl"))
    dest = pd.read_pickle(os.path.join(HERE, "sa1_dest.pkl")); sp = pd.read_pickle(os.path.join(HERE, "sa2_points.pkl"))
    # attach analysis-3 values by sampling rasters
    from sa1_market import sample_ras
    import rasterio
    def samp(path, lon, lat):
        with rasterio.open(path) as d:
            a = d.read(1)
            from rasterio.transform import rowcol
            r, c = rowcol(d.transform, lon, lat)
            r = np.clip(r, 0, a.shape[0] - 1); c = np.clip(c, 0, a.shape[1] - 1)
            return a[r, c]
    cells["water_dry_km"] = samp(os.path.join(OUT, "dist_reliable_water_km.tif"), cells.lon.values, cells.lat.values)
    cells["water_any_km"] = samp(os.path.join(OUT, "dist_any_water_km.tif"), cells.lon.values, cells.lat.values)
    cells["suit_dry"] = samp(os.path.join(OUT, "suit_dry_class.tif"), cells.lon.values, cells.lat.values)
    cells["suit_wet"] = samp(os.path.join(OUT, "suit_wet_class.tif"), cells.lon.values, cells.lat.values)
    cells.to_pickle(os.path.join(HERE, "final_cells.pkl"))
    rows = []
    for name in DISTRICTS:
        c = cells[cells.district == name]
        f = os.path.join(PK, f"pack_{name}.sqlite")
        if os.path.exists(f): os.remove(f)
        db = sqlite3.connect(f)
        db.execute("create table meta(k text, v text)")
        db.executemany("insert into meta values(?,?)", [("district", name), ("pack_version", "2026.10.02"), ("crs", "WGS84"), ("cell_km", "1"), ("note", "school trial; see Design Document section 7")])
        db.execute("create table cells(id integer primary key, lon real, lat real, t_major integer, m_major text, t_local integer, mai integer, t_service integer, cover integer, water_dry_km real, suit_dry integer, suit_wet integer, tick integer, in_zone integer)")
        cov = {"Covered (<=30 min)": 0, "Partly (30-60 min)": 1, "Underserved (>60 min)": 2}
        tick = {"Low": 0, "Moderate": 1, "High": 2}
        recs = []
        for _, r in c.iterrows():
            f_ = lambda v: None if pd.isna(v) else int(round(v))
            recs.append((int(r.cell_id), round(r.lon, 4), round(r.lat, 4), f_(r.t_major), r.m_major, f_(r.t1), f_(r.mai), f_(r.t_any), cov.get(r.cover, 2), None if pd.isna(r.water_dry_km) else round(float(r.water_dry_km), 1), int(r.suit_dry), int(r.suit_wet), tick[r.tick_class], int(bool(r.in_surv))))
        db.executemany("insert into cells values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", recs)
        db.execute("create table features(kind text, name text, lon real, lat real, weight real, source text)")
        feats = []
        dd = dest[dest.weight >= 1]
        poly = districts().loc[name].geometry.buffer(0.25)
        for _, r in dd.iterrows():
            if poly.contains(Point(r.lon, r.lat)): feats.append((r.kind, r["name"], round(r.lon, 4), round(r.lat, 4), r.weight, "OpenStreetMap"))
        for _, r in sp.iterrows():
            if poly.contains(Point(r.lon, r.lat)): feats.append((r.kind, r["name"], round(r.lon, 4), round(r.lat, 4), r.weight, r.source))
        pts = pd.concat([points(n, "water_pts") for n in DISTRICTS]).drop_duplicates("osm_id")
        for _, r in pts.iterrows():
            if poly.contains(Point(r.lon, r.lat)): feats.append(("water point", r["name"], round(r.lon, 4), round(r.lat, 4), 1, "OpenStreetMap"))
        db.executemany("insert into features values (?,?,?,?,?,?)", feats)
        db.execute("create index cells_ll on cells(lat, lon)")
        db.commit(); db.execute("vacuum"); db.close()
        gz = f + ".gz"
        with open(f, "rb") as a, gzip.open(gz, "wb", 9) as b: shutil.copyfileobj(a, b)
        rows.append(dict(district=name, cells=len(c), sqlite_kb=os.path.getsize(f) / 1024, gz_kb=os.path.getsize(gz) / 1024, features=len(feats)))
    tot_sql = sum(r["sqlite_kb"] for r in rows); tot_gz = sum(r["gz_kb"] for r in rows)
    json.dump(dict(rows=rows, summary=f"{max(r['gz_kb'] for r in rows):.0f} KB or less per district compressed ({tot_gz:.0f} KB for all three; {tot_sql:.0f} KB uncompressed)"), open(os.path.join(OUT, "pack_sizes.json"), "w"), indent=1)
    print(rows)
    # example decision card from a real Gwanda cell (median-time populated cell)
    g = cells[(cells.district == "Gwanda") & (cells["pop"] > cells[cells.district == "Gwanda"]["pop"].median()) & cells.t_any.notna() & cells.t_major.notna()]
    g = g.iloc[(g.t_major - g.t_major.median()).abs().argsort()[:1]].iloc[0]
    suit_names = {1: "very low", 2: "low", 3: "moderate", 4: "high", 5: "very high"}
    lines = [
        f"**Market:** nearest major market {g.m_major}, about {int(round(g.t_major))} minutes by road (assumed truck speeds).",
        f"**Vet:** nearest service point {g.nearest_service}, about {int(round(g.t_any)) if not pd.isna(g.t_any) else 'more than 120'} minutes; class: {g.cover}. (Service points are proxies until real data are added.)",
        f"**Water and grazing:** reliable water {g.water_dry_km:.1f} km away in the dry season; grazing suitability {suit_names[int(g.suit_dry)]} in the dry season and {suit_names[int(g.suit_wet)]} in the wet season.",
        f"**Disease:** {'inside' if g.in_surv else 'outside'} the 40 km surveillance ring of the reported FMD event; district is in the May 2026 FMD vaccination campaign; tick-borne disease suitability {g.tick_class.lower()} (mean rainfall {g.rain_mm:.0f} mm).",
    ]
    json.dump(dict(lines=lines, cell=int(g.cell_id), lon=float(g.lon), lat=float(g.lat)), open(os.path.join(OUT, "example_card.json"), "w"))
    print(lines)


if __name__ == "__main__":
    counts(); build()
