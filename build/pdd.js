const L = require("./lib");
const fs = require("fs");
const path = require("path");
const { h1, h2, h3, p, note, bullets, bullet, table, callout, img, cover, toc, build, pb, spacer } = L;
const RES = path.join(__dirname, "results");
function csv(name) {
  const t = fs.readFileSync(path.join(RES, name), "utf8").trim().split(/\r?\n/);
  const h = t[0].split(",");
  return t.slice(1).map((l) => { const c = l.split(","); const o = {}; h.forEach((k, i) => (o[k] = c[i])); return o; });
}
const jf = (n) => JSON.parse(fs.readFileSync(path.join(RES, n), "utf8"));
const f1 = (x) => (isNaN(Number(x)) ? "n/a" : Number(x).toFixed(1));
const f0 = (x) => (isNaN(Number(x)) ? "n/a" : Math.round(Number(x)).toLocaleString("en-US"));
const R1 = csv("sa1_district_stats.csv"), R2 = csv("sa2_district_stats.csv"), R3 = csv("sa3_district_stats.csv"), R4 = csv("sa4_district_stats.csv");
const PK = jf("pack_sizes.json");
const g = (a, d) => a.find((r) => r.district === d);
const D3 = ["Mhondoro-Ngezi", "Gwanda", "Beitbridge"];

const sections = ["Executive summary", "Problem and opportunity", "Vision, goals and non-goals", "Study area", "Users and personas",
  "Product principles", "Scope and requirements", "GIS component and spatial analyses", "Connectivity requirements (core constraint)",
  "Success metrics", "Starting point: the current prototype", "Roadmap", "Risks and mitigations", "Assumptions and dependencies",
  "Compliance with the project brief", "Decisions and open questions"];

const c = [];
c.push(...cover("Product Design Document", "Native Android livestock information, GIS and marketplace platform", [
  ["Course / brief", "HGISEO400 project brief (due 02/10/2026)"],
  ["Product", "MarketMoo: livestock information and marketplace, with GIS decision support"],
  ["Pilot area", "Mhondoro-Ngezi, Beitbridge and Gwanda districts, Zimbabwe"],
  ["Companion documents", "Design Document (technical and UX); App Flow Document"],
  ["Delivery form", "Native Android app (APK) with Django backend, Neon PostgreSQL, Backblaze B2 storage and a React manager dashboard"],
  ["Version / date", "Draft v0.2, 2 October 2026"],
  ["Group", "[TO CONFIRM: group name and members]"],
]));
c.push(...toc(sections));

// 1
c.push(h1("1. Executive summary"));
c.push(p("MarketMoo is a mobile-first, offline-first **native Android** platform that helps young farmers and agripreneurs raise, price and sell livestock. It combines six services the brief asks for (farming information, expert advice, markets, finance, weather and record-keeping) with an interactive GIS component that turns location into practical decisions."));
c.push(p("The product is livestock-focused (cattle, goats, sheep, pigs, poultry) and is piloted in three contrasting districts: **Mhondoro-Ngezi** (higher-rainfall, mixed farming, closer to urban markets), **Gwanda** and **Beitbridge** (semi-arid, extensive grazing, long distances, water-limited, cross-border trade)."));
c.push(p("Four spatial analyses are designed in detail. Each answers a question a farmer actually asks:"));
c.push(...bullets([
  "**Market accessibility:** where can I sell, and how long and how costly is the trip?",
  "**Vet and service accessibility:** how far is the nearest vet or dip tank, and which wards are underserved?",
  "**Water proximity and grazing suitability:** where is water and forage near me, and how does that change by season?",
  "**Disease risk zones:** is my area, or the animals I am buying, affected by an outbreak or movement restriction?",
]));
c.push(p("Limited connectivity is treated as the core design constraint, not a nice-to-have. The heavy GIS work is done in advance by the project team and shipped as small per-district data packs, so the phone only does light lookups (nearest facility, point-in-polygon) with no network. Records, listings and outbreak reports are written locally first and synchronised when a signal appears."));
c.push(p("The four analyses were **run on real open data** for the three districts (OpenStreetMap, CHIRPS rainfall, Sentinel-2 vegetation, ESA WorldCover, JRC surface water, WorldPop, terrain tiles and public outbreak reports). Headline results, computed by the project's GIS pipeline:"));
c.push(table(["District", "Median road time to a major market", "Population within 60 min of a major market", "Mean rainfall (mm/yr)", "Mean distance to reliable water", "Tick-borne disease suitability (high)"],
  D3.map((d) => [d, f0(g(R1, d).median_min) + " min", f1(g(R1, d).pct_pop_le60) + " %", f0(g(R3, d).mean_rain_mm), f1(g(R3, d).mean_dist_reliable_water_km) + " km", f1(g(R4, d).pct_area_tick_high) + " % of area"]),
  [1500, 1500, 1700, 1300, 1500, 1526], { firstColShade: true }));
