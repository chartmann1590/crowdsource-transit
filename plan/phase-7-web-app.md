# Phase 7: Web App

> **Goal:** Build a beautiful, fully functional React web app hosted on GitHub Pages that mirrors all Android app features — map browsing, stop/route detail, ratings, comments, crowdsourcing, and user authentication.

---

## Overview

**Stack:** Vite + React + TypeScript + Firebase JS SDK + MapLibre GL JS

**Hosting:** GitHub Pages (via GitHub Actions, deployed from `web/dist`)

**Architecture:** Component-based React with custom hooks for Firebase data. No state management library needed at this scale — React Context for auth state, custom hooks for RTDB subscriptions.

---

## Project Structure

```
web/src/
├── firebase/
│   ├── config.ts               # Firebase app init
│   ├── auth.ts                 # Auth helpers
│   ├── stops.ts                # Stop queries
│   ├── comments.ts             # Comment queries
│   ├── ratings.ts              # Rating queries
│   └── crowdsource.ts          # Crowdsource submission
├── hooks/
│   ├── useAuth.ts              # Firebase auth state
│   ├── useNearbyStops.ts       # GeoFire radius query
│   ├── useStop.ts              # Single stop subscription
│   ├── useComments.ts          # Comment list subscription
│   └── useUserRating.ts        # User's rating for a target
├── components/
│   ├── Map/
│   │   ├── MapView.tsx         # MapLibre GL map
│   │   ├── StopMarker.tsx      # Custom map marker
│   │   └── MapControls.tsx     # Zoom/locate controls
│   ├── Stop/
│   │   ├── StopCard.tsx        # Card in sidebar list
│   │   ├── StopDetail.tsx      # Full stop detail panel
│   │   └── StopList.tsx        # List of stop cards
│   ├── Review/
│   │   ├── ReviewCard.tsx      # Single review display
│   │   ├── ReviewList.tsx      # List of reviews
│   │   ├── ReviewForm.tsx      # Write a review form
│   │   └── StarRating.tsx      # Interactive/display stars
│   ├── Transit/
│   │   └── TransitBadge.tsx    # Colored type pill
│   ├── Auth/
│   │   ├── LoginModal.tsx      # Sign in modal
│   │   └── AuthContext.tsx     # Auth provider
│   └── UI/
│       ├── Navbar.tsx
│       ├── SearchBar.tsx
│       └── LoadingSpinner.tsx
├── pages/
│   ├── Home.tsx                # Map home page
│   ├── StopPage.tsx            # /stop/:stopId
│   ├── RoutePage.tsx           # /route/:routeId
│   ├── SearchPage.tsx          # /search
│   ├── ProfilePage.tsx         # /profile
│   ├── AddStopPage.tsx         # /add-stop
│   └── AboutPage.tsx           # /about
├── styles/
│   ├── design-tokens.css       # From DESIGN.md
│   ├── globals.css
│   └── maplibre-overrides.css
├── types/
│   └── transit.ts              # (from Phase 3)
├── utils/
│   ├── distance.ts             # Haversine distance calc
│   ├── transit-colors.ts       # Type → color map
│   └── format.ts               # Date/number formatting
├── App.tsx
└── main.tsx
```

---

## Step 1: Design Tokens CSS

Create `web/src/styles/design-tokens.css`:

```css
:root {
  /* Colors */
  --color-primary: #1565C0;
  --color-primary-light: #1E88E5;
  --color-primary-dark: #0D47A1;
  --color-secondary: #FF6F00;
  --color-secondary-light: #FFA000;
  --color-secondary-dark: #E65100;
  
  --color-surface-dark: #0F1724;
  --color-surface: #1A2332;
  --color-surface-elevated: #243044;
  --color-surface-card: #2A3650;
  
  --color-on-surface: #E8EAED;
  --color-on-surface-secondary: #9AA0A6;
  --color-on-primary: #FFFFFF;
  --color-on-secondary: #000000;
  
  --color-success: #00C853;
  --color-warning: #FFB300;
  --color-error: #F44336;
  --color-rating: #FFC107;
  
  --color-bus: #4CAF50;
  --color-train: #2196F3;
  --color-subway: #9C27B0;
  --color-ferry: #00BCD4;
  --color-tram: #FF9800;

  /* Spacing */
  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;
  --space-2xl: 48px;

  /* Border radius */
  --radius-xs: 4px;
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 24px;
  --radius-full: 9999px;

  /* Shadows */
  --shadow-1: 0 1px 3px rgba(0, 0, 0, 0.4);
  --shadow-2: 0 4px 8px rgba(0, 0, 0, 0.5);
  --shadow-3: 0 8px 16px rgba(0, 0, 0, 0.6);

  /* Typography */
  --font-display: 'Google Sans', system-ui, sans-serif;
  --font-body: 'Roboto', system-ui, sans-serif;
  --font-mono: 'Roboto Mono', monospace;
}
```

