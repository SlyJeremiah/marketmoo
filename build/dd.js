const fs = require("fs");
const path = require("path");
const L = require("./lib");
const { h1, h2, h3, p, note, bullets, table, callout, img, cover, toc, build, spacer } = L;
const {
  Paragraph, ImageRun, AlignmentType, TextRun,
} = require("docx");

// ---------- results helpers (all numbers in Section 7 come from results/*.csv, produced by build/data/*.py)
const RES = path.join(__dirname, "results");
function csv(name) {
  const t = fs.readFileSync(path.join(RES, name), "utf8").trim().split(/\r?\n/);
  const h = t[0].split(",");
  return t.slice(1).map((l) => { const c = l.split(","); const o = {}; h.forEach((k, i) => (o[k] = c[i])); return o; });
}
const jf = (name) => JSON.parse(fs.readFileSync(path.join(RES, name), "utf8"));
const f1 = (x) => (x === "" || x === undefined || isNaN(Number(x)) ? "n/a" : Number(x).toFixed(1));
const f0 = (x) => (x === "" || x === undefined || isNaN(Number(x)) ? "n/a" : Math.round(Number(x)).toLocaleString("en-US"));
function mapImg(file, width, caption) {
  const buf = fs.readFileSync(path.join(RES, "maps", file));
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  const pxW = Math.round(width * 96), pxH = Math.round(pxW * h / w);
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 80, after: 40 }, keepNext: true, children: [new ImageRun({ type: "png", data: buf, transformation: { width: pxW, height: pxH }, altText: { title: caption, description: caption, name: file } })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 }, children: [new TextRun({ text: caption, italics: true, size: 18, color: "595959", font: "Calibri" })] }),
  ];
}

const S = {};
try {
  S.sa1 = csv("sa1_district_stats.csv"); S.sa2 = csv("sa2_district_stats.csv"); S.sa3 = csv("sa3_district_stats.csv"); S.sa4 = csv("sa4_district_stats.csv");
  S.m3 = jf("sa3_meta.json"); S.counts = jf("data_counts.json"); S.pack = jf("pack_sizes.json"); S.sa4m = jf("sa4_meta.json"); S.pairs = jf("sa1_town_pairs.json");
} catch (e) { console.error("results missing:", e.message); process.exit(1); }
const by = (arr, d) => arr.find((r) => r.district === d);
const D3 = ["Mhondoro-Ngezi", "Gwanda", "Beitbridge"];

const sections = ["Purpose and scope", "Design overview and key decisions", "System architecture", "Technology choices", "Offline and low-bandwidth design",
  "Dataset register", "Spatial analyses: methods and results", "Data model", "API and sync contract", "UX and interface design", "Testing and validation",
  "From the HTML prototype to the native app: backlog", "Security, privacy and safety", "Deployment and operations", "Open technical questions"];

const c = [];
c.push(...cover("Design Document", "Native Android architecture, offline strategy, GIS methods with real-data results, data model and UX", [
  ["Course / brief", "HGISEO400 project brief (due 02/10/2026)"],
  ["Product", "MarketMoo: livestock information and marketplace, with GIS decision support"],
  ["Pilot area", "Mhondoro-Ngezi, Gwanda and Beitbridge districts, Zimbabwe"],
  ["Delivery form", "Native Android app (APK; scaffold built, see MarketMooApp), Django backend, Neon PostgreSQL, Backblaze B2, React manager dashboard"],
  ["Depends on", "Product Design Document (requirement IDs FR-xx, NFR-xx)"],
  ["Companion", "App Flow Document (screens and user flows)"],
  ["Version / date", "Draft v0.2, 2 October 2026"],
  ["Group", "[TO CONFIRM: group name and members]"],
]));
c.push(...toc(sections));

// 1
c.push(h1("1. Purpose and scope"));
c.push(p("This document explains how MarketMoo is built: the system architecture, the technology choices, how the Android app works with little or no connectivity, how each of the four spatial analyses is computed **and what they produced on real open data for the three pilot districts**, the data model, and how the system will be tested."));
c.push(p("It covers the native Android application, the supporting backend and manager dashboard, and the GIS processing pipeline. Business context, personas and requirements are in the Product Design Document; screens and flows are in the App Flow Document."));
c.push(callout("Decisions taken with the group's answers", [
  "Delivery is a **native Android APK** (not a PWA).",
  "The spatial analyses **must run on real data**, so Section 7 reports results computed from open datasets.",
  "No partner data is available (school trial), so every dataset is taken from open internet sources; gaps are stated, not hidden.",
  "The stack follows the group's reference architecture (Kotlin and Jetpack Compose with SQLCipher and MapLibre on the device; Django REST Framework on Render; React manager dashboard on Vercel) with two substitutions made when the group supplied its accounts: **Neon serverless PostgreSQL replaces Supabase**, and **Backblaze B2 replaces Cloudflare R2**. Celery background jobs are planned, not built.",
]));

c.push(spacer());
c.push(h3("Implementation status (3 October 2026)"));
c.push(table(["Part", "State", "Evidence"], [
  ["Android app (MarketMooApp)", "Built; compiles; not yet run on a device", "Sign-in with phone and code, encrypted offline records and listings, map, Farm Insights from real packs, authenticated sync with photo upload, pools, outbreak reports, pack updates; debug APK builds, unit tests pass"],
  ["Django API (MarketMooApi)", "Built, tested, schema created on Neon", "50 automated tests pass; migrations applied to the Neon database; three packs uploaded to the Backblaze B2 bucket and a ranged download verified"],
  ["Manager dashboard (MarketMooDashboard)", "Built and exercised against a local test server", "Sign-in, overview charts, listing moderation (approve changed the public feed), disease reports and notices, pools, map, users, packs; production build and tests pass"],
  ["Not done", "Planned", "Deployment to Render and Vercel; Celery alert jobs and a real SMS gateway; real facility and dip-tank data; field validation; Shona and Ndebele strings; on-device and load testing"],
], [2300, 2300, 4426], { firstColShade: true }));

