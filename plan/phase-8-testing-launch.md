# Phase 8: Testing, Security Audit & Launch

> **Goal:** Verify the entire system works end-to-end, harden security rules, prepare for Play Store submission, and perform a final production readiness check.

---

## Step 1: Firebase Security Rules Testing

Firebase Security Rules can be tested using the Firebase Emulator Suite + the `@firebase/rules-unit-testing` library.

Create `scripts/test-security-rules.js`:

```javascript
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { ref, get, set, push } = require('firebase/database');
const fs = require('fs');

async function runTests() {
  const testEnv = await initializeTestEnvironment({
    projectId: 'crowdtransit-app',
    database: {
      rules: fs.readFileSync('../firebase/database.rules.json', 'utf8'),
      host: 'localhost',
      port: 9000,
    },
  });

  console.log('=== Testing Firebase Security Rules ===\n');
  
  // Setup: add a test stop
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), 'stops/test_stop_1'), {
      stopId: 'test_stop_1',
      name: 'Test Stop',
      lat: 37.0, lng: -122.0,
      ratingSum: 0, ratingCount: 0,
      active: true,
    });
  });

  // Test 1: Unauthenticated read of stops → ALLOW
  {
    const db = testEnv.unauthenticatedContext().database();
    await assertSucceeds(get(ref(db, 'stops/test_stop_1')));
    console.log('✓ Test 1 PASS: Unauthenticated can read stops');
  }

  // Test 2: Unauthenticated write to stops → DENY
  {
    const db = testEnv.unauthenticatedContext().database();
    await assertFails(set(ref(db, 'stops/hacked'), { name: 'malicious' }));
    console.log('✓ Test 2 PASS: Unauthenticated cannot write stops');
  }

  // Test 3: Authenticated user write their own rating → ALLOW
  {
    const db = testEnv.authenticatedContext('user_alice').database();
    await assertSucceeds(set(ref(db, 'ratings/stops/test_stop_1/user_alice'), {
      userId: 'user_alice',
      overall: 4,
      targetType: 'stop',
      targetId: 'test_stop_1',
      createdAt: Date.now(),
    }));
    console.log('✓ Test 3 PASS: Auth user can write own rating');
  }

  // Test 4: Authenticated user write another user's rating → DENY
  {
    const db = testEnv.authenticatedContext('user_bob').database();
    await assertFails(set(ref(db, 'ratings/stops/test_stop_1/user_alice'), {
      userId: 'user_alice',
      overall: 1,
      targetType: 'stop',
      targetId: 'test_stop_1',
      createdAt: Date.now(),
    }));
    console.log('✓ Test 4 PASS: Auth user cannot write other user\'s rating');
  }

  // Test 5: Comment without required fields → DENY
  {
    const db = testEnv.authenticatedContext('user_alice').database();
    await assertFails(push(ref(db, 'comments/stops/test_stop_1'), {
      userId: 'user_alice',
      // Missing: text, targetType, targetId, createdAt
    }));
    console.log('✓ Test 5 PASS: Comment without required fields rejected');
  }

  // Test 6: Valid comment → ALLOW
  {
    const db = testEnv.authenticatedContext('user_alice').database();
    await assertSucceeds(push(ref(db, 'comments/stops/test_stop_1'), {
      userId: 'user_alice',
      displayName: 'Alice',
      isAnonymous: false,
      avatarInitials: 'A',
      targetType: 'stop',
      targetId: 'test_stop_1',
      text: 'Great stop!',
      helpfulCount: 0,
      flagged: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }));
    console.log('✓ Test 6 PASS: Valid comment allowed');
  }

  // Test 7: User writes their own profile → ALLOW
  {
    const db = testEnv.authenticatedContext('user_carol').database();
    await assertSucceeds(set(ref(db, 'users/user_carol'), {
      userId: 'user_carol',
      displayName: 'Carol',
      joinedAt: Date.now(),
    }));
    console.log('✓ Test 7 PASS: User can write own profile');
  }

  // Test 8: User writes another user's profile → DENY
  {
    const db = testEnv.authenticatedContext('user_carol').database();
    await assertFails(set(ref(db, 'users/user_dave'), {
      userId: 'user_dave',
      displayName: 'hacked',
      joinedAt: Date.now(),
    }));
    console.log('✓ Test 8 PASS: User cannot write other profiles');
  }

  await testEnv.cleanup();
  console.log('\n=== All security rule tests passed ===');
}

runTests().catch(err => {
  console.error('FAIL:', err.message);
  process.exit(1);
});
```