c.push(note("Source: Design Document, Section 7. Service-access results use proxy service points because open data contain no veterinary facilities for these districts; they demonstrate the method, not true coverage."));
c.push(callout("What this document gives you", [
  "A product definition: who it is for, what it does, what is in and out of scope, and how success is measured.",
  "Traceable requirements (IDs such as FR-MK-01 and NFR-03) that the Design Document and App Flow Document refer back to.",
  "A frank baseline of the existing HTML prototype (v1.2): what already works, and the gaps to close before the native app satisfies the brief.",
  "Decisions already taken: native APK, real-data analyses, and the Kotlin / Django / Supabase / React stack.",
]));

// 2
c.push(h1("2. Problem and opportunity"));
c.push(h2("2.1 The problem"));
c.push(...bullets([
  "**Small herds, weak market power.** Young and smallholder farmers sell one or two animals at a time, often to the first trader who arrives, with little price information.",
  "**Distance and cost are invisible.** Farmers cannot easily compare the real cost (travel time, transport, shrinkage) of reaching different markets.",
  "**Vets and dip tanks are unevenly spread.** In dispersed districts the nearest animal-health service can be many hours away, and farmers do not know which areas are underserved.",
  "**Water and grazing decide the herd's year.** In semi-arid districts, knowing where water and forage remain is a daily planning problem.",
  "**Disease news travels slowly.** Notifiable diseases and movement controls are communicated through offices and word of mouth, so buyers and sellers can unknowingly trade in restricted areas.",
  "**Records are on paper or in memory,** which limits access to finance that asks for proof of herd history and income.",
  "**Connectivity is patchy and data is expensive.** Most tools assume a stable connection and heavy page loads.",
]));
c.push(h2("2.2 The opportunity"));
c.push(p("Location is the thread that connects these problems. A farm pin, a facility layer and a travel-time surface can answer most of the questions above without any live internet, provided the analysis is prepared in advance and packaged compactly. This is the case for GIS in the brief: not maps for their own sake, but better decisions at the farm gate."));

// 3
c.push(h1("3. Vision, goals and non-goals"));
c.push(callout("Vision", ["Every young livestock farmer in the pilot districts can see where to sell, where to get help, where the water is and where the risk is, from a basic Android phone, with or without a signal."]));
c.push(spacer());
c.push(h2("3.1 Goals"));
c.push(table(["#", "Goal", "How we will know"], [
  ["G1", "Connect farmers to buyers and aggregated (pooled) demand", "Listings published; pool commitments; enquiries per listing"],
  ["G2", "Give location-aware decision support through four spatial analyses", "Insight cards viewed; actions taken (call vet, open listing)"],
  ["G3", "Bring vets, extension officers and best-practice content within reach", "Expert contacts made; guide and advisor use offline"],
  ["G4", "Make finance easier to find and prepare for", "Finance programmes viewed through the eligibility filter; loan-ready record reports exported"],
  ["G5", "Work acceptably with little or no connectivity", "Offline task success; data used per session (Section 10)"],
  ["G6", "Be a credible, documented prototype for assessment", "Brief compliance matrix (Section 15) fully covered"],
], [700, 4300, 4026]));
c.push(h2("3.2 Non-goals (for this release)"));
c.push(...bullets([
  "Online payments or escrow. Deals are agreed between parties (call, SMS, WhatsApp) and settled off-platform. Escrow is a roadmap item.",
  "Crop production advice. Crops are documented as a possible later phase only.",
  "Replacing the Department of Veterinary Services (DVS) or issuing movement permits. The app points users to the official process.",
  "Live AI diagnosis. The pest and disease advisor is a curated, offline knowledge base reviewed by a vet; a live AI proxy is an optional online upgrade.",
  "National coverage. The pilot is three districts.",
]));

