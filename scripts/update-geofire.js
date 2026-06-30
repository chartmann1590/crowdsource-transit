const { db } = require('./lib/firebase-admin');
const ngeohash = require('ngeohash');

const BATCH_SIZE = 500;

async function indexAllStops() {
  console.log('Indexing stops in GeoFire...');

  const stopsSnap = await db.ref('stops').once('value');
  const stops = stopsSnap.val() || {};
  const stopEntries = Object.entries(stops);

  console.log(`Indexing ${stopEntries.length} stops...`);

  let count = 0;

  for (let i = 0; i < stopEntries.length; i += BATCH_SIZE) {
    const batch = stopEntries.slice(i, i + BATCH_SIZE);
    const updates = {};

    for (const [stopId, stop] of batch) {
      if (stop.lat && stop.lng && stop.active !== false) {
        const geohash = ngeohash.encode(stop.lat, stop.lng, 9);
        updates[`geofire/stops/${stopId}`] = {
          g: geohash,
          l: [stop.lat, stop.lng],
        };
        count++;
      }
    }

    if (Object.keys(updates).length > 0) {
      await db.ref('/').update(updates);
    }
  }

  console.log(`GeoFire indexing complete: ${count} stops indexed`);
}

indexAllStops().catch(err => {
  console.error('GeoFire indexing failed:', err);
  process.exit(1);
});
