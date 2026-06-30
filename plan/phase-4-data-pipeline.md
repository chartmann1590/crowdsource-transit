# Phase 4: Data Pipeline — GTFS Import

> **Goal:** Download free GTFS transit data from the Mobility Database, parse it, and import stops/routes/agencies into Firebase RTDB. Start with major US agencies, then expand. Create a repeatable, updateable pipeline.

---

## Overview

GTFS (General Transit Feed Specification) is the universal standard for public transit data. Every major transit agency in the world publishes a GTFS zip file containing CSV files describing stops, routes, trips, and schedules.

**Sources:**
- **Mobility Database**: https://mobilitydatabase.org — catalog of 6000+ GTFS feeds worldwide
- **Direct agency feeds** for major US cities (more reliable/timely)
- **Transitland API** (optional enrichment): https://www.transit.land/

**Pipeline:**
1. Download Mobility Database catalog (CSV listing all known feeds)
2. Filter for target agencies
3. Download each agency's GTFS zip
4. Parse GTFS CSV files (stops.txt, routes.txt, agency.txt, trips.txt, stop_times.txt)
5. Transform data to our schema
6. Import to Firebase RTDB
7. Index with GeoFire

---

## Priority US Agencies (Phase 4 Target — 30 agencies)

| Agency | City | State | GTFS Feed |
|--------|------|-------|-----------|
| MTA New York City Transit | New York | NY | http://web.mta.info/developers/data/nyct/subway/google_transit.zip |
| MTA Bus | New York | NY | http://web.mta.info/developers/data/bus/google_transit_bronx.zip |
| WMATA | Washington DC | DC | https://api.wmata.com/gtfs/rail-gtfs-static.zip |
| SFMTA | San Francisco | CA | https://www.sfmta.com/sites/default/files/reports-and-documents/2024/08/google_transit.zip |
| Chicago CTA | Chicago | IL | https://www.transitchicago.com/downloads/sch_data/google_transit.zip |
| LA Metro | Los Angeles | CA | https://gitlab.com/LACMTA/gtfs_rail/-/raw/master/gtfs_rail.zip |
| MBTA | Boston | MA | https://cdn.mbta.com/MBTA_GTFS.zip |
| SEPTA | Philadelphia | PA | https://www3.septa.org/developer/gtfs/google_rail.zip |
| BART | San Francisco Bay Area | CA | https://api.bart.gov/gtfs/google_transit.zip |
| Caltrain | Peninsula CA | CA | https://www.caltrain.com/media/googletransit/googletrains_google_transit.zip |
| King County Metro | Seattle | WA | https://kingcounty.gov/~/media/depts/metro/schedules/google/google_transit.zip |
| Sound Transit | Seattle | WA | https://www.soundtransit.org/sites/default/files/gtfs-next-departure-data-zip-file.zip |
| TriMet | Portland | OR | https://developer.trimet.org/schedule/gtfs.zip |
| RTD Denver | Denver | CO | https://www.rtd-denver.com/files/gtfs/google_transit.zip |
| DART | Dallas | TX | https://www.dart.org/transitdata/latest/google_transit.zip |
| Metro Houston | Houston | TX | https://ridemetro.org/Pages/DevelopersData.aspx |
| VTA | San Jose | CA | http://www.vta.org/getting-around/gtfs-data |
| AC Transit | Oakland | CA | https://api.actransit.org/transit/gtfs/download |
| Muni Metro | San Francisco | CA | (included in SFMTA feed) |
| NJ Transit | New Jersey | NJ | https://www.njtransit.com/developer-tools |
| MARC Train | Maryland | MD | https://feeds.mta.maryland.gov/gtfs/marc-train |
| SunTran | Albuquerque | NM | https://www.cabq.gov/transit/documents/gtfs.zip |
| Capital Metro | Austin | TX | https://data.texas.gov/ |
| Valley Metro | Phoenix | AZ | https://www.valleymetro.org/maps-schedules/stop-schedule/google-transit |
| Pace | Chicago Suburbs | IL | https://www.pacebus.com/about/gtfs-google-transit-feed-specification-data |
| Metra | Chicago | IL | https://transitfeeds.com/p/metra/134 |
| Miami-Dade Transit | Miami | FL | https://transitfeeds.com/p/miami-dade-county/435 |
| MARTA | Atlanta | GA | https://itsmarta.com/google-transit-data.aspx |
| Pittsburgh Port Authority | Pittsburgh | PA | https://www.portauthority.org/business-center/developer-resources/ |
| Metro St. Louis | St. Louis | MO | https://www.metrostlouis.org/developer-resources/ |