// 4
c.push(h1("4. Study area"));
c.push(p("The three districts were chosen because they differ in exactly the ways the spatial analyses are meant to expose. The descriptions below are working hypotheses drawn from general knowledge of the districts; each must be checked against data (Design Document, Section 7) before it is presented as a finding."));
c.push(table(["", "Mhondoro-Ngezi", "Gwanda", "Beitbridge"], [
  ["Province", "Mashonaland West", "Matabeleland South", "Matabeleland South"],
  ["Climate (hypothesis)", "Higher rainfall, wetter natural region; better pasture", "Semi-arid; drought-prone; extensive grazing", "Hot, driest of the three; water-limited"],
  ["Livestock systems (hypothesis)", "Mixed crop-livestock; poultry and pigs closer to towns; cattle", "Cattle and goat ranching; communal and A1/A2 areas", "Goats and cattle; cross-border trade in animals"],
  ["Access (hypothesis)", "Closer to Kadoma, Chegutu, Kwekwe and Harare corridor markets", "Long distances; Gwanda town is the hub; mining demand", "Border post and Beitbridge town; N1 corridor"],
  ["Likely analysis story", "Best market access; test service gaps in resettlement areas", "Large underserved wards; water and grazing decisive", "Water proximity and heat; border-trade market access"],
  ["Language (hypothesis)", "Shona, English", "Ndebele, English", "Venda, Ndebele, Shona, English"],
], [1700, 2440, 2440, 2446], { firstColShade: true }));
c.push(note("Natural region, carrying capacity, language and trade flows are to be confirmed from AGRITEX, DVS, ZIMSTAT and field interviews. [TO CONFIRM]"));

// 5
c.push(h1("5. Users and personas"));
c.push(p("The personas below are illustrative composites for design purposes. They should be validated or replaced by 5 to 8 short interviews per district. [TO CONFIRM]"));
c.push(table(["Persona", "Context", "Needs", "Constraints"], [
  ["**Tafadzwa**, 24\nSmallholder, Gwanda", "10 cattle, 25 goats. Sells 2 to 3 animals per quarter, mostly to local traders.", "Better price; knowing where water is in the dry season; a vet when animals are sick.", "Basic Android phone, intermittent 2G/3G, expensive data, shares phone."],
  ["**Nomsa**, 29\nAgripreneur, Beitbridge", "Buys and finishes goats; sells to butcheries and cross-border traders.", "Buyers and bulk demand; transport cost; disease news; finance for expansion.", "Time-poor; some smartphone use; needs WhatsApp-friendly flows."],
  ["**Tendai**, 27\nStarter, Mhondoro-Ngezi", "Poultry and pigs near a growth point; considering a loan.", "Records for a loan application; vaccination reminders; a pool for bulk sales.", "Wants proof of income and herd history; limited record-keeping habit."],
  ["**Buyer / trader / abattoir**", "Needs volume and quality at predictable times and places.", "Find pools and listings by distance; see aggregation points.", "Wants less time wasted travelling to find animals."],
  ["**Vet / extension officer**", "Covers a large area with few vehicles.", "Receive verified reports; reach farmers with alerts; see where coverage is thin.", "Time and transport; irregular connectivity."],
  ["**Admin (project team, later DVS or a partner)**", "Maintains data and trust.", "Verify listings and experts; publish notices; update layers and content.", "Needs simple tools, audit trail, and low maintenance."],
], [1700, 2400, 2700, 2226]));
c.push(h2("5.1 Jobs to be done"));
c.push(...bullets([
  "When I have animals ready to sell, I want to know the best reachable market and a fair price so that I do not accept the first low offer.",
  "When an animal looks sick, I want to know what it might be and who to call, even with no signal.",
  "When the dry season bites, I want to know where water and grazing are within reach so that I can plan herd movement.",
  "When I apply for a loan, I want my records and herd history in a form a lender accepts.",
  "When an outbreak is declared nearby, I want to hear quickly and know what it means for my movements and sales.",
]));

// 6
c.push(h1("6. Product principles"));
c.push(table(["Principle", "What it means in practice"], [
  ["Offline-first, not offline-tolerant", "Every core task (records, browsing cached listings, guides, advisor, map insights) runs from the device. Online adds freshness, not function."],
  ["Precompute, then ship small", "Heavy GIS runs on the team's computers. The phone receives compact, versioned district packs and does only lightweight lookups."],
  ["Data-thrift by default", "Text first, images resized on the device, no autoplay or large background images, a Data Saver switch, and visible download sizes."],
  ["Decisions, not dashboards", "Each spatial result ends with an action: call this vet, open this listing, set this reminder."],
  ["Trust and safety", "Verified badges, moderation of new sellers, area-level disease alerts that never name farms, and clear pointers to the Department of Veterinary Services."],
  ["Local by design", "English, Shona and Ndebele; plain words; icons that support low literacy; WhatsApp and phone calls as the way deals actually happen."],
  ["Privacy proportionate to purpose", "Collect only what is needed. No national ID in the app. Public map positions are deliberately blurred."],
], [2800, 6226], { firstColShade: true }));

