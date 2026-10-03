const L = require("./lib");
const { h1, h2, h3, p, note, bullets, table, callout, img, cover, toc, build, spacer } = L;

const sections = ["Purpose and conventions", "Roles and entry points", "Navigation map", "Global states and rules", "User flows F1 to F7",
  "Admin flows", "Screen inventory", "Offline and error states", "Changes from the current prototype navigation", "Open questions"];

const c = [];
c.push(...cover("App Flow Document", "Navigation, user flows, screens and states", [
  ["Course / brief", "HGISEO400 project brief (due 02/10/2026)"],
  ["Product", "MarketMoo: livestock information and marketplace, with GIS decision support"],
  ["Pilot area", "Mhondoro-Ngezi, Beitbridge and Gwanda districts, Zimbabwe"],
  ["Depends on", "Product Design Document (requirements); Design Document (architecture, GIS)"],
  ["Delivery form", "Native Android app (APK)"],
  ["Version / date", "Draft v0.2, 2 October 2026"],
  ["Group", "[TO CONFIRM: group name and members]"],
]));
c.push(...toc(sections));

c.push(h1("1. Purpose and conventions"));
c.push(p("This document shows how people move through MarketMoo: which screens exist, how they connect, what happens at each decision, and how the app behaves online, weak and offline. It is the bridge between the requirements and the interface build."));
c.push(table(["Symbol (diagrams)", "Meaning"], [
  ["Green rounded box", "Start or end of a flow"],
  ["White box", "A screen the user sees"],
  ["Light green box", "A user action"],
  ["Yellow diamond", "A decision or branch"],
  ["Orange box and dashed arrow", "Local (offline) step or a retry loop"],
  ["Blue box", "Server or sync step"],
  ["Grey box", "Admin step"],
], [3000, 6026], { firstColShade: true }));
c.push(note("Requirement IDs (FR-xx) refer to the Product Design Document. Screen IDs (S-xx) are listed in Section 7."));

c.push(h1("2. Roles and entry points"));
c.push(table(["Role", "Entry", "Account needed?", "Main journeys"], [
  ["Guest (any visitor)", "Open app, choose Explore", "No", "Browse market, guides, advisor, map, weather; shortlist locally"],
  ["Farmer / agripreneur", "Phone and SMS one-time code", "Yes", "All guest journeys plus sell, pools, records, outbreak report, finance matching, farm insights tied to a saved pin"],
  ["Buyer / trader", "Same as farmer, role Buyer", "Yes", "Search listings by distance, join or create pool offers, save shortlist"],
  ["Vet / extension officer", "Phone and code; admin verifies role", "Yes", "Verified directory entry, receive advice requests, confirm outbreak reports"],
  ["Admin", "Web console with staff login", "Yes", "Moderation, verification, outbreak notices, layers, content, analytics"],
], [1900, 2400, 1300, 3426], { zebra: true }));

c.push(h1("3. Navigation map"));
c.push(p("A bottom tab bar with five tabs (Home, Market, Map, Learn, Help) keeps every main area one tap away. Account is in the top bar. Back returns to the previous screen and never logs the user out."));
c.push(...img("f0_sitemap.png", 6.2, "Figure 1: Sitemap"));

c.push(h1("4. Global states and rules"));
c.push(h3("Connection states (shown in the top bar chip)"));
c.push(table(["State", "Detection", "Behaviour"], [
  ["Online", "Ping succeeds quickly", "Normal sync and refresh; images load unless Data Saver is on"],
  ["Weak", "Ping slow or times out", "Small batches; text only; banner 'Weak signal: saving locally'"],
  ["Offline", "No network or ping fails", "Everything works from the device; online-only items show cached data with a timestamp"],
], [1400, 2800, 4826], { firstColShade: true }));
c.push(h3("Rules that apply on every screen"));
c.push(...bullets([
  "Every save is local first and shows a confirmation at once; items show a Pending or Synced badge.",
  "Online-only actions never fail silently: they show what is cached, how old it is, and what will happen when the signal returns.",
  "Large downloads show their size first and can be paused; Data Saver mode blocks images and background refresh.",
  "A guest who taps a feature that needs an account (sell, join pool, records sync) is asked for the phone number, then returned to the exact screen they came from.",
  "Language can be changed from any screen via Account.",
  "A Home icon in the top bar of every screen (and the Home tab) returns to the landing page and clears the screens opened above it.",
  "Disease warnings and restricted-zone flags always include a path to a vet or DVS contact.",
]));

