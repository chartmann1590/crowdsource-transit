const AdmZip = require('adm-zip');
const csv = require('csv-parser');
const { Readable } = require('stream');

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

function parseCsvString(csvString) {
  return new Promise((resolve, reject) => {
    const results = [];
    const stream = Readable.from([csvString]);
    stream
      .pipe(csv({
        mapHeaders: ({ header }) => header.trim().replace(/^\uFEFF/, '')
      }))
      .on('data', (data) => results.push(data))
      .on('end', () => resolve(results))
      .on('error', reject);
  });
}

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