// 2
c.push(h1("2. Design overview and key decisions"));
c.push(table(["#", "Decision", "Rationale", "Alternative considered"], [
  ["D1", "Native Android app in Kotlin with Jetpack Compose", "Required APK; best offline behaviour, background sync, encrypted storage and native map performance on low-end phones", "PWA (rejected by the group); cross-platform frameworks (heavier runtime)"],
  ["D2", "Offline-first: encrypted local database is the source of truth for the user's own data (Room with SQLCipher, key held in Android Keystore)", "Connectivity is the core constraint; every write must succeed instantly and be protected on shared phones", "Online-first with caching"],
  ["D3", "Precompute GIS in batch; ship per-district packs", "Phones should not run routing or raster analysis; precomputed results are small and instant (measured in Section 5.5)", "On-demand server geoprocessing; on-device routing"],
  ["D4", "Neon serverless PostgreSQL as system of record; Django REST Framework API in front", "Standard SQL on a free tier that scales to zero; Django gives admin, validation and permissions. Spatial filtering uses latitude and longitude with bounding boxes and haversine; PostGIS stays an option when listings grow", "Supabase (the original plan); direct client access to the database (weaker server-side rules)"],
  ["D5", "Backblaze B2 (S3-compatible API) for photos and downloadable packs, reached through short-lived presigned URLs", "Cheap private bucket; HTTP range requests give resumable downloads; the API never handles image bytes", "Cloudflare R2 (the original plan)"],
  ["D6", "Phone number and SMS one-time code issued by the Django API (codes stored as keyed hashes)", "No passwords to forget; no national ID stored", "Name and password (the HTML prototype); Supabase Auth"],
  ["D7", "Contact-based deals (call, SMS, WhatsApp); no in-app payment", "Matches how deals happen; no payment compliance burden", "Escrow and mobile money (roadmap)"],
  ["D8", "Public positions blurred to about 1 km", "Reduces stock-theft risk while keeping distance sorting useful", "Exact locations"],
  ["D9", "Curated, vet-reviewed advisor that runs offline; optional TFLite photo hint later", "Reliable and auditable; zero data cost", "Live generative AI only"],
  ["D10", "Manager dashboard in React with MapLibre GL JS and Recharts", "Same map stack as the app; simple charts for coverage and pool progress", "Django admin only"],
], [550, 2700, 3200, 2576], { zebra: true }));
c.push(callout("Free-tier realities to design around", [
  "Render's free web services sleep after inactivity, so the first request after a pause can take tens of seconds. The sync client must tolerate slow or failed first calls (retry with backoff) and never block the UI.",
  "Neon free projects scale to zero when idle, so the first query after a pause is slower, and free storage and compute are limited; keep data small and take backups.",
  "These limits are acceptable for a school trial and must be re-assessed before a pilot with real farmers. [TO CONFIRM current limits]",
], "FFF4E0", "E67E22"));

// 3
c.push(h1("3. System architecture"));
c.push(p("Four layers, matching the reference stack. The device holds a complete working copy of what the farmer needs. The backend is small and stateless. GIS processing is run by the team in batch and delivered to devices as data packs, so map insights never wait for a server."));
c.push(...img("architecture.png", 6.2, "Figure 1: MarketMoo architecture (Android app, Django API, Neon and Backblaze B2, manager dashboard, GIS pipeline)"));
c.push(h3("Component responsibilities"));
c.push(table(["Component", "Responsibility"], [
  ["Compose UI", "Five-tab Material 3 interface in MarketMoo colours; language and Data Saver settings; accessibility support"],
  ["Room + SQLCipher", "Encrypted local database for records, listings, shortlist, content and the sync queue; key in Android Keystore"],
  ["MapLibre Native", "Vector basemap from a district PMTiles file [TO CONFIRM PMTiles support in the chosen MapLibre version; fallback MBTiles offline regions]; layers for facilities, zones and analysis overlays"],
  ["District pack", "Downloaded SQLite and GeoJSON pack: facilities, towns and markets, 1 km grid lookups for the four analyses, simplified restricted zones"],
  ["WorkManager sync", "Background upload of the op-log and download of changes; constraints on network; exponential backoff"],
  ["Django REST Framework", "Authenticated API for listings, pools, records sync, outbreak reports, packs manifest; validation and permissions"],
  ["Celery workers (planned)", "Outbreak alert fan-out (push and SMS), pack builds, image post-processing, nightly jobs"],
  ["Neon PostgreSQL", "Managed PostgreSQL for users, listings, pools, records, outbreaks, packs and the sync audit trail"],
  ["Backblaze B2", "Resized photos and versioned pack files; presigned URLs, HTTP range requests"],
  ["Manager dashboard", "Verification and moderation queues, outbreak notice editor, layer and pack manager, coverage and pool analytics"],
  ["GIS pipeline", "Reproducible Python and QGIS scripts that turn source data into the four analysis layers and the district packs"],
], [2400, 6626], { firstColShade: true }));

// 4
c.push(h1("4. Technology choices"));
c.push(table(["Concern", "Choice", "Why", "Notes"], [
  ["Android app", "Kotlin, Jetpack Compose, Material 3, Hilt, Coroutines", "Modern, concise UI toolkit; small runtime", "Target minSdk 26 (Android 8) [TO CONFIRM]"],
  ["Local database", "Room on SQLCipher", "Encrypted on shared phones; SQL for lookups", "Passphrase wrapped by an Android Keystore key"],
  ["Map", "MapLibre Native Android; PMTiles basemap", "Open-source, offline capable, same style spec as the web dashboard", "Basemap built from OSM with Tippecanoe and the PMTiles CLI"],
  ["Spatial lookups on device", "Kotlin lookups against the 1 km grid table; point-in-polygon with JTS or a small custom routine", "No routing on the phone", "Zones simplified to about 100 KB per district"],
  ["On-device ML (optional)", "TensorFlow Lite", "Offline photo hint for common livestock skin and eye conditions", "Out of scope for the school trial; vet-validated labels needed"],
  ["Sync", "WorkManager with idempotent UUID operations", "Survives process death and reboot", "Unmetered-network option for large packs"],
  ["Backend API", "Python 3.12, Django 5, DRF", "Batteries included; admin; permissions; tested", "Hosted on Render (free tier for the trial)"],
  ["Jobs", "Celery with a Redis broker", "Alerts, pack builds", "Broker add-on or a hosted free tier [TO CONFIRM]"],
  ["Database and auth", "Neon PostgreSQL; phone OTP and staff password login in Django", "Managed database; no external auth service to depend on", "SMS provider for Zimbabwe [TO CONFIRM]; console backend in the trial"],
  ["Object storage", "Backblaze B2 through its S3-compatible API (boto3)", "Range requests for packs; low cost", "Private bucket; the API hands out presigned URLs"],
  ["Manager dashboard", "React 18, Recharts, MapLibre GL JS on Vercel", "Fast to build; shared map style", "Staff password sign-in; token in session storage; CORS limited to the dashboard origin"],
  ["GIS pipeline", "Python: GeoPandas, rasterio, SciPy sparse graphs, Shapely; QGIS for cartography", "Reproducible; no heavy server", "Scripts included in the project repository"],
  ["Earth observation", "Open COG and STAC services (CHIRPS, Sentinel-2 via AWS Earth Search, ESA WorldCover, JRC water)", "Windowed reads, no large downloads", "Used in Section 7"],
  ["Weather", "Open-Meteo", "No key; daily forecast for the farm pin", "Cached on device with a timestamp"],
  ["Build and CI", "Gradle, GitHub Actions, signed release APK", "Repeatable builds", "Size budget check in CI"],
], [1500, 2500, 2700, 2326], { zebra: true }));