c.push(h1("5. User flows F1 to F7"));

c.push(h2("F1  First launch and onboarding"));
c.push(...img("f1_onboarding.png", 6.0, "Figure 2: F1 First launch and onboarding"));
c.push(table(["Step", "User", "System", "Offline note"], [
  ["1", "Opens the app", "Shows splash and language choice", "Shell is cached after first load"],
  ["2", "Picks district and optionally allows GPS", "Stores district; offers the district data pack with its size", "Pack download can be postponed to Wi-Fi"],
  ["3", "Chooses to browse as guest, or to sign in", "Guest goes to Market, Learn, Map; sign-in asks for phone number", "Guest mode works without any account"],
  ["4", "Enters phone number", "Sends SMS one-time code; voice code option", "If no signal, entries are kept and the user can retry"],
  ["5", "Enters code", "Creates account; asks role and farm profile (livestock, ward, drop a pin)", "Profile saved locally, synced later"],
  ["6", "Finishes", "Shows the farmer home with weather, alerts, records, finance and sell", ""],
], [600, 2300, 3500, 2626], { zebra: true }));

c.push(h2("F2  Browse and buy"));
c.push(...img("f2_buy.png", 6.0, "Figure 3: F2 Browse and buy"));
c.push(table(["Step", "User", "System"], [
  ["1", "Opens Market", "Shows cached listing cards (species, price, ward, distance, verified badge) and refreshes in the background when online"],
  ["2", "Searches and filters", "Filters locally by species, district, price, availability and distance from the user's pin"],
  ["3", "Opens a listing", "Shows details, seller, payment methods, transport. If the listing is inside an active restricted zone, shows a warning to confirm movement rules with DVS"],
  ["4", "Chooses Call or SMS, WhatsApp, or Save", "tel: or sms: link; WhatsApp opens with a pre-filled enquiry (quantity, price, buyer name); Save adds to the local shortlist"],
  ["5", "Agrees the deal outside the app", "App offers to rate the seller later when online"],
], [600, 2600, 5826], { zebra: true }));

c.push(h2("F3  Sell livestock"));
c.push(...img("f3_sell.png", 6.0, "Figure 4: F3 Sell livestock"));
c.push(table(["Step", "User", "System"], [
  ["1", "Opens Sell (from Home)", "If not signed in, asks for the phone number and returns here"],
  ["2", "Fills the form", "Species, breed, colour, sex, age, quantity, price, payment methods, transport, contact; inline validation that keeps entries"],
  ["3", "Confirms location", "Uses the farm pin; public view is blurred to about 1 km"],
  ["4", "Adds a photo (optional)", "Resizes on the device to about 800 px and 60 KB or less"],
  ["5", "Publishes", "Saves locally as Pending; syncs text first, image later; new sellers go through moderation; then the listing appears on Market and Map"],
  ["6", "Next", "Offers to join a matching pool"],
], [600, 2600, 5826], { zebra: true }));

c.push(h2("F4  Join a Market Hub pool"));
c.push(...img("f4_pool.png", 6.0, "Figure 5: F4 Join a Market Hub pool"));
c.push(table(["Step", "User", "System"], [
  ["1", "Opens Market Hub", "Lists pools near the pin with species, target, deadline and progress (cached; refreshed when online)"],
  ["2", "Opens a pool", "Shows details, buyer, aggregation point and date; checks eligibility (species, district, verified account)"],
  ["3", "Commits quantity, age, weight and ready date", "Stores locally as Pending and syncs; the server checks that the pool is still open"],
  ["4", "Waits", "Progress bar updates for all members; if the target is met, a buyer offer is posted and members receive SMS"],
  ["5", "Collection", "The aggregation point and date appear on the map; if the pool lapses, members are told and commitments are released"],
], [600, 2600, 5826], { zebra: true }));

