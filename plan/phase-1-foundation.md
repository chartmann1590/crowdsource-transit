# Phase 1: Foundation & Infrastructure

> **Goal:** Create the GitHub repository, Firebase project, and scaffold all project directories so every subsequent phase has a working baseline to build on.

---

## Overview

This phase establishes everything that nothing else can start without:
- GitHub repo with proper branch protection and secrets
- Firebase project with Auth, Realtime Database, and Hosting configured
- Android project scaffold (Kotlin + Compose, Gradle, dependencies)
- Web project scaffold (Vite + React + TypeScript)
- CI/CD pipelines (GitHub Actions for web deploy + Android CI)
- Firebase security rules (open for dev, locked for production)

By the end of this phase, `git push` deploys the web app to GitHub Pages and Firebase Hosting. The Android app compiles and runs on an emulator showing a placeholder screen.

---

## Step 1: Create GitHub Repository

```bash
cd h:\crowdsource-transit

# Initialize git
git init
git branch -M main

# Create the repo on GitHub (public)
gh repo create crowdsource-transit \
  --public \
  --description "Community-powered crowdsourced public transit locator — Android + Web" \
  --source . \
  --remote origin

# Create .gitignore
cat > .gitignore << 'EOF'
# Android
android/.gradle/
android/local.properties
android/app/build/
android/build/
*.keystore
*.jks

# Web
web/node_modules/
web/dist/
web/.env
web/.env.local

# Firebase
.firebase/
firebase-debug.log

# Scripts
scripts/node_modules/
scripts/.env

# Google Services
android/app/google-services.json
web/src/firebase-config.ts

# OS
.DS_Store
Thumbs.db
*.log

# IDE
.idea/
*.iml
.vscode/
EOF

git add .gitignore
git commit -m "chore: initialize repository with .gitignore"
git push -u origin main
```

---

## Step 2: Create Firebase Project

```bash
# Login to Firebase (if not already)
firebase login

# Create new Firebase project
firebase projects:create crowdtransit-app --display-name "CrowdTransit"

# Set as default project
firebase use crowdtransit-app

# Initialize Firebase features
firebase init
```

**During `firebase init`, select:**
- [x] Realtime Database
- [x] Hosting: Configure files for Firebase Hosting
- [x] Emulators (Database, Auth, Hosting)