// 7
c.push(h1("7. Scope and requirements"));
c.push(p("Priority uses MoSCoW: **M** must, **S** should, **C** could. The release in scope is the assessed prototype plus a documented path to a pilot."));
c.push(h2("7.1 Functional requirements"));
const fr = [
  ["FR-ON-01", "Onboarding", "Choose language and district; optional GPS; guest browsing without an account.", "M"],
  ["FR-ON-02", "Onboarding", "Register and sign in with phone number and SMS one-time code; farm profile (livestock, ward, farm pin).", "M"],
  ["FR-MK-01", "Marketplace", "Browse and search listings by species, district, price, availability and distance; list and map views.", "M"],
  ["FR-MK-02", "Marketplace", "Create a listing offline with photo (resized on device) and farm location; sync later.", "M"],
  ["FR-MK-03", "Marketplace", "Contact seller by call, SMS or a pre-filled WhatsApp message; shortlist for later.", "M"],
  ["FR-MK-04", "Marketplace", "District price benchmarks derived from listings and admin-entered auction prices.", "S"],
  ["FR-MK-05", "Marketplace", "Seller verification badge and moderation of new sellers; report a listing.", "S"],
  ["FR-HB-01", "Market Hub", "View pools near me with target, deadline and progress; join with quantity and ready date.", "M"],
  ["FR-HB-02", "Market Hub", "Pool outcome: buyer offer, aggregation point on the map, SMS to members; lapse handling.", "S"],
  ["FR-GI-01", "GIS", "Interactive map with offline basemap, layer toggles, farm pin, legend and scale.", "M"],
  ["FR-GI-02", "GIS", "Market accessibility insight for the farm pin (nearest markets, travel time, indicative cost).", "M"],
  ["FR-GI-03", "GIS", "Vet and service accessibility insight (nearest vet, dip tank, extension; gap warning).", "M"],
  ["FR-GI-04", "GIS", "Water proximity and grazing suitability insight (nearest water, suitability class, season).", "M"],
  ["FR-GI-05", "GIS", "Disease risk insight (zone status, active notices); warning on listings in restricted zones.", "M"],
  ["FR-GI-06", "GIS", "Admin view: ward-level coverage gaps and suggested new service locations.", "S"],
  ["FR-WX-01", "Weather", "Five-day forecast for the farm pin, cached; last-updated time shown.", "M"],
  ["FR-WX-02", "Weather", "Livestock advisories from forecast (heat stress, dipping window, post-rain disease watch).", "S"],
  ["FR-LN-01", "Guides", "Husbandry guides by species, available offline, with downloadable packs.", "M"],
  ["FR-LN-02", "Advisor", "Curated pest and disease advisor, offline, with sources and a vet-referral prompt.", "M"],
  ["FR-LN-03", "Advisor", "Report suspected outbreak (GPS, species, photo, count), queued offline; admin verification.", "S"],
  ["FR-EX-01", "Experts", "Directory sorted by distance with call and SMS; request advice asynchronously.", "M"],
  ["FR-FN-01", "Finance", "Funding programmes with eligibility filter (age, gender, species, district), checklists and provider links.", "M"],
  ["FR-RC-01", "Records", "Offline records: health, breeding, feed, sale, expense, mortality; per animal or group.", "M"],
  ["FR-RC-02", "Records", "Summaries (costs, income), vaccination reminders, export to CSV or PDF, share by WhatsApp.", "S"],
  ["FR-AD-01", "Admin", "Web console: verify listings and experts, publish outbreak notices, upload layers, edit content, view analytics.", "M"],
  ["FR-CX-01", "Connectivity", "Visible online/offline state, Pending/Synced badges, Data Saver mode, download sizes, manual sync.", "M"],
  ["FR-CX-02", "Connectivity", "SMS or USSD fallback for outbreak alerts and nearest-vet lookup.", "C"],
];
c.push(table(["ID", "Area", "Requirement", "Pri"], fr, [1100, 1250, 6100, 576], { zebra: true }));
c.push(h2("7.2 Representative user stories"));
c.push(...bullets([
  "As a farmer with no signal, I can record a vaccination and see it marked Pending, so that I do not lose work when the network drops. (FR-RC-01, FR-CX-01)",
  "As a farmer, I can drop a pin on my farm and see my three nearest markets with travel time, so that I choose where to sell. (FR-GI-02)",
  "As a farmer, I am told when my area is under an anthrax movement restriction, so that I do not buy or sell there unknowingly. (FR-GI-05)",
  "As a buyer, I can filter listings to within 100 km of an abattoir pick-up point, so that I plan one collection trip. (FR-MK-01, FR-HB-02)",
  "As an extension officer, I can see wards beyond 60 minutes from any vet, so that I can plan outreach days. (FR-GI-06)",
  "As an admin, I can verify a new seller, so that buyers can trust the listing. (FR-MK-05, FR-AD-01)",
]));