---

## Step 2: Auth Context

Create `web/src/components/Auth/AuthContext.tsx`:

```tsx
import { createContext, useContext, useEffect, useState } from 'react';
import { User, onAuthStateChanged, signInAnonymously, GoogleAuthProvider, signInWithPopup, signOut as fbSignOut } from 'firebase/auth';
import { ref, set, get, serverTimestamp } from 'firebase/database';
import { auth, database } from '../../firebase/config';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  signInAnon: () => Promise<void>;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (u) => {
      setUser(u);
      setLoading(false);
      if (u) await ensureUserProfile(u);
    });
    return unsub;
  }, []);

  async function ensureUserProfile(u: User) {
    const userRef = ref(database, `users/${u.uid}`);
    const snap = await get(userRef);
    if (!snap.exists()) {
      const name = u.displayName || 'Rider';
      const initials = name.split(' ').slice(0, 2).map((w: string) => w[0]?.toUpperCase() ?? 'R').join('');
      await set(userRef, {
        userId: u.uid,
        displayName: name,
        isAnonymous: u.isAnonymous,
        avatarInitials: initials,
        joinedAt: Date.now(),
        stats: { reviewCount: 0, stopsAdded: 0, helpfulVotes: 0, reportCount: 0 },
        badges: {},
        lastActiveAt: Date.now(),
      });
    }
  }

  async function signInAnon() {
    await signInAnonymously(auth);
  }

  async function signInWithGoogle() {
    const provider = new GoogleAuthProvider();
    if (auth.currentUser?.isAnonymous) {
      const { linkWithPopup } = await import('firebase/auth');
      await linkWithPopup(auth.currentUser, provider);
    } else {
      await signInWithPopup(auth, provider);
    }
  }

  async function signOut() {
    await fbSignOut(auth);
  }

  return (
    <AuthContext.Provider value={{ user, loading, signInAnon, signInWithGoogle, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
```

---

## Step 3: Custom Firebase Hooks

Create `web/src/hooks/useStop.ts`:

```typescript
import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';
import { Stop } from '../types/transit';

export function useStop(stopId: string | null) {
  const [stop, setStop] = useState<Stop | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!stopId) { setLoading(false); return; }
    const r = ref(database, `stops/${stopId}`);
    const unsub = onValue(r, (snap) => {
      setStop(snap.val() as Stop | null);
      setLoading(false);
    });
    return unsub;
  }, [stopId]);

  return { stop, loading };
}
```

Create `web/src/hooks/useComments.ts`:

```typescript
import { useEffect, useState } from 'react';
import { ref, onValue, query, orderByChild } from 'firebase/database';
import { database } from '../firebase/config';
import { Comment } from '../types/transit';

export function useComments(targetType: string, targetId: string | null) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!targetId) { setLoading(false); return; }
    const q = query(
      ref(database, `comments/${targetType}/${targetId}`),
      orderByChild('createdAt'),
    );
    const unsub = onValue(q, (snap) => {
      const data: Comment[] = [];
      snap.forEach((child) => data.push({ commentId: child.key!, ...child.val() }));
      setComments(data.reverse());
      setLoading(false);
    });
    return unsub;
  }, [targetType, targetId]);

  return { comments, loading };
}
```

Create `web/src/hooks/useNearbyStops.ts`:

```typescript
import { useEffect, useState } from 'react';
import { ref } from 'firebase/database';
import { database } from '../firebase/config';
import { Stop } from '../types/transit';
import { haversineDistance } from '../utils/distance';

// Client-side approximation using bounding box query
// For production, use GeoFire JS library
export function useNearbyStops(lat: number | null, lng: number | null, radiusKm: number = 1) {
  const [stops, setStops] = useState<Stop[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!lat || !lng) return;
    
    // Import GeoFire dynamically
    import('geofire-common').then(({ geohashQueryBounds, distanceBetween }) => {
      const { onValue, query, orderByChild, startAt, endAt } = require('firebase/database');
      const bounds = geohashQueryBounds([lat, lng], radiusKm * 1000);
      
      const allStops: Stop[] = [];
      setLoading(true);
      
      Promise.all(
        bounds.map(([start, end]: [string, string]) => {
          return new Promise<void>((resolve) => {
            const q = query(
              ref(database, 'geofire/stops'),
              orderByChild('g'),
              startAt(start),
              endAt(end),
            );
            onValue(q, (snap: any) => {
              snap.forEach((child: any) => {
                const loc = child.val().l;
                const dist = distanceBetween(loc, [lat, lng]);
                if (dist <= radiusKm) {
                  // Fetch the actual stop data
                  // This is simplified — production should batch these
                }
              });
              resolve();
            }, { onlyOnce: true });
          });
        })
      ).then(() => {
        setStops(allStops);
        setLoading(false);
      });
    });
  }, [lat, lng, radiusKm]);

  return { stops, loading };
}
```

---

## Step 4: MapView Component

Create `web/src/components/Map/MapView.tsx`:

```tsx
import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { Stop } from '../../types/transit';
import { TRANSIT_COLORS } from '../../utils/transit-colors';
import './MapView.css';

interface MapViewProps {
  stops: Stop[];
  selectedStopId?: string | null;
  onStopClick?: (stopId: string) => void;
  onMapMove?: (lat: number, lng: number) => void;
  initialLat?: number;
  initialLng?: number;
  initialZoom?: number;
}

export function MapView({
  stops,
  selectedStopId,
  onStopClick,
  onMapMove,
  initialLat = 37.7749,
  initialLng = -122.4194,
  initialZoom = 13,
}: MapViewProps) {
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<Map<string, maplibregl.Marker>>(new Map());

  useEffect(() => {
    if (!mapContainer.current) return;

    const map = new maplibregl.Map({
      container: mapContainer.current,
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: [initialLng, initialLat],
      zoom: initialZoom,
    });

    // Geolocation control
    map.addControl(new maplibregl.GeolocateControl({
      positionOptions: { enableHighAccuracy: true },
      trackUserLocation: true,
      showUserHeading: true,
    }), 'bottom-right');

    map.addControl(new maplibregl.NavigationControl(), 'bottom-right');

    map.on('moveend', () => {
      const center = map.getCenter();
      onMapMove?.(center.lat, center.lng);
    });

    mapRef.current = map;

    return () => {
      markersRef.current.forEach(m => m.remove());
      map.remove();
    };
  }, []);

  // Add/update stop markers
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded()) return;

    // Remove old markers
    markersRef.current.forEach(m => m.remove());
    markersRef.current.clear();

    // Add new markers
    stops.forEach((stop) => {
      if (!stop.lat || !stop.lng) return;

      const primaryType = stop.transitTypes?.[0] || 'bus';
      const color = TRANSIT_COLORS[primaryType] || '#1565C0';
      const isSelected = stop.stopId === selectedStopId;

      const el = document.createElement('div');
      el.className = `stop-marker ${isSelected ? 'selected' : ''}`;
      el.style.cssText = `
        width: ${isSelected ? '20px' : '14px'};
        height: ${isSelected ? '20px' : '14px'};
        background: ${color};
        border: 2px solid white;
        border-radius: 50%;
        cursor: pointer;
        box-shadow: 0 2px 6px rgba(0,0,0,0.5);
        transition: all 0.2s ease;
      `;

      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([stop.lng, stop.lat])
        .addTo(map);

      el.addEventListener('click', () => onStopClick?.(stop.stopId));
      markersRef.current.set(stop.stopId, marker);
    });
  }, [stops, selectedStopId, onStopClick]);

  return (
    <div
      ref={mapContainer}
      style={{ width: '100%', height: '100%' }}
    />
  );
}
```

Create `web/src/utils/transit-colors.ts`:

```typescript
export const TRANSIT_COLORS: Record<string, string> = {
  bus: '#4CAF50',
  train: '#2196F3',
  subway: '#9C27B0',
  ferry: '#00BCD4',
  tram: '#FF9800',
  cable_car: '#795548',
  monorail: '#607D8B',
  funicular: '#E91E63',
};
```

---

## Step 5: TransitBadge Component

Create `web/src/components/Transit/TransitBadge.tsx`:

```tsx
import { TRANSIT_COLORS } from '../../utils/transit-colors';

interface TransitBadgeProps {
  type: string;
  label?: string;
  size?: 'sm' | 'md';
}

export function TransitBadge({ type, label, size = 'md' }: TransitBadgeProps) {
  const color = TRANSIT_COLORS[type] || '#1565C0';
  const fontSize = size === 'sm' ? '10px' : '12px';
  const padding = size === 'sm' ? '2px 6px' : '3px 8px';

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        background: color,
        color: 'white',
        borderRadius: '9999px',
        padding,
        fontSize,
        fontWeight: 500,
        textTransform: 'capitalize',
      }}
    >
      {label ?? type}
    </span>
  );
}
```

---

## Step 6: StarRating Component

Create `web/src/components/Review/StarRating.tsx`:

```tsx
import { useState } from 'react';

interface StarRatingProps {
  rating: number;
  maxStars?: number;
  size?: number;
  interactive?: boolean;
  onRate?: (rating: number) => void;
}

export function StarRating({ rating, maxStars = 5, size = 20, interactive = false, onRate }: StarRatingProps) {
  const [hovered, setHovered] = useState(0);

  return (
    <span style={{ display: 'inline-flex', gap: '2px' }}>
      {Array.from({ length: maxStars }, (_, i) => i + 1).map((star) => {
        const filled = star <= (interactive && hovered ? hovered : rating);
        return (
          <svg
            key={star}
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill={filled ? '#FFC107' : 'none'}
            stroke={filled ? '#FFC107' : '#9AA0A6'}
            strokeWidth="1.5"
            style={{ cursor: interactive ? 'pointer' : 'default' }}
            onMouseEnter={() => interactive && setHovered(star)}
            onMouseLeave={() => interactive && setHovered(0)}
            onClick={() => interactive && onRate?.(star)}
          >
            <polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26" />
          </svg>
        );
      })}
    </span>
  );
}
```

---

## Step 7: Home Page (Map + Sidebar)

Create `web/src/pages/Home.tsx`:

```tsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapView } from '../components/Map/MapView';
import { StopCard } from '../components/Stop/StopCard';
import { Navbar } from '../components/UI/Navbar';
import { ref, query, orderByChild, startAt, endAt, limitToFirst, get } from 'firebase/database';
import { database } from '../firebase/config';
import { Stop } from '../types/transit';
import styles from './Home.module.css';

export function Home() {
  const navigate = useNavigate();
  const [selectedStopId, setSelectedStopId] = useState<string | null>(null);
  const [nearbyStops, setNearbyStops] = useState<Stop[]>([]);
  const [mapCenter, setMapCenter] = useState({ lat: 37.7749, lng: -122.4194 });
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);

  // Get user location on mount
  useEffect(() => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setUserLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setMapCenter({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      () => {} // Silently fail — use default SF location
    );
  }, []);

  // Load stops near map center
  useEffect(() => {
    loadNearbyStops(mapCenter.lat, mapCenter.lng);
  }, [mapCenter]);

  async function loadNearbyStops(lat: number, lng: number) {
    // Simplified: query stops by state/city proximity
    // In production, use GeoFire geohash queries
    const latDelta = 0.02; // ~2km
    try {
      const snap = await get(
        query(
          ref(database, 'stops'),
          orderByChild('lat'),
          startAt(lat - latDelta),
          endAt(lat + latDelta),
          limitToFirst(50),
        )
      );
      const stops: Stop[] = [];
      snap.forEach((child) => {
        const stop = child.val() as Stop;
        // Also filter by longitude
        if (Math.abs(stop.lng - lng) < 0.02) {
          stops.push(stop);
        }
      });
      setNearbyStops(stops.slice(0, 20));
    } catch (e) {
      console.error('Error loading stops:', e);
    }
  }

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.layout}>
        {/* Left sidebar */}
        <aside className={styles.sidebar}>
          <div className={styles.sidebarHeader}>
            <h2>Nearby Stops</h2>
            <span className={styles.count}>{nearbyStops.length} found</span>
          </div>
          <div className={styles.stopList}>
            {nearbyStops.map((stop) => (
              <StopCard
                key={stop.stopId}
                stop={stop}
                selected={stop.stopId === selectedStopId}
                onClick={() => {
                  setSelectedStopId(stop.stopId);
                }}
                onViewDetail={() => navigate(`/stop/${stop.stopId}`)}
              />
            ))}
          </div>
        </aside>

        {/* Map */}
        <main className={styles.mapContainer}>
          <MapView
            stops={nearbyStops}
            selectedStopId={selectedStopId}
            onStopClick={(id) => setSelectedStopId(id)}
            onMapMove={(lat, lng) => setMapCenter({ lat, lng })}
            initialLat={mapCenter.lat}
            initialLng={mapCenter.lng}
          />
        </main>
      </div>
    </div>
  );
}
```

