const fs = require('fs');
const path = require('path');
const { google } = require('googleapis');

const PACKAGE_NAME = 'com.charles.crowdtransit.app';
const PRODUCT_ID = 'remove_ads_monthly';
const BASE_PLAN_ID = 'monthly-autorenewing';

async function main() {
  const keyPath = path.join(__dirname, 'service-account.json');
  const credentials = JSON.parse(fs.readFileSync(keyPath, 'utf8'));

  const auth = new google.auth.GoogleAuth({
    credentials,
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });

  const publisher = google.androidpublisher({ version: 'v3', auth });

  // Base plan starts DRAFT on purpose: the product exists and is fully configured, but
  // nothing can actually be purchased until a human flips it to ACTIVE in Play Console —
  // a deliberate safety margin for a real-money product created via automation.
  const requestBody = {
    packageName: PACKAGE_NAME,
    productId: PRODUCT_ID,
    basePlans: [
      {
        basePlanId: BASE_PLAN_ID,
        // state is output-only — new base plans always start DRAFT (per the API docs:
        // "Newly added base plans will remain in draft state until activated"), which is
        // exactly the safety margin wanted here: nothing purchasable until a human
        // activates it in Play Console.
        autoRenewingBasePlanType: {
          billingPeriodDuration: 'P1M',
          gracePeriodDuration: 'P3D',
          resubscribeState: 'RESUBSCRIBE_STATE_ACTIVE',
        },
        regionalConfigs: [
          {
            regionCode: 'US',
            price: { currencyCode: 'USD', units: '1', nanos: 990000000 },
            newSubscriberAvailability: true,
          },
        ],
      },
    ],
    listings: [
      {
        languageCode: 'en-US',
        title: 'Remove Ads',
        benefits: [
          'No banner ads on the map',
          'No interstitial ads when opening a stop',
        ],
        description: 'Go ad-free in CrowdTransit. Cancel anytime from Google Play.',
      },
    ],
  };

  try {
    const result = await publisher.monetization.subscriptions.create({
      packageName: PACKAGE_NAME,
      productId: PRODUCT_ID,
      'regionsVersion.version': '2022/02',
      requestBody,
    });
    console.log('Created subscription:');
    console.log(JSON.stringify(result.data, null, 2));
  } catch (err) {
    console.error('Failed:', JSON.stringify(err.response?.data || err.message, null, 2));
    process.exit(1);
  }
}

main();
