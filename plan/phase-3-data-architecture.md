# Phase 3: Data Architecture & Firebase Schema

> **Goal:** Define and implement the complete Firebase Realtime Database schema, security rules, GeoFire indexes, and all data models. This phase makes the database production-ready and establishes the contract that all app code writes against.

---

## Overview

Firebase Realtime Database (RTDB) stores all CrowdTransit data. We chose RTDB (as specified) over Firestore because it has **no daily read/write quotas on Spark plan** (only connection limits), making it more forgiving for a growing crowdsourced app.

We use **GeoFire** (Firebase's geolocation library) layered on top of RTDB to enable radius-based queries like "find all stops within 1km of my location."

---

## Database URL

```
https://crowdtransit-app-default-rtdb.firebaseio.com
```

---

## Complete RTDB Schema

### Root Structure

```
/
├── agencies/             # Transit agencies
├── routes/               # Transit routes  
├── stops/                # Transit stops (GTFS + crowdsourced)
├── trips/                # GTFS trips (schedule data)
├── stop_times/           # GTFS stop times (departures per trip)
├── ratings/              # Ratings for stops, routes, agencies
├── comments/             # User comments/reviews
├── users/                # Public user profiles
├── reports/              # Crowdsourced incident reports
├── crowdsourced/         # User-submitted stops pending review
├── geofire/              # GeoFire index for stop locations
├── meta/                 # App metadata (version, last GTFS update)
└── moderation/           # Flagged content (admin only)
```

---

### `/agencies/{agencyId}`

`agencyId` = sanitized GTFS agency ID (e.g., `sfmta`, `mta_nyc`, `wmata`)

```json
{
  "agencyId": "sfmta",
  "name": "San Francisco Municipal Transportation Agency",
  "shortName": "SFMTA / Muni",
  "url": "https://www.sfmta.com",
  "timezone": "America/Los_Angeles",
  "lang": "en",
  "phone": "415-701-2311",
  "country": "US",
  "state": "CA",
  "city": "San Francisco",
  "lat": 37.7749,
  "lng": -122.4194,
  "transitTypes": ["bus", "tram", "subway"],
  "gtfsFeedUrl": "https://www.sfmta.com/sites/default/files/reports-and-documents/2024/08/google_transit.zip",
  "gtfsRealtimeUrl": "https://api.511.org/transit/tripupdates?agency=SF",
  "lastUpdated": 1735000000000,
  "ratingSum": 1250,
  "ratingCount": 347,
  "verified": true,
  "active": true
}
```

---

### `/routes/{routeId}`

`routeId` = `{agencyId}_{gtfsRouteId}` (e.g., `sfmta_38`, `mta_nyc_A`)

```json
{
  "routeId": "sfmta_38",
  "agencyId": "sfmta",
  "gtfsRouteId": "38",
  "shortName": "38",
  "longName": "Geary",
  "desc": "Geary Boulevard / O'Farrell Street",
  "type": "bus",
  "color": "#4CAF50",
  "textColor": "#FFFFFF",
  "url": "https://www.sfmta.com/routes/38-geary",
  "country": "US",
  "state": "CA",
  "city": "San Francisco",
  "agencyName": "SFMTA",
  "stopCount": 47,
  "ratingSum": 430,
  "ratingCount": 112,
  "lastUpdated": 1735000000000,
  "active": true,
  "firstStopId": "sfmta_7354",
  "lastStopId": "sfmta_6295",
  "headsign": "Downtown"
}
```

**Transit types:** `bus`, `train`, `subway`, `ferry`, `tram`, `cable_car`, `monorail`, `funicular`

---

### `/stops/{stopId}`

`stopId` = `{agencyId}_{gtfsStopId}` for GTFS stops, `user_{uid}_{timestamp}` for crowdsourced stops

```json
{
  "stopId": "sfmta_7354",
  "agencyId": "sfmta",
  "gtfsStopId": "7354",
  "name": "Geary Blvd & Masonic Ave",
  "desc": "Inbound stop on the 38 Geary",
  "lat": 37.7832,
  "lng": -122.4446,
  "code": "13278",
  "url": "",
  "locationType": 0,
  "wheelchairBoarding": 1,
  "country": "US",
  "state": "CA",
  "city": "San Francisco",
  "timezone": "America/Los_Angeles",
  "transitTypes": ["bus"],
  "routeIds": {
    "sfmta_38": true,
    "sfmta_38R": true
  },
  "agencyIds": {
    "sfmta": true
  },
  "ratingSum": 215,
  "ratingCount": 58,
  "commentCount": 23,
  "features": {
    "shelter": true,
    "bench": true,
    "lighting": true,
    "elevator": false,
    "escalator": false,
    "ticketMachine": false,
    "bikeParking": false,
    "parking": false
  },
  "crowdsourced": false,
  "verified": true,
  "addedBy": null,
  "addedAt": 1700000000000,
  "lastUpdated": 1735000000000,
  "active": true,
  "reportCount": 0
}
```

**For crowdsourced stops** (`crowdsourced: true`):
```json
{
  "stopId": "user_abc123_1735000000000",
  "crowdsourced": true,
  "addedBy": "abc123",
  "status": "approved",
  "verifiedBy": ["user1", "user2", "user3"],
  "verificationCount": 3
}
```

---

### `/trips/{tripId}`

`tripId` = `{agencyId}_{gtfsTripId}`

```json
{
  "tripId": "sfmta_1234567",
  "agencyId": "sfmta",
  "routeId": "sfmta_38",
  "serviceId": "WKDY",
  "headsign": "Downtown / Civic Center",
  "directionId": 0,
  "blockId": "",
  "shapeId": "sfmta_38_inbound",
  "wheelchairAccessible": 1,
  "active": true
}
```

---

### `/stop_times/{stopId}/{tripId}`

Departure times for a specific stop on a specific trip. Keyed by stopId for efficient per-stop lookups.

```json
{
  "tripId": "sfmta_1234567",
  "routeId": "sfmta_38",
  "agencyId": "sfmta",
  "arrivalTime": "08:30:00",
  "departureTime": "08:30:00",
  "stopSequence": 5,
  "headsign": "Downtown",
  "serviceId": "WKDY",
  "directionId": 0
}
```

---

### `/ratings/{targetType}/{targetId}/{userId}`

`targetType` = `stops`, `routes`, or `agencies`

```json
{
  "userId": "abc123",
  "displayName": "Charlie H.",
  "isAnonymous": false,
  "targetType": "stop",
  "targetId": "sfmta_7354",
  "overall": 4,
  "subcategories": {
    "cleanliness": 3,
    "safety": 5,
    "accessibility": 4,
    "reliability": 4
  },
  "transitType": "bus",
  "createdAt": 1735000000000,
  "updatedAt": 1735000000000
}
```

**Denormalized summary on each stop/route/agency:**
```
ratingSum / ratingCount = average (computed client-side or via function)
```

When a user rates, we:
1. Write to `/ratings/stops/{stopId}/{userId}`
2. Update `/stops/{stopId}/ratingSum` += delta
3. Update `/stops/{stopId}/ratingCount` += 1 (or 0 if updating)

---

### `/comments/{targetType}/{targetId}/{commentId}`

`commentId` = Firebase push key (auto-generated)

```json
{
  "commentId": "-NxAbc123def",
  "userId": "abc123",
  "displayName": "Charlie H.",
  "isAnonymous": false,
  "avatarInitials": "CH",
  "targetType": "stop",
  "targetId": "sfmta_7354",
  "text": "This stop has a great shelter but the bench is always wet from the fog. Bus 38 is usually on time here.",
  "transitType": "bus",
  "routeId": "sfmta_38",
  "rating": 4,
  "helpful": {
    "abc456": true,
    "abc789": true
  },
  "helpfulCount": 2,
  "flagged": false,
  "flagCount": 0,
  "createdAt": 1735000000000,
  "updatedAt": 1735000000000,
  "editedAt": null
}
```

---

### `/users/{userId}`

Only public profile data. No emails, no private info.

```json
{
  "userId": "abc123",
  "displayName": "Charlie H.",
  "isAnonymous": false,
  "avatarInitials": "CH",
  "joinedAt": 1730000000000,
  "stats": {
    "reviewCount": 47,
    "stopsAdded": 12,
    "helpfulVotes": 183,
    "reportCount": 8
  },
  "badges": {
    "pioneer": true,
    "reviewer": true,
    "contributor": false,
    "veteran": false
  },
  "lastActiveAt": 1735000000000,
  "publicProfile": true
}
```

**Badges:**
- `pioneer`: First 100 users to join
- `reviewer`: 10+ reviews written
- `contributor`: 5+ stops added
- `veteran`: Member 1+ year with 50+ reviews

---

### `/reports/{reportId}`

Crowdsourced incident reports (delays, closures, accessibility issues, etc.)

```json
{
  "reportId": "-NxReport123",
  "userId": "abc123",
  "isAnonymous": false,
  "stopId": "sfmta_7354",
  "routeId": "sfmta_38",
  "agencyId": "sfmta",
  "type": "delay",
  "severity": "moderate",
  "title": "38 Geary running 15+ minutes late",
  "description": "Multiple buses have passed showing 15+ minute delays. No announcement from SFMTA.",
  "createdAt": 1735000000000,
  "expiresAt": 1735086400000,
  "confirmedBy": {
    "abc456": true,
    "abc789": true
  },
  "confirmCount": 2,
  "resolved": false,
  "resolvedAt": null,
  "active": true
}
```

**Report types:** `delay`, `closure`, `accessibility_issue`, `safety_concern`, `route_change`, `new_stop_nearby`, `other`
**Severity:** `low`, `moderate`, `high`, `critical`

---

### `/crowdsourced/{submissionId}`

Pending user-submitted stops (queue for community verification):

```json
{
  "submissionId": "-NxSub123",
  "submittedBy": "abc123",
  "submittedAt": 1735000000000,
  "status": "pending",
  "stopData": {
    "name": "New Bus Stop on Oak St",
    "lat": 37.7751,
    "lng": -122.4192,
    "transitTypes": ["bus"],
    "agencyId": "sfmta",
    "code": "",
    "notes": "Newly installed stop, not in SFMTA data yet",
    "features": {
      "shelter": false,
      "bench": true
    }
  },
  "verifications": {
    "abc456": true,
    "abc789": true
  },
  "verificationCount": 2,
  "rejections": {},
  "rejectionCount": 0,
  "promoted": false,
  "promotedAt": null,
  "promotedStopId": null
}
```

**Promotion logic:** When `verificationCount >= 3`, automatically create a stop in `/stops/` and mark `promoted: true`.

---

### `/geofire/stops/{stopId}`

GeoFire index — used for radius queries. GeoFire writes this automatically.

```json
{
  "g": "9q8yy9mf",
  "l": [37.7832, -122.4446]
}
```

---

### `/meta`

```json
{
  "version": "1.0.0",
  "lastGtfsUpdate": 1735000000000,
  "agencyCount": 127,
  "stopCount": 485203,
  "routeCount": 3847,
  "reviewCount": 12483,
  "userCount": 2341,
  "gtfsImportStatus": "complete"
}
```

---

## Security Rules

Create `firebase/database.rules.json`:

```json
{
  "rules": {
    "agencies": {
      ".read": true,
      ".write": false,
      "$agencyId": {
        ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'admin'"
      }
    },
    "routes": {
      ".read": true,
      ".write": false
    },
    "stops": {
      ".read": true,
      ".write": false,
      "$stopId": {
        "ratingSum": {
          ".write": "auth != null"
        },
        "ratingCount": {
          ".write": "auth != null"
        },
        "commentCount": {
          ".write": "auth != null"
        }
      }
    },
    "trips": {
      ".read": true,
      ".write": false
    },
    "stop_times": {
      ".read": true,
      ".write": false
    },
    "ratings": {
      ".read": true,
      "$targetType": {
        "$targetId": {
          "$userId": {
            ".read": true,
            ".write": "auth != null && auth.uid === $userId",
            ".validate": "newData.hasChildren(['userId', 'overall', 'targetType', 'targetId', 'createdAt']) && newData.child('overall').isNumber() && newData.child('overall').val() >= 1 && newData.child('overall').val() <= 5"
          }
        }
      }
    },
    "comments": {
      ".read": true,
      "$targetType": {
        "$targetId": {
          "$commentId": {
            ".write": "auth != null && (!data.exists() || data.child('userId').val() === auth.uid)",
            ".validate": "newData.hasChildren(['userId', 'text', 'targetType', 'targetId', 'createdAt']) && newData.child('text').isString() && newData.child('text').val().length > 0 && newData.child('text').val().length <= 2000"
          }
        }
      }
    },
    "users": {
      ".read": true,
      "$userId": {
        ".write": "auth != null && auth.uid === $userId",
        ".validate": "newData.hasChildren(['userId', 'joinedAt'])"
      }
    },
    "reports": {
      ".read": true,
      "$reportId": {
        ".write": "auth != null",
        ".validate": "newData.hasChildren(['userId', 'type', 'title', 'createdAt'])"
      }
    },
    "crowdsourced": {
      ".read": true,
      "$submissionId": {
        ".write": "auth != null",
        ".validate": "newData.hasChildren(['submittedBy', 'submittedAt', 'stopData'])"
      }
    },
    "geofire": {
      ".read": true,
      ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'admin'"
    },
    "meta": {
      ".read": true,
      ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'admin'"
    },
    "moderation": {
      ".read": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'admin'",
      ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'admin'"
    }
  }
}
```

**Deploy security rules:**
```bash
firebase deploy --only database
```

---

## RTDB Indexes

Add to `firebase.json` under `"database"`:

```json
{
  "database": {
    "rules": "firebase/database.rules.json",
    "indexes": [
      {
        "path": "/stops",
        "index": ["agencyId", "country", "state", "city", "active", "crowdsourced"]
      },
      {
        "path": "/routes",
        "index": ["agencyId", "type", "country", "state", "city", "active"]
      },
      {
        "path": "/comments/stops",
        "index": ["createdAt", "targetId", "userId"]
      },
      {
        "path": "/reports",
        "index": ["stopId", "active", "createdAt", "type"]
      },
      {
        "path": "/crowdsourced",
        "index": ["status", "submittedAt", "verificationCount"]
      }
    ]
  }
}
```

---

## TypeScript/Kotlin Data Models

### TypeScript (Web)

Create `web/src/types/transit.ts`:

```typescript
export type TransitType = 'bus' | 'train' | 'subway' | 'ferry' | 'tram' | 'cable_car' | 'monorail' | 'funicular';

export interface Agency {
  agencyId: string;
  name: string;
  shortName: string;
  country: string;
  state: string;
  city: string;
  lat: number;
  lng: number;
  transitTypes: TransitType[];
  ratingSum: number;
  ratingCount: number;
  verified: boolean;
  active: boolean;
}

export interface Stop {
  stopId: string;
  agencyId: string;
  name: string;
  desc?: string;
  lat: number;
  lng: number;
  code?: string;
  country: string;
  state: string;
  city: string;
  transitTypes: TransitType[];
  routeIds: Record<string, boolean>;
  ratingSum: number;
  ratingCount: number;
  commentCount: number;
  features: {
    shelter: boolean;
    bench: boolean;
    lighting: boolean;
    elevator: boolean;
    escalator: boolean;
    ticketMachine: boolean;
    bikeParking: boolean;
    parking: boolean;
  };
  crowdsourced: boolean;
  verified: boolean;
  addedBy: string | null;
  addedAt: number;
  lastUpdated: number;
  active: boolean;
}

export interface Route {
  routeId: string;
  agencyId: string;
  shortName: string;
  longName: string;
  type: TransitType;
  color: string;
  textColor: string;
  country: string;
  state: string;
  city: string;
  ratingSum: number;
  ratingCount: number;
  active: boolean;
}

export interface Rating {
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  overall: number;
  subcategories?: {
    cleanliness?: number;
    safety?: number;
    accessibility?: number;
    reliability?: number;
  };
  transitType?: TransitType;
  createdAt: number;
  updatedAt: number;
}

export interface Comment {
  commentId: string;
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  avatarInitials: string;
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  text: string;
  transitType?: TransitType;
  routeId?: string;
  rating?: number;
  helpfulCount: number;
  flagged: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface UserProfile {
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  avatarInitials: string;
  joinedAt: number;
  stats: {
    reviewCount: number;
    stopsAdded: number;
    helpfulVotes: number;
    reportCount: number;
  };
  badges: Record<string, boolean>;
  lastActiveAt: number;
}

export interface Report {
  reportId: string;
  userId: string;
  isAnonymous: boolean;
  stopId?: string;
  routeId?: string;
  agencyId?: string;
  type: 'delay' | 'closure' | 'accessibility_issue' | 'safety_concern' | 'route_change' | 'new_stop_nearby' | 'other';
  severity: 'low' | 'moderate' | 'high' | 'critical';
  title: string;
  description: string;
  createdAt: number;
  expiresAt: number;
  confirmCount: number;
  resolved: boolean;
  active: boolean;
}

export interface CrowdsourcedSubmission {
  submissionId: string;
  submittedBy: string;
  submittedAt: number;
  status: 'pending' | 'approved' | 'rejected';
  stopData: Partial<Stop>;
  verificationCount: number;
  rejectionCount: number;
  promoted: boolean;
}
```

### Kotlin (Android)

Create `android/app/src/main/java/com/crowdtransit/app/model/TransitModels.kt`:

```kotlin
package com.crowdtransit.app.model

import com.google.firebase.database.PropertyName

data class Stop(
    val stopId: String = "",
    val agencyId: String = "",
    val name: String = "",
    val desc: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val code: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val transitTypes: List<String> = emptyList(),
    val routeIds: Map<String, Boolean> = emptyMap(),
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val commentCount: Long = 0L,
    val features: StopFeatures = StopFeatures(),
    val crowdsourced: Boolean = false,
    val verified: Boolean = false,
    val addedBy: String? = null,
    val addedAt: Long = 0L,
    val lastUpdated: Long = 0L,
    val active: Boolean = true
) {
    val averageRating: Float get() = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else 0f
}

data class StopFeatures(
    val shelter: Boolean = false,
    val bench: Boolean = false,
    val lighting: Boolean = false,
    val elevator: Boolean = false,
    val escalator: Boolean = false,
    val ticketMachine: Boolean = false,
    val bikeParking: Boolean = false,
    val parking: Boolean = false
)

data class Route(
    val routeId: String = "",
    val agencyId: String = "",
    val shortName: String = "",
    val longName: String = "",
    val type: String = "bus",
    val color: String = "#4CAF50",
    val textColor: String = "#FFFFFF",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val active: Boolean = true
) {
    val averageRating: Float get() = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else 0f
}

data class Agency(
    val agencyId: String = "",
    val name: String = "",
    val shortName: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val transitTypes: List<String> = emptyList(),
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val verified: Boolean = false,
    val active: Boolean = true
)

data class Comment(
    val commentId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val avatarInitials: String = "",
    val targetType: String = "stop",
    val targetId: String = "",
    val text: String = "",
    val transitType: String = "",
    val routeId: String = "",
    val rating: Int = 0,
    val helpfulCount: Long = 0L,
    val flagged: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class Rating(
    val userId: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val targetType: String = "stop",
    val targetId: String = "",
    val overall: Int = 0,
    val subcategories: Map<String, Int> = emptyMap(),
    val transitType: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val avatarInitials: String = "",
    val joinedAt: Long = 0L,
    val stats: UserStats = UserStats(),
    val badges: Map<String, Boolean> = emptyMap(),
    val lastActiveAt: Long = 0L
)

data class UserStats(
    val reviewCount: Long = 0L,
    val stopsAdded: Long = 0L,
    val helpfulVotes: Long = 0L,
    val reportCount: Long = 0L
)

data class Report(
    val reportId: String = "",
    val userId: String = "",
    val isAnonymous: Boolean = false,
    val stopId: String = "",
    val routeId: String = "",
    val agencyId: String = "",
    val type: String = "delay",
    val severity: String = "moderate",
    val title: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val confirmCount: Long = 0L,
    val resolved: Boolean = false,
    val active: Boolean = true
)
```

---

## GeoFire Setup (Node.js import script)

Create `scripts/update-geofire.js`:

```javascript
const { initializeApp, cert } = require('firebase-admin/app');
const { getDatabase } = require('firebase-admin/database');
const GeoFire = require('geofire').GeoFire;
require('dotenv').config();

initializeApp({
  credential: cert(require(process.env.GOOGLE_APPLICATION_CREDENTIALS)),
  databaseURL: process.env.FIREBASE_DATABASE_URL,
});

const db = getDatabase();

async function indexAllStops() {
  console.log('Indexing stops in GeoFire...');
  const geoFire = new GeoFire(db.ref('geofire/stops'));
  
  const stopsSnap = await db.ref('stops').once('value');
  const stops = stopsSnap.val() || {};
  
  let count = 0;
  const batch = [];
  
  for (const [stopId, stop] of Object.entries(stops)) {
    if (stop.lat && stop.lng && stop.active) {
      batch.push(geoFire.set(stopId, [stop.lat, stop.lng]));
      count++;
      
      // Process in batches of 500 to avoid overloading
      if (batch.length >= 500) {
        await Promise.all(batch);
        batch.length = 0;
        console.log(`Indexed ${count} stops...`);
      }
    }
  }
  
  if (batch.length > 0) {
    await Promise.all(batch);
  }
  
  console.log(`GeoFire indexing complete: ${count} stops indexed`);
}

indexAllStops().catch(console.error);
```

---

## Deploy Database Rules

```bash
firebase deploy --only database
```

---

## Verification

- [ ] Firebase RTDB exists at `https://crowdtransit-app-default-rtdb.firebaseio.com`
- [ ] Security rules deployed — anonymous users can read, must be authenticated to write
- [ ] TypeScript types compile without errors (`cd web && npx tsc --noEmit`)
- [ ] Kotlin models compile without errors (`cd android && ./gradlew compileDebugKotlin`)
- [ ] GeoFire index structure is correct (test with dummy data)
- [ ] Test: anonymous user CAN read `/stops`, CANNOT write to `/stops`
- [ ] Test: authenticated user CAN write to `/ratings/{type}/{id}/{uid}` using their own uid
- [ ] Test: authenticated user CANNOT write to `/ratings/{type}/{id}/otherUserId`
- [ ] Test: validate that a comment with no `text` field is rejected by rules