// 8
c.push(h1("8. GIS component and spatial analyses"));
c.push(p("The brief requires an interactive GIS component and at least three spatial analyses. This design delivers **four**, plus an interactive map that hosts them. The full methods, datasets, parameters and validation plan are in the Design Document (Section 7); this section states what each analysis is for."));
c.push(...img("pipelines.png", 6.2, "Figure 1: The four spatial analyses, from data to the farmer's screen"));
c.push(table(["Analysis", "Question it answers", "Decision it supports", "Primary user"], [
  ["1. Market accessibility", "How long and how costly is it to reach each market, abattoir or pooling hub?", "Where to sell; where to place a pooling point", "Farmer, buyer, admin"],
  ["2. Vet and service accessibility", "Who is covered by a vet, dip tank or extension office within 30 and 60 minutes, and who is not?", "Whom to call; where outreach is needed", "Farmer, officer"],
  ["3. Water proximity and grazing suitability", "How far is water in dry and wet season, and how suitable is the surrounding land for grazing?", "Herd movement; stocking; purchase of feed", "Farmer"],
  ["4. Disease risk zones", "Is this location in or near a notified outbreak or restricted area?", "Whether to move or trade animals; when to vaccinate or dip", "Farmer, buyer, officer"],
], [2000, 3100, 2326, 1600], { firstColShade: true }));
c.push(spacer());
c.push(h3("Interactive GIS features"));
c.push(...bullets([
  "Offline basemap per district and layer toggles (markets, vets, dip tanks, water, pools, disease zones, ward scores).",
  "Farm pin by GPS or tap; the same pin drives weather, listings by distance and every insight card.",
  "Result cards with a plain-language sentence and one action button; map overlays for isochrones, coverage and suitability.",
  "Public positions are blurred to about 1 km so that exact farm locations are not exposed (stock-theft risk).",
]));

// 9
c.push(h1("9. Connectivity requirements (core constraint)"));
c.push(p("The brief says connectivity will be assessed explicitly. The requirements below are testable and are the targets we will measure against on throttled networks and a low-spec Android phone. Numbers are proposed targets and should be tuned after the first tests. [TO CONFIRM]"));
c.push(table(["ID", "Requirement", "Target"], [
  ["NFR-01", "Core tasks work with no connection after the first setup (records, guides, advisor, cached listings, map insights, shortlist)", "100% of the Must tasks in the offline test matrix pass"],
  ["NFR-02", "APK size", "15 MB or less per-device download. First build measured: 55 MB debug, 36 MB minified release with two ABIs; per-ABI App Bundle needed to approach target"],
  ["NFR-03", "Typical online session", "Under 150 KB per session (excluding optional photos)"],
  ["NFR-04", "District data pack (basemap + facilities + analysis layers)", `Lookup pack measured at ${PK.summary}; vector basemap estimated 5 to 20 MB; resumable download; size shown first`],
  ["NFR-05", "Listing photo", "Resized on device to about 800 px and 60 KB or less; uploaded after text, on Wi-Fi if possible"],
  ["NFR-06", "Time to open a cached screen on a 2 GB RAM Android", "2 seconds or less"],
  ["NFR-07", "No data loss", "Every write is stored locally first; sync is idempotent (UUIDs); conflicts are surfaced, not silently dropped"],
  ["NFR-08", "Graceful degradation", "Every online-only feature shows a clear offline message and a cached fallback with the last-updated time"],
  ["NFR-09", "Alternative channels", "WhatsApp deep links for deals; SMS one-time codes; SMS or USSD for critical alerts (proposed)"],
  ["NFR-10", "Accessibility and language", "English, Shona and Ndebele; icon plus text; tap targets 44 px or larger; readable in sunlight"],
], [900, 5200, 2926]));
c.push(spacer());
c.push(table(["Feature", "Offline behaviour", "Online enhancement"], [
  ["Marketplace", "Last synced listings; shortlist; draft and queue new listings", "Fresh listings, photos, enquiries, price benchmarks"],
  ["Map and insights", "Basemap, facility and analysis layers from the district pack; client-side lookups", "Updated layers; new outbreak polygons"],
  ["Weather", "Last forecast with timestamp", "Refresh; livestock advisories"],
  ["Guides and advisor", "Fully offline content packs", "Content updates; optional live AI proxy"],
  ["Records", "Full function; Pending badges", "Background sync; backup; export"],
  ["Experts and finance", "Cached directory and programme cards", "Updates; live provider pages"],
  ["Pools", "View last-known state; queue commitment", "Live progress; buyer offers"],
], [1700, 3900, 3426], { firstColShade: true }));

