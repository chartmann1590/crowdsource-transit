# CrowdTransit Web App

React + TypeScript + Vite web client for CrowdTransit. Deployed to GitHub Pages.

## Setup

```bash
npm install
cp .env.example .env.local   # configure Firebase credentials
npm run dev                   # starts dev server at http://localhost:5173
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `VITE_FIREBASE_API_KEY` | Firebase API key |
| `VITE_FIREBASE_AUTH_DOMAIN` | Firebase auth domain |
| `VITE_FIREBASE_DATABASE_URL` | Firebase RTDB URL |
| `VITE_FIREBASE_PROJECT_ID` | Firebase project ID |
| `VITE_TRANSITLAND_API_KEY` | Transitland API key (optional) |

## Build & Deploy

```bash
npm run build    # outputs to dist/
npm run preview  # preview production build locally
```

Deployment to GitHub Pages is automated via `.github/workflows/deploy-web.yml` — pushes to `main` on `web/**` paths trigger a build and deploy.

## Project Layout

```
web/src/
├── components/
│   ├── Auth/          # AuthProvider, LoginModal
│   ├── Map/           # MapView, StopMarker, MapControls
│   ├── Review/        # ReviewForm, ReviewCard, StarRating
│   ├── Stop/          # StopCard, StopDetail, StopList
│   ├── Transit/       # TransitBadge (colored type pills)
│   └── UI/            # Navbar, SearchBar, LoadingSpinner
├── firebase/          # Firebase config + data queries
├── hooks/             # useAuth, useNearbyStops, useStop, useComments
├── pages/             # Home, StopPage, RoutePage, SearchPage, etc.
├── styles/            # Design tokens, globals, map overrides
├── types/             # TypeScript transit data models
└── utils/             # Distance calc, transit colors, formatting
```

## Tech

- **React 19** with TypeScript
- **Vite** for build tooling
- **Firebase JS SDK** for auth + realtime database
- **MapLibre GL JS** for vector maps (OpenStreetMap)
- **react-router-dom v7** for client-side routing
