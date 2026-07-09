# Security Policy

## Supported Versions

CrowdTransit is under active development. Only the latest release (web deployment
and most recent Android release build) is supported with security fixes.

## Reporting a Vulnerability

If you find a security vulnerability in CrowdTransit, please report it privately
rather than opening a public issue:

1. Use [GitHub's private vulnerability reporting](https://github.com/chartmann1590/crowdsource-transit/security/advisories/new)
   for this repository, or
2. Open a [GitHub Security Advisory](https://docs.github.com/en/code-security/security-advisories)
   draft directly.

Please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce (a minimal proof of concept, if possible)
- The affected component (Android app, web app, or the data pipeline scripts)

We'll acknowledge reports as soon as possible and keep you updated as the issue
is investigated and fixed. Please allow a reasonable time for a fix to ship before
any public disclosure.

## Scope

- `android/` — the Kotlin/Jetpack Compose Android app
- `web/` — the React/TypeScript web app
- `scripts/` — the GTFS import and data pipeline scripts
- `firebase/` — Firebase Realtime Database security rules

Third-party services this project depends on (Firebase, Transitland, AdMob,
MapLibre/OpenStreetMap tiles) should be reported to their respective vendors.