Run:
```bash
# Start emulators
firebase emulators:start --only database

# In another terminal
cd scripts
node test-security-rules.js
```

---

## Step 2: Android Unit Tests

Create `android/app/src/test/java/com/crowdtransit/app/model/StopTest.kt`:

```kotlin
package com.crowdtransit.app.model

import org.junit.Test
import org.junit.Assert.*

class StopTest {
    
    @Test
    fun `averageRating returns 0 when no ratings`() {
        val stop = Stop(ratingSum = 0L, ratingCount = 0L)
        assertEquals(0f, stop.averageRating, 0.001f)
    }
    
    @Test
    fun `averageRating calculates correctly`() {
        val stop = Stop(ratingSum = 18L, ratingCount = 5L)
        assertEquals(3.6f, stop.averageRating, 0.001f)
    }
    
    @Test
    fun `averageRating handles single rating`() {
        val stop = Stop(ratingSum = 5L, ratingCount = 1L)
        assertEquals(5f, stop.averageRating, 0.001f)
    }
}
```

Create `android/app/src/test/java/com/crowdtransit/app/model/RouteTest.kt`:

```kotlin
package com.crowdtransit.app.model

import org.junit.Test
import org.junit.Assert.*

class RouteTest {
    
    @Test
    fun `averageRating returns 0 when no ratings`() {
        val route = Route(ratingSum = 0L, ratingCount = 0L)
        assertEquals(0f, route.averageRating, 0.001f)
    }
    
    @Test
    fun `averageRating calculates correctly`() {
        val route = Route(ratingSum = 20L, ratingCount = 4L)
        assertEquals(5f, route.averageRating, 0.001f)
    }
}
```

Run:
```bash
cd android
./gradlew test
```

Expected: All tests pass.

---

## Step 3: Web Unit Tests

Install test dependencies:
```bash
cd web
npm install --save-dev vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

Add to `web/vite.config.ts`:
```typescript
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
```

Create `web/src/test/setup.ts`:
```typescript
import '@testing-library/jest-dom';
```

Create `web/src/components/Review/StarRating.test.tsx`:
```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { StarRating } from './StarRating';

describe('StarRating', () => {
  it('renders 5 stars', () => {
    render(<StarRating rating={3} />);
    const stars = screen.getAllByRole('img', { hidden: true });
    // SVG elements — check for 5 star shapes
    expect(document.querySelectorAll('svg').length).toBe(5);
  });

  it('calls onRate when clicked in interactive mode', async () => {
    const onRate = vi.fn();
    render(<StarRating rating={0} interactive onRate={onRate} />);
    const svgs = document.querySelectorAll('svg');
    fireEvent.click(svgs[3]); // Click 4th star
    expect(onRate).toHaveBeenCalledWith(4);
  });
});
```

Create `web/src/components/Transit/TransitBadge.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import { TransitBadge } from './TransitBadge';