// 5
c.push(h1("5. Offline and low-bandwidth design"));
c.push(p("This section answers the brief's explicit connectivity criterion. It defines what is stored on the phone, how it gets there, how writes are protected, and how the app behaves when the network is weak."));
c.push(h2("5.1 Four storage tiers"));
c.push(table(["Tier", "Content", "Delivery", "Strategy", "Size"], [
  ["0  App", "Code, icons, language strings", "APK from the Play Store or direct download", "Release build with resource shrinking and App Bundle splits", "Target 15 MB. Measured: 36 MB minified release (two ABIs), 55 MB debug; MapLibre and SQLCipher libraries dominate"],
  ["1  Content packs", "Guides, advisor knowledge base, finance cards, expert directory per district", "Bundled at first install; updated as small deltas", "Stored in Room; version numbers", "Under 1 MB"],
  ["2  District pack", "Facilities, markets, water points, 1 km analysis grid, zones", "Chosen at onboarding; resumable download over HTTP range", "SQLite file plus GeoJSON, versioned with a checksum", `Measured: ${S.pack.summary}`],
  ["3  Dynamic data", "Listings (last 200), pool status, weather, outbreak notices, photos", "Fetched when online", "Network-first with timeout, then cache; photos lazy", "1 to 5 MB, capped, oldest evicted"],
], [1100, 2400, 1900, 2200, 1426], { zebra: true }));
c.push(h2("5.2 Data budget rules"));
c.push(...bullets([
  "**No web fonts and no decorative photography.** The HTML prototype loaded four 1600 px photos on every page; the app uses flat colour and vector icons.",
  "**Photos are optional and small.** Resized on the device to about 800 px and 60 KB or less (WebP where supported), uploaded after the text record, and shown as placeholders in Data Saver mode until tapped.",
  "**Compress and diff.** gzip on all responses; cursor-based delta sync.",
  "**Show the cost.** Every download shows its size; large packs recommend Wi-Fi and can be paused and resumed.",
  "**Data Saver mode.** Disables photo loading and background refresh; switched on by default when the connection reports as slow.",
  "**Everything needed is on the device.** No third-party CDN is needed at runtime.",
]));
c.push(h2("5.3 Detecting connectivity"));
c.push(p("The system's connectivity flag only says a network is attached, not that data flows. The app combines it with a tiny health ping (about 1 byte, 3 second timeout) to the API. The result sets a visible state: **Online**, **Weak** or **Offline**. Sync runs on Online or Weak with small batches; the UI never blocks on the network, which also covers cold starts of free-tier servers."));
c.push(h2("5.4 Write path and sync lifecycle"));
c.push(...img("sync_flow.png", 6.0, "Figure 2: Offline write and sync lifecycle"));
c.push(h3("Rules"));
c.push(...bullets([
  "**Local first.** A save writes to the encrypted local database with a client-generated UUID, status Pending, shows confirmation at once, and enqueues an operation.",
  "**Idempotent push.** Operations carry the UUID and a base version, so replays cannot create duplicates.",
  "**Server-assigned versions.** Device clocks are unreliable, so ordering uses server versions.",
  "**Retry triggers.** App open, connectivity change, WorkManager schedule, and a manual Sync now button, with exponential backoff.",
  "**Photos last.** The text record syncs first so the data is safe.",
  "**Visible status.** Pending, Synced and Needs attention badges; a Sync status screen lists anything stuck and why.",
]));
c.push(h3("Conflict rules by entity"));
c.push(table(["Entity", "Owner edits", "Conflict rule"], [
  ["Farm record", "Owner only (multi-device possible)", "Field-level merge by server version; same field on two devices asks the user which to keep"],
  ["Listing", "Owner only", "Owner fields merge as above; status (live, sold, removed) is server-authoritative"],
  ["Pool commitment", "Member; admin can adjust", "Server-authoritative on pool state; if the pool closed while offline the commitment is rejected with a clear message"],
  ["Outbreak report", "Reporter (append-only)", "No conflicts; admins change verification status separately"],
  ["Shortlist, settings", "Local only", "Not synced"],
], [1900, 2500, 4626], { firstColShade: true }));
c.push(h2("5.5 District packs: measured sizes"));
c.push(p("The packs below were built from the analysis outputs in Section 7 with the project's pack builder. Each contains the 1 km analysis grid lookup, towns, markets, service points, water points and simplified zones as one SQLite file, and the same content compressed for download. These are real figures for the lookup data; the vector basemap is additional and is estimated separately."));
c.push(table(["District", "Grid cells", "SQLite pack (KB)", "Compressed (KB)", "Facilities and places"], S.pack.rows.map((r) => [r.district, f0(r.cells), f0(r.sqlite_kb), f0(r.gz_kb), f0(r.features)]), [2200, 1400, 1800, 1800, 1826], { firstColShade: true }));
c.push(note("Vector basemap (PMTiles) per district is estimated at 5 to 20 MB and must be measured when the basemap is built. [TO CONFIRM]"));

