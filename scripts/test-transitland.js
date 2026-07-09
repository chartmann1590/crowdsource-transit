const https = require('https');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '.env') });

function test() {
  const apiKey = process.env.TRANSITLAND_API_KEY;
  if (!apiKey) {
    console.error('Missing TRANSITLAND_API_KEY in scripts/.env');
    process.exit(1);
  }
  const url = `https://api.transit.land/api/v2/rest/stops/s-dred9eryky-albanyny?apikey=${apiKey}`;
  console.log('Fetching:', url.replace(apiKey, '***'));
  
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