---

## File Structure

```
scripts/
├── package.json
├── .env
├── .env.example
├── service-account.json         # Firebase Admin SDK key (gitignored)
├── data/
│   ├── mobility-catalog.csv     # Downloaded from Mobility Database
│   └── gtfs/                    # Downloaded GTFS zips (temp, gitignored)
├── lib/
│   ├── firebase-admin.js        # Firebase Admin SDK setup
│   ├── gtfs-parser.js           # GTFS CSV parsing utilities
│   ├── gtfs-transformer.js      # GTFS → our schema transformer
│   └── geofire-indexer.js       # GeoFire indexing
├── fetch-mobility-catalog.js    # Download Mobility Database catalog
├── import-agency.js             # Import a single agency's GTFS data
├── import-all.js                # Import all priority agencies
├── update-geofire.js            # Re-index all stops in GeoFire
└── verify-import.js             # Verify data integrity post-import
```

---

## Step 1: Firebase Admin SDK Setup

Get service account key:
1. Firebase Console → Project Settings → Service Accounts
2. Click "Generate New Private Key"
3. Save as `scripts/service-account.json` (gitignored)

Create `scripts/lib/firebase-admin.js`:

```javascript
const { initializeApp, cert, getApps } = require('firebase-admin/app');
const { getDatabase } = require('firebase-admin/database');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

let app;
if (getApps().length === 0) {
  app = initializeApp({
    credential: cert(path.join(__dirname, '../service-account.json')),
    databaseURL: process.env.FIREBASE_DATABASE_URL,
  });
} else {
  app = getApps()[0];
}

const db = getDatabase(app);
module.exports = { db };
```

---

## Step 2: GTFS Parser

Create `scripts/lib/gtfs-parser.js`:

```javascript
const AdmZip = require('adm-zip');
const csv = require('csv-parser');
const { Readable } = require('stream');

/**
 * Parse a GTFS zip buffer and return all CSV files as arrays of objects.
 * @param {Buffer} zipBuffer
 * @returns {Promise<Object>} - { stops: [...], routes: [...], agency: [...], trips: [...] }
 */
async function parseGtfsZip(zipBuffer) {
  const zip = new AdmZip(zipBuffer);
  const result = {};
  
  const filesToParse = ['agency.txt', 'stops.txt', 'routes.txt', 'trips.txt', 'calendar.txt'];
  
  for (const filename of filesToParse) {
    const entry = zip.getEntry(filename);
    if (!entry) {
      console.warn(`  Warning: ${filename} not found in GTFS zip`);
      result[filename.replace('.txt', '')] = [];
      continue;
    }
    
    const content = entry.getData().toString('utf-8');
    result[filename.replace('.txt', '')] = await parseCsvString(content);
  }
  
  return result;
}

/**
 * Parse CSV string into array of objects.
 * @param {string} csvString
 * @returns {Promise<Array>}
 */
function parseCsvString(csvString) {
  return new Promise((resolve, reject) => {
    const results = [];
    const stream = Readable.from([csvString]);
    stream
      .pipe(csv({ 
        mapHeaders: ({ header }) => header.trim().replace(/^﻿/, '') // Remove BOM
      }))
      .on('data', (data) => results.push(data))
      .on('end', () => resolve(results))
      .on('error', reject);
  });
}

/**
 * Download a GTFS zip from a URL.
 * @param {string} url
 * @returns {Promise<Buffer>}
 */
async function downloadGtfsZip(url) {
  const fetch = (await import('node-fetch')).default;
  const response = await fetch(url, {
    headers: { 'User-Agent': 'CrowdTransit/1.0 (crowdtransit.app)' },
    timeout: 60000,
  });
  
  if (!response.ok) {
    throw new Error(`Failed to download GTFS: ${response.status} ${response.statusText} from ${url}`);
  }
  
  return Buffer.from(await response.arrayBuffer());
}

module.exports = { parseGtfsZip, parseCsvString, downloadGtfsZip };
```

