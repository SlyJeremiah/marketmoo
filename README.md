# MarketMoo: livestock information, GIS and marketplace platform

HGISEO400 project (school trial). Mobile-first and offline-first, for young livestock farmers in Mhondoro-Ngezi, Gwanda and Beitbridge districts, Zimbabwe.

| Folder | What it is |
|---|---|
| `MarketMooApp/` | Native Android app (Kotlin, Jetpack Compose, SQLCipher, MapLibre) |
| `MarketMooDashboard/` | Manager dashboard (React, Vite, Recharts, MapLibre GL JS) |
| `MarketMooApi/` | Django REST API (kept as its own repository: marketmoobackend) |
| `build/data/`, `build/*.py` | GIS pipeline: open-data download and the four spatial analyses; `build/results/` holds statistics, maps and district packs |
| `build/*.js` | Builds the three Word documents from the results |
| `MarketMoo_1_Product_Design_Document.docx`, `_2_Design_Document.docx`, `_3_App_Flow_Document.docx` | Product design, technical design (with the real-data results) and app flow |

Read the Design Document, Section 1, for the implementation status and Section 7 for the analyses and their limits.
Secrets (database URL, storage keys) live only in untracked `.env` files; see each folder's README and `.env.example`.
