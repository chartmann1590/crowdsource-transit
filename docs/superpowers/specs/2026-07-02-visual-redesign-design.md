# CrowdTransit Redesign — "Sunny Transit"

**Date:** 2026-07-02
**Scope:** Full visual reskin of the web app and Android app, plus four new interactive feature sets: gamification, live check-ins & quick reports, playful micro-interactions, and photos on stops. One shared design system applied to both platforms. Implementation is phased (see Phasing) so each phase ships working.

## Why

The current design is a Stitch-generated dark "Technical Elegance" theme (muddy gray-blue surfaces, washed-out `#a9c7ff` primary). It clashes with the light map basemap (OpenFreeMap Liberty) both apps already use, and the user dislikes it. Direction chosen: **bright, friendly, modern** — light-first, bold color, big type — and a more fun, interactive product overall.

## Design System

### Color

Light theme only. The Android app forces the light color scheme regardless of system setting; a dark theme is out of scope for this redesign.

| Token | Hex | Usage |
|---|---|---|
| `background` | `#FAFAF7` | Warm off-white page/screen background |
| `surface` | `#FFFFFF` | Cards, sheets, nav bars |
| `surface-sunken` | `#F1F1EC` | Input fields, wells, secondary chips |
| `ink` | `#1A1D1B` | Primary text |
| `ink-secondary` | `#5A605C` | Secondary text |
| `ink-faint` | `#9AA19C` | Placeholders, disabled |
| `outline` | `#E4E4DE` | Borders, dividers |
| `primary` | `#00A862` | Hero green — buttons, links, active states, FAB, map accents |
| `primary-dark` | `#007A47` | Hover/pressed, text-on-light-green |
| `primary-tint` | `#E0F5EB` | Selected backgrounds, badges, chips |
| `on-primary` | `#FFFFFF` | Text/icons on primary |
| `accent` | `#FFB000` | Ratings stars, warm highlights |
| `error` | `#DE3730` | Errors, destructive actions |
| `success` | `#00A862` | Same as primary (green = go) |

Transit-mode colors stay vivid (they read great on light backgrounds):

| Mode | Hex |
|---|---|
| Bus | `#00A862` |
| Train / rail | `#2563EB` |
| Subway / metro | `#8B2FC9` |
| Ferry | `#0891B2` |
| Tram | `#EA7317` |

Mode badges: white text on solid mode color, fully rounded (pill).

### Typography

One font family everywhere: **Plus Jakarta Sans** (friendly, geometric, free via Google Fonts / bundleable for Android). Fallback: system-ui / sans-serif.

| Role | Size / weight |
|---|---|
| Display (page titles) | 32px / 800 |
| Headline (section, card titles) | 22px / 700 |
| Title (stop names) | 17px / 700 |
| Body | 15px / 400 |
| Label / caption | 13px / 600, `ink-secondary` |

Android maps these onto the Material 3 typography scale in `Type.kt` (displaySmall through labelMedium equivalents).

### Shape & elevation

- Cards and sheets: 20px radius. Buttons and inputs: 14px. Chips/badges/search bar: fully rounded (pill).
- Shadows are soft and barely-there: `0 1px 2px rgba(26,29,27,.06), 0 4px 16px rgba(26,29,27,.08)` for cards; one stronger level for modals/bottom sheets. No dark heavy shadows.
- Prefer a `1px outline` border + soft shadow over elevation stacking.

### Components (both platforms)

- **Primary button:** solid `primary`, white text, pill, 48dp/px min height, subtle press scale.
- **Secondary button:** `primary-tint` background, `primary-dark` text.
- **Search bar:** white pill, soft shadow, `ink-faint` placeholder, prominent on Home.
- **Stop cards:** white, 20px radius, stop name in Title weight, mode pills, distance as `primary-tint` badge, amber star ratings.
- **Bottom nav (Android) / Navbar (web):** white surface, `primary` active icon + label, `ink-faint` inactive.
- **Map markers:** filled circles in mode color with white stroke (already GeoJSON circle layers) — recolor to the vivid mode palette; selected stop uses `primary` with larger radius.
- **Rating stars:** `accent` amber filled, `outline` empty.

## New Features

All features work for anonymous users (Firebase anonymous auth already issues uids). RTDB is the only backend — no new infrastructure.

### Gamification

- **Points** for contributions, awarded client-side on successful write: +10 new stop, +5 review, +3 photo, +2 check-in or quick report.
- **Levels** by total points: Pedestrian (0) → Commuter (50) → Regular (150) → Conductor (400) → Transit Legend (1000). Level shows as a colored ring around the profile avatar and next to usernames on reviews.
- **Badges** for milestones: first review, first stop added, 10 stops, 25 reviews, first photo, 7-day contribution streak. Displayed as a grid on the profile page.
- **Leaderboard** tab on the Profile page: top 50 by points, all-time. Anonymous users appear as "Anonymous Rider".
- **Data:** `/users/{uid}/stats` (points, counts, streak, badges) and `/leaderboard/{uid}` (displayName, points) fanned out on the same client write. Security rules restrict writes to the owner's own nodes.
- Streak = contributed on N consecutive calendar days (local time), stored as lastContributionDate + streakCount.

### Live check-ins & quick reports

- On any stop (sheet/detail page): one-tap **"I'm here"** check-in, plus quick-report chips: **On time / Late / Crowded / Empty / Not running**.
- Reports and check-ins are live for **90 minutes** (client-filtered by timestamp; no server TTL needed).
- **Stop detail** shows a live activity strip: "3 people here · reported Late 5 min ago".
- **Map:** stops with activity in the last 90 minutes render a pulsing halo around the marker (extra animated circle layer driven by the same GeoJSON source).
- Realtime via RTDB listeners — updates appear without refresh on both platforms.
- **Data:** `/activity/{stopId}/{pushId}` = { uid, type: checkin|on_time|late|crowded|empty|not_running, timestamp }. One check-in per user per stop per 90-minute window (client-enforced; rules require auth and own uid).

