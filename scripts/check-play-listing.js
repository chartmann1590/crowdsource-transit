const { google } = require('googleapis');

const PACKAGE_NAME = 'com.charles.crowdtransit.app';

const IMAGE_TYPES = [
  'icon',
  'featureGraphic',
  'promoGraphic',
  'tvBanner',
  'phoneScreenshots',
  'sevenInchScreenshots',
  'tenInchScreenshots',
  'tvScreenshots',
  'wearScreenshots',
];

async function main() {
  const rawKey = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  if (!rawKey) {
    console.error('Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON env var');
    process.exit(1);
  }
  const credentials = JSON.parse(rawKey);

  const auth = new google.auth.GoogleAuth({
    credentials,
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });

  const publisher = google.androidpublisher({ version: 'v3', auth });

  const edit = await publisher.edits.insert({ packageName: PACKAGE_NAME });
  const editId = edit.data.id;
  console.log(`Created edit ${editId}\n`);

  try {
    const details = await publisher.edits.details.get({ packageName: PACKAGE_NAME, editId });
    console.log('=== App details ===');
    console.log(JSON.stringify(details.data, null, 2));

    const listingsRes = await publisher.edits.listings.list({ packageName: PACKAGE_NAME, editId });
    const listings = listingsRes.data.listings || [];
    console.log(`\n=== Store listings (${listings.length} language(s)) ===`);
    for (const listing of listings) {
      console.log(`\n--- ${listing.language} ---`);
      console.log('title:', listing.title);
      console.log('shortDescription:', listing.shortDescription);
      console.log('fullDescription length:', (listing.fullDescription || '').length, 'chars');
      console.log('video:', listing.video || '(none)');
    }

    console.log('\n=== Graphics/screenshots per listing language ===');
    for (const listing of listings) {
      console.log(`\n--- ${listing.language} ---`);
      for (const imageType of IMAGE_TYPES) {
        try {
          const imgRes = await publisher.edits.images.list({
            packageName: PACKAGE_NAME,
            editId,
            language: listing.language,
            imageType,
          });
          const count = (imgRes.data.images || []).length;
          console.log(`${imageType}: ${count} image(s)`);
        } catch (err) {
          console.log(`${imageType}: error (${err.message})`);
        }
      }
    }

    const tracksRes = await publisher.edits.tracks.list({ packageName: PACKAGE_NAME, editId });
    console.log('\n=== Release tracks ===');
    console.log(JSON.stringify(tracksRes.data.tracks, null, 2));
  } finally {
    await publisher.edits.delete({ packageName: PACKAGE_NAME, editId }).catch(() => {});
  }
}

main().catch((err) => {
  console.error('Failed:', err.response?.data || err.message);
  process.exit(1);
});