---

## Step 3: GTFS Transformer

Create `scripts/lib/gtfs-transformer.js`:

```javascript
/**
 * GTFS route_type to our transit type mapping.
 */
const ROUTE_TYPE_MAP = {
  '0': 'tram',
  '1': 'subway',
  '2': 'train',
  '3': 'bus',
  '4': 'ferry',
  '5': 'cable_car',
  '6': 'tram',
  '7': 'funicular',
  '11': 'bus',
  '12': 'monorail',
  '100': 'train',
  '101': 'train',
  '102': 'train',
  '200': 'bus',
  '400': 'subway',
  '401': 'subway',
  '700': 'bus',
  '900': 'tram',
  '1000': 'ferry',
  '1100': 'train',
};

const TRANSIT_TYPE_COLORS = {
  bus: '#4CAF50',
  train: '#2196F3',
  subway: '#9C27B0',
  ferry: '#00BCD4',
  tram: '#FF9800',
  cable_car: '#795548',
  monorail: '#607D8B',
  funicular: '#E91E63',
};

/**
 * Sanitize a string to be a valid Firebase key.
 * Firebase keys cannot contain . $ # [ ] /
 */
function sanitizeKey(str) {
  return str
    .toLowerCase()
    .replace(/[.$#\[\]\/]/g, '_')
    .replace(/\s+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
}

/**
 * Transform GTFS agency data to our schema.
 */
function transformAgency(gtfsAgency, agencyId, metadata) {
  return {
    agencyId,
    name: gtfsAgency.agency_name || '',
    shortName: (gtfsAgency.agency_name || '').split(' ').slice(-2).join(' '),
    url: gtfsAgency.agency_url || '',
    timezone: gtfsAgency.agency_timezone || 'UTC',
    lang: gtfsAgency.agency_lang || 'en',
    phone: gtfsAgency.agency_phone || '',
    country: metadata.country || 'US',
    state: metadata.state || '',
    city: metadata.city || '',
    lat: metadata.lat || 0,
    lng: metadata.lng || 0,
    transitTypes: metadata.transitTypes || ['bus'],
    gtfsFeedUrl: metadata.feedUrl || '',
    lastUpdated: Date.now(),
    ratingSum: 0,
    ratingCount: 0,
    verified: true,
    active: true,
  };
}

/**
 * Transform GTFS stop data to our schema.
 */
function transformStop(gtfsStop, agencyId, metadata) {
  const lat = parseFloat(gtfsStop.stop_lat);
  const lng = parseFloat(gtfsStop.stop_lon);
  
  if (isNaN(lat) || isNaN(lng) || lat === 0 || lng === 0) {
    return null; // Skip stops without valid coordinates
  }
  
  const stopId = `${agencyId}_${sanitizeKey(gtfsStop.stop_id)}`;
  
  return {
    stopId,
    agencyId,
    gtfsStopId: gtfsStop.stop_id || '',
    name: (gtfsStop.stop_name || '').trim(),
    desc: (gtfsStop.stop_desc || '').trim(),
    lat,
    lng,
    code: gtfsStop.stop_code || '',
    url: gtfsStop.stop_url || '',
    locationType: parseInt(gtfsStop.location_type || '0'),
    wheelchairBoarding: parseInt(gtfsStop.wheelchair_boarding || '0'),
    country: metadata.country || 'US',
    state: metadata.state || '',
    city: metadata.city || '',
    timezone: gtfsStop.stop_timezone || metadata.timezone || 'UTC',
    transitTypes: metadata.transitTypes || ['bus'],
    routeIds: {},
    agencyIds: { [agencyId]: true },
    ratingSum: 0,
    ratingCount: 0,
    commentCount: 0,
    features: {
      shelter: false,
      bench: false,
      lighting: false,
      elevator: false,
      escalator: false,
      ticketMachine: false,
      bikeParking: false,
      parking: false,
    },
    crowdsourced: false,
    verified: true,
    addedBy: null,
    addedAt: Date.now(),
    lastUpdated: Date.now(),
    active: true,
    reportCount: 0,
  };
}

/**
 * Transform GTFS route data to our schema.
 */
function transformRoute(gtfsRoute, agencyId, metadata) {
  const transitType = ROUTE_TYPE_MAP[gtfsRoute.route_type] || 'bus';
  const routeId = `${agencyId}_${sanitizeKey(gtfsRoute.route_id)}`;
  
  return {
    routeId,
    agencyId,
    gtfsRouteId: gtfsRoute.route_id || '',
    shortName: (gtfsRoute.route_short_name || '').trim(),
    longName: (gtfsRoute.route_long_name || '').trim(),
    desc: (gtfsRoute.route_desc || '').trim(),
    type: transitType,
    color: `#${gtfsRoute.route_color || TRANSIT_TYPE_COLORS[transitType].replace('#', '')}`,
    textColor: `#${gtfsRoute.route_text_color || 'FFFFFF'}`,
    url: gtfsRoute.route_url || '',
    country: metadata.country || 'US',
    state: metadata.state || '',
    city: metadata.city || '',
    agencyName: metadata.agencyName || '',
    stopCount: 0,
    ratingSum: 0,
    ratingCount: 0,
    lastUpdated: Date.now(),
    active: true,
  };
}