### Playful micro-interactions

- Confetti burst when points are earned (web: canvas-confetti; Android: Compose particle animation).
- Marker drop-in bounce when stops load; pulsing halo on active stops.
- Star rating stars pop/scale as tapped.
- Springy bottom-sheet and page transitions (CSS transitions / Compose animateXAsState + spring specs).
- Pull-to-refresh with a small bus animation (Android; web uses a bus-themed loading spinner).
- Haptic feedback on Android for check-ins, ratings, and point awards.

### Photos on stops

- Camera/gallery attach button on stop detail and in the review form.
- Client-side compression to max 800px / ~100KB JPEG, stored **base64 in RTDB** under `/photos/{stopId}/{pushId}` = { uid, data, timestamp }. Chosen because Firebase Storage is not configured and new buckets require a paid plan; at hobby scale this is fine and is swappable for Storage later behind the same interface.
- Stop detail shows a horizontal photo strip; tap for fullscreen viewer with swipe.
- Cap 10 photos per stop (client-enforced oldest-visible; rules cap payload size).

## Phasing

Each phase ships working, in order:

1. **Phase A — Reskin + micro-interactions** (both platforms): design system, all screens restyled, animations/haptics.
2. **Phase B — Live check-ins & quick reports** (data + UI + map halos).
3. **Phase C — Gamification** (points/levels/badges/leaderboard, confetti hooks into Phase A animation work).
4. **Phase D — Photos** (capture, compression, strip, viewer).

## Implementation Scope

### Web (`web/`)

1. **`src/styles/design-tokens.css`** — replace all custom properties with the palette above; keep existing token *names* where they exist (`--color-primary`, `--color-surface`, etc.) so component CSS keeps working, adding new ones (`--color-surface-sunken`, `--color-primary-tint`, ink names alias to `on-surface` names).
2. **Font:** load Plus Jakarta Sans in `index.html` (Google Fonts, weights 400/600/700/800); update `--font-display`/`--font-body`.
3. **Every `*.module.css`** (~15 files: pages + components) — audit for hardcoded dark hex values and dark-theme assumptions (light text, dark shadows); restyle to the new system. Global styles in `App.tsx`-adjacent CSS / `index.css` likewise.
4. **Map layer colors** in `MapView.tsx`/`StopMarker.tsx` — vivid mode palette, `primary` selection color.
5. **`hero.png`** on About/landing — replace or drop if it's dark-themed (verify during implementation; a simple gradient hero in `primary-tint` is an acceptable replacement).

### Android (`android/`)

1. **`ui/theme/Color.kt`** — new palette constants.
2. **`ui/theme/Theme.kt`** — single `lightColorScheme` mapped from the palette (primary, surface, background, outline, error, containers); force light (`darkTheme` ignored or mapped to the same scheme); status bar icons dark-on-light.
3. **`ui/theme/Type.kt`** — bundle Plus Jakarta Sans in `res/font/`, map to Material 3 type scale.
4. **`ui/theme/Shape.kt`** — 14/20px radii, pill for small.
5. **Screens/components audit** (~10 screen packages + `ui/components/`) — most use `MaterialTheme` tokens and will cascade; find and fix hardcoded `Color(0xFF...)` values, mode-badge colors, marker colors in `MapLibreView.kt`, star color in `ReviewCard.kt`.
6. **`res/values`** — theme.xml / colors.xml for splash and status bar.

### Shared

- **`DESIGN.md`** — rewrite as the new single source of truth (this palette, type, shape, component specs). The Stitch project references are removed.
- **`firebase/database.rules.json`** — new rules for `/users/{uid}/stats`, `/leaderboard/{uid}`, `/activity/{stopId}`, `/photos/{stopId}` (auth required, owner-only writes where applicable, size cap on photo payloads).
- New web deps: `canvas-confetti` (or equivalent tiny lib). Android: no new deps expected (Compose animation + built-in haptics).

## Error handling / risk

- Web token names are kept backward-compatible so any missed CSS module still renders sanely (worst case: right palette, slightly off styling) rather than broken.
- Android: screens using `MaterialTheme.colorScheme` inherit automatically; the audit greps for `Color(0x` to catch stragglers.
- Map basemap stays OpenFreeMap Liberty (already light, already matches).

## Verification

- Web: `npm run build` passes; visual pass over Home, Search, Route, Stop detail, Profile, About, Add-stop, login modal in the browser (light backgrounds, no leftover dark surfaces, legible text everywhere).
- Android: `gradlew assembleDebug` passes; install on device/emulator and visually check each screen; grep confirms no orphaned dark palette constants.
- Features (per phase): exercise the flow end-to-end on both platforms against the Firebase emulator or live RTDB — earn points and see confetti + updated leaderboard; check in on one client and watch the halo/activity strip appear live on another; upload a photo and view it fullscreen. Rules validated with `firebase_validate_security_rules` / emulator.

## Out of scope

- Dark mode (can be added later on top of the token system)
- Layout/navigation restructuring beyond what the new features require
- Map basemap changes
- Firebase Storage, Cloud Functions, or any server-side aggregation (leaderboard fan-out is client-side)
- Moderation/reporting tools for photos and reviews (future work)
- New illustrations/branding assets beyond replacing the dark hero image
