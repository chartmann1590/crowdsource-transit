# CrowdTransit

**Find your ride. Share the knowledge.**

Community-powered public transit locator — find nearby bus stops, train stations, and transit routes anywhere in the world. Rate stops, leave reviews, and add missing locations. Powered by GTFS open data and community contributions.

[![GitHub Pages Deploy](https://github.com/chartmann1590/crowdsource-transit/actions/workflows/deploy-web.yml/badge.svg)](https://chartmann1590.github.io/crowdsource-transit/)
[![Android CI](https://github.com/chartmann1590/crowdsource-transit/actions/workflows/android-ci.yml/badge.svg)](https://github.com/chartmann1590/crowdsource-transit/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Features

- Interactive map with transit stops using OpenStreetMap (free, no API key required)
- Community ratings and reviews for stops and routes
- Add missing stops — crowdsourced with community verification
- Real-time data via Transitland API (6,000+ agencies worldwide)
- Optional accounts via email/Google, or browse completely anonymously
- Dark theme designed for readability and battery life
- Works on Android, web, and mobile browsers

## Live Demo

**[crowdtransit.app](https://chartmann1590.github.io/crowdsource-transit/)**

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Android** | Kotlin + Jetpack Compose + Hilt |
| **Web** | React 19 + TypeScript + Vite |
| **Maps** | MapLibre GL (free OpenStreetMap tiles) |
| **Backend** | Firebase Realtime Database (Spark/free tier) |
| **Auth** | Firebase Authentication (Anonymous + Google + Email) |
| **Data** | GTFS feeds via Transitland API |
| **Hosting** | GitHub Pages (web) + Firebase Hosting (optional) |
| **Design** | Google Stitch MCP design system |
| **CI/CD** | GitHub Actions |

## Quick Start

### Web App

```bash
cd web
npm install
cp .env.example .env.local   # fill in your Firebase config
npm run dev                   # starts at http://localhost:5173
```

### Android App

```bash
# Place google-services.json in android/app/
cd android
./gradlew assembleDebug
```

### Data Pipeline (GTFS Import)

```bash
cd scripts
npm install
cp .env.example .env          # fill in Firebase + Transitland credentials
node import-all.js
```

## Project Structure

```
crowdsource-transit/
├── android/          # Android app (Kotlin + Jetpack Compose)
├── web/              # Web app (React + TypeScript + Vite)
├── scripts/          # GTFS import & data pipeline
├── firebase/         # Security rules & config
├── .github/          # CI/CD workflows
├── plan/             # Phase-by-phase development plans
├── DESIGN.md         # Design system specification
└── README.md
```

## Architecture

CrowdTransit is a **serverless**, Firebase-backed application:

- **Firebase RTDB** stores all transit data (stops, routes, ratings, comments)
- **GeoFire** indexes stop locations for radius-based nearby queries
- **Transitland API** provides live GTFS data from 6,000+ agencies worldwide
- **MapLibre GL** renders OpenStreetMap vector tiles (free, unlimited)
- **Anonymous auth** allows browsing without creating an account
- Community contributions write directly to RTDB through validated security rules

Both the Android and Web apps share the same Firebase backend, providing a consistent real-time experience across platforms.

## Contributing

This is a community-powered project. Contributions are welcome!

1. Check the [plan/](plan/) folder for the development roadmap
2. See open issues for feature requests and bugs
3. Read [DESIGN.md](DESIGN.md) for design system guidelines
4. Submit PRs against `main`

## License

MIT — see [LICENSE](LICENSE)

---

Built with open data from public transit agencies and the CrowdTransit community.