// 6
c.push(h1("6. Dataset register"));
c.push(p("Every dataset below was obtained from the open internet during this project, with the access date 2 October 2026. Nothing was supplied by DVS or AGRITEX."));
const cn = S.counts;
c.push(table(["#", "Dataset", "Source (access)", "Form and scale", "Used in", "Notes"], [
  ["1", "District boundaries: Mhondoro-Ngezi; Gwanda with Gwanda Urban; Beitbridge with Beitbridge Urban", "geoBoundaries gbOpen, Zimbabwe ADM2 (GitHub release data)", "Polygons, simplified", "All", "Urban council areas merged into their districts; wards not used"],
  ["2", "Road network", `OpenStreetMap, Overpass API and Geofabrik extract of 30 Sep 2026 (ODbL)`, `${f0(cn.road_ways)} ways in study area plus 50 km margin`, "SA1, SA2", "Class and surface tags incomplete; speeds assumed"],
  ["3", "Towns, villages, markets, abattoirs, butchers, business centres", "OpenStreetMap (ODbL)", `${f0(cn.places)} places; ${f0(cn.destinations)} market destinations after de-duplication`, "SA1", "Markets are sparsely tagged"],
  ["4", "Veterinary facilities and agro-dealers", "OpenStreetMap (ODbL)", `${f0(cn.osm_service_points)} service points found`, "SA2", "**Data gap**: OSM has no veterinary facilities in the study area"],
  ["5", "Water points, reservoirs, main rivers", "OpenStreetMap (ODbL)", `${f0(cn.water_pts)} points; ${f0(cn.water_poly)} water polygons; ${f0(cn.rivers)} river segments`, "SA3", "Boreholes and wells largely unmapped"],
  ["6", "Surface water seasonality", "JRC Global Surface Water v1.4 (2021 release), Google Cloud Storage", "30 m, months of water per year", "SA3", "Used to find reliable (9 months or more) water"],
  ["7", "Rainfall", "CHIRPS v2.0 annual, 2019 to 2023 (UCSB Climate Hazards Center)", "About 5 km; mean and coefficient of variation", "SA3, SA4", "Five years is short for a climatology"],
  ["8", "Vegetation (NDVI)", "Sentinel-2 L2A via AWS Earth Search STAC; scenes 20 Aug to 10 Oct 2025, cloud under 8 per cent", "About 100 m after decimation; SCL cloud mask", "SA3", "One dry season; least-cloudy scene per tile"],
  ["9", "Elevation and slope", "AWS Terrain Tiles (Terrarium, zoom 10)", "About 150 m, resampled to 100 m", "SA3", "Slope in per cent"],
  ["10", "Land cover", "ESA WorldCover 2021 v200 (CC BY 4.0)", "10 m, decimated to 100 m", "SA3", "Built-up and water excluded"],
  ["11", "Population", "WorldPop 2020, Zimbabwe constrained, per pixel (CC BY 4.0)", "About 100 m aggregated to 1 km", "SA1, SA2 weighting", "Used as a proxy for livestock demand"],
  ["12", "Disease records", "Public reports: FMD in Mangwe (AllAfrica, 29 Jan 2026); FMD vaccination in four districts (Dairy Business MEA, 13 May 2026); theileriosis in Mhondoro-Ngezi (survey preprint)", "Event and district level", "SA4", "Approximate locations; see Section 7.4"],
  ["13", "Dip tanks", "Zimbabwe Geoportal (licence PDDL 1.0)", "Points, about 1 MB GeoJSON", "SA2 (not yet used)", "**Download requires a free account**; place the file in build/data/user_supplied/dip_tanks.geojson and re-run the pipeline"],
], [350, 1900, 2300, 1600, 900, 1976], { zebra: true }));
c.push(h3("OSM features obtained per district (query area = district plus 0.45 degree margin)"));
c.push(table(["District", "Road ways", "Places", "Market-type features", "Water points", "Water polygons", "Main rivers", "Vets (name search)"],
  D3.map((d_) => { const b = S.counts.by_district[d_]; return [d_, f0(b.roads), f0(b.places), f0(b.markets), f0(b.water_pts), f0(b.water_poly), f0(b.rivers_main), f0(b.vets)]; }),
  [1500, 1000, 900, 1200, 1100, 1200, 1000, 1126], { firstColShade: true }));
c.push(callout("Data completeness warning", [
  "The public Overpass servers returned repeated timeouts. OSM roads, places and markets were retrieved for all three districts, but **water points and water polygons for Mhondoro-Ngezi, and main rivers for Mhondoro-Ngezi and Gwanda (zeros in the table), could not be retrieved in time**.",
  "For Mhondoro-Ngezi the water analysis therefore uses JRC surface water only (Gwanda lacks main rivers from OSM but has JRC water as well), so distances to water are biased upward for these districts and **not strictly comparable** with Beitbridge. Re-running build/data/fetch_osm7.py and then the pipeline fixes this.",
], "FDECEA", "C0392B"));
c.push(note("Coordinate systems: stored in WGS 84; distance and area work in a transverse Mercator centred on 30 degrees east (custom, covers all three districts with low distortion). Licences must be carried into the app's About screen."));

// 7
c.push(h1("7. Spatial analyses: methods and results"));
c.push(p("Each analysis lists purpose, inputs, method and parameters, then the results actually computed for the three districts. All parameter values are proposed defaults that the group should calibrate. The figures are generated by the pipeline in build/data and can be regenerated."));
c.push(...mapImg("study_area.png", 5.0, "Figure 3: The three study districts"));
c.push(...img("pipelines.png", 6.2, "Figure 4: Pipeline overview for the four analyses"));

// 7.1
const a1 = S.sa1;
c.push(h2("7.1 Market accessibility"));
c.push(table(null, [
  ["Purpose", "Show how long it takes to reach markets, abattoirs and towns, and which areas are poorly connected."],
  ["Inputs", "OSM road network; destinations (towns and cities, marketplaces, abattoirs and auctions, growth points, business centres, butchers); 1 km origin cells inside each district; WorldPop population."],
  ["Method", "1. Build a road graph from OSM for the districts plus a 50 km margin (so nearby towns outside a district count).\n2. Assign truck speeds by road class (primary and trunk 60 km/h, secondary 50, tertiary 40, unclassified 30, residential 25, track 15; unpaved surface capped at 35).\n3. Snap origins and destinations to the nearest road node (maximum 10 km; the off-road leg at 10 km/h).\n4. Least-cost travel time with Dijkstra (SciPy sparse graph) from every destination.\n5. Per cell: time to the nearest major market (weight 2 or more: towns, cities, marketplaces, abattoirs, growth points), time to the nearest local trading point, top three markets.\n6. Market Access Index: sum of weight x time (hours, at least 0.25) to the power minus 1.5, ranked 0 to 100."],
  ["Outputs", "1 km grid lookup; district statistics; maps."],
], [1400, 7626], { firstColShade: true }));
c.push(spacer());
c.push(table(["District", "Population in grid", "Median minutes to major market", "Population within 60 min (%)", "Within 120 min (%)", "Cells over 120 min (%)", "Median minutes to any trading point"],
  D3.map((d) => { const r = by(a1, d); return [d, f0(r.pop), f1(r.median_min), f1(r.pct_pop_le60), f1(r.pct_pop_le120), f1(r.pct_cells_gt120), f1(r.median_local_min)]; }),
  [1500, 1200, 1400, 1300, 1200, 1200, 1226], { firstColShade: true }));
