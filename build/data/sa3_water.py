"""SA3 Water proximity and grazing suitability (weighted overlay with AHP weights)."""
from common import *
from rasterio import features
from scipy import ndimage as ndi

with rasterio.open(os.path.join(RAS, "dem.tif")) as d:
    TR = d.transform; SHAPE = d.shape
KM_X, KM_Y = 0.1032, 0.1113   # km per 0.001 deg at about 20.5 S


def rasterize(geoms, all_touched=True):
    geoms = [g for g in geoms if g is not None and not g.is_empty]
    if not geoms: return np.zeros(SHAPE, dtype=bool)
    return features.rasterize([(g, 1) for g in geoms], out_shape=SHAPE, transform=TR, fill=0, all_touched=all_touched, dtype="uint8").astype(bool)


def dist_km(mask):
    if not mask.any(): return np.full(SHAPE, 999.0, dtype="float32")
    return ndi.distance_transform_edt(~mask, sampling=(KM_Y, KM_X)).astype("float32")


def ahp(mat):
    mat = np.array(mat, dtype=float)
    vals, vecs = np.linalg.eig(mat)
    k = np.argmax(vals.real)
    w = np.abs(vecs[:, k].real); w = w / w.sum()
    n = len(mat); ci = (vals[k].real - n) / (n - 1)
    ri = {3: 0.58, 4: 0.90, 5: 1.12, 6: 1.24}[n]
    return w, ci / ri


def reclass(a, bins, scores):
    out = np.full(a.shape, scores[-1], dtype="float32")
    for b, s in reversed(list(zip(bins, scores))):
        out = np.where(a <= b, s, out)
    return out


