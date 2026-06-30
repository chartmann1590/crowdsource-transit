const { db } = require('./lib/firebase-admin');
const { downloadGtfsZip, parseGtfsZip } = require('./lib/gtfs-parser');
const { transformAgency, transformStop, transformRoute } = require('./lib/gtfs-transformer');

const BATCH_SIZE = 500;

async function importAgency(agencyConfig) {
  const {
    agencyId,
    feedUrl,
    metadata,
  } = agencyConfig;

  console.log(`\n=== Importing ${agencyId} ===`);
  console.log(`  Feed URL: ${feedUrl}`);

  let zipBuffer;
  try {
    zipBuffer = await downloadGtfsZip(feedUrl);
    console.log(`  Downloaded ${(zipBuffer.length / 1024 / 1024).toFixed(1)} MB`);
  } catch (err) {
    console.error(`  ERROR downloading ${agencyId}: ${err.message}`);
    return { success: false, agencyId, error: err.message };
  }

  let gtfsData;
  try {
    gtfsData = await parseGtfsZip(zipBuffer);
  } catch (err) {
    console.error(`  ERROR parsing ${agencyId}: ${err.message}`);
    return { success: false, agencyId, error: err.message };
  }

  const { agency, stops, routes } = gtfsData;
  console.log(`  Found: ${agency.length} agencies, ${stops.length} stops, ${routes.length} routes`);

  const agencyData = agency[0]
    ? transformAgency(agency[0], agencyId, { ...metadata, feedUrl })
    : null;

  if (agencyData) {
    await db.ref(`agencies/${agencyId}`).set(agencyData);
    console.log(`  ✓ Agency written: ${agencyData.name}`);
  }

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

if (require.main === module) {
  const agencyId = process.argv[2];
  if (!agencyId) {
    console.error('Usage: node import-agency.js <agencyId>');
    console.error('Available agencies can be found in import-all.js');
    process.exit(1);
  }

  const { PRIORITY_AGENCIES } = require('./import-all');
  const config = PRIORITY_AGENCIES.find(a => a.agencyId === agencyId);
  if (!config) {
    console.error(`Unknown agency: ${agencyId}`);
    process.exit(1);
  }

  importAgency(config).then(result => {
    if (result.success) {
      console.log(`\nImport complete: ${result.stopCount} stops, ${result.routeCount} routes`);
    } else {
      console.error(`\nImport failed: ${result.error}`);
      process.exit(1);
    }
  }).catch(err => {
    console.error('Import failed:', err);
    process.exit(1);
  });
}

module.exports = { importAgency };
