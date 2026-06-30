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
    feedUrl: 'https://storage.googleapis.com/storage/v1/b/mdb-latest/o/us-district-of-columbia-washington-wmata-gtfs-1847.zip?alt=media',
    metadata: {
      country: 'US', state: 'DC', city: 'Washington',
      lat: 38.9072, lng: -77.0369,
      transitTypes: ['subway', 'bus'],
    },
  },
  {
    agencyId: 'sfmta',
    feedUrl: 'https://data.sfgov.org/download/dni7-qpv3/application/x-zip-compressed',
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
    feedUrl: 'https://metro.kingcounty.gov/GTFS/google_transit.zip',
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

  for (const agency of PRIORITY_AGENCIES) {
    const result = await importAgency(agency);
    results.push(result);

    await new Promise(resolve => setTimeout(resolve, 2000));
  }

  await db.ref('meta/gtfsImportStatus').set('complete');
  await db.ref('meta/lastGtfsUpdate').set(Date.now());

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

if (require.main === module) {
  importAll().catch(err => {
    console.error('Import failed:', err);
    process.exit(1);
  });
}

module.exports = { importAll, PRIORITY_AGENCIES };
