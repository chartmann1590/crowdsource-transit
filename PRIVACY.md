# Privacy Policy

**Last updated:** August 7, 2026

## Overview

CrowdTransit is a community-powered public transit locator. This policy explains what data we collect, why we collect it, and how it is handled.

## Data We Collect

### Information You Provide
- **Account data** – If you sign up (email/password or Google), we store your email address, display name, and profile photo via Firebase Authentication.
- **Ratings & reviews** – Ratings and comments you submit about transit stops.
- **Crowdsourced stops** – Location and details of stops you add.
- **Check-ins / activity** – When you check in at a stop or report route status.
- **Photos** – Photos you upload of transit stops.

### Information Collected Automatically
- **Location** – With your permission, we access your device's coarse/fine location to show nearby stops. Location data is used only on-device and is not stored on our servers unless you explicitly submit it (e.g., adding a stop).
- **Location during turn-by-turn navigation** – When you start live navigation for a planned trip, the app continues reading your precise location while the app is in the foreground and while a persistent "navigating" notification is shown, so it can advance you through walking and transit legs, warn you when to get off, and detect if you've gone off route. This location use is shown to you in an in-app disclosure before navigation starts, runs only for the duration of an active navigation session, is never stored on our servers, and stops immediately when you end navigation or the session completes. We do not collect or request background location access.
- **Crash reports** – Firebase Crashlytics collects crash logs and device information (OS version, device model) to help us fix bugs.
- **Performance data** – Firebase Performance Monitoring collects app responsiveness and latency metrics.
- **Usage data** – Firebase Analytics collects anonymized interaction data to improve the app.
- **AI Assistant ("Hopper")** – Hopper is an optional, opt-in on-device AI assistant. If you enable it, an AI model (Gemma, provided by Google) is downloaded to your device and runs entirely locally. Your messages to Hopper, and Hopper's replies, are processed on-device and are **never transmitted to us, to Google, or to any other server** — the feature works even in airplane mode once the model is downloaded. Chat history is stored only in the app's local database on your device and is never synced to Firebase or anywhere else; you can clear it at any time from Settings. If your device supports it and you choose the camera-enabled version, photos you take to "scan a stop sign" are processed on-device and deleted immediately after Hopper responds — they are never saved or uploaded. Hopper can make mistakes; always verify times and route information with your transit agency.

### Permissions
- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) – Required to show nearby transit stops and, when you start it, to run live turn-by-turn navigation.
- **Camera** – Optional, used only when uploading a photo of a stop.
- **Foreground service / notifications** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`) – Used while you have an active turn-by-turn navigation session, and separately while downloading the optional Hopper AI model, so each can run reliably and show a progress notification. No background location access is requested.
- **Camera (Hopper, optional)** – If you enable Hopper's camera-capable variant on a supported device, the camera may be used to let Hopper read a stop or station sign on request. Images are processed on-device and deleted immediately afterward.

## How We Use Data

- To provide nearby transit stop information
- To display community ratings, reviews, and photos
- To verify and publish crowdsourced stop additions
- To improve app stability and performance
- To detect and prevent abuse

## Data Storage & Sharing

- **Firebase (Google)** – All user-generated data (ratings, comments, photos, activity) is stored in Firebase Realtime Database. Firebase Authentication manages account credentials. Trips you explicitly choose to save are stored the same way, scoped to your account. See [Google's Privacy Policy](https://policies.google.com/privacy).
- **Transitland API** – We fetch GTFS transit data (stops, routes, schedules) from the Transitland API, including when planning a trip. No personal data is sent to Transitland.
- **OpenRouteService (via our own Cloudflare Worker proxy)** – To draw walking directions for a planned trip, the app sends only the walking leg's coordinates (no account or device identifiers) through a CrowdTransit-operated proxy to OpenRouteService. See [openrouteservice.org](https://openrouteservice.org/) and [Cloudflare's Privacy Policy](https://www.cloudflare.com/privacypolicy/).
- **Shared trip links** – When you copy a trip's share link or plain-text directions, the itinerary is encoded directly into that link/text — no separate copy is stored or transmitted to us unless you also choose to save the trip to your account.
- **AdMob** – This app displays banner and interstitial ads served by Google AdMob. AdMob may collect advertising identifiers and other device information to serve and measure ads. See [Google's Privacy Policy](https://policies.google.com/privacy) and [How Google uses information from sites or apps that use our services](https://policies.google.com/technologies/partner-sites).
- **Google Play Billing** – The optional Remove Ads subscription is purchased and managed entirely through Google Play Billing. We never see or store your payment details — Google handles the transaction and only tells the app whether an active subscription exists, so ads can be turned off for that device/account. See [Google Play's Privacy Policy](https://policies.google.com/privacy) and [Play Billing terms](https://play.google/play-billing-pricing/).
- **Hopper AI model download** – If you enable Hopper, the AI model file (an open-weight Gemma model, licensed Apache 2.0) is downloaded directly from Hugging Face's servers to your device. That download request is the only network activity involved in setting up Hopper; no account or device identifiers beyond what any file download requires are sent, and once downloaded, Hopper needs no further network access to function.

We do **not** sell, rent, or share your personal data with third parties for their own marketing purposes.

## Data Retention

User-generated content (ratings, comments, photos, activity) remains visible until you delete it or request account deletion. Crash logs are retained per Firebase Crashlytics retention policy (typically 90 days).

## Your Rights & Choices

- **Anonymous browsing** – You can use the app without an account. Most features (map, stop details, schedules) are available anonymously.
- **Account deletion** – Submit a request on our <a href="https://chartmann1590.github.io/crowdsource-transit/account-deletion.html" target="_blank">Account Deletion page</a>, or email us at the address below.
- **Location** – You can deny or revoke location permission at any time via your device settings.
- **Opt out of analytics** – You can disable analytics sharing via your device settings (Android: Settings > Privacy > Ads).
- **Hopper AI Assistant** – Hopper is entirely optional. You can decline it during onboarding, and enable, disable, or delete it (and its downloaded model and chat history) at any time from Settings > AI Assistant.

## Children's Privacy

CrowdTransit is rated **Everyone** and does not knowingly collect data from children under 13. If we learn that a child under 13 has provided personal data, we will delete it.

## Changes to This Policy

We may update this policy. Changes will be posted here with an updated "Last updated" date.

## Contact

For questions or data deletion requests:  
**Email:** (add support email here)  
**GitHub:** [github.com/chartmann1590/crowdsource-transit](https://github.com/chartmann1590/crowdsource-transit)
