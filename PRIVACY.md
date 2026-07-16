# Privacy Policy

**Last updated:** July 16, 2026

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
- **Crash reports** – Firebase Crashlytics collects crash logs and device information (OS version, device model) to help us fix bugs.
- **Performance data** – Firebase Performance Monitoring collects app responsiveness and latency metrics.
- **Usage data** – Firebase Analytics collects anonymized interaction data to improve the app.

### Permissions
- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) – Required to show nearby transit stops.
- **Camera** – Optional, used only when uploading a photo of a stop.

## How We Use Data

- To provide nearby transit stop information
- To display community ratings, reviews, and photos
- To verify and publish crowdsourced stop additions
- To improve app stability and performance
- To detect and prevent abuse

## Data Storage & Sharing

- **Firebase (Google)** – All user-generated data (ratings, comments, photos, activity) is stored in Firebase Realtime Database. Firebase Authentication manages account credentials. See [Google's Privacy Policy](https://policies.google.com/privacy).
- **Transitland API** – We fetch GTFS transit data from the Transitland API. No personal data is sent to Transitland.
- **AdMob** – This app displays banner and interstitial ads served by Google AdMob. AdMob may collect advertising identifiers and other device information to serve and measure ads. See [Google's Privacy Policy](https://policies.google.com/privacy) and [How Google uses information from sites or apps that use our services](https://policies.google.com/technologies/partner-sites).

We do **not** sell, rent, or share your personal data with third parties for their own marketing purposes.

## Data Retention

User-generated content (ratings, comments, photos, activity) remains visible until you delete it or request account deletion. Crash logs are retained per Firebase Crashlytics retention policy (typically 90 days).

## Your Rights & Choices

- **Anonymous browsing** – You can use the app without an account. Most features (map, stop details, schedules) are available anonymously.
- **Account deletion** – Submit a request on our <a href="https://chartmann1590.github.io/crowdsource-transit/account-deletion.html" target="_blank">Account Deletion page</a>, or email us at the address below.
- **Location** – You can deny or revoke location permission at any time via your device settings.
- **Opt out of analytics** – You can disable analytics sharing via your device settings (Android: Settings > Privacy > Ads).

## Children's Privacy

CrowdTransit is rated **Everyone** and does not knowingly collect data from children under 13. If we learn that a child under 13 has provided personal data, we will delete it.

## Changes to This Policy

We may update this policy. Changes will be posted here with an updated "Last updated" date.

## Contact

For questions or data deletion requests:  
**Email:** (add support email here)  
**GitHub:** [github.com/chartmann1590/crowdsource-transit](https://github.com/chartmann1590/crowdsource-transit)