module.exports = {
  transformAgency,
  transformStop,
  transformRoute,
  sanitizeKey,
  ROUTE_TYPE_MAP,
  TRANSIT_TYPE_COLORS,
};
```

---

## Step 4: Agency Import Script

Create `scripts/import-agency.js`:

```javascript
const { db } = require('./lib/firebase-admin');
const { downloadGtfsZip, parseGtfsZip } = require('./lib/gtfs-parser');
const { transformAgency, transformStop, transformRoute } = require('./lib/gtfs-transformer');

const BATCH_SIZE = 500; // Write in batches to avoid RTDB timeouts

/**
 * Import a single transit agency's GTFS data into Firebase.
 * @param {Object} agencyConfig - Agency configuration object
 */
async function importAgency(agencyConfig) {
  const {
    agencyId,
    feedUrl,
    metadata, // country, state, city, lat, lng, transitTypes
  } = agencyConfig;

  console.log(`\n=== Importing ${agencyId} ===`);
  console.log(`  Feed URL: ${feedUrl}`);

  // 1. Download GTFS zip
  console.log('  Downloading GTFS zip...');
  let zipBuffer;
  try {
    zipBuffer = await downloadGtfsZip(feedUrl);
    console.log(`  Downloaded ${(zipBuffer.length / 1024 / 1024).toFixed(1)} MB`);
  } catch (err) {
    console.error(`  ERROR downloading ${agencyId}: ${err.message}`);
    return { success: false, agencyId, error: err.message };
  }

  // 2. Parse GTFS
  console.log('  Parsing GTFS CSV files...');
  let gtfsData;
  try {
    gtfsData = await parseGtfsZip(zipBuffer);
  } catch (err) {
    console.error(`  ERROR parsing ${agencyId}: ${err.message}`);
    return { success: false, agencyId, error: err.message };
  }

  const { agency, stops, routes } = gtfsData;
  console.log(`  Found: ${agency.length} agencies, ${stops.length} stops, ${routes.length} routes`);

  // 3. Transform agency
  const agencyData = agency[0]
    ? transformAgency(agency[0], agencyId, { ...metadata, feedUrl })
    : null;

  // 4. Write agency to Firebase
  if (agencyData) {
    await db.ref(`agencies/${agencyId}`).set(agencyData);
    console.log(`  ✓ Agency written: ${agencyData.name}`);
  }

  // 5. Transform and write stops in batches
  console.log(`  Writing ${stops.length} stops...`);
  let stopCount = 0;
  let skippedCount = 0;
  
  for (let i = 0; i < stops.length; i += BATCH_SIZE) {
    const batch = stops.slice(i, i + BATCH_SIZE);
    const updates = {};
    
    for (const gtfsStop of batch) {
      const stop = transformStop(gtfsStop, agencyId, metadata);
      if (stop) {
        updates[`stops/${stop.stopId}`] = stop;
        stopCount++;
      } else {
        skippedCount++;
      }
    }
    
    if (Object.keys(updates).length > 0) {
      await db.ref('/').update(updates);
    }
    
    console.log(`  Progress: ${Math.min(i + BATCH_SIZE, stops.length)}/${stops.length} stops processed`);
  }
  
  console.log(`  ✓ Stops: ${stopCount} written, ${skippedCount} skipped (no coords)`);

  // 6. Transform and write routes in batches
  console.log(`  Writing ${routes.length} routes...`);
  let routeCount = 0;
  
  for (let i = 0; i < routes.length; i += BATCH_SIZE) {
    const batch = routes.slice(i, i + BATCH_SIZE);
    const updates = {};
    
    for (const gtfsRoute of batch) {
      const route = transformRoute(gtfsRoute, agencyId, {
        ...metadata,
        agencyName: agencyData?.name || '',
      });
      updates[`routes/${route.routeId}`] = route;
      routeCount++;
    }
    
    await db.ref('/').update(updates);
  }
  
  console.log(`  ✓ Routes: ${routeCount} written`);

  // 7. Update meta counts
  const metaRef = db.ref('meta');
  const meta = (await metaRef.once('value')).val() || {};
  await metaRef.update({
    stopCount: (meta.stopCount || 0) + stopCount,
    routeCount: (meta.routeCount || 0) + routeCount,
    agencyCount: (meta.agencyCount || 0) + 1,
    lastGtfsUpdate: Date.now(),
  });

  return { success: true, agencyId, stopCount, routeCount };
}