def main():
    dist = districts()
    # --- water features
    pts, polys, riv = [], [], []
    for name in DISTRICTS:
        p = points(name, "water_pts"); pts.append(p)
        w = points(name, "water_poly"); polys.append(w)
        rv = lines(name, "rivers_main")
        if len(rv) == 0:
            rv = lines(name, "rivers"); rv = rv[rv.waterway == "river"] if len(rv) else rv
        riv.append(rv)
    pts = pd.concat(pts).drop_duplicates("osm_id"); polys = pd.concat(polys).drop_duplicates("osm_id")
    riv = gpd.GeoDataFrame(pd.concat(riv, ignore_index=True), crs=4326)
    print("water points", len(pts), "water polygons(centres)", len(polys), "river segments", len(riv), flush=True)
    jrc = read_ras("jrc_seasonality")[0]
    # reliable: man-made points (wells, boreholes, towers, springs), reservoirs/lakes, JRC seasonality >= 9 months
    manmade = pts[pts.man_made.isin(["water_well", "water_tower", "borehole", "water_works"]) | pts.natural.eq("spring") | pts.amenity.eq("drinking_water")]
    reservoirs = polys[polys.landuse.eq("reservoir") | polys.water.isin(["reservoir", "lake"])]
    rel = rasterize(list(manmade.geometry)) | rasterize(list(reservoirs.geometry)) | (jrc >= 9) & (jrc <= 12)
    any_w = rel | rasterize(list(polys.geometry)) | rasterize(list(riv.geometry)) | ((jrc >= 1) & (jrc <= 12))
    d_rel = dist_km(rel); d_any = dist_km(any_w)
    print("reliable water px", int(rel.sum()), "any water px", int(any_w.sum()), flush=True)
    # --- criteria
    dem = read_ras("dem")[0].astype("float32")
    dem[dem < -500] = np.nan
    dzdy, dzdx = np.gradient(np.nan_to_num(dem, nan=np.nanmean(dem)), KM_Y * 1000, KM_X * 1000)
    slope = np.hypot(dzdx, dzdy) * 100
    rain = read_ras("chirps_mean")[0]; cv = read_ras("chirps_cv")[0]
    wc = read_ras("worldcover")[0]
    ndvi_p = os.path.join(RAS, "ndvi_dry2025.tif")
    have_ndvi = os.path.exists(ndvi_p)
    if have_ndvi:
        ndvi = read_ras("ndvi_dry2025")[0]
        have_ndvi = (ndvi > -9).mean() > 0.5
    # scores 1..5
    s_wd = reclass(d_rel, [3, 6, 10, 15], [5, 4, 3, 2, 1])
    s_ww = reclass(d_any, [3, 6, 10, 15], [5, 4, 3, 2, 1])
    s_rain = reclass(-rain, [-800, -650, -500, -350], [5, 4, 3, 2, 1])   # larger rainfall -> higher score
    s_rain = np.where(cv > 0.30, np.maximum(s_rain - 1, 1), s_rain)
    s_slope = reclass(slope, [5, 10, 20, 30], [5, 4, 3, 2, 1])
    lc_map = {10: 3, 20: 5, 30: 5, 40: 2, 60: 1, 70: 1, 90: 3, 95: 3, 100: 2}
    s_lc = np.full(SHAPE, 3, dtype="float32")
    for k, v in lc_map.items(): s_lc[wc == k] = v
    excl = np.isin(wc, [50, 80])  # built-up, permanent water
    if have_ndvi:
        s_ndvi = reclass(-ndvi, [-0.45, -0.35, -0.25, -0.15], [5, 4, 3, 2, 1])
        crit = ["water access", "dry-season NDVI", "rainfall", "slope", "land cover"]
        # pairwise judgments (Saaty): water >> NDVI > rain > slope ~ land cover
        M = [[1, 2, 2, 4, 4], [1 / 2, 1, 2, 3, 3], [1 / 2, 1 / 2, 1, 3, 3], [1 / 4, 1 / 3, 1 / 3, 1, 1], [1 / 4, 1 / 3, 1 / 3, 1, 1]]
    else:
        crit = ["water access", "rainfall", "slope", "land cover"]
        M = [[1, 2, 4, 4], [1 / 2, 1, 3, 3], [1 / 4, 1 / 3, 1, 1], [1 / 4, 1 / 3, 1, 1]]
    w, cr = ahp(M)
    print("criteria", crit, "weights", np.round(w, 3), "CR", round(cr, 3), "NDVI used:", have_ndvi, flush=True)

    def suit(water_score, wts):
        stack = [water_score] + ([s_ndvi] if have_ndvi else []) + [s_rain, s_slope, s_lc]
        S = sum(wi * si for wi, si in zip(wts, stack))
        S = np.where(excl, np.nan, S)
        return S
    def classes(S):
        return np.digitize(S, [1.8, 2.6, 3.4, 4.2]) + 1
    S_dry = suit(s_wd, w); S_wet = suit(s_ww, w)
    C_dry = np.where(np.isnan(S_dry), 0, classes(S_dry)).astype("uint8")
    C_wet = np.where(np.isnan(S_wet), 0, classes(S_wet)).astype("uint8")
    # save rasters
    prof = dict(driver="GTiff", height=SHAPE[0], width=SHAPE[1], count=1, crs="EPSG:4326", transform=TR, compress="deflate")
    for nm, arr, dt, nd in [("suit_dry_class", C_dry, "uint8", 0), ("suit_wet_class", C_wet, "uint8", 0), ("dist_reliable_water_km", d_rel, "float32", None), ("dist_any_water_km", d_any, "float32", None), ("slope_pct", slope.astype("float32"), "float32", None)]:
        with rasterio.open(os.path.join(OUT, nm + ".tif"), "w", dtype=dt, nodata=nd, **prof) as dst: dst.write(arr.astype(dt), 1)
    # sensitivity: +-5 pp on each weight (renormalised); share of cells changing class (dry)
    base = C_dry
    chg = []
    for i in range(len(w)):
        for delta in (-0.05, 0.05):
            w2 = w.copy(); w2[i] = max(w2[i] + delta, 0.01); w2 = w2 / w2.sum()
            c2 = np.where(np.isnan(suit(s_wd, w2)), 0, classes(suit(s_wd, w2))).astype("uint8")
            valid = base > 0
            chg.append((crit[i], delta, float((c2[valid] != base[valid]).mean() * 100)))
    # district stats
    rows = []
    for name in DISTRICTS:
        m = rasterize([dist.loc[name].geometry], all_touched=False)
        v = m & (base > 0)
        area = v.sum() * KM_X * KM_Y
        row = dict(district=name, area_km2=float(m.sum() * KM_X * KM_Y), mean_dist_reliable_water_km=float(d_rel[m].mean()), median_dist_reliable_water_km=float(np.median(d_rel[m])),
                   pct_area_gt10km_reliable=float((d_rel[m] > 10).mean() * 100), mean_dist_any_water_km=float(d_any[m].mean()),
                   mean_rain_mm=float(rain[m][rain[m] > 0].mean()), mean_cv=float(cv[m][cv[m] > 0].mean()))
        if have_ndvi:
            nv = ndvi[m]; row["mean_ndvi_dry"] = float(nv[nv > -9].mean())
        for k, nm in enumerate(["very_low", "low", "moderate", "high", "very_high"], 1):
            row[f"dry_{nm}_pct"] = float(((C_dry == k) & m).sum() / max(v.sum(), 1) * 100)
            row[f"wet_{nm}_pct"] = float(((C_wet == k) & m).sum() / max(v.sum(), 1) * 100)
        rows.append(row)
    st = pd.DataFrame(rows)
    st.to_csv(os.path.join(OUT, "sa3_district_stats.csv"), index=False)
    json.dump(dict(criteria=crit, weights=[float(x) for x in w], CR=float(cr), ndvi_used=bool(have_ndvi), sensitivity=chg,
                   n_reliable_px=int(rel.sum()), n_manmade=int(len(manmade)), n_reservoirs=int(len(reservoirs)), n_river_segments=int(len(riv))),
              open(os.path.join(OUT, "sa3_meta.json"), "w"), indent=1)
    print(st.round(1).T.to_string()); print("sensitivity (%cells changing class):"); [print(" ", c) for c in chg]


if __name__ == "__main__":
    main()