c.push(...mapImg("sa1_market_access.png", 6.3, "Figure 5: Road travel time to the nearest major market (minutes)"));
c.push(p("**Validation status.** The model has not been checked against GPS-timed trips. A plausibility check of the Gwanda to Beitbridge town route gives " + (S.pairs.length ? `${f0(S.pairs[0][2])} minutes over ${f0(S.pairs[0][3])} km of road (about 60 km/h on average)` : "no result") + ", which is in the range expected for that road (roughly 190 km; to be confirmed). The group should drive or time a sample of routes before presenting the numbers as predictions. Limitations: OSM omits some rural tracks and surface types; seasonal closures are not modelled; the off-road leg is a rough assumption; the nearest major market for Mhondoro-Ngezi is often a town just outside the district, which is why the 50 km margin matters."));

// 7.2
const a2 = S.sa2;
c.push(h2("7.2 Vet and service accessibility"));
c.push(table(null, [
  ["Purpose", "Measure how well farmers are covered by veterinary services and find underserved areas."],
  ["Inputs", "Service points (below), the road graph from 7.1, and population as a demand proxy."],
  ["Data caveat", "OpenStreetMap contains **no veterinary facilities, dip tanks or AGRITEX offices** in the study area, and the Zimbabwe Geoportal dip-tank layer needs a login. This run therefore uses a **proxy**: one District Veterinary Services office assumed at each district's main town (Gwanda: the provincial veterinary office for Matabeleland South is reported to be in Gwanda; Beitbridge and Mhondoro-Ngezi are assumptions). The results show the **method working**, not the true coverage; they will change when real dip tank and vet locations are added."],
  ["Method", "Travel time from each service point to every 1 km cell (Dijkstra, limit 120 minutes). Coverage classes: covered (30 minutes or less), partly covered (30 to 60), underserved (over 60). Two-Step Floating Catchment Area (2SFCA): for each service point j, R(j) = weight(j) / population within 60 minutes; for each cell, A(i) = sum of R(j) of points within 60 minutes, reported per 1000 people. Service weights: vet or DVS office 1.0, AGRITEX 0.5, dip tank 0.3, agro-dealer 0.3."],
], [1400, 7626], { firstColShade: true }));
c.push(spacer());
c.push(table(["District", "Service points in district (incl. proxy)", "Median minutes to nearest service", "Population within 30 min (%)", "Within 60 min (%)", "Over 60 min (%)"],
  D3.map((d) => { const r = by(a2, d); return [d, f0(r.service_points_in_district), f1(r.median_min_to_service), f1(r.pct_pop_le30), f1(r.pct_pop_le60), f1(r.pct_pop_gt60)]; }),
  [1500, 1700, 1600, 1400, 1400, 1426], { firstColShade: true }));
c.push(...mapImg("sa2_service_access.png", 6.3, "Figure 6: Travel-time coverage of veterinary services (proxy service points)"));
c.push(p("Optional extension for the admin dashboard: choose p new mobile-clinic or dip-tank sites from candidate locations (growth points) that cover the most uncovered population within 60 minutes (Maximal Coverage Location Problem). Not run, because meaningful siting needs the real facility layer first."));

// 7.3
const a3 = S.sa3;
c.push(h2("7.3 Water proximity and grazing suitability"));
c.push(table(null, [
  ["Purpose", "Show where reliable water is within reach in the dry season and how suitable the land is for grazing."],
  ["Inputs", "OSM water points, reservoirs and main rivers; JRC surface water seasonality; CHIRPS rainfall mean and variability; Sentinel-2 dry-season NDVI; slope from terrain tiles; WorldCover land cover."],
  ["Water access", `Reliable water = man-made points (wells, boreholes, towers, springs), reservoirs and lakes, or cells with surface water for 9 or more months a year (JRC). Any water adds streams, ponds and seasonal water. Euclidean distance rasters at 100 m. Reclassified: up to 3 km = 5, 3 to 6 = 4, 6 to 10 = 3, 10 to 15 = 2, over 15 = 1.`],
  ["Suitability", `Weighted overlay of ${S.m3.criteria.join(", ")}. Weights by Analytic Hierarchy Process from a pairwise matrix set by the team (illustrative until reviewed by an extension officer): ${S.m3.criteria.map((k, i) => `${k} ${(S.m3.weights[i]).toFixed(2)}`).join("; ")}. Consistency ratio ${S.m3.CR.toFixed(3)} (acceptable below 0.10). Rainfall score is lowered by one class where year-to-year variation exceeds 30 per cent. Built-up areas and water bodies are excluded. Five classes from the weighted score (below 1.8, 2.6, 3.4, 4.2). A wet-season variant replaces reliable water with any water; the NDVI composite is the dry-season one only.`],
], [1400, 7626], { firstColShade: true }));
c.push(spacer());
c.push(table(["District", "Area (km2)", "Mean rainfall (mm)", "Mean distance to reliable water (km)", "Area over 10 km from reliable water (%)", "Mean distance to any water (km)"],
  D3.map((d) => { const r = by(a3, d); return [d, f0(r.area_km2), f0(r.mean_rain_mm), f1(r.mean_dist_reliable_water_km), f1(r.pct_area_gt10km_reliable), f1(r.mean_dist_any_water_km)]; }),
  [1500, 1200, 1400, 1700, 1700, 1526], { firstColShade: true }));
c.push(spacer());
c.push(table(["District", "Dry season: low or very low (%)", "Moderate (%)", "High or very high (%)", "Wet season: low or very low (%)", "Moderate (%)", "High or very high (%)"],
  D3.map((d) => { const r = by(a3, d); const n = (a, b) => Number(r[a]) + Number(r[b]); return [d, f1(n("dry_very_low_pct", "dry_low_pct")), f1(r.dry_moderate_pct), f1(n("dry_high_pct", "dry_very_high_pct")), f1(n("wet_very_low_pct", "wet_low_pct")), f1(r.wet_moderate_pct), f1(n("wet_high_pct", "wet_very_high_pct"))]; }),
  [1500, 1300, 1100, 1300, 1400, 1100, 1326], { firstColShade: true }));
c.push(...mapImg("sa3_grazing_suitability.png", 6.3, "Figure 7: Dry-season grazing suitability (100 m)"));
c.push(...mapImg("sa3_water_distance.png", 6.3, "Figure 8: Distance to reliable water in the dry season (km)"));
const sens = S.m3.sensitivity;
const maxSens = Math.max(...sens.map((x) => x[2]));
c.push(p(`**Sensitivity.** Changing any single weight by 5 percentage points (and renormalising) changes the class of between ${Math.min(...sens.map((x) => x[2])).toFixed(1)} and ${maxSens.toFixed(1)} per cent of cells; the most influential criteria are slope and land cover, because their classes are coarse. **Data caveat.** Water distances and suitability for Mhondoro-Ngezi rest on JRC surface water only (OSM water features were not retrieved), so treat cross-district comparisons of water access with caution until that is fixed. **Validation status.** No field validation was possible in this school trial. The next step is a participatory check: ask farmers and extension officers to rate about 20 sample points per district and compare them with the model (confusion matrix and kappa). **Limitations.** OSM water data are incomplete (boreholes in particular), NDVI is one dry season, and the AHP judgments are the team's.`));

