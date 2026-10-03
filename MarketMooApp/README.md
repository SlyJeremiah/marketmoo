# MarketMoo Android app (school-trial scaffold)

Kotlin, Jetpack Compose (Material 3), Room on SQLCipher (key wrapped by Android Keystore), WorkManager sync queue,
MapLibre Native with an offline map (no tile server). Three real district data packs from the GIS pipeline
(`../build/results/packs`) are bundled in `app/src/main/assets/packs`.

## What works in this scaffold
- Five-tab app: Home, Market, Map, Learn, Help, plus Records, Farm Insights and Sync status.
- Map: district outlines, 1 km travel-time grid, markets, service points (proxy) and water points, all from the packs; tap to set the farm pin.
- Farm Insights: the four analyses for the pin (market access, service access, water and grazing, disease risk) read from the pack.
- Records and listings: saved locally (encrypted), queued for sync, Pending/Synced badges. Public listing position is blurred to about 1 km in the sync payload.
- Offline advisor and guides from `assets/kb.json` (draft, needs vet review). Experts and market listings are DEMO data.

## Added in this round
- Sign-in with phone number and one-time code (token kept encrypted with an Android Keystore key); Account screen with profile, server address, pack updates, sign out, delete account, erase local data.
- Sync now talks to the API: per-operation results, Needs attention with the server's reason, retries with backoff, Pending until signed in.
- Market: live listings from the server (nearest first, data-saver aware), my listings, pools you can join (server decides when a pool is closed), photo picker (shrunk on the phone, uploaded straight to object storage).
- Disease notices and outbreak reports (queued like any write; never public until a manager verifies).
- District pack updates from the server: resumable download, checksum verified.
- Server address defaults to `http://10.0.2.2:8000` (the host computer from the emulator); cleartext HTTP is allowed only for that host and localhost.

## Not built yet
PMTiles basemap, weather, finance, push alerts, Shona and Ndebele strings, TFLite hint, a device and emulator test run.
The Sync status screen has a clearly labelled demo switch that marks items Synced without a server (presentations only).

## Build
```
./gradlew --offline assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew --offline testDebugUnitTest
./gradlew --offline assembleRelease    # unsigned, minified; sign before installing
```
`local.properties` points to the Android SDK; library versions match the Gradle cache on this machine (hence `--offline`).
Measured sizes: debug 55 MB, release (unsigned, minified, arm64 + armeabi-v7a) 36 MB. MapLibre and SQLCipher native libraries dominate;
publish as an App Bundle or per-ABI APKs to cut the download. The 15 MB target in the Product Design Document is not met yet.
Not yet run on a device or emulator (no system image is installed here).
