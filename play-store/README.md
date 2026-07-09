# Play Store Listing Assets

Everything needed to submit CrowdTransit to the Google Play Console.

## Structure

```
play-store/
  listing/                     Store listing text
    app-title.txt               Title (<=30 chars)
    short-description.txt       Short description (<=80 chars)
    full-description.txt        Full description (<=4000 chars)
    release-notes.txt           "What's new" text for this release
    metadata.txt                Category, content rating, ad/IAP disclosures
  graphics/
    icon-512.png                 App icon, 512x512 (Play Store requirement)
    feature-graphic-1024x500.png Feature graphic, 1024x500
    phone/                       Phone screenshots, captioned, ready to upload
      01-map.png ... 05-live-activity.png
      raw-*.png                  Uncropped source screenshots (Pixel 8 Pro), kept for reference
    tablet-7in/                  7" tablet screenshots (see note below)
    tablet-10in/                 10" tablet screenshots (see note below)
  video/
    promo-video.mp4               ~38s promo video for the store listing
    voiceover-script.txt          Narration script
    voiceover.mp3 / voiceover.srt Generated narration audio + timed captions
    card-intro.png / card-free.png / card-outro.png  Title/outro cards used in the video
```

## How these were made

- **Phone screenshots** are real captures from a physical Pixel 8 Pro running a debug
  build of the app, then captioned/framed with a script (brand-green caption band,
  rounded corners, drop shadow).
- **Tablet screenshots**: a real Android tablet emulator could not be gotten running
  reliably in the environment these were produced in (repeated silent crashes in the
  emulator's GPU/Vulkan init, independent of the app). Since CrowdTransit doesn't have
  a distinct tablet layout — it's the same Compose UI at a larger canvas — the tablet
  images here are the same real phone screenshots composited onto correctly-sized
  7"/10" canvases with the same caption treatment, rather than native tablet-emulator
  captures. If you get a tablet AVD or physical tablet running, swap these for genuine
  captures before submitting; the phone screenshots are unaffected by this.
- **Icon and feature graphic** were rendered to match the app's actual launcher icon
  (`android/app/src/main/res/drawable/ic_launcher.xml`) and brand colors from
  `DESIGN.md`, not hand-drawn from scratch.
- **Video**: built with ffmpeg from the captioned screenshots plus two title/outro
  cards, narrated with Microsoft Edge's neural TTS (`en-US-AndrewNeural`, via the
  `edge-tts` package) for a natural-sounding voiceover, with burned-in captions
  synced to the narration (`voiceover.srt`, rendered via ffmpeg's `subtitles` filter).

## Before submitting

- Fill in the placeholders in `listing/metadata.txt` (support email, privacy policy
  URL, production website URL).
- Replace the tablet screenshots with native captures if possible (see note above).
- Google Play requires a privacy policy URL for apps handling user data (this app has
  auth/ratings/photos) — make sure one exists and is linked before publishing.