**Hosting answers:**
- Public directory: `web/dist`
- Single-page app: Yes
- Auto-deploys with GitHub: No (we'll use GitHub Actions)

This creates:
- `firebase.json`
- `.firebaserc`
- `database.rules.json`

---

## Step 3: Enable Firebase Services in Console

After creating the project, enable these via Firebase Console (https://console.firebase.google.com/project/crowdtransit-app):

**Authentication → Sign-in method → Enable:**
1. Anonymous
2. Google (add your support email)
3. Email/Password

**Realtime Database → Create Database:**
- Location: `us-central1`
- Start in test mode (we'll lock it down in Phase 3)

**Hosting:**
- Already configured by `firebase init`

---

## Step 4: Configure `firebase.json`

```json
{
  "database": {
    "rules": "firebase/database.rules.json"
  },
  "hosting": {
    "public": "web/dist",
    "ignore": [
      "firebase.json",
      "**/.*",
      "**/node_modules/**"
    ],
    "rewrites": [
      {
        "source": "**",
        "destination": "/index.html"
      }
    ],
    "headers": [
      {
        "source": "**/*.@(js|css)",
        "headers": [
          {
            "key": "Cache-Control",
            "value": "max-age=31536000"
          }
        ]
      }
    ]
  },
  "emulators": {
    "auth": { "port": 9099 },
    "database": { "port": 9000 },
    "hosting": { "port": 5000 },
    "ui": { "enabled": true }
  }
}
```

Save to `firebase.json` in the project root.

---

## Step 5: Move Firebase Config Files

```bash
mkdir -p firebase
mv database.rules.json firebase/database.rules.json
```

Edit `firebase.json` to point to `firebase/database.rules.json`.

Initial `firebase/database.rules.json` (development — open):
```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

**NOTE:** This is for development only. Phase 3 replaces this with proper rules.

---

## Step 6: Scaffold Web App (Vite + React + TypeScript)

```bash
cd h:\crowdsource-transit

# Create Vite React TypeScript project
npm create vite@latest web -- --template react-ts
cd web

# Install dependencies
npm install

# Install Firebase JS SDK
npm install firebase

# Install MapLibre GL JS
npm install maplibre-gl

# Install additional utilities
npm install @maplibre/maplibre-gl-geocoder
npm install react-router-dom
npm install @types/react-router-dom

# Return to root
cd ..
```

**Create `web/src/firebase/config.ts`** (uses environment variables — never commit actual values):
```typescript
import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getDatabase } from 'firebase/database';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  databaseURL: import.meta.env.VITE_FIREBASE_DATABASE_URL,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const database = getDatabase(app);
```

**Create `web/.env.example`** (commit this — no secrets):
```
VITE_FIREBASE_API_KEY=your-api-key-here
VITE_FIREBASE_AUTH_DOMAIN=crowdtransit-app.firebaseapp.com
VITE_FIREBASE_DATABASE_URL=https://crowdtransit-app-default-rtdb.firebaseio.com
VITE_FIREBASE_PROJECT_ID=crowdtransit-app
VITE_FIREBASE_STORAGE_BUCKET=crowdtransit-app.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=your-sender-id
VITE_FIREBASE_APP_ID=your-app-id
```

Create `web/.env.local` from the real Firebase Console values (never commit this file — it's in .gitignore).

**Update `web/vite.config.ts`:**
```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
```

**Create placeholder `web/src/App.tsx`:**
```tsx
import './App.css'

function App() {
  return (
    <div className="app">
      <h1>CrowdTransit</h1>
      <p>Community-powered transit, coming soon.</p>
    </div>
  )
}

export default App
```

Test locally:
```bash
cd web
npm run dev
# Opens at http://localhost:5173
```

---

## Step 7: Scaffold Android App

Open Android Studio and create a new project:
- **Template:** Empty Activity (Jetpack Compose)
- **Name:** CrowdTransit
- **Package:** com.crowdtransit.app
- **Save location:** `h:\crowdsource-transit\android`
- **Language:** Kotlin
- **Min SDK:** API 24 (Android 7.0 — covers 95%+ of devices)
- **Build config:** Kotlin DSL (build.gradle.kts)

**After creation, edit `android/app/build.gradle.kts`** to add dependencies:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.crowdtransit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crowdtransit.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // MapLibre Native Android
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    // GeoFire for location queries
    implementation("com.firebase:geofire-android:3.2.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Coil (image loading, for user avatars)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

**Edit `android/build.gradle.kts`** (project-level):
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
```

---

## Step 8: Add Firebase to Android App

1. Go to Firebase Console → Project Settings → Add App → Android
2. Package name: `com.crowdtransit.app`
3. App nickname: `CrowdTransit Android`
4. Download `google-services.json`
5. Place in `android/app/google-services.json`
6. **Add to `.gitignore`** — this file is sensitive (it's already in our .gitignore)

Add Firebase config as a GitHub Secret instead:
```bash
# Base64 encode the google-services.json
cat android/app/google-services.json | base64 -w 0

# Copy the output and add as GitHub secret
gh secret set GOOGLE_SERVICES_JSON --body "$(cat android/app/google-services.json | base64 -w 0)"
```

---

## Step 9: Add Firebase Web Config as GitHub Secrets

Get your Firebase web config from Firebase Console → Project Settings → Your apps → Web:

```bash
gh secret set VITE_FIREBASE_API_KEY --body "AIza..."
gh secret set VITE_FIREBASE_AUTH_DOMAIN --body "crowdtransit-app.firebaseapp.com"
gh secret set VITE_FIREBASE_DATABASE_URL --body "https://crowdtransit-app-default-rtdb.firebaseio.com"
gh secret set VITE_FIREBASE_PROJECT_ID --body "crowdtransit-app"
gh secret set VITE_FIREBASE_STORAGE_BUCKET --body "crowdtransit-app.appspot.com"
gh secret set VITE_FIREBASE_MESSAGING_SENDER_ID --body "123456789"
gh secret set VITE_FIREBASE_APP_ID --body "1:123:web:abc"
gh secret set FIREBASE_TOKEN --body "$(firebase login:ci)"
```

---

## Step 10: GitHub Actions — Web Deploy to GitHub Pages

Create `.github/workflows/deploy-web.yml`:

```yaml
name: Deploy Web to GitHub Pages

on:
  push:
    branches: [main]
    paths:
      - 'web/**'
      - '.github/workflows/deploy-web.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: "pages"
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: web/package-lock.json

      - name: Install dependencies
        run: cd web && npm ci

      - name: Build
        run: cd web && npm run build
        env:
          VITE_FIREBASE_API_KEY: ${{ secrets.VITE_FIREBASE_API_KEY }}
          VITE_FIREBASE_AUTH_DOMAIN: ${{ secrets.VITE_FIREBASE_AUTH_DOMAIN }}
          VITE_FIREBASE_DATABASE_URL: ${{ secrets.VITE_FIREBASE_DATABASE_URL }}
          VITE_FIREBASE_PROJECT_ID: ${{ secrets.VITE_FIREBASE_PROJECT_ID }}
          VITE_FIREBASE_STORAGE_BUCKET: ${{ secrets.VITE_FIREBASE_STORAGE_BUCKET }}
          VITE_FIREBASE_MESSAGING_SENDER_ID: ${{ secrets.VITE_FIREBASE_MESSAGING_SENDER_ID }}
          VITE_FIREBASE_APP_ID: ${{ secrets.VITE_FIREBASE_APP_ID }}

      - uses: actions/upload-pages-artifact@v3
        with:
          path: web/dist

  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/deploy-pages@v4
        id: deployment
```

**Enable GitHub Pages:**
```bash
gh api repos/chartmann1590/crowdsource-transit/pages \
  --method POST \
  --field source='{"branch":"gh-pages","path":"/"}' 2>/dev/null || true

# Use GitHub Pages via Actions (modern way)
gh repo edit crowdsource-transit --enable-pages --pages-source-branch main 2>/dev/null || true
```

Actually use the workflow-based approach by going to GitHub → Settings → Pages → Source: GitHub Actions.

---

## Step 11: GitHub Actions — Android CI

Create `.github/workflows/android-ci.yml`:

```yaml
name: Android CI

on:
  push:
    branches: [main]
    paths:
      - 'android/**'
      - '.github/workflows/android-ci.yml'
  pull_request:
    branches: [main]
    paths:
      - 'android/**'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Decode google-services.json
        run: |
          echo "${{ secrets.GOOGLE_SERVICES_JSON }}" | base64 --decode > android/app/google-services.json

      - name: Grant execute permission to gradlew
        run: chmod +x android/gradlew

      - name: Build debug APK
        run: cd android && ./gradlew assembleDebug

      - name: Run unit tests
        run: cd android && ./gradlew test

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 12: Create scripts/ Folder

```bash
mkdir -p scripts
cd scripts
npm init -y
npm install firebase-admin
npm install node-fetch
npm install csv-parser
npm install adm-zip
npm install dotenv
cd ..
```

Create `scripts/.env.example`:
```
FIREBASE_DATABASE_URL=https://crowdtransit-app-default-rtdb.firebaseio.com
FIREBASE_PROJECT_ID=crowdtransit-app
GOOGLE_APPLICATION_CREDENTIALS=./service-account.json
OPENROUTESERVICE_API_KEY=your-key-here
TRANSITLAND_API_KEY=your-key-here
```

---

## Step 13: Initial Commit and Push

```bash
cd h:\crowdsource-transit

git add -A
git commit -m "feat: scaffold Android + web projects with Firebase integration

- Vite + React + TypeScript web app
- Kotlin + Compose Android project with all dependencies
- Firebase config (Realtime DB, Auth, Hosting)
- GitHub Actions: web deploy to Pages, Android CI
- GTFS import scripts scaffold
- Development database rules (open)"

git push origin main
```

---

## Verification

- [ ] GitHub repo exists at `https://github.com/chartmann1590/crowdsource-transit`
- [ ] Firebase project `crowdtransit-app` exists in Firebase Console
- [ ] Firebase Auth has Anonymous, Google, Email/Password enabled
- [ ] Firebase Realtime Database created and accessible
- [ ] `web/` builds with `npm run build` locally
- [ ] `android/` builds with `./gradlew assembleDebug` locally
- [ ] Pushing to main triggers both GitHub Actions workflows
- [ ] GitHub Pages shows the placeholder web app
- [ ] All Firebase secrets stored in GitHub repo secrets
