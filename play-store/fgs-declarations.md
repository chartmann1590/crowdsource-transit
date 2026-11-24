# CrowdTransit - Foreground Service declarations (Play Console)

Prepared 2026-11-23 for the resubmission of com.charles.crowdtransit.app.
Enter these in Play Console > App content > Foreground service permissions.
The manifest already matches: FOREGROUND_SERVICE_LOCATION and
FOREGROUND_SERVICE_DATA_SYNC are declared, there is NO background-location
permission, and navigation shows a prominent in-app disclosure before it
starts (ItineraryDetailScreen: "CrowdTransit uses your precise location
while navigating...").

## Manifest mapping

| Service | Manifest type | Permission | User trigger |
|---|---|---|---|
| NavigationService | location | FOREGROUND_SERVICE_LOCATION | Rider starts turn-by-turn navigation for a planned trip |
| AssistantDownloadService | dataSync | FOREGROUND_SERVICE_DATA_SYNC | Rider enables Hopper and downloads the on-device AI model (~2 GB) |
| AssistantInferenceService | dataSync | FOREGROUND_SERVICE_DATA_SYNC | A Hopper reply is generating on-device |

## FOREGROUND_SERVICE_LOCATION

**Purpose / feature (short answer):** Live turn-by-turn navigation.

**Justification text:**
"CrowdTransit uses a foreground service with the location type only while
the rider is actively navigating a planned trip. The service displays a
persistent 'navigating' notification and reads the rider's precise location
so the app can advance them through walking and transit legs, warn them
when to get off, and detect when they have gone off route. The service
starts only after the app shows a prominent in-app disclosure and the rider
taps Start navigation, and it stops as soon as navigation ends or is
cancelled. The app does not request background location access, and
navigation location is never stored on our servers or shared."

**Demo video:** REQUIRED - no existing video covers the location service.
The video must show, in one continuous capture:
1. A planned trip's itinerary open in the app.
2. The prominent in-app location disclosure dialog shown before navigation
   starts (and the system location permission prompt if it appears).
3. Tapping Start navigation.
4. The live navigation screen WITH the persistent navigation notification
   visible in the notification shade.
5. Navigation progressing (current leg / get-off warning) and the user
   ending navigation, after which the notification disappears.

## FOREGROUND_SERVICE_DATA_SYNC

**Purpose / feature (short answer):** On-device AI model download and
inference (Hopper assistant).

**Justification text:**
"Two services use the dataSync type, both for the optional Hopper AI
assistant and both started by the user from Settings > AI Assistant.
AssistantDownloadService downloads the ~2 GB on-device AI model file from
Hugging Face so the download can finish if the user leaves the app; it
shows a progress notification and stops when the download completes.
AssistantInferenceService keeps the app process at foreground priority
while a reply is generated on-device - inference over a photo can take over
a minute, and without the service the system killed the process (and the
reply) when the user briefly switched apps. Neither service uploads
personal data: Hopper conversations and scanned images are processed
entirely on the device and never transmitted."

**Demo video:** play-store/fgs-datasync-demo.mp4 (in this repo) shows the
model download running under its progress notification - use it for the
dataSync declaration. If the reviewer asks about the inference service
specifically, also capture: asking Hopper a question, the generation
notification while switching to another app and back, and the completed
reply.