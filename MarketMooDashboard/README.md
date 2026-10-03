# MarketMoo Manager (web dashboard)

React 18, Vite, Recharts and MapLibre GL JS. For moderators and project managers; farmers use the Android app.

| Page | What it does |
|---|---|
| Overview | Users, listings, reports, notices, pools; 14-day sign-up, listing and sync charts |
| Listings | Review queue: approve, reject, mark sold, remove; verify sellers |
| Disease | Farmer reports (private) and public notices: start review, publish a notice from a report, create or close notices |
| Pools | Create pools, see progress, close or reopen |
| Map | Listings (blurred positions), disease zones (control and surveillance circles) and private report points on an OpenStreetMap basemap |
| Users | Search by phone, verify sellers |
| Data packs | Registered district packs with size, storage location and checksum |

## Run
```
npm install
copy .env.example .env.local      (set VITE_API_URL to the API address)
npm run dev                       http://localhost:5173
npm test
npm run build
```
Sign in with a staff account (created with `createsuperuser` on the API). The token is kept in session storage, so closing the tab signs out.
The API must list the dashboard origin in `MARKETMOO_CORS_ORIGINS`.

## Deploy on Vercel
Import the repository, set the project root to this folder, set `VITE_API_URL` to the HTTPS address of the API. `vercel.json` rewrites all paths to `index.html`.
The first screen loads about 60 KB (gzipped); the charts and map load on demand.
