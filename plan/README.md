# CrowdTransit — Master Plan

> **App:** CrowdTransit — A community-powered crowdsourced public transit locator for the world.  
> **Platforms:** Android (Kotlin + Jetpack Compose) + Web (GitHub Pages)  
> **Backend:** Firebase Spark (free tier) — Realtime Database, Authentication, Hosting  
> **Design:** Google Stitch MCP  
> **Data:** GTFS feeds via Mobility Database + crowdsourced user contributions  

---

## Project Vision

CrowdTransit helps people worldwide find their nearest bus, train, or public transit — for free. It combines official GTFS transit data with real user contributions (stops, routes, ratings, comments) to create a living, community-maintained transit map. Users can stay anonymous or create an account. Everything is beautifully designed and works both on Android and in any browser.

---

## Phases

| # | File | Focus | When |
|---|------|--------|------|
| 1 | [phase-1-foundation.md](phase-1-foundation.md) | GitHub repo, Firebase project, CI/CD, project scaffolding | First |
| 2 | [phase-2-design-system.md](phase-2-design-system.md) | Google Stitch MCP — design system, all screens, DESIGN.md | Second |
| 3 | [phase-3-data-architecture.md](phase-3-data-architecture.md) | Firebase RTDB schema, security rules, GeoFire, indexes | Third |
| 4 | [phase-4-data-pipeline.md](phase-4-data-pipeline.md) | GTFS import scripts — Mobility Database → Firebase RTDB | Fourth |
| 5 | [phase-5-android-core.md](phase-5-android-core.md) | Android app: maps, auth, browse stops/routes, real-time | Fifth |
| 6 | [phase-6-android-features.md](phase-6-android-features.md) | Android: ratings, comments, crowdsourcing, search, directions | Sixth |
| 7 | [phase-7-web-app.md](phase-7-web-app.md) | Web app: React + Firebase + MapLibre → GitHub Pages | Seventh |
| 8 | [phase-8-testing-launch.md](phase-8-testing-launch.md) | Testing, security audit, Play Store, GitHub Pages deploy | Eighth |

---

## Tech Stack Summary

| Layer | Technology | Why |
|-------|-----------|-----|
| Android | Kotlin + Jetpack Compose + Hilt | Modern, Google-recommended, Compose-first |
| Android Maps | MapLibre Native Android | Free, OpenStreetMap, no API key billing |
| Web | React (Vite) + TypeScript | Fast builds, Firebase JS SDK compatible |
| Web Maps | MapLibre GL JS | Free, OSM tiles, vector, beautiful |
| Backend | Firebase Realtime Database | User-specified; unlimited connections on Spark |
| Auth | Firebase Auth (Anon + Google + Email) | 50K MAU free on Spark |
| Hosting | GitHub Pages (web) + Firebase Hosting | Both free |
| Transit Data | GTFS via Mobility Database (6000+ feeds) | Free, global, community-maintained |
| Geo Queries | GeoFire (RTDB extension) | Location radius queries on RTDB |
| Directions | OpenRouteService API (free tier) | 2K requests/day free, no CC required |
| Tile Server | OpenFreeMap / OSM tile CDN | Free, unlimited, global |
| Design | Google Stitch MCP | AI-generated high-fidelity UI, exports to code |
| CI/CD | GitHub Actions | Free for public repos |

---

## Firebase RTDB Top-Level Structure

```
/agencies/{agencyId}         — Transit agencies (source of truth)
/routes/{routeId}            — Routes (from GTFS)
/stops/{stopId}              — Stops (from GTFS + crowdsourced)
/trips/{tripId}              — Trips / schedules
/ratings/{targetId}          — Ratings for stops, routes, agencies
/comments/{targetId}         — Comments/reviews
/users/{userId}              — Public user profiles
/reports/{reportId}          — Crowdsourced incident reports
/geo/stops                   — GeoFire-indexed stop locations
/geo/agencies                — GeoFire-indexed agency locations
```

---

## Key Constraints

- **Firebase Spark plan only** — no billing, no Cloud Functions that exceed Spark limits
- **No Google Maps API** — use MapLibre + OpenStreetMap (free, unlimited)
- **No server required** — GitHub Pages (static) + Firebase backend only
- **Anonymous-first UX** — users can do everything without creating an account
- **Crowdsourced by design** — all data additions/edits go through Firebase RTDB
- **US-first data** — initial GTFS import focuses on major US agencies, expandable globally
- **GTFS standard** — all transit data must conform to GTFS format for consistency

---

## GitHub Repo Structure

```
crowdsource-transit/
├── android/                 # Android app (Gradle)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/crowdtransit/app/
│   │   │   │   ├── ui/          # Jetpack Compose screens
│   │   │   │   ├── viewmodel/   # ViewModels
│   │   │   │   ├── data/        # Repositories + Firebase
│   │   │   │   ├── model/       # Data classes
│   │   │   │   └── di/          # Hilt modules
│   │   │   └── res/
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── web/                     # Web app (Vite + React + TypeScript)
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── firebase/
│   │   ├── hooks/
│   │   └── types/
│   ├── index.html
│   └── vite.config.ts
├── scripts/                 # GTFS import scripts (Node.js)
│   ├── import-gtfs.js
│   ├── fetch-mobility-db.js
│   └── update-geofire.js
├── .github/
│   └── workflows/
│       ├── deploy-web.yml   # GitHub Pages deployment
│       └── android-ci.yml   # Android build + test
├── firebase/
│   ├── database.rules.json
│   └── .firebaserc
├── plan/                    # This folder
├── DESIGN.md                # Google Stitch design system spec
├── firebase.json
└── README.md
```

---

## Critical External Accounts/Services Needed

1. **Firebase project** — create via `firebase init` (new project: `crowdtransit-app`)
2. **GitHub repo** — `chartmann1590/crowdsource-transit` (public)
3. **Transitland API key** — free at https://www.transit.land/ (optional, for enriched data)
4. **OpenRouteService API key** — free at https://openrouteservice.org/ (2K/day directions)
5. **Google OAuth client** — created automatically in Firebase Console
6. **SHA-1 fingerprint** — needed for Google Sign-In on Android (from keystore)
