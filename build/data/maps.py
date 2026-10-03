from common import *
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap, BoundaryNorm
from matplotlib.patches import Patch
from rasterio import features
from sa3_water import rasterize, TR, SHAPE

MAPS = os.path.join(OUT, "maps")
dist = districts()
distm = dist.to_crs(MCRS)
plt.rcParams.update({"font.size": 8, "font.family": "DejaVu Sans"})
GREEN = "#1f7a3a"


def grid(df, col, name, cat=None):
    c = df[df.district == name]
    x0, y0, x1, y1 = distm.loc[name].geometry.bounds
    nx = int((x1 - x0) // 1000) + 1; ny = int((y1 - y0) // 1000) + 1
    a = np.full((ny, nx), np.nan)
    ix = ((c.x.values - x0) // 1000).astype(int); iy = ((c.y.values - y0) // 1000).astype(int)
    vals = c[col].values
    a[iy, ix] = vals
    return a, (x0, x0 + nx * 1000, y0, y0 + ny * 1000)


def base(ax, name, roads=None):
    g = distm.loc[name].geometry
    gpd.GeoSeries([g], crs=MCRS).boundary.plot(ax=ax, color="black", lw=0.8, zorder=5)
    if roads is not None:
        r = roads[roads.highway.isin(["trunk", "primary", "secondary"])]
        r.to_crs(MCRS).clip(g.buffer(5000)).plot(ax=ax, color="#555555", lw=0.5, zorder=4)
    ax.set_aspect("equal"); ax.set_xticks([]); ax.set_yticks([])
    for s in ax.spines.values(): s.set_visible(False)
    x0, y0, x1, y1 = g.bounds
    ax.set_xlim(x0 - 3000, x1 + 3000); ax.set_ylim(y0 - 3000, y1 + 3000)


def roads_for(name):
    return lines(name, "roads", ("highway",))


def panel_fig(title, fn, w=9.0, h=4.0):
    fig, axs = plt.subplots(1, 3, figsize=(w, h))
    fig.suptitle(title, fontsize=10, fontweight="bold", color="#155c2b", x=0.01, ha="left")
    return fig, axs


def ptsm(df, ax, **kw):
    if len(df):
        g = gpd.GeoSeries(gpd.points_from_xy(df.x, df.y), crs=MCRS); ax.scatter(g.x, g.y, zorder=6, **kw)


def sa1():
    cells = pd.read_pickle(os.path.join(HERE, "sa1_cells.pkl")); dest = pd.read_pickle(os.path.join(HERE, "sa1_dest.pkl"))
    fig, axs = panel_fig("SA1  Market accessibility: road travel time to the nearest market or town (minutes)", None)
    cmap = ListedColormap(["#1a9850", "#91cf60", "#fee08b", "#fc8d59", "#d73027", "#7f0000"]); norm = BoundaryNorm([0, 30, 60, 120, 180, 240, 1e4], cmap.N)
    for ax, name in zip(axs, DISTRICTS):
        a, ext = grid(cells, "t_major", name)
        im = ax.imshow(a, origin="lower", extent=ext, cmap=cmap, norm=norm, interpolation="nearest", zorder=1)
        base(ax, name, roads_for(name)); ax.set_title(name, fontsize=9)
        d = dest[(dest.weight >= 2)]
        ptsm(d, ax, s=14, c="white", edgecolors="black", linewidths=0.8, marker="o")
    fig.colorbar(im, ax=axs, orientation="horizontal", fraction=0.05, pad=0.04, ticks=[15, 45, 90, 150, 210], label="minutes (classes: <30, 30-60, 60-120, 120-180, 180-240, >240)  |  white dots = towns, markets, abattoirs")
    fig.savefig(os.path.join(MAPS, "sa1_market_access.png"), dpi=170, bbox_inches="tight"); plt.close(fig)


def sa2():
    cells = pd.read_pickle(os.path.join(HERE, "sa2_cells.pkl")); sp = pd.read_pickle(os.path.join(HERE, "sa2_points.pkl"))
    fig, axs = panel_fig("SA2  Vet and service accessibility: travel-time coverage classes", None)
    order = ["Covered (<=30 min)", "Partly (30-60 min)", "Underserved (>60 min)"]; colors = ["#1a9850", "#fee08b", "#d73027"]
    cells["cc"] = cells.cover.map({k: i for i, k in enumerate(order)})
    for ax, name in zip(axs, DISTRICTS):
        a, ext = grid(cells, "cc", name)
        ax.imshow(a, origin="lower", extent=ext, cmap=ListedColormap(colors), vmin=0, vmax=2, interpolation="nearest", zorder=1)
        base(ax, name, roads_for(name)); ax.set_title(name, fontsize=9)
        ptsm(sp[sp.source.str.startswith("Open")], ax, s=22, c="#1e88e5", edgecolors="white", marker="^")
        ptsm(sp[~sp.source.str.startswith("Open")], ax, s=26, c="white", edgecolors="black", marker="*", linewidths=0.8)
    h = [Patch(color=c, label=l) for c, l in zip(colors, order)]
    fig.legend(handles=h, loc="lower center", ncol=3, frameon=False, bbox_to_anchor=(0.5, -0.02))
    fig.text(0.5, -0.07, "white star = assumed District Veterinary Services office (proxy); blue triangle = service point from OpenStreetMap", ha="center", fontsize=7)
    fig.savefig(os.path.join(MAPS, "sa2_service_access.png"), dpi=170, bbox_inches="tight"); plt.close(fig)


def sa3():
    fig, axs = panel_fig("SA3  Grazing suitability, dry season (weighted overlay, 100 m)", None)
    with rasterio.open(os.path.join(OUT, "suit_dry_class.tif")) as d: C = d.read(1)
    pts = pd.concat([points(n, "water_pts") for n in DISTRICTS]).drop_duplicates("osm_id")
    cols = ["#d73027", "#fc8d59", "#fee08b", "#91cf60", "#1a9850"]
    for ax, name in zip(axs, DISTRICTS):
        g = dist.loc[name].geometry; x0, y0, x1, y1 = g.bounds
        m = rasterize([g], all_touched=False)
        arr = np.where(m & (C > 0), C, np.nan)
        ax.imshow(arr, extent=(TR.c, TR.c + TR.a * SHAPE[1], TR.f + TR.e * SHAPE[0], TR.f), cmap=ListedColormap(cols), vmin=1, vmax=5, interpolation="nearest", zorder=1)
        gpd.GeoSeries([g], crs=4326).boundary.plot(ax=ax, color="black", lw=0.8, zorder=5)
        p = pts[pts.within(g.buffer(0.02))]; ax.scatter(p.lon, p.lat, s=6, c="#1e88e5", zorder=6)
        ax.set_xlim(x0 - 0.03, x1 + 0.03); ax.set_ylim(y0 - 0.03, y1 + 0.03); ax.set_aspect(1.07); ax.set_xticks([]); ax.set_yticks([])
        for s in ax.spines.values(): s.set_visible(False)
        ax.set_title(name, fontsize=9)
    h = [Patch(color=c, label=l) for c, l in zip(cols, ["1 very low", "2 low", "3 moderate", "4 high", "5 very high"])] + [Patch(color="#1e88e5", label="water points (OSM)")]
    fig.legend(handles=h, loc="lower center", ncol=6, frameon=False, bbox_to_anchor=(0.5, -0.02))
    fig.savefig(os.path.join(MAPS, "sa3_grazing_suitability.png"), dpi=170, bbox_inches="tight"); plt.close(fig)
    # water distance
    fig, axs = panel_fig("SA3  Distance to reliable water, dry season (km)", None)
    with rasterio.open(os.path.join(OUT, "dist_reliable_water_km.tif")) as d: D = d.read(1)
    cmap = ListedColormap(["#08519c", "#6baed6", "#fee08b", "#fc8d59", "#b30000"]); norm = BoundaryNorm([0, 3, 6, 10, 15, 999], cmap.N)
    for ax, name in zip(axs, DISTRICTS):
        g = dist.loc[name].geometry; x0, y0, x1, y1 = g.bounds
        m = rasterize([g], all_touched=False)
        im = ax.imshow(np.where(m, D, np.nan), extent=(TR.c, TR.c + TR.a * SHAPE[1], TR.f + TR.e * SHAPE[0], TR.f), cmap=cmap, norm=norm, interpolation="nearest", zorder=1)
        gpd.GeoSeries([g], crs=4326).boundary.plot(ax=ax, color="black", lw=0.8, zorder=5)
        ax.set_xlim(x0 - 0.03, x1 + 0.03); ax.set_ylim(y0 - 0.03, y1 + 0.03); ax.set_aspect(1.07); ax.set_xticks([]); ax.set_yticks([])
        for s in ax.spines.values(): s.set_visible(False)
        ax.set_title(name, fontsize=9)
    fig.colorbar(im, ax=axs, orientation="horizontal", fraction=0.05, pad=0.04, ticks=[1.5, 4.5, 8, 12.5, 20], label="km to reliable water (classes: <3, 3-6, 6-10, 10-15, >15)")
    fig.savefig(os.path.join(MAPS, "sa3_water_distance.png"), dpi=170, bbox_inches="tight"); plt.close(fig)


def sa4():
    from matplotlib.gridspec import GridSpec
    cells = pd.read_pickle(os.path.join(HERE, "sa4_cells.pkl"))
    zones = gpd.read_file(os.path.join(OUT, "sa4_event_zones.geojson")).to_crs(MCRS)
    all2 = gpd.read_file(os.path.join(HERE, "zwe_adm2.geojson")).set_index("shapeName").to_crs(MCRS)
    fig = plt.figure(figsize=(9.5, 5.2))
    gs = GridSpec(2, 3, figure=fig, width_ratios=[1.25, 1, 1], height_ratios=[1, 1.15], wspace=0.05, hspace=0.12)
    fig.suptitle("SA4  Disease risk: reported FMD zones and rainfall-based tick-borne disease suitability", fontsize=10, fontweight="bold", color="#155c2b", x=0.01, ha="left")
    ax = fig.add_subplot(gs[:, 0])
    sub = all2.loc[["Mangwe", "Matobo", "Gwanda", "Beitbridge", "Mhondoro-Ngezi", "Insiza", "Umzingwane", "Bulilima", "Mwenezi", "Chegutu", "Zvimba", "Sanyati", "Kwekwe", "Gwanda Urban", "Beitbridge Urban"]].copy()
    sub["c"] = ["#f4a582" if n in ("Mangwe", "Matobo", "Gwanda", "Beitbridge", "Gwanda Urban", "Beitbridge Urban") else ("#92c5de" if n == "Mhondoro-Ngezi" else "#eeeeee") for n in sub.index]
    sub.plot(ax=ax, color=sub["c"], edgecolor="#777777", lw=0.5)
    for _, r in zones.iterrows():
        gpd.GeoSeries([r.geometry], crs=MCRS).boundary.plot(ax=ax, color="#b30000" if r.zone.startswith("control") else "#e6550d", lw=1.2, ls="-" if r.zone.startswith("control") else "--")
    ev = zones.iloc[0].geometry.centroid
    ax.annotate("FMD event\n(Jan 2026)", (ev.x, ev.y), xytext=(ev.x - 190000, ev.y - 60000), fontsize=6.5, arrowprops=dict(arrowstyle="-", lw=0.6))
    for n, dy in [("Gwanda", 0), ("Beitbridge", -30000), ("Mhondoro-Ngezi", 0)]:
        c = sub.loc[n].geometry.representative_point(); ax.annotate(n, (c.x, c.y + dy), fontsize=6.5, ha="center", fontweight="bold")
    ax.set_aspect("equal"); ax.set_xticks([]); ax.set_yticks([])
    for sp_ in ax.spines.values(): sp_.set_visible(False)
    ax.legend(handles=[Patch(color="#f4a582", label="FMD vaccination campaign districts (May 2026)"), Patch(color="#92c5de", label="Mhondoro-Ngezi (theileriosis hotspot)"),
                       plt.Line2D([], [], color="#b30000", label="20 km control zone around event"), plt.Line2D([], [], color="#e6550d", ls="--", label="40 km surveillance ring (assumed)")],
              loc="upper left", fontsize=5.8, frameon=True, framealpha=0.9)
    colmap = {"Low": "#ffffb2", "Moderate": "#fd8d3c", "High": "#800026"}
    for pos, name in zip([gs[0, 1:], gs[1, 1], gs[1, 2]], ["Mhondoro-Ngezi", "Gwanda", "Beitbridge"]):
        a2 = fig.add_subplot(pos)
        c = cells[cells.district == name]
        a2.scatter(c.x, c.y, c=c.tick_class.map(colmap), s=3.2, marker="s", linewidths=0)
        gpd.GeoSeries([distm.loc[name].geometry], crs=MCRS).boundary.plot(ax=a2, color="black", lw=0.7)
        x0, y0, x1, y1 = distm.loc[name].geometry.bounds
        a2.set_xlim(x0 - 3000, x1 + 3000); a2.set_ylim(y0 - 3000, y1 + 3000); a2.set_aspect("equal"); a2.set_xticks([]); a2.set_yticks([])
        for sp_ in a2.spines.values(): sp_.set_visible(False)
        a2.set_title(f"{name}  (mean {c.rain_mm.mean():.0f} mm)", fontsize=8)
    fig.legend(handles=[Patch(color=cc, label=l) for cc, l in zip(["#ffffb2", "#fd8d3c", "#800026"], ["Low (<550 mm)", "Moderate (550-700 mm)", "High (>=700 mm)"])], loc="lower right", ncol=3, fontsize=7, frameon=False, title="Tick-borne disease suitability (annual rainfall proxy)", title_fontsize=7, bbox_to_anchor=(0.98, -0.03))
    fig.savefig(os.path.join(MAPS, "sa4_disease_risk.png"), dpi=170, bbox_inches="tight"); plt.close(fig)


def overview():
    fig, ax = plt.subplots(figsize=(7.5, 6))
    all2 = gpd.read_file(os.path.join(HERE, "zwe_adm2.geojson")).to_crs(MCRS)
    all2.plot(ax=ax, color="#f2f2f2", edgecolor="#bbbbbb", lw=0.4)
    for n, c in zip(DISTRICTS, ["#1f7a3a", "#e0a800", "#1e88e5"]):
        distm.loc[[n]].plot(ax=ax, color=c, edgecolor="black", lw=0.8, alpha=0.8)
        p = distm.loc[n].geometry.representative_point(); ax.annotate(n, (p.x, p.y), fontsize=8, ha="center", fontweight="bold")
    ax.set_xlim(distm.total_bounds[0] - 250000, distm.total_bounds[2] + 250000); ax.set_ylim(distm.total_bounds[1] - 150000, distm.total_bounds[3] + 150000)
    ax.set_aspect("equal"); ax.set_xticks([]); ax.set_yticks([]); ax.set_title("Study districts (district boundaries: geoBoundaries ADM2)", fontsize=9)
    for s in ax.spines.values(): s.set_visible(False)
    fig.savefig(os.path.join(MAPS, "study_area.png"), dpi=170, bbox_inches="tight"); plt.close(fig)


if __name__ == "__main__":
    import sys
    for f in sys.argv[1:] or ["overview", "sa1", "sa2", "sa3", "sa4"]:
        globals()[f](); print("map", f, flush=True)
