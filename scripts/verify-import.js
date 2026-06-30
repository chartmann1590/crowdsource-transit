const { db } = require('./lib/firebase-admin');

async function verify() {
  const meta = (await db.ref('meta').once('value')).val();
  console.log('Meta:', JSON.stringify(meta, null, 2));

  const stopsSnap = await db.ref('stops').limitToFirst(5).once('value');
  const stops = stopsSnap.val();
  console.log('\nSample stops:');
  Object.values(stops || {}).forEach(stop => {
    console.log(`  - ${stop.name} (${stop.lat}, ${stop.lng}) [${stop.agencyId}]`);
  });

  const agenciesSnap = await db.ref('agencies').once('value');
  const agencies = agenciesSnap.val();
  console.log(`\nAgencies imported: ${Object.keys(agencies || {}).length}`);

  const geofireSnap = await db.ref('geofire/stops').limitToFirst(3).once('value');
  console.log(`\nGeoFire sample: ${JSON.stringify(geofireSnap.val(), null, 2)}`);
}

if (require.main === module) {
  verify().catch(err => {
    console.error('Verification failed:', err);
    process.exit(1);
  });
}

module.exports = { verify };