module.exports = { importAgency };
```

---

## Step 5: Master Import Script

Create `scripts/import-all.js`:

```javascript
const { importAgency } = require('./import-agency');
const { db } = require('./lib/firebase-admin');

const PRIORITY_AGENCIES = [
  {
    agencyId: 'mta_nyc_subway',
    feedUrl: 'http://web.mta.info/developers/data/nyct/subway/google_transit.zip',
    metadata: {
      country: 'US', state: 'NY', city: 'New York',
      lat: 40.7128, lng: -74.0060,
      transitTypes: ['subway'],
    },
  },
  {
    agencyId: 'mta_nyc_bus_manhattan',
    feedUrl: 'http://web.mta.info/developers/data/nyct/bus/google_transit_manhattan.zip',
    metadata: {
      country: 'US', state: 'NY', city: 'New York',
      lat: 40.7831, lng: -73.9712,
      transitTypes: ['bus'],
    },
  },
  {
    agencyId: 'wmata',
    feedUrl: 'https://api.wmata.com/gtfs/rail-gtfs-static.zip',
    metadata: {
      country: 'US', state: 'DC', city: 'Washington',
      lat: 38.9072, lng: -77.0369,
      transitTypes: ['subway', 'bus'],
    },
  },
  {
    agencyId: 'sfmta',
    feedUrl: 'https://www.sfmta.com/sites/default/files/reports-and-documents/2024/08/google_transit.zip',
    metadata: {
      country: 'US', state: 'CA', city: 'San Francisco',
      lat: 37.7749, lng: -122.4194,
      transitTypes: ['bus', 'tram', 'subway'],
      timezone: 'America/Los_Angeles',
    },
  },
  {
    agencyId: 'cta',
    feedUrl: 'https://www.transitchicago.com/downloads/sch_data/google_transit.zip',
    metadata: {
      country: 'US', state: 'IL', city: 'Chicago',
      lat: 41.8827, lng: -87.6233,
      transitTypes: ['bus', 'subway', 'train'],
      timezone: 'America/Chicago',
    },
  },
  {
    agencyId: 'mbta',
    feedUrl: 'https://cdn.mbta.com/MBTA_GTFS.zip',
    metadata: {
      country: 'US', state: 'MA', city: 'Boston',
      lat: 42.3601, lng: -71.0589,
      transitTypes: ['bus', 'subway', 'train', 'ferry'],
      timezone: 'America/New_York',
    },
  },
  {
    agencyId: 'bart',
    feedUrl: 'https://api.bart.gov/gtfs/google_transit.zip',
    metadata: {
      country: 'US', state: 'CA', city: 'San Francisco Bay Area',
      lat: 37.8044, lng: -122.2712,
      transitTypes: ['train'],
      timezone: 'America/Los_Angeles',
    },
  },
  {
    agencyId: 'la_metro',
    feedUrl: 'https://gitlab.com/LACMTA/gtfs_bus/-/raw/master/gtfs_bus.zip',
    metadata: {
      country: 'US', state: 'CA', city: 'Los Angeles',
      lat: 34.0522, lng: -118.2437,
      transitTypes: ['bus', 'subway', 'train'],
      timezone: 'America/Los_Angeles',
    },
  },
  {
    agencyId: 'trimet',
    feedUrl: 'https://developer.trimet.org/schedule/gtfs.zip',
    metadata: {
      country: 'US', state: 'OR', city: 'Portland',
      lat: 45.5051, lng: -122.6750,
      transitTypes: ['bus', 'tram', 'train'],
      timezone: 'America/Los_Angeles',
    },
  },
  {
    agencyId: 'king_county_metro',
    feedUrl: 'https://kingcounty.gov/~/media/depts/metro/schedules/google/google_transit.zip',
    metadata: {
      country: 'US', state: 'WA', city: 'Seattle',
      lat: 47.6062, lng: -122.3321,
      transitTypes: ['bus'],
      timezone: 'America/Los_Angeles',
    },
  },
];