c.push(h2("F5  Farm records (offline-first)"));
c.push(...img("f5_records.png", 6.0, "Figure 6: F5 Farm records"));
c.push(table(["Step", "User", "System"], [
  ["1", "Opens My Records", "Works fully offline; shows recent records and due reminders"],
  ["2", "Adds a record", "Type (health, breeding, feed, sale, expense, mortality), date, animal or group from a saved list, cost, notes"],
  ["3", "Saves", "Stored on the device with a Pending badge; no network needed"],
  ["4", "Reviews summary", "Costs, income, vaccination due dates; local notification when a date is due"],
  ["5", "Exports", "CSV or PDF, shared by WhatsApp or saved; marked loan-ready when complete and synced"],
  ["6", "Signal returns", "Background sync; badge becomes Synced; conflicts, if any, ask which version to keep"],
], [600, 2600, 5826], { zebra: true }));
c.push(h3("Sync detail"));
c.push(...img("sync_flow.png", 6.0, "Figure 7: Offline write and sync lifecycle"));

c.push(h2("F6  Farm Insights (the four spatial analyses)"));
c.push(...img("f6_insights.png", 6.0, "Figure 8: F6 Farm Insights"));
c.push(table(["Step", "User", "System"], [
  ["1", "Opens Map", "Loads the offline basemap and facility layers from the district pack; if no pack, offers the download or a light online map"],
  ["2", "Marks the farm boundary", "Draws it by tapping corners or adding GPS positions (live hectares), or uploads a zipped shapefile (converted on the phone, previewed in orange, confirmed). A quick pin is the fallback. The boundary centre becomes the pin and is saved to the profile; the outline stays private"],
  ["3", "Opens Insights and picks A, B, C or D", "Looks up the pin's cell in the cached grid and reads precomputed values"],
  ["A", "Market access", "Nearest 3 markets, travel time, indicative cost, isochrone overlay"],
  ["B", "Service access", "Nearest vet and dip tank with time; coverage class; if outside 60 minutes, offers remote advice"],
  ["C", "Water and grazing", "Nearest reliable water (dry and wet season), grazing suitability class, simple hint"],
  ["D", "Disease risk", "Zone status for the pin, active notices and dates, tick and rain watch"],
  ["4", "Acts", "Call vet, open a listing, view pools, set a reminder, or share the result card"],
], [600, 2600, 5826], { zebra: true }));

c.push(h2("F7  Pest and disease advice, and outbreak reporting"));
c.push(...img("f7_disease.png", 6.0, "Figure 9: F7 Pest and disease advice and outbreak report"));
c.push(table(["Step", "User", "System"], [
  ["1", "Opens Learn, then Advisor", "Chat-style interface backed by the offline knowledge base; quick-question chips"],
  ["2", "Searches or taps a problem", "Shows signs, first actions, prevention and sources; every answer ends with a vet prompt"],
  ["3", "Suspect notifiable disease", "Red alert: 'Call DVS or a vet now', with the nearest vet's travel time"],
  ["4", "Reports an outbreak", "Form with GPS, species, count and photo; saved as Pending"],
  ["5", "Sync", "Report reaches admin; DVS verifies; a notice is published with a zone and buffer"],
  ["6", "Alert", "Users with a pin inside the zone get push or SMS; affected listings are flagged"],
], [600, 2600, 5826], { zebra: true }));

