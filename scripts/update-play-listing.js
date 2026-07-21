const fs = require('fs');
const path = require('path');
const { google } = require('googleapis');

const PACKAGE_NAME = 'com.charles.crowdtransit.app';
const LANGUAGE = 'en-US';
const LISTING_DIR = path.join(__dirname, '..', 'play-store', 'listing');

function readListingFile(name) {
  return fs.readFileSync(path.join(LISTING_DIR, name), 'utf8').trim();
}

function readOptionalListingFile(name) {
  const filePath = path.join(LISTING_DIR, name);
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, 'utf8').trim() : undefined;
}

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

  const title = readListingFile('app-title.txt');
  const shortDescription = readListingFile('short-description.txt');
  const fullDescription = readListingFile('full-description.txt');
  const video = readOptionalListingFile('video-url.txt');

  const edit = await publisher.edits.insert({ packageName: PACKAGE_NAME });
  const editId = edit.data.id;
  console.log(`Created edit ${editId}`);

  try {
    await publisher.edits.listings.update({
      packageName: PACKAGE_NAME,
      editId,
      language: LANGUAGE,
      requestBody: {
        language: LANGUAGE,
        title,
        shortDescription,
        fullDescription,
        ...(video ? { video } : {}),
      },
    });
    console.log(`Updated ${LANGUAGE} listing (title/shortDescription/fullDescription${video ? '/video' : ''})`);

    await publisher.edits.commit({
      packageName: PACKAGE_NAME,
      editId,
    });
    console.log(`Committed edit ${editId}`);
  } catch (err) {
    await publisher.edits.delete({ packageName: PACKAGE_NAME, editId }).catch(() => {});
    throw err;
  }
}

main().catch((err) => {
  console.error('Failed:', err.response?.data || err.message);
  process.exit(1);
});