// 7.4
const a4 = S.sa4;
const rec = jf("sa4_records.json");
c.push(h2("7.4 Disease risk zones"));
c.push(table(null, [
  ["Purpose", "Warn farmers and buyers about reported outbreaks, restricted areas and areas where tick-borne disease is favoured."],
  ["Real records used", `(1) Foot-and-mouth disease (SAT 1) confirmed in Mangwe District, Matabeleland South, reported 5 January 2026 at the Maholi and Hannavale dip tanks: 54 cases among 2,403 cattle; quarantine, movement restrictions and vaccination within 20 km (AllAfrica, 29 January 2026). (2) A joint Zimbabwe and Botswana campaign in May 2026 vaccinated ${f0(rec.campaign.vaccinated)} of ${f0(rec.campaign.targeted)} targeted cattle in Beitbridge, Gwanda, Mangwe and Matobo districts, described as high-risk border areas (Dairy Business MEA, 13 May 2026). (3) Mhondoro-Ngezi is described as a district badly affected by theileriosis (January disease) in a survey of 320 farmers.`],
  ["Method", "1. **Control zones:** a 20 km circle around the event (the vaccination radius reported) and a 40 km surveillance ring (an assumed parameter for DVS to set). The dip-tank coordinates were not available, so the Mangwe district centroid is used and the location is approximate.\n2. **High-risk districts:** the four campaign districts are flagged for FMD advisories.\n3. **Tick-borne disease suitability:** from CHIRPS mean annual rainfall; below 550 mm low, 550 to 700 moderate, 700 or more high (thresholds are assumptions to be set with a vet).\n4. **Listing screen:** market destinations stand in for listing locations and are tested against the zones (on the phone this is a point-in-polygon check against cached polygons).\n5. **Hotspot statistics** (kernel density, Getis-Ord Gi*) are specified for use once DVS-verified reports accumulate; with a single event they are not meaningful and were not run."],
], [1400, 7626], { firstColShade: true }));
c.push(spacer());
c.push(table(["District", "In FMD campaign district", "Mean rainfall (mm)", "Area high tick suitability (%)", "Area moderate (%)", "Area low (%)", "Area within 40 km surveillance ring (%)"],
  D3.map((d) => { const r = by(a4, d); return [d, r.in_fmd_campaign_district === "True" ? "Yes" : "No", f0(r.mean_rain_mm), f1(r.pct_area_tick_high), f1(r.pct_area_tick_moderate), f1(r.pct_area_tick_low), f1(r.pct_area_in_surv40)]; }),
  [1500, 1100, 1100, 1400, 1300, 1000, 1626], { firstColShade: true }));
c.push(...mapImg("sa4_disease_risk.png", 6.3, "Figure 9: Reported FMD zones and rainfall-based tick-borne disease suitability"));
c.push(p(`The reported event lies about ${f0(S.sa4m.event_to_gwanda_km)} km from the Gwanda district boundary, so **no part of the three study districts falls inside the 20 km control zone or the assumed 40 km surveillance ring**. Gwanda and Beitbridge are nevertheless part of the May 2026 FMD vaccination campaign area, so the app would show an FMD advisory (not a movement ban) for listings there. The rainfall pattern is consistent with the published description of Mhondoro-Ngezi as a theileriosis hotspot, because it is the wettest of the three districts and the only one with high or moderate tick suitability; this is a plausibility check, not a validation. **Limitations.** Very few public records; under-reporting; the event coordinates are district-level; thresholds and the surveillance ring are assumptions. Any notice shown in the app requires DVS verification and area-level display only.`));

// 7.5
c.push(h2("7.5 Integrated decision card"));
c.push(p("On the phone, the farm pin selects its 1 km cell in the district pack. The card combines the four results and gives each an action. Example for a cell in Gwanda district built from the real pack values:"));
let ex = {};
try { ex = jf("example_card.json"); } catch (e) { ex = null; }
if (ex) c.push(callout("Example decision card (real values from the Gwanda pack)", ex.lines));

// 8
c.push(h1("8. Data model"));
c.push(p("Neon PostgreSQL, accessed by Django models. Positions are stored as latitude and longitude (listings only ever as blurred public positions). IDs are UUIDs generated on the device where offline creation is possible. All tables carry created_at, updated_at, a server version and a soft-delete flag."));
c.push(table(["Table", "Key fields", "Geometry / notes"], [
  ["users", "id, phone (unique), role (farmer, buyer, vet, admin), language, district_id, verified, consent_at", "No national ID"],
  ["farms", "id, user_id, name, ward, livestock_types[], location_precise, location_public", "Point; public point blurred to about 1 km"],
  ["listings", "id, farm_id, species, breed, sex, age_months, qty, price_usd, payment_methods[], transport, status, photo_key, pool_id", "Location from farm; status server-authoritative"],
  ["pools", "id, species, target_qty, deadline, status, buyer_id, aggregation_point_id", "Aggregation point is a Point"],
  ["pool_commitments", "id, pool_id, user_id, qty, age_months, ready_date, status", "Server validates pool state"],
  ["records", "id, farm_id, type, animal_ref, date, cost_usd, notes, version", "Offline-first; owner only"],
  ["facilities", "id, type (vet, dip_tank, agritex, agrodealer, market, abattoir, pool_hub), name, phone, verified_at, source", "Point; source and date shown to users"],
  ["water_features", "id, type (dam, river, borehole, pan, spring), reliable", "Point or line"],
  ["outbreaks", "id, disease, species, status, reported_by, verified_by, started_on, ended_on, source_url", "Point (admin only) and public area"],
  ["control_zones", "id, outbreak_id, level, valid_from, valid_to", "Polygon"],
  ["analysis_cells", "cell_id, district, minutes_to_major_market, top3_markets, market_access_index, minutes_to_service, coverage_class, water_dry_km, suitability_dry, suitability_wet, tick_class, in_zone", "1 km grid; exported into packs"],
  ["experts", "id, name, role, district, phone, languages, verified", "Point"],
  ["finance_programmes", "id, provider, summary, eligibility (age, gender, species, districts), fees, link, verified_at", "Content table"],
  ["content_articles", "id, species, topic, language, body, reviewed_by, reviewed_at", "Vet-reviewed"],
  ["sync_ops", "id, user_id, entity, entity_id, op, payload, base_version, applied_at", "Audit and idempotency"],
  ["packs", "id, district, kind, version, size_bytes, sha256, r2_key", "Manifest for downloads"],
], [1800, 4600, 2626], { zebra: true }));
c.push(...bullets([
  "Permission classes in DRF and object-level checks in the sync handlers: farmers read and write only their own farms, records and commitments; listings and pools are readable by all; outbreaks expose only public geometry to non-admins; admins' actions are logged.",
]));

