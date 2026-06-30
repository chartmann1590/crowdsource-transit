const { importAgency } = require('./import-agency');
const { PRIORITY_AGENCIES } = require('./import-all');

const FAILED_IDS = ['wmata', 'sfmta', 'king_county_metro'];

async function reimportFailed() {
  console.log(`Re-importing ${FAILED_IDS.length} previously failed agencies...`);

  for (const agencyId of FAILED_IDS) {
    const config = PRIORITY_AGENCIES.find(a => a.agencyId === agencyId);
    if (!config) {
      console.error(`Config not found for ${agencyId}`);
      continue;
    }
    const result = await importAgency(config);
    if (result.success) {
      console.log(`  ✓ ${agencyId}: ${result.stopCount} stops, ${result.routeCount} routes`);
    } else {
      console.error(`  ✗ ${agencyId}: ${result.error}`);
    }
    await new Promise(resolve => setTimeout(resolve, 2000));
  }

  console.log('\nRe-import complete.');
}

reimportFailed().catch(err => {
  console.error('Re-import failed:', err);
  process.exit(1);
});
