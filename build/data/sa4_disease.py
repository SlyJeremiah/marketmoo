"""SA4 Disease risk zones: reported-event control buffers, campaign high-risk districts, rainfall-based tick-borne disease suitability."""
from common import *
import requests, time

EVENTS = [
    dict(id="E1", disease="Foot-and-mouth disease (SAT 1)", species="Cattle", date="2026-01-05", district="Mangwe", province="Matabeleland South",
         places="Maholi and Hannavale dip tanks", detail="54 cases among 2,403 cattle; quarantine, movement restrictions, vaccination within a 20 km radius",
         source="https://allafrica.com/stories/202601290360.html", location_quality="district-level (dip tank coordinates not found); Mangwe centroid used"),
]
CAMPAIGN_DISTRICTS = ["Beitbridge", "Gwanda", "Mangwe", "Matobo"]
CAMPAIGN = dict(date="2026-05-13", vaccinated=72227, targeted=78034, source="https://dairybusinessmea.com/2026/05/13/zimbabwe-botswana-vaccinate-72000-cattle-against-fmd-as-south-africa-battles-outbreak-control-challenges/")
THEILERIOSIS = dict(district="Mhondoro-Ngezi", note="Described as a district badly affected by theileriosis (January disease) in a survey of 320 farmers", source="https://www.researchsquare.com/article/rs-2087240/v1")
BUF_CONTROL_KM = 20   # reported vaccination/control radius
BUF_SURV_KM = 40      # assumed surveillance ring (parameter; to be set by DVS)
TICK_BINS = (550, 700)


def main():
    all2 = gpd.read_file(os.path.join(HERE, "zwe_adm2.geojson")).set_index("shapeName")
    dist = districts()
    m = all2.to_crs(MCRS)
    # try to locate the dip tanks
    loc = None
    try:
        r = requests.get("https://nominatim.openstreetmap.org/search", params={"q": "Hannavale, Matabeleland South, Zimbabwe", "format": "jsonv2", "limit": 1}, headers={"User-Agent": "MarketMooSchoolProject/0.1 (student GIS assignment)"}, timeout=40).json()
        if r: loc = (float(r[0]["lon"]), float(r[0]["lat"]), r[0]["display_name"])
    except Exception as e:
        print("nominatim fail", e)
    ev_pt = gpd.GeoSeries([all2.loc["Mangwe"].geometry.centroid], crs=4326)
    if loc:
        print("located Hannavale:", loc)
    ev_m = ev_pt.to_crs(MCRS).iloc[0]
    zones = {"control_20km": ev_m.buffer(BUF_CONTROL_KM * 1000), "surveillance_40km": ev_m.buffer(BUF_SURV_KM * 1000)}
    gpd.GeoDataFrame({"zone": list(zones), "geometry": list(zones.values())}, crs=MCRS).to_crs(4326).to_file(os.path.join(OUT, "sa4_event_zones.geojson"), driver="GeoJSON")
    json.dump(dict(events=EVENTS, campaign=CAMPAIGN, theileriosis=THEILERIOSIS, campaign_districts=CAMPAIGN_DISTRICTS), open(os.path.join(OUT, "sa4_records.json"), "w"), indent=1)
    cells = pd.read_pickle(os.path.join(HERE, "sa2_cells.pkl")) if os.path.exists(os.path.join(HERE, "sa2_cells.pkl")) else pd.read_pickle(os.path.join(HERE, "sa1_cells.pkl"))
    cells_pts = gpd.GeoSeries(gpd.points_from_xy(cells.x, cells.y), crs=MCRS)
    cells["in_control"] = cells_pts.within(zones["control_20km"]).values
    cells["in_surv"] = cells_pts.within(zones["surveillance_40km"]).values
    cells["fmd_highrisk_district"] = cells.district.isin(CAMPAIGN_DISTRICTS)
    # tick-borne disease suitability from CHIRPS mean annual rainfall
    from sa1_market import sample_ras
    rain = sample_ras("chirps_mean", cells.lon.values, cells.lat.values)
    cells["rain_mm"] = rain
    cells["tick_class"] = np.where(rain >= TICK_BINS[1], "High", np.where(rain >= TICK_BINS[0], "Moderate", "Low"))
    cells.to_pickle(os.path.join(HERE, "sa4_cells.pkl"))
    # destinations (as proxy listing locations) inside zones
    dest = pd.read_pickle(os.path.join(HERE, "sa1_dest.pkl"))
    dpts = gpd.GeoSeries(gpd.points_from_xy(dest.x, dest.y), crs=MCRS)
    dest["in_control"] = dpts.within(zones["control_20km"]).values
    dest["in_surv"] = dpts.within(zones["surveillance_40km"]).values
    dest["district"] = ""
    for name in DISTRICTS:
        dest.loc[dpts.within(m.loc[name].geometry).values, "district"] = name
    rows = []
    for name in DISTRICTS:
        c = cells[cells.district == name]
        pop = c["pop"].values; tot = max(pop.sum(), 1e-9)
        d = dest[dest.district == name]
        rows.append(dict(district=name, in_fmd_campaign_district=name in CAMPAIGN_DISTRICTS,
                         pct_area_in_control20=float(c.in_control.mean() * 100), pct_area_in_surv40=float(c.in_surv.mean() * 100),
                         pct_pop_in_surv40=float(pop[c.in_surv.values].sum() / tot * 100),
                         mean_rain_mm=float(c.rain_mm.mean()), pct_area_tick_high=float((c.tick_class == "High").mean() * 100),
                         pct_area_tick_moderate=float((c.tick_class == "Moderate").mean() * 100), pct_area_tick_low=float((c.tick_class == "Low").mean() * 100),
                         destinations_in_district=len(d), destinations_in_surv40=int(d.in_surv.sum())))
    st = pd.DataFrame(rows); st.to_csv(os.path.join(OUT, "sa4_district_stats.csv"), index=False)
    print(st.round(1).T.to_string())
    d0 = ev_m.distance(m.loc["Gwanda"].geometry) / 1000
    print("distance from event point to Gwanda district boundary (km):", round(d0, 1))
    json.dump(dict(event_to_gwanda_km=float(d0), located=loc), open(os.path.join(OUT, "sa4_meta.json"), "w"))


if __name__ == "__main__":
    main()