// 10
c.push(h1("10. Success metrics"));
c.push(table(["Metric", "Definition", "Pilot target (proposed)"], [
  ["Offline task success", "Share of Must tasks completed with the network off in scripted tests", "95% or higher"],
  ["Sync reliability", "Pending items that reach Synced within 24 hours of a connection", "98% or higher"],
  ["Data per session", "Median KB transferred per online session", "150 KB or less"],
  ["Activation", "Registered farmers who create a record or listing within 14 days", "50% or higher"],
  ["Market reach", "Enquiries (call, SMS, WhatsApp taps) per live listing", "At least 2 per listing in 30 days"],
  ["Spatial insight use", "Farmers who open at least one insight card and take an action", "40% or higher"],
  ["Service gap closure", "Wards flagged underserved that receive outreach or a new service point", "Tracked, no target at pilot"],
  ["Trust", "Listings reported as fraudulent or misleading", "Under 2%"],
  ["Usability", "Task completion without help in testing with farmers", "80% or higher; SUS score 70 or higher"],
], [2200, 4100, 2726], { firstColShade: true }));
c.push(note("Targets are proposals for the pilot and should be agreed with the group and, ideally, with an extension partner. [TO CONFIRM]"));

// 11
c.push(h1("11. Starting point: the current prototype"));
c.push(p("The starting file is a single-page HTML prototype (MarketMoo v1.2). It has a strong front end and a clear information structure. It does not yet meet the brief's GIS and connectivity requirements. The table summarises what is there and what must change; details and fixes are in the Design Document (Section 12)."));
c.push(h3("What already works"));
c.push(...bullets([
  "Welcome slideshow, farmer sign-up and sign-in, guest explore mode, and a dashboard with nine tiles.",
  "Marketplace with search and filters, listing detail, quantity selector, cart, WhatsApp 'Buy now' and a sell form.",
  "Market Hub pools with progress bars and a join form; husbandry guides; a curated pest and disease advisor; vet directory; finance cards; Open-Meteo weather; a Leaflet map; and a records form.",
  "A consistent visual identity (green, gold, earth tones) and responsive layout.",
]));
c.push(h3("Gaps against the brief"));
c.push(table(["Area", "Observation in v1.2", "Needed"], [
  ["GIS", "Map shows five fixed demo farmers; no analysis; listings carry only a province, not a location", "Four spatial analyses; real coordinates; facility, water and disease layers; offline basemap"],
  ["Offline", "Records are stored locally and flagged unsynced, but there is no service worker, manifest or sync engine. Leaflet, fonts and photos load from the internet", "Native app with encrypted local database, WorkManager sync queue, bundled assets and district packs"],
  ["Backend", "All data in the browser's localStorage; listings are not shared between users; photos stored as large base64 strings", "Database, API, object storage, image resizing"],
  ["Security and privacy", "Password and national ID stored in plain text in localStorage; sign-in by name and password; user text inserted into pages as HTML", "Phone and OTP sign-in; no national ID; hashed secrets on a server; output escaping"],
  ["Weather", "Fails offline; no caching; no livestock advice", "Cache with timestamp; advisories"],
  ["Finance and experts", "Static lists; experts not sorted by distance; finance is links only", "Distance sorting; eligibility filter; checklists"],
  ["Navigation", "Back button on the dashboard logs the user out; several icons render empty; font links malformed", "Predictable navigation; icon set; fixed asset loading"],
  ["Language", "English only", "English, Shona, Ndebele"],
], [1500, 4000, 3526], { zebra: true }));