// 9
c.push(h1("9. API and sync contract"));
c.push(table(["Method and path", "Purpose", "Notes"], [
  ["POST /v1/auth/otp/request, /verify", "Phone sign-in", "Codes stored as keyed hashes; limited per IP and per number"],
  ["GET /v1/packs/manifest?district=", "Pack versions, sizes, checksums", "Tiny; cached"],
  ["GET (presigned B2 URL from manifest)", "Download a pack", "HTTP range; resumable"],
  ["POST /v1/sync", "Push a batch of operations", "Idempotent; returns per-operation results and conflicts"],
  ["GET /v1/sync?since={cursor}", "Pull changes", "Cursor-based; gzip"],
  ["GET /v1/listings?near=&radius=&species=&since=", "Listings feed", "Bounding box, then haversine; nearest first"],
  ["GET /v1/pools; POST /v1/pools/{id}/commitments", "Pools", "Server-authoritative"],
  ["POST /v1/outbreak-reports", "Suspected outbreak", "Append-only"],
  ["GET /v1/outbreaks/active?district=", "Active zones", "Also in the district pack"],
  ["GET /v1/experts?near=; GET /v1/finance?...", "Directory and programmes", "Also cached"],
  ["POST /v1/photos/presign", "Short-lived URL to upload a listing photo to B2", "Key is bound to user and listing; 150 KB limit"],
  ["POST /v1/auth/staff-login", "Dashboard sign-in (staff accounts only)", "Throttled; farmers never have passwords"],
  ["/v1/manager/* (stats, listings, users, reports, outbreaks, pools, packs)", "Dashboard moderation and analytics", "Staff or admin role required"],
  ["GET /v1/ping", "Connectivity check", "1 byte; also wakes a sleeping free-tier server"],
], [3300, 2800, 2926], { zebra: true }));

// 10
c.push(h1("10. UX and interface design"));
c.push(h2("10.1 Visual language (carried over from the HTML prototype)"));
c.push(table(["Token", "Value", "Use"], [
  ["Primary green", "#1F7A3A (dark #155C2B)", "Primary buttons, headings, brand"],
  ["Gold", "#FFE08A", "Highlight, secondary buttons, active states"],
  ["Earth brown", "#8B5E34", "Accents, sub-headings"],
  ["Cream", "#FAF7F0", "Backgrounds"],
  ["Danger", "#C0392B", "Errors, alerts"],
  ["Blue", "#1E88E5", "Information, sync and online state"],
  ["Orange (new)", "#E67E22", "Offline and Pending states"],
  ["Shape", "14 dp corner radius, soft elevation", "Cards and dialogs"],
  ["Type", "System font (Roboto); minimum 16 sp body", "No web fonts"],
], [2000, 3400, 3626], { firstColShade: true }));
c.push(h2("10.2 Navigation"));
c.push(p("A bottom navigation bar with five destinations keeps every main area one tap away: **Home**, **Market**, **Map**, **Learn**, **Help**. Account sits in the top bar. Back returns to the previous screen and never logs the user out. The sitemap is in the App Flow Document."));
c.push(h2("10.3 Core components"));
c.push(...bullets([
  "**Connection chip** (Online, Weak, Offline) opening Sync status.",
  "**Status badges** on records and listings: Pending (orange), Synced (green), Needs attention (red).",
  "**Result card** for each spatial insight: one sentence, one number, a small map, one action.",
  "**Download card** for packs: name, size, progress, pause, Wi-Fi hint.",
  "**Listing card**: photo or placeholder, species, price, ward, distance, verified badge, zone warning.",
  "**Empty and error states** with the next step in plain language.",
]));
c.push(h2("10.4 Accessibility and inclusion"));
c.push(...bullets([
  "Icon plus text on every action; touch targets of 48 dp; high contrast for sunlight; colour never the only signal.",
  "English, Shona and Ndebele string resources; Venda later; short plain sentences.",
  "Optional read-aloud with Android text-to-speech for guides and advisor. [TO CONFIRM]",
  "Numeric keypad inputs, forgiving validation and saved drafts; quick sign-out for shared phones.",
]));

// 11
c.push(h1("11. Testing and validation"));
c.push(table(["Area", "Method", "Pass criteria"], [
  ["APK size and performance", "Release build analysis; Android Studio profiler; low-end device (2 GB RAM)", "APK within budget; cold start and cached screens within 2 s"],
  ["Offline matrix", "Scripted run of every Must task in airplane mode, then reconnect", "All pass; no data lost; Pending becomes Synced"],
  ["Network conditions", "Emulator network throttling (2G, 3G), packet loss, toggling; free-tier server cold start", "No duplicates; retries succeed; UI never blocks"],
  ["Sync correctness", "Kill the app mid-sync; duplicate submissions; wrong device clock; two devices editing", "Idempotent; server versions order edits; conflicts surfaced"],
  ["Pack integrity", "Interrupt and resume download; checksum failure; low storage", "Resumes; bad pack rejected; clear message"],
  ["GIS: market access", "Time about 20 routes with GPS and compare with the model", "Median error within 20 per cent [TO CONFIRM]"],
  ["GIS: service access", "Replace proxy points with the Zimbabwe Geoportal dip tanks and verified vet locations; spot-check facilities by phone", "At least 90 per cent of sampled facilities exist"],
  ["GIS: suitability", "Participatory ratings at about 20 points per district; confusion matrix and kappa; weight sensitivity", "Kappa at least 0.4 [TO CONFIRM]"],
  ["GIS: disease", "Point-in-polygon edge cases; review of rules with a vet", "No in or out errors at zone edges; rules signed off"],
  ["Data quality", "Valid geometries, CRS, attribute completeness, data dates recorded", "No invalid geometries; metadata complete"],
  ["Usability", "5 to 8 farmers per district; unaided tasks; System Usability Scale", "80 per cent completion; SUS 70 or higher"],
  ["Security", "OWASP mobile and API checks; SQLCipher key handling; row-level security tests", "No critical findings"],
  ["Content", "Vet review of advisor and guides; translation review", "Signed off"],
], [1700, 4000, 3326], { zebra: true }));