Create `web/src/pages/Home.module.css`:

```css
.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-surface-dark);
}

.layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 360px;
  min-width: 280px;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--color-surface-elevated);
}

.sidebarHeader {
  padding: var(--space-md);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-surface-elevated);
}

.sidebarHeader h2 {
  color: var(--color-on-surface);
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.count {
  color: var(--color-on-surface-secondary);
  font-size: 12px;
}

.stopList {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.mapContainer {
  flex: 1;
  position: relative;
}

@media (max-width: 768px) {
  .layout {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
    max-height: 40vh;
  }
}
```

---

## Step 8: StopCard Component (Web)

Create `web/src/components/Stop/StopCard.tsx`:

```tsx
import { Stop } from '../../types/transit';
import { StarRating } from '../Review/StarRating';
import { TransitBadge } from '../Transit/TransitBadge';
import styles from './StopCard.module.css';

interface StopCardProps {
  stop: Stop;
  selected?: boolean;
  onClick: () => void;
  onViewDetail: () => void;
}

export function StopCard({ stop, selected, onClick, onViewDetail }: StopCardProps) {
  const avgRating = stop.ratingCount > 0 ? stop.ratingSum / stop.ratingCount : 0;

  return (
    <div
      className={`${styles.card} ${selected ? styles.selected : ''}`}
      onClick={onClick}
    >
      <div className={styles.header}>
        <span className={styles.name}>{stop.name}</span>
        <span className={styles.city}>{stop.city}, {stop.state}</span>
      </div>
      <div className={styles.badges}>
        {(stop.transitTypes || []).map((type) => (
          <TransitBadge key={type} type={type} size="sm" />
        ))}
      </div>
      <div className={styles.footer}>
        <StarRating rating={avgRating} size={14} />
        <span className={styles.reviewCount}>({stop.ratingCount})</span>
        <button
          className={styles.detailBtn}
          onClick={(e) => { e.stopPropagation(); onViewDetail(); }}
        >
          Details →
        </button>
      </div>
    </div>
  );
}
```

Create `web/src/components/Stop/StopCard.module.css`:

```css
.card {
  background: var(--color-surface-card);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  cursor: pointer;
  border: 1px solid transparent;
  transition: border-color 0.15s, background 0.15s;
}

.card:hover {
  background: var(--color-surface-elevated);
}

.selected {
  border-color: var(--color-primary);
  background: var(--color-surface-elevated);
}

.header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: var(--space-sm);
}

.name {
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.city {
  color: var(--color-on-surface-secondary);
  font-size: 12px;
}

.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: var(--space-sm);
}

.footer {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.reviewCount {
  color: var(--color-on-surface-secondary);
  font-size: 11px;
}

.detailBtn {
  margin-left: auto;
  background: none;
  border: none;
  color: var(--color-primary-light);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  font-weight: 500;
}
```

---

## Step 9: ReviewForm Component

Create `web/src/components/Review/ReviewForm.tsx`:

```tsx
import { useState } from 'react';
import { push, ref, serverTimestamp } from 'firebase/database';
import { database } from '../../firebase/config';
import { useAuth } from '../Auth/AuthContext';
import { StarRating } from './StarRating';
import styles from './ReviewForm.module.css';

interface ReviewFormProps {
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export function ReviewForm({ targetType, targetId, onSuccess, onCancel }: ReviewFormProps) {
  const { user } = useAuth();
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const [transitType, setTransitType] = useState('bus');
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user || rating === 0) return;

    setSubmitting(true);
    try {
      const displayName = isAnonymous ? 'Anonymous Rider' : (user.displayName || 'Rider');
      const initials = isAnonymous ? '?' : displayName.split(' ').slice(0, 2).map((w: string) => w[0]?.toUpperCase() ?? 'R').join('');
      
      const now = Date.now();
      const comment = {
        userId: user.uid,
        displayName,
        isAnonymous,
        avatarInitials: initials,
        targetType,
        targetId,
        text,
        transitType,
        rating,
        helpfulCount: 0,
        flagged: false,
        createdAt: now,
        updatedAt: now,
      };
      
      await push(ref(database, `comments/${targetType}/${targetId}`), comment);
      onSuccess?.();
    } finally {
      setSubmitting(false);
    }
  }

  const transitTypes = ['bus', 'train', 'subway', 'ferry', 'tram'];

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <h3 className={styles.title}>Write a Review</h3>
      
      <div className={styles.field}>
        <label>Overall Rating *</label>
        <StarRating rating={rating} size={32} interactive onRate={setRating} />
      </div>
      
      <div className={styles.field}>
        <label>Your Experience</label>
        <textarea
          className={styles.textarea}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Share what it's like at this stop..."
          maxLength={2000}
          rows={4}
        />
      </div>
      
      <div className={styles.field}>
        <label>What did you ride?</label>
        <div className={styles.chips}>
          {transitTypes.map((t) => (
            <button
              key={t}
              type="button"
              className={`${styles.chip} ${transitType === t ? styles.chipSelected : ''}`}
              onClick={() => setTransitType(t)}
            >
              {t.charAt(0).toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
      </div>
      
      <label className={styles.anonToggle}>
        <input
          type="checkbox"
          checked={isAnonymous}
          onChange={(e) => setIsAnonymous(e.target.checked)}
        />
        Post anonymously
      </label>
      
      <div className={styles.actions}>
        {onCancel && (
          <button type="button" className={styles.cancelBtn} onClick={onCancel}>
            Cancel
          </button>
        )}
        <button
          type="submit"
          className={styles.submitBtn}
          disabled={rating === 0 || submitting}
        >
          {submitting ? 'Posting...' : 'Post Review'}
        </button>
      </div>
    </form>
  );
}
```

---

## Step 10: React Router Setup

Update `web/src/App.tsx`:

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './components/Auth/AuthContext';
import { Home } from './pages/Home';
import { StopPage } from './pages/StopPage';
import { ProfilePage } from './pages/ProfilePage';
import { AddStopPage } from './pages/AddStopPage';
import { AboutPage } from './pages/AboutPage';
import { SearchPage } from './pages/SearchPage';
import './styles/design-tokens.css';
import './styles/globals.css';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/stop/:stopId" element={<StopPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/add-stop" element={<AddStopPage />} />
          <Route path="/about" element={<AboutPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
```

Update `web/index.html` to include Google Fonts:
```html
<head>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Google+Sans:wght@400;500;700&family=Roboto:wght@400;500&display=swap" rel="stylesheet">
</head>
```

---

## Step 11: GitHub Pages Deploy Config

The GitHub Actions workflow from Phase 1 handles deployment. Additionally:

1. Set `base: '/'` in `vite.config.ts` (already done in Phase 1)
2. Add a `404.html` that redirects to `index.html` for SPA routing on GitHub Pages:

Create `web/public/404.html`:
```html
<!DOCTYPE html>
<html>
  <head>
    <script>
      const path = window.location.pathname;
      window.location.replace('/?path=' + encodeURIComponent(path));
    </script>
  </head>
</html>
```

Add redirect handling in `web/src/main.tsx`:
```tsx
// Handle GitHub Pages SPA redirect
const urlParams = new URLSearchParams(window.location.search);
const redirectPath = urlParams.get('path');
if (redirectPath) {
  window.history.replaceState(null, '', redirectPath);
}
```

---

## Verification

- [ ] `npm run dev` starts the web app locally at localhost:5173
- [ ] Map loads with dark OSM tiles from OpenFreeMap (no API key required)
- [ ] Nearby stops appear as colored pins on the map
- [ ] Clicking a pin highlights it and shows it in the sidebar
- [ ] Clicking "Details →" navigates to the stop detail page
- [ ] Stop detail page shows name, transit badges, star rating, and reviews
- [ ] Review form submits successfully and review appears in real-time
- [ ] Google Sign-In button works (requires Firebase OAuth setup)
- [ ] Anonymous users can read all data but see "Sign in" prompt when trying to write
- [ ] GitHub Pages deployment works — pushing to main deploys the app
- [ ] Web app is fully responsive on mobile (320px – 1440px)
- [ ] App works at `https://chartmann1590.github.io/crowdsource-transit/`