describe('TransitBadge', () => {
  it('renders the transit type label', () => {
    render(<TransitBadge type="bus" label="Bus 38" />);
    expect(screen.getByText('Bus 38')).toBeInTheDocument();
  });

  it('uses correct color for bus', () => {
    const { container } = render(<TransitBadge type="bus" />);
    const badge = container.firstChild as HTMLElement;
    expect(badge.style.background).toBe('rgb(76, 175, 80)'); // #4CAF50
  });

  it('uses correct color for subway', () => {
    const { container } = render(<TransitBadge type="subway" />);
    const badge = container.firstChild as HTMLElement;
    expect(badge.style.background).toBe('rgb(156, 39, 176)'); // #9C27B0
  });
});
```

Run:
```bash
cd web
npm test
```

---

## Step 4: End-to-End Manual Test Script

Work through this test script on both the web app and Android app:

### Auth Flow
- [ ] Open app for the first time → onboarding shown (Android) / map loads (web)
- [ ] Continue as guest → anonymous auth fires, user can browse without creating account
- [ ] Sign in with Google → popup opens, credential links to anonymous account
- [ ] Sign out → redirected to map, still can browse (anonymous sign-in again)
- [ ] Create account with email/password → account created, profile exists in RTDB

### Map & Discovery
- [ ] Map loads with dark OSM tiles (no blank screen)
- [ ] Location permission prompt → GPS location used as map center
- [ ] Transit stop pins appear on the map in correct colors (bus=green, train=blue, etc.)
- [ ] Tapping a pin shows stop name (Android: bottom sheet, web: sidebar highlight)
- [ ] Zooming in/out works smoothly
- [ ] Bottom sheet (Android) drags up/down correctly

### Stop Detail
- [ ] Navigate to stop detail from map pin or stop card
- [ ] Stop name, city, transit types, average rating, review count all display correctly
- [ ] Reviews list shows most recent first
- [ ] "Write a Review" button → navigates to review form
- [ ] "Get Directions" button → opens maps app with walking directions to stop

### Rating & Reviews
- [ ] Tap 3 stars in review form → 3 stars highlight
- [ ] Write comment text > 0 characters → Submit button enables
- [ ] Submit review → review appears immediately in stop detail (real-time)
- [ ] Rating sum and count update on the stop after submission
- [ ] Posting anonymously → shows "Anonymous Rider" in review card
- [ ] "Helpful" toggle on review card → helpfulCount increments/decrements

### Crowdsourcing — Add Stop
- [ ] Tap + FAB → add stop form opens
- [ ] Fill name, select transit types, agency → Submit enabled
- [ ] Submit → entry appears in `/crowdsourced` in Firebase Console
- [ ] Submission shows "pending" status

### Search
- [ ] Type 3+ characters → results appear within 0.5s
- [ ] Results show stop name, city, transit badges
- [ ] Tap a result → navigate to stop detail

### Security (RTDB Console Test)
- [ ] Open Firebase Console → RTDB → try to edit a stop directly
  - While not logged in as admin: changes should only be possible via app
- [ ] Run `node scripts/test-security-rules.js` → all 8 tests pass

---

## Step 5: Firebase Spark Plan Limit Review

Before launch, verify all usage stays within Spark plan free tier:

| Resource | Limit | Our Usage | Risk |
|----------|-------|-----------|------|
| RTDB connections | 100 simultaneous | Start: <10 | Low |
| RTDB storage | Varies | ~500MB after initial import | Monitor |
| RTDB bandwidth | 10 GB/month | Depends on active users | Monitor |
| Auth users | 50,000 MAU | Start: <100 | Low |
| Hosting bandwidth | 10 GB/month | Static web app | Low |
| Hosting storage | 10 GB | <50 MB | Low |

**Firebase spending alerts:**
1. Firebase Console → Project Settings → Usage and billing
2. Set a budget alert at $1/month
3. Upgrade to Blaze plan **only when needed** (pay-as-you-go, still free below limits)

Note: RTDB has no daily read/write quotas on Spark — only connection and bandwidth limits. This is why we chose RTDB over Firestore for this project.

---

## Step 6: Android Play Store Preparation

### Create Release Keystore

```bash
keytool -genkey -v \
  -keystore android/crowdtransit-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias crowdtransit