// 12
c.push(h1("12. Roadmap"));
c.push(table(["Phase", "Timing", "Content"], [
  ["0  Assignment deliverable", "By 2 Oct 2026", "This document set with the four analyses run on real open data; HTML prototype as the interaction reference. Built and tested locally: Kotlin app (sign-in, offline records, map, insights, sync), Django API, Neon database and React manager dashboard. Next: deploy, field test, real facility data."],
  ["1  Pilot build", "Next 8 to 12 weeks [TO CONFIRM]", "Backend and sync; real facility and water data collection; district packs; admin console; field usability tests."],
  ["2  Pilot run", "3 months [TO CONFIRM]", "100 to 200 farmers across the three districts; DVS and AGRITEX partnership; measure Section 10 metrics."],
  ["3  Scale", "Later", "More districts; SMS and USSD; escrow and mobile-money integration; crops module; price intelligence."],
], [2300, 2000, 4726], { firstColShade: true }));

// 13
c.push(h1("13. Risks and mitigations"));
c.push(table(["Risk", "Impact", "Mitigation"], [
  ["Facility and water data missing or out of date", "Spatial analyses unreliable", "Combine OSM with DVS and AGRITEX lists; field verification; show data date; allow user-added points with moderation"],
  ["Low adoption (digital literacy, phone sharing)", "Little value realised", "Icon-led UI, local languages, extension officers as onboarding agents, WhatsApp-friendly flows"],
  ["Fraud or fake listings", "Loss of trust", "Verification badges, moderation of new sellers, report button, no upfront payments in-app"],
  ["Stock theft from exposed locations", "Physical harm", "Blur public positions to about 1 km; exact pin visible only to the owner"],
  ["Incorrect health advice", "Animal and human harm", "Vet-reviewed content; prominent disclaimer; notifiable diseases always point to DVS"],
  ["Outbreak data politically or commercially sensitive", "Partner reluctance; stigma", "Area-level alerts only; DVS-verified; no farm names"],
  ["Storage and download limits on cheap phones", "Offline pack fails", "Small, tiered packs; resumable download; show sizes; persistent-storage request"],
  ["Scope too large for the deadline", "Incomplete assessment", "MoSCoW; demo the four analyses on precomputed layers; keep admin minimal"],
  ["Regulatory (data protection, animal movement)", "Compliance gaps", "Data minimisation; consent; check Zimbabwe's Cyber and Data Protection Act and DVS rules [TO CONFIRM]"],
], [2700, 1900, 4426]));

// 14
c.push(h1("14. Assumptions and dependencies"));
c.push(...bullets([
  "Target users have an Android phone (many will be low-end) and can reach intermittent 2G or 3G at least weekly, at a trading centre or home.",
  "Open datasets (OpenStreetMap, CHIRPS, Sentinel-2, terrain tiles, ESA WorldCover, JRC surface water, WorldPop) were obtained and used in the school trial. They are not sufficient on their own for a field pilot: facility, dip-tank and outbreak data need DVS and AGRITEX.",
  "DVS or AGRITEX will share or verify facility lists and outbreak notices, at least as periodic exports. [TO CONFIRM]",
  "WhatsApp and mobile money (EcoCash) are the dominant channels for deals, as the prototype already assumes.",
  "Prices are shown in USD as in the prototype, with local currency optional. [TO CONFIRM]",
  "The group has access to QGIS and Python for batch GIS, and Render, Neon, Backblaze B2 and Vercel (the group's accounts) for the trial.",
  "No partner data are available (school trial). Where open data are missing (veterinary facilities, dip tanks) the analyses say so and use clearly labelled proxies.",
]));

// 15
c.push(h1("15. Compliance with the project brief"));
c.push(table(["Brief requirement", "Where it is covered"], [
  ["Mobile-first application with backend and admin tools", "Sections 7 and 9; Design Document Sections 3 to 5; Admin console FR-AD-01"],
  ["Interactive GIS component", "Section 8; FR-GI-01 to FR-GI-06; App Flow F6"],
  ["At least three spatial analyses relevant to agriculture", "Section 8 delivers four: market accessibility, service accessibility, water and grazing, disease risk. Methods and real-data results in Design Document Section 7"],
  ["Identify appropriate datasets", "Design Document Section 6 (dataset register with source, licence and use); datasets were downloaded and used in Section 7"],
  ["Suitable software and technologies", "Design Document Section 3 (architecture) and Section 4 (technology choices and rationale)"],
  ["Farming information and best-practice content", "FR-LN-01, FR-LN-02"],
  ["Agricultural experts and advisory support", "FR-EX-01, FR-LN-03"],
  ["Markets for buying and selling", "FR-MK-01 to FR-MK-05; FR-HB-01, FR-HB-02"],
  ["Finance and funding opportunities", "FR-FN-01"],
  ["Weather forecasts relevant to location", "FR-WX-01, FR-WX-02"],
  ["Simple record-keeping tools", "FR-RC-01, FR-RC-02"],
  ["Acceptable function with limited connectivity and data (assessed explicitly)", "Section 9; Design Document Section 5 (offline and sync design, measured pack sizes) and Section 11 (testing under throttled networks)"],
], [3800, 5226], { firstColShade: true }));