c.push(h2("Other short flows"));
c.push(table(["Flow", "Steps"], [
  ["Experts (FR-EX-01)", "Help tab, list sorted by distance, tap Call, SMS or Ask. Ask is queued offline and answered later; verified badge shown."],
  ["Finance (FR-FN-01)", "Home, Finance, set eligibility (age, gender, species, district) or use the profile, see matching programmes, open checklist, then provider link. Cards show last-checked date."],
  ["Weather (FR-WX-01)", "Home, Weather for the pin; shows the cached forecast with its age; Refresh when online; advisories such as heat stress or a dipping window."],
  ["Guides and packs (FR-LN-01)", "Learn, pick species, read offline; Offline packs screen manages downloads and sizes."],
  ["Shortlist", "Saved listings in Market; works offline; tapping a listing re-checks status when online."],
  ["Account", "Profile and farm pin, language, Data Saver, Sync status, export and delete my data, log out."],
], [2200, 6826], { firstColShade: true }));

c.push(h1("6. Admin flows"));
c.push(table(["Flow", "Steps", "Result"], [
  ["Verify a seller or expert", "Queue shows new accounts; admin checks phone, listing and optional documents; approve or reject", "Verified badge shown; listing goes live"],
  ["Moderate listings", "Reports and flagged items; remove, request changes or approve", "Marketplace stays trustworthy"],
  ["Publish outbreak notice", "Review farmer reports, confirm with DVS, set disease, zone and dates, publish", "Zone appears on map; alerts sent; listings flagged"],
  ["Update layers and packs", "Upload new facility or analysis data, run the pipeline, build and publish a versioned pack", "Devices offered the new pack by size"],
  ["Edit content", "Update guides, advisor entries, finance cards, with reviewer and date", "Content packs refreshed"],
  ["Analytics and gaps", "View active users, sync failures, underserved wards, pool progress", "Evidence for outreach and improvements"],
], [2200, 4300, 2526], { zebra: true }));

c.push(h1("7. Screen inventory"));
c.push(table(["ID", "Screen", "Purpose and key content", "Data / offline behaviour"], [
  ["S-01", "Splash and language", "Brand, language choice, Data Saver", "Static"],
  ["S-02", "District and pack", "Pick district, GPS option, pack size and download", "Manifest cached"],
  ["S-03", "Sign in (phone and code)", "Phone entry, code entry, resend, voice option", "Online needed for the code; entries kept"],
  ["S-04", "Role and farm profile", "Role, livestock types, ward, farm pin", "Saved locally, synced"],
  ["S-05", "Home", "Weather summary, alerts, shortcuts to records, sell, finance", "Cached"],
  ["S-06", "Market (explore)", "Search, filters, listing cards, list and map toggle", "Cached feed"],
  ["S-07", "Listing detail", "Photo, details, seller, payment, zone warning, contact buttons", "Cached"],
  ["S-08", "Shortlist", "Saved listings with quantity", "Local only"],
  ["S-09", "Sell livestock", "Listing form, photo, location confirm", "Local first"],
  ["S-10", "Market Hub", "Pools list with progress", "Cached; commitments queued"],
  ["S-11", "Pool detail and join", "Details, eligibility, commitment form", "Queued"],
  ["S-12", "Map and layers", "Basemap, layer toggles, farm pin, legend", "District pack"],
  ["S-13", "Farm Insights", "Menu of four analyses, result cards, overlays", "Cached grid lookup"],
  ["S-14", "Weather", "Current and 5-day forecast, advisories", "Cached with timestamp"],
  ["S-15", "Learn: guides", "Species guides, offline packs", "Offline"],
  ["S-16", "Advisor", "Chat-style knowledge base, chips, sources", "Offline"],
  ["S-17", "Report outbreak", "GPS, species, count, photo, description", "Queued"],
  ["S-18", "Help: vets and experts", "List sorted by distance, call, SMS, ask", "Cached; asks queued"],
  ["S-19", "Finance", "Eligibility filter, programme cards, checklist, links", "Cached; links online"],
  ["S-20", "My Records", "Add form, list, filters, reminders", "Offline"],
  ["S-21", "Record summary and export", "Costs, income, due dates, CSV or PDF", "Offline"],
  ["S-22", "Account", "Profile, language, Data Saver, delete data, log out", "Local and server"],
  ["S-23", "Sync status", "Pending and failed items, Sync now, storage used", "Local"],
  ["S-24", "About and help", "Mission, contact (replace placeholders), data sources and dates", "Static"],
  ["A-01 to A-06", "Admin console", "Verification queue, moderation, outbreak notice editor, layer and pack manager, content editor, analytics", "Online (web)"],
], [800, 2000, 3800, 2426], { zebra: true }));