```

**Store the keystore password and alias password in GitHub Secrets:**
```bash
gh secret set KEYSTORE_PASSWORD --body "your-keystore-password"
gh secret set KEY_ALIAS --body "crowdtransit"
gh secret set KEY_PASSWORD --body "your-key-password"
cat android/crowdtransit-release.jks | base64 -w 0 | gh secret set RELEASE_KEYSTORE
```

**Add to `.gitignore`:**
```
*.jks
*.keystore
```

### Update `android/app/build.gradle.kts` for release signing:

```kotlin
android {
    signingConfigs {
        release {
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            storeFile = System.getenv("KEYSTORE_PATH")?.let { File(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### Add Release CI Workflow

Create `.github/workflows/android-release.yml`:

```yaml
name: Android Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Decode files
        run: |
          echo "${{ secrets.GOOGLE_SERVICES_JSON }}" | base64 --decode > android/app/google-services.json
          echo "${{ secrets.RELEASE_KEYSTORE }}" | base64 --decode > android/crowdtransit-release.jks
      
      - name: Build release AAB
        run: cd android && ./gradlew bundleRelease
        env:
          KEYSTORE_PATH: ../crowdtransit-release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      
      - uses: actions/upload-artifact@v4
        with:
          name: release-aab
          path: android/app/build/outputs/bundle/release/app-release.aab
```

### App Store Assets Needed

- **Icon:** 512×512 PNG (generate from Stitch/design system)
- **Feature graphic:** 1024×500 PNG
- **Screenshots:** 4 minimum (phone screenshots from emulator):
  - Map home screen
  - Stop detail with reviews  
  - Add Stop form
  - User profile
- **Short description:** 80 chars max
- **Full description:** up to 4000 chars

**Short description:**
> Find nearby transit, rate stops & routes, add missing stops. Community-powered.

**Full description:**
```
CrowdTransit is a free, community-powered app to find public transit near you — anywhere in the world.

🗺️ FIND TRANSIT INSTANTLY
• See bus stops, train stations, subway entrances, ferry terminals near you
• Real-time map with color-coded pins by transit type
• Works globally — data from 6,000+ transit agencies

⭐ RATE & REVIEW
• Rate stops and routes on cleanliness, safety, accessibility, and reliability
• Leave detailed comments to help other riders
• Mark reviews as helpful to surface the best feedback

➕ ADD MISSING STOPS
• Know of a stop that's not in the app? Add it!
• Community verification — once 3 users confirm, it's official
• Report delays, closures, and accessibility issues in real-time

👤 YOUR CHOICE OF PRIVACY
• Browse completely anonymously — no account required
• Create an account with email or Google to track your contributions
• Post reviews anonymously even with an account

🌍 GLOBAL & FREE
• Initial coverage: 30+ US cities including New York, Chicago, San Francisco, Los Angeles, Washington DC, Boston, Seattle, and more
• Growing globally with every community contribution
• All data is free and open — powered by GTFS open transit standards

Built on open data from public transit agencies and the CrowdTransit community.
```

---

## Step 7: Production Firebase Security Rules (Final)

Deploy the finalized security rules (already written in Phase 3):

```bash
firebase deploy --only database
```

Verify rules are deployed:
```bash
firebase database:rules:get
```

---

## Step 8: Web App SEO & Meta Tags

Update `web/index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta name="theme-color" content="#1565C0" />
  
  <title>CrowdTransit — Find Your Ride, Share the Knowledge</title>
  <meta name="description" content="Community-powered public transit locator. Find bus stops, train stations, and more near you. Rate stops, leave reviews, and add missing locations." />
  <meta name="keywords" content="public transit, bus stops, train stations, transit map, crowdsourced" />
  
  <!-- Open Graph -->
  <meta property="og:title" content="CrowdTransit" />
  <meta property="og:description" content="Find nearby transit stops, rate them, and help your community." />
  <meta property="og:type" content="website" />
  <meta property="og:url" content="https://chartmann1590.github.io/crowdsource-transit/" />
  
  <!-- PWA manifest -->
  <link rel="manifest" href="/manifest.json" />
  
  <!-- Fonts -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500&display=swap" rel="stylesheet">
</head>
```

Create `web/public/manifest.json` (PWA support):
```json
{
  "name": "CrowdTransit",
  "short_name": "CrowdTransit",
  "description": "Community-powered public transit locator",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#0F1724",
  "theme_color": "#1565C0",
  "icons": [
    {
      "src": "/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

---

## Step 9: README

Create `README.md` in project root:

```markdown
# CrowdTransit

Community-powered public transit locator — Android app + web app.

Find nearby bus stops, train stations, and transit routes. Rate stops, leave reviews, and add missing locations. Powered by GTFS open data and community contributions.

## Features

- 🗺️ Interactive map with transit stops (OpenStreetMap, free)
- ⭐ Community ratings and reviews for stops and routes
- ➕ Add missing stops (crowdsourcing with community verification)
- 🔔 Real-time incident reports (delays, closures, issues)
- 👤 Optional accounts (email/Google) or stay anonymous
- 🌍 Global coverage via GTFS open transit data

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android | Kotlin + Jetpack Compose + Firebase |
| Web | React + TypeScript + Firebase JS SDK |
| Maps | MapLibre GL (free, OpenStreetMap) |
| Backend | Firebase Realtime Database |
| Auth | Firebase Authentication |
| Hosting | GitHub Pages + Firebase Hosting |
| Data | GTFS feeds (Mobility Database, 6000+ agencies) |

## Development

### Prerequisites
- Node.js 22+
- Java 17+
- Android Studio
- Firebase CLI: `npm install -g firebase-tools`

### Web App
```bash
cd web
npm install
cp .env.example .env.local   # Fill in your Firebase config
npm run dev
```

### Android App
```bash
# Place google-services.json in android/app/
cd android
./gradlew assembleDebug
```

### GTFS Import
```bash
cd scripts
npm install
cp .env.example .env   # Fill in Firebase credentials
node import-all.js
```

## Contributing

This is a crowdsourced app — contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT
```

---

## Final Launch Checklist

### Firebase
- [ ] Security rules deployed and tested (8/8 tests pass)
- [ ] Realtime Database created in us-central1 region
- [ ] Auth providers enabled: Anonymous, Google, Email/Password
- [ ] Firebase Hosting connected (optional — GitHub Pages is primary)

### Android
- [ ] App compiles in release mode (`./gradlew bundleRelease`)
- [ ] All unit tests pass (`./gradlew test`)
- [ ] App runs on physical device (not just emulator)
- [ ] Location permissions work on Android 13+ (precise + coarse)
- [ ] Google Sign-In works (requires SHA-1 in Firebase Console)
- [ ] Anonymous sign-in works
- [ ] Map loads correctly
- [ ] At least 3 stops visible near a populated US location
- [ ] Stop detail, rating, and review submission all work
- [ ] Play Store account created at play.google.com/console
- [ ] Release AAB uploaded to Play Store internal testing track

### Web
- [ ] `npm run build` produces clean dist/ output
- [ ] App deployed at `https://chartmann1590.github.io/crowdsource-transit/`
- [ ] Map loads on desktop and mobile
- [ ] All pages render correctly (no blank screens)
- [ ] Auth works (Google Sign-In popup)
- [ ] Review form submits successfully
- [ ] HTTPS served (GitHub Pages provides this automatically)
- [ ] Lighthouse score ≥ 80 on Performance, Accessibility, Best Practices

### Data
- [ ] At least 10 US transit agencies imported
- [ ] At least 50,000 stops in Firebase RTDB
- [ ] GeoFire index populated
- [ ] `node scripts/verify-import.js` passes

### Documentation
- [ ] README.md complete
- [ ] DESIGN.md committed with all design tokens
- [ ] All phase plan files in `plan/` folder
- [ ] `plan/README.md` updated with actual completion dates
