const https = require('https');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '.env') });

function test() {
  const apiKey = process.env.TRANSITLAND_API_KEY;
  if (!apiKey) {
    console.error('Missing TRANSITLAND_API_KEY in scripts/.env');
    process.exit(1);
  }
  const endpoint = '/api/v2/rest/stops/s-dred9eryky-albanyny';
  const url = `https://api.transit.land${endpoint}?apikey=${apiKey}`;
  console.log('Fetching:', `https://api.transit.land${endpoint}?apikey=***`);
  
  https.get(url, (res) => {
    console.log('Status:', res.statusCode, res.statusMessage);
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
      console.log('Response:', data.slice(0, 1500));
    });
  }).on('error', (err) => {
    console.error('Error:', err.message);
  });
}

test();
