# MarketMoo: GIS pipeline and document build

Everything in `results/` is produced by the scripts in `data/`. Re-run in this order (Python 3.11 with geopandas, rasterio, scipy):

1. `data/fetch_osm*.py`: OpenStreetMap features via Overpass into `data/raw/` (resumable; tiles cached in `data/raw/tiles/`).
   Not yet retrieved because of server timeouts: Mhondoro-Ngezi water points and polygons, main rivers for Mhondoro-Ngezi and Gwanda.
   Run `python fetch_osm7.py` later, then re-run steps 3 to 6.
2. `data/fetch_rasters.py`, `fetch_ndvi.py`: CHIRPS, JRC water, ESA WorldCover, terrain tiles, Sentinel-2 NDVI into `data/rasters/`.
   WorldPop 2020 was downloaded to `data/big/` and warped to `rasters/worldpop_mean_ppp.tif`.
3. `data/sa1_market.py`, `sa2_service.py`, `sa3_water.py`, `sa4_disease.py`: the four analyses (CSV, GeoTIFF, GeoJSON in `results/`).
   To use real dip tanks: put the Zimbabwe Geoportal file at `data/user_supplied/dip_tanks.geojson` (free login needed to download), then re-run `sa2_service.py`.
4. `data/pack_build.py`: SQLite district packs in `results/packs/` plus `pack_sizes.json`, `data_counts.json`, `example_card.json`.
5. `data/maps.py`: figures in `results/maps/`.
6. `node pdd.js; node dd.js; node flow.js`: build the three Word documents (they read `results/`).

Environment note: on this machine `PROJ_LIB` pointed at PostgreSQL's PROJ data. `data/common.py` overrides it with rasterio's bundled `proj_data`.