// 12
c.push(h1("12. From the HTML prototype to the native app: backlog"));
c.push(p("Findings from reading the HTML prototype (index.html, v1.2) and what happens to each in the native build. Priority: **M** must, **S** should, **C** could."));
c.push(table(["ID", "Finding in the HTML prototype", "Native app action", "Pri"], [
  ["R-01", "No manifest or service worker; Leaflet, tiles, fonts and photos loaded from the internet, so it cannot run offline despite saying records work offline", "Everything the app needs ships in the APK or the district pack", "M"],
  ["R-02", "All data in localStorage (about 5 MB); photos as base64 strings", "Room with SQLCipher; photos as resized files", "M"],
  ["R-03", "Sign-up stores password and national ID in plain text in the browser; sign-in by name and password", "Phone and OTP; no national ID; secrets on the server", "M"],
  ["R-04", "User text inserted into pages with innerHTML", "Compose text rendering is safe by default; server-side validation", "M"],
  ["R-05", "Listings have only a province; no coordinates", "Farm pin and ward on profile and listings", "M"],
  ["R-06", "Map shows five fixed demo farmers with exact coordinates; user listings never appear", "Markers from real listings with blurred public positions; analysis layers", "M"],
  ["R-07", "No spatial analysis", "Four analyses and Farm Insights (Section 7)", "M"],
  ["R-08", "Records have a synced flag but nothing syncs", "Sync engine and Pending/Synced UI", "M"],
  ["R-09", "Four 1600 px background photos rotating every 6 seconds on every page", "Removed", "M"],
  ["R-10", "Malformed font links and CSS typos", "Not applicable in native; no web fonts", "S"],
  ["R-11", "Weather fails offline and is not cached", "Cache with timestamp; livestock advisories", "S"],
  ["R-12", "Dashboard Back button logs the user out", "Bottom navigation; Back goes back", "S"],
  ["R-13", "Dashboard tile icons empty; buttons rely on emoji", "Vector icon set with text labels", "S"],
  ["R-14", "Pool progress static; joining only creates a listing", "Server-side pools with commitments", "S"],
  ["R-15", "'Buy all' only works for one farmer", "One WhatsApp message per farmer", "S"],
  ["R-16", "Placeholder contacts (+263 000 000 000, example.com emails)", "Real or clearly marked demo contacts", "S"],
  ["R-17", "Experts list static, not sorted by distance", "Sorted by distance; call, SMS, ask; verified badge", "S"],
  ["R-18", "Finance cards state specific amounts, fees and fund names", "Verify every claim; add last-checked date; eligibility filter", "M"],
  ["R-19", "Advisor gives treatment and culling instructions (for example African Swine Fever, anthrax)", "Vet review; align with DVS guidance; keep consult-a-vet prompt", "M"],
  ["R-20", "'Alert me when updated' has no delivery mechanism", "Push (FCM) and SMS via Celery, or remove", "S"],
  ["R-21", "English only", "String resources for Shona and Ndebele", "S"],
  ["R-22", "Single 2,800-line file", "Modular Kotlin project with a data layer", "S"],
], [650, 4100, 3700, 576], { zebra: true }));

// 13
c.push(h1("13. Security, privacy and safety"));
c.push(table(["Risk", "Control"], [
  ["Account takeover and OTP abuse", "SMS OTP with rate limits and expiry; lockout; device binding"],
  ["Data at rest on shared phones", "SQLCipher database; key wrapped by Android Keystore; screen lock recommended"],
  ["Injection and broken access control", "Parameterised queries; DRF permissions; row-level security; tests per role"],
  ["Transport", "HTTPS with TLS 1.3; certificate pinning for the API host [TO CONFIRM]"],
  ["Location privacy and stock theft", "Public positions blurred to about 1 km; exact pin visible to owner and admins only; no live tracking"],
  ["Fraudulent listings", "Verification badges; moderation of new sellers; report button; warnings about advance payments"],
  ["Sensitive outbreak data", "DVS verification before publication; area-level only; time-limited"],
  ["Personal data", "Collect phone and district only; consent screen; export and delete my data; retention limits [check Zimbabwe's data protection law]"],
  ["Secrets and dashboard", "Secrets in environment variables; manager dashboard behind staff login and IP allowlist"],
  ["Content safety", "Vet-reviewed content with date; disclaimer; escalation to DVS and vets"],
], [2800, 6226], { firstColShade: true }));

// 14
c.push(h1("14. Deployment and operations"));
c.push(...bullets([
  "**Environments:** local, staging, production; for the trial a single Render service, one Neon project, one Backblaze B2 bucket and one Vercel project (the Neon database and B2 bucket already exist and hold the schema and the three packs).",
  "**Android release:** Gradle build, signed release APK (and App Bundle), versioned; GitHub Actions builds on each tag and checks APK size.",
  "**GIS pipeline:** the scripts in build/data produce the analysis layers and the packs; each run writes a manifest with dataset dates and checksums; packs are uploaded to R2.",
  "**Jobs:** Celery workers for alerts and pack builds; scheduled nightly clean-up.",
  "**Monitoring:** sync failure rate, pack download completion, API errors without personal data.",
  "**Backups:** scheduled database export; documented restore test.",
  "**Support:** a help number and WhatsApp contact; extension officers as local onboarding agents.",
]));

// 15
c.push(h1("15. Open technical questions"));
c.push(table(["#", "Question", "Default"], [
  ["T1", "Zimbabwe Geoportal dip-tank layer needs a free account. Can a group member sign in, download it and place it in build/data/user_supplied/? This replaces the proxy service points in 7.2.", "Re-run SA2 with the layer"],
  ["T2", "Which SMS gateway can send OTPs to Zimbabwean numbers within budget?", "Stub in the trial"],
  ["T3", "Is the Render free tier acceptable for the demo, given cold starts?", "Wake the server before the presentation"],
  ["T4", "Does the chosen MapLibre Native version read PMTiles directly, or do we package MBTiles offline regions?", "Test in the first build"],
  ["T5", "Can the group time about 20 road trips (for 7.1 validation) and rate about 20 grazing points per district (7.3)?", "Plan a half-day field exercise or ask farmers by phone"],
  ["T6", "Which public sources can supply more outbreak records (WAHIS, ProMED, DVS notices) for 7.4?", "Add as found, each with a source link"],
  ["T7", "Do you want per-animal records or group-level records only?", "Group-level, per-animal optional"],
  ["T8", "Is a demo video or live demo expected, and on which device?", "Prepare both"],
], [550, 6000, 2476], { zebra: true }));

build("MarketMoo_2_Design_Document.docx", c, "Design Document");