async function importAll() {
  console.log(`Starting import of ${PRIORITY_AGENCIES.length} priority agencies`);
  console.log(`Start time: ${new Date().toISOString()}`);
  
  // Initialize meta
  await db.ref('meta').set({
    version: '1.0.0',
    importStarted: Date.now(),
    agencyCount: 0,
    stopCount: 0,
    routeCount: 0,
    reviewCount: 0,
    userCount: 0,
    gtfsImportStatus: 'in_progress',
  });

  const results = [];
  
  // Import agencies sequentially to avoid rate limiting
  for (const agency of PRIORITY_AGENCIES) {
    const result = await importAgency(agency);
    results.push(result);
    
    // Small delay between agencies to be nice to servers
    await new Promise(resolve => setTimeout(resolve, 2000));
  }

  // Mark import complete
  await db.ref('meta/gtfsImportStatus').set('complete');
  await db.ref('meta/lastGtfsUpdate').set(Date.now());

  // Summary
  const successful = results.filter(r => r.success);
  const failed = results.filter(r => !r.success);
  
  console.log('\n=== Import Summary ===');
  console.log(`Successful: ${successful.length}/${results.length} agencies`);
  console.log(`Total stops: ${successful.reduce((sum, r) => sum + (r.stopCount || 0), 0)}`);
  console.log(`Total routes: ${successful.reduce((sum, r) => sum + (r.routeCount || 0), 0)}`);
  
  if (failed.length > 0) {
    console.log('\nFailed agencies:');
    failed.forEach(r => console.log(`  - ${r.agencyId}: ${r.error}`));
  }
  
  console.log(`\nEnd time: ${new Date().toISOString()}`);
}

importAll().catch(err => {
  console.error('Import failed:', err);
  process.exit(1);
});
```

---

## Step 6: GeoFire Indexer

Create `scripts/update-geofire.js`:

```javascript
const { db } = require('./lib/firebase-admin');
const GeoFire = require('geofire').GeoFire;