// 16
c.push(h1("16. Decisions and open questions"));
c.push(p("The first table records the decisions taken from the group's answers. The second lists the questions that still change the design, each with a default so that work can continue."));
c.push(h2("16.1 Decisions already taken (from the group's answers)"));
c.push(table(["Topic", "Answer", "Effect on the documents"], [
  ["Marking rubric or template", "None", "Documents follow this structure; the compliance matrix (Section 15) maps each brief requirement"],
  ["Real data for the analyses", "Yes, required", "Four analyses run on open data (Design Document Section 7)"],
  ["Delivery form", "Native APK required", "Native Android architecture; PWA content removed"],
  ["Existing facility, water, outbreak data", "None; search the internet", "Open datasets and public reports used; gaps stated; dip-tank layer needs a login (not used)"],
  ["Partner support (DVS, AGRITEX)", "None; school trial", "Proxies clearly labelled; DVS verification kept as a requirement for a real pilot"],
  ["Backend and pack sizes", "Open: 'play around'", "Reference stack adopted with the group's accounts: Django REST on Render, Neon PostgreSQL (instead of Supabase), Backblaze B2 (instead of R2), React dashboard on Vercel; pack sizes measured"],
], [2400, 2000, 4626], { firstColShade: true }));
c.push(h2("16.2 Questions still open"));
const q = [
  ["Q1", "Group and ownership", "Who is in the group, and who owns design, GIS, Android, back end and documentation?", "Roles table to be added"],
  ["Q2", "Dip tanks", "Can someone sign in to the Zimbabwe Geoportal (free account), download the Dip Tanks layer (PDDL 1.0) and place it in build/data/user_supplied/? This replaces the proxy service points in the service-access analysis.", "Re-run the pipeline when available"],
  ["Q3", "Validation", "Can the group time about 20 road trips and rate about 20 grazing points per district (calls to farmers or extension staff are enough) to validate the market-access and suitability models?", "Documented as next steps"],
  ["Q4", "More outbreak records", "Which sources may be used for additional outbreak records (WAHIS, ProMED, DVS notices)?", "Add as found, each with a source link"],
  ["Q5", "Users", "Is the target strictly youth (the prototype and Empower Bank use 18 to 35), and should women-focused finance be filtered by gender?", "Age 18 to 35 as primary; optional gender field for finance matching only"],
  ["Q6", "Marketplace model", "Contact-only marketplace, or must the prototype support in-app payments or escrow?", "Contact-only; payments as roadmap"],
  ["Q7", "Pools", "Who negotiates with abattoirs or buyers for pools: the operator, a cooperative, or buyers posting offers?", "Buyers post offers; admin facilitates"],
  ["Q8", "Currency and units", "USD only, or also ZiG and liveweight prices?", "USD per head; liveweight optional"],
  ["Q9", "Languages", "Which languages matter most for the three districts and who can check Shona, Ndebele and possibly Venda translations?", "English first; Shona and Ndebele string files"],
  ["Q10", "Content ownership", "Who reviews advisor and guide content (a vet)? Some prototype advice (for example culling after African Swine Fever) must match DVS rules.", "Mark 'draft: vet review required'"],
  ["Q11", "Privacy and ethics", "Who is the data controller, and does the school require an ethics note for any field work with farmers?", "No national ID; consent screen; ethics note"],
  ["Q12", "Presentation", "Is a live demo or video expected on 2 October, and on which device?", "Prepare both"],
  ["Q13", "Study-area facts", "Can you confirm natural regions, main livestock, languages and trade flows for the three districts (AGRITEX, ZIMSTAT)?", "Section 4 stays as hypotheses"],
];
c.push(table(["#", "Topic", "Question", "Default if unanswered"], q, [550, 1500, 4476, 2500], { zebra: true }));

build("MarketMoo_1_Product_Design_Document.docx", c, "Product Design Document");
