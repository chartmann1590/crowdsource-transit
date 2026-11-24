# CrowdTransit - Play Console Data Safety answers

Prepared 2026-11-23 for the resubmission of com.charles.crowdtransit.app.
Source of truth: PRIVACY.md and android/app/build.gradle.kts (Firebase Auth /
Realtime Database / Analytics / Crashlytics / Performance, AdMob 25.5.0,
Play Billing 9.1.0, Play Services Location, LiteRT-LM on-device AI).

## Basics

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
  (HTTPS/TLS for Firebase, Transitland, the OpenRouteService proxy, AdMob.)
- Account creation methods: **Username and password** (email + password) and
  **OAuth** (Google). The app also starts an anonymous guest session
  automatically; no personal data is attached to it.
- Account deletion URL:
  https://chartmann1590.github.io/crowdsource-transit/account-deletion.html
  (In-app deletion is also available: Profile > Delete Account.)
- Do you provide a way for users to request that their data be deleted
  without deleting their account? **Yes** - the same deletion page / email.
- Privacy policy URL:
  https://github.com/chartmann1590/crowdsource-transit/blob/main/PRIVACY.md

## Data types

| Data type | Collected | Shared | Ephemeral | Optional / Required | Purposes |
|---|---|---|---|---|---|
| Name | Yes | No | No | Optional | App functionality, Account management |
| Email address | Yes | No | No | Optional | App functionality, Account management |
| User IDs | Yes | No | No | Optional | App functionality, Account management |
| Precise location | Yes | Yes | No | Optional | App functionality |
| Approximate location | Yes | Yes | No | Optional | App functionality |
| In-app search history | Yes | Yes | No | Optional | App functionality |
| Purchase history | Yes | No | No | Optional | App functionality |
| Photos | Yes | No | No | Optional | App functionality |
| Other user-generated content | Yes | No | No | Optional | App functionality |
| App interactions | Yes | No | No | Required | Analytics |
| Crash logs | Yes | No | No | Required | Analytics |
| Diagnostics | Yes | No | No | Required | Analytics |
| Device or other IDs | Yes | Yes | No | Required | Analytics, Advertising or marketing |

## Notes behind the answers

- **Location "shared":** stop searches send the search area to the
  Transitland API, and walking directions send the leg's coordinates to
  OpenRouteService through the app's own Cloudflare proxy - both solely to
  provide the feature the user asked for. Turn-by-turn navigation location
  is used on-device only and is never stored or sent anywhere.
- **Photos / other UGC** (reviews, ratings, check-ins, crowdsourced stops,
  saved trips) are stored in Firebase Realtime Database, a service provider,
  so they are not "shared"; they are visible to other riders inside the app,
  which is the feature itself.
- **Device or other IDs shared:** the advertising ID goes to Google AdMob
  for ads and ad measurement. Users with an active Remove Ads subscription
  never trigger an ad request, so no advertising ID is collected for them.
- **Purchase history:** Play Billing reports whether the Remove Ads
  subscription is active; the app never sees payment details.
- **Hopper AI assistant:** fully on-device (LiteRT-LM). Chats and scanned
  signs are never collected or transmitted, so no data type is declared
  for it. The one-time model download comes from Hugging Face like any
  file download.