const BATCH_SIZE = 200;

async function indexAllStops() {
  console.log('Starting GeoFire indexing...');
  const geoFire = new GeoFire(db.ref('geofire/stops'));
  
  const stopsSnap = await db.ref('stops').once('value');
  const stops = stopsSnap.val() || {};
  const stopEntries = Object.entries(stops);
  
  console.log(`Indexing ${stopEntries.length} stops...`);
  
  let count = 0;
  
  for (let i = 0; i < stopEntries.length; i += BATCH_SIZE) {
    const batch = stopEntries.slice(i, i + BATCH_SIZE);
    const promises = batch
      .filter(([, stop]) => stop.lat && stop.lng && stop.active)
      .map(([stopId, stop]) => geoFire.set(stopId, [stop.lat, stop.lng]));
    
    await Promise.all(promises);
    count += promises.length;
    
    if (i % 2000 === 0) {
      console.log(`  Indexed ${count}/${stopEntries.length} stops...`);
    }
  }
  
  console.log(`GeoFire indexing complete: ${count} stops indexed`);
}

indexAllStops().catch(err => {
  console.error('GeoFire indexing failed:', err);
  process.exit(1);
});
```

---

## Step 7: Add package.json Scripts

Edit `scripts/package.json`:

```json
{
  "name": "crowdtransit-scripts",
  "version": "1.0.0",
  "scripts": {
    "import:all": "node import-all.js",
    "import:agency": "node import-agency.js",
    "index:geofire": "node update-geofire.js",
    "verify": "node verify-import.js"
  },
  "dependencies": {
    "firebase-admin": "^13.0.0",
    "node-fetch": "^3.3.2",
    "csv-parser": "^3.0.0",
    "adm-zip": "^0.5.16",
    "dotenv": "^16.4.7",
    "geofire": "^6.0.0"
  }
}
```

---

## Step 8: Run the Import

```bash
cd scripts

# Copy and fill in the .env
cp .env.example .env
# Edit .env with your Firebase credentials

# Run import (takes 20-60 minutes for 10 agencies)
node import-all.js

# After import, run GeoFire indexing
node update-geofire.js
```

---

## Step 9: Verify Import

Create `scripts/verify-import.js`:

```javascript
const { db } = require('./lib/firebase-admin');

async function verify() {
  const meta = (await db.ref('meta').once('value')).val();
  console.log('Meta:', JSON.stringify(meta, null, 2));
  
  // Check a sample stop
  const stopsSnap = await db.ref('stops').limitToFirst(5).once('value');
  const stops = stopsSnap.val();
  console.log('\nSample stops:');
  Object.values(stops || {}).forEach(stop => {
    console.log(`  - ${stop.name} (${stop.lat}, ${stop.lng}) [${stop.agencyId}]`);
  });
  
  // Check agencies
  const agenciesSnap = await db.ref('agencies').once('value');
  const agencies = agenciesSnap.val();
  console.log(`\nAgencies imported: ${Object.keys(agencies || {}).length}`);
  
  // Check GeoFire index
  const geofireSnap = await db.ref('geofire/stops').limitToFirst(3).once('value');
  console.log(`\nGeoFire sample: ${JSON.stringify(geofireSnap.val(), null, 2)}`);
}

verify().catch(console.error);
```

Run:
```bash
node verify-import.js
```

---

## Verification Checklist

- [ ] Service account JSON downloaded and placed at `scripts/service-account.json`
- [ ] `scripts/.env` filled with correct Firebase credentials
- [ ] `node import-all.js` completes without fatal errors
- [ ] Firebase Console shows data in `/stops`, `/routes`, `/agencies`
- [ ] At least 50,000 stops imported total
- [ ] At least 10 agencies imported
- [ ] GeoFire index at `/geofire/stops` is populated
- [ ] `node verify-import.js` shows correct counts
- [ ] Stop has `lat` and `lng` that match real-world location (spot check via Google Maps)
- [ ] Route has correct `type` (bus/train/subway) not just "bus" for everything