c.push(h1("8. Offline and error states"));
c.push(table(["Situation", "What the user sees", "What happens"], [
  ["Offline when opening Market", "Banner 'Offline: showing listings from [time]'", "Cached feed used; filters still work"],
  ["Offline when saving a record or listing", "Saved. Pending badge", "Queued; retries when connection returns"],
  ["Sync fails repeatedly", "Needs attention badge and a plain reason in Sync status", "User can retry, edit or delete the item"],
  ["No district pack", "Map shows a light online map or a prompt to download", "Insights unavailable until pack is installed; guide to Wi-Fi"],
  ["Pack download interrupted", "Progress paused with percentage", "Resumes from the same byte"],
  ["Storage nearly full", "Warning with options to clear images or old packs", "Records are never deleted automatically"],
  ["GPS not available", "Prompt to tap the map or choose a ward", "Insights use the ward centroid, marked approximate"],
  ["OTP not received", "Resend and voice code options", "Entries kept; retry timer"],
  ["Pool closed while offline", "Message that the pool closed, with other pools", "Commitment rejected cleanly"],
  ["Restricted zone listing", "Warning banner and DVS contact", "User may still view; contact allowed with warning"],
  ["Weather unavailable", "Last forecast and its age", "Refresh when online"],
  ["Server error", "Friendly message and retry", "Data stays local"],
], [2500, 3200, 3326], { zebra: true }));

c.push(h1("9. Changes from the current prototype navigation"));
c.push(table(["Prototype (v1.2)", "Target"], [
  ["Welcome page with two big buttons (My Market, Explore) and a slideshow", "Short splash with language and district; no slideshow to save data"],
  ["Explore guests must enter name and email first", "Guests browse freely; contact details only when needed"],
  ["Auth chooser, then separate sign-up and log-in pages with national ID and password", "One phone-number and code flow"],
  ["Dashboard of nine tiles; Back logs the user out", "Home plus five-tab bar; Back goes back"],
  ["Cart", "Shortlist (no payment in app), grouped by farmer for WhatsApp"],
  ["Live map as a separate page from Explore", "Map tab with layers and Farm Insights"],
  ["Pest and Disease as a dashboard tile", "Inside Learn, with an outbreak report path"],
  ["Weather, Finance, Records, Hub as separate tiles", "Weather and Records on Home; Hub inside Market; Finance on Home"],
], [4500, 4526], { zebra: true }));

c.push(h1("10. Open questions"));
c.push(table(["#", "Question", "Default"], [
  ["N1", "Should buyers have a separate role and onboarding, or the same flow as farmers?", "Same flow, role chosen at profile step"],
  ["N2", "Is sign-in required to use the map insights, or can guests set a temporary pin?", "Guests can set a local pin; not saved to a server"],
  ["N3", "Who responds to Ask an expert, and what is the expected response time?", "Verified experts; no promise in the prototype"],
  ["N4", "Should the outbreak report be open to everyone or to verified farmers only?", "Verified farmers and officers"],
  ["N5", "Do pools need payment or deposit commitments?", "No; commitment only"],
  ["N6", "Should the prototype include the admin console screens, or are they described only?", "Describe; build a minimal verification and notice editor"],
  ["N7", "Which languages must the screens and error messages be provided in for the demo?", "English plus a few Shona and Ndebele screens"],
], [550, 6000, 2476], { zebra: true }));

build("MarketMoo_3_App_Flow_Document.docx", c, "App Flow Document");
