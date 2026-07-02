# CrowdTransit Design System — "Sunny Transit"

> Single source of truth for all visual decisions and interactive feature data model.
> Superseded the original Stitch-generated dark theme (2026-07-02).
> Full design rationale: `docs/superpowers/specs/2026-07-02-visual-redesign-design.md`

---

## Brand

- **App Name:** CrowdTransit
- **Tagline:** Find your ride. Share the knowledge.
- **Personality:** Fun, friendly, community-driven, a little playful
- **Aesthetic:** Sunny Transit — bright, light-first, bold hero green, big rounded cards, oversized friendly type
- **Fun factor:** confetti on contributions, live check-ins, gamified points/levels/badges, photos on stops

### Core Principles
- Map is always center stage, on a light basemap
- Light backgrounds, soft shadows, generous rounding — nothing feels heavy
- Anonymous-friendly — no forced login blocking content or contributions
- Accessibility first — high contrast, 48dp minimum touch targets
- Contributing should feel rewarding: instant feedback (points, confetti, live activity)

---

## Color Tokens

| Token | Hex | Usage |
|-------|-----|-------|
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

### Semantic Transit Colors

| Token | Hex | Usage |
|-------|-----|-------|
| `transit-bus` | `#00A862` | Bus route / badge |
| `transit-train` | `#2563EB` | Train / commuter rail |
| `transit-subway` | `#8B2FC9` | Subway / metro |
| `transit-ferry` | `#0891B2` | Ferry |
| `transit-tram` | `#EA7317` | Tram |
| `rating-star` | `#FFB000` | Star rating fill |

Mode badges: white text on solid mode color, fully rounded (pill).

---

## Typography

One family everywhere: **Plus Jakarta Sans** (system-ui/sans-serif fallback).

| Role | Size / Weight |
|------|----------------|
| Display (page titles) | 32px / 800 |
| Headline (section, card titles) | 22px / 700 |
| Title (stop names) | 17px / 700 |
| Body | 15px / 400 |
| Label / caption | 13px / 600, `ink-secondary` |

---

## Spacing Scale

| Token | Value |
|-------|-------|
| `xs` | 4px |
| `sm` | 8px |
| `md` | 16px |
| `lg` | 24px |
| `xl` | 32px |
| `2xl` | 48px |

---

## Shape & Elevation

| Token | Value | Usage |
|-------|-------|-------|
| `sm` | 4px | fine detail |
| `md` | 14px | buttons, inputs |
| `lg` | 20px | cards, sheets, modals |
| `full` | 9999px | chips, badges, search bar, FAB |

Shadows are soft and barely-there:
- Card: `0 1px 2px rgba(26,29,27,.06), 0 4px 16px rgba(26,29,27,.08)`
- Modal/bottom sheet: one level stronger, still soft (no heavy dark shadows)

Prefer a 1px `outline` border + soft shadow over elevation stacking.

Interaction: cards scale down `0.98` on press (150ms ease-out).

---

## Components

### Primary Button
Solid `primary`, white text, pill, 48dp/px min height, subtle press scale.

### Secondary Button
`primary-tint` background, `primary-dark` text, pill.

### Search Bar
White pill, soft shadow, `ink-faint` placeholder.

### Stop Card
White, `lg` (20px) radius, stop name in Title weight, mode pills, distance badge (`primary-tint`), amber star rating.

### Star Rating
Amber (`accent`) filled stars, `outline` empty. Stars scale/pop on tap.

### Bottom Navigation / Navbar
White surface, `primary` active icon + label, `ink-faint` inactive.

### Map Markers
Filled GeoJSON circle layers in mode color with white stroke. Selected stop: `primary`, larger radius. Stops with activity in the last 90 minutes: additional pulsing halo layer.

### Level Ring
Colored ring around profile avatar; ring color/fill progresses with level (see Gamification).

### Badge Grid
Profile page grid of earned milestone badges, locked badges shown dimmed/outlined.

### Quick Report Chips
Pill chips on stop detail: On time / Late / Crowded / Empty / Not running.

---

## Motion

- Card press: `scale(0.98)` → `scale(1)`, 150ms ease-out
- Marker drop-in: bounce on load
- Confetti burst: on any point-earning action
- Bottom sheet / modal: spring-based slide-up
- Pull-to-refresh: small bus animation (Android), bus-themed spinner (web)
- Android: haptic feedback on check-ins, ratings, point awards

---

## Map Tiles

Style: OpenFreeMap Liberty — `https://tiles.openfreemap.org/styles/liberty` (light basemap, unchanged — already matches the new light UI).

---

## Interactive Features — Data Model (Firebase RTDB)

All features work for anonymous users (Firebase anonymous auth issues a uid). No new backend infrastructure — RTDB only. See `firebase/database.rules.json` for the enforced security rules.

### Gamification

- `/users/{uid}/stats` — `{ points, stopsAdded, reviewsWritten, photosAdded, checkins, streakCount, lastContributionDate, badges: { [badgeId]: true } }`. Owner-write only.
- `/leaderboard/{uid}` — `{ displayName, points }`. Fanned out on the same write as stats. Owner-write only, public read, indexed on `points`.
- **Points:** +10 new stop, +5 review, +3 photo, +2 check-in/report.
- **Levels (by total points):** Pedestrian (0) → Commuter (50) → Regular (150) → Conductor (400) → Transit Legend (1000).
- **Badges:** first review, first stop added, 10 stops, 25 reviews, first photo, 7-day contribution streak.
- **Streak:** contributed on N consecutive calendar days (local time).

### Live Check-ins & Quick Reports

- `/activity/{stopId}/{pushId}` — `{ uid, type: checkin|on_time|late|crowded|empty|not_running, timestamp }`. Owner-write only (per push id), public read, indexed on `timestamp`.
- Live for 90 minutes, client-filtered by timestamp (no server TTL).
- Map: stops with recent activity get a pulsing halo. Stop detail: live activity strip ("3 people here · reported Late 5 min ago").

### Photos on Stops

- `/photos/{stopId}/{pushId}` — `{ uid, data (base64 JPEG, ≤800px / ~100KB, size-capped by rules), timestamp }`. Owner-write only (per push id), public read, indexed on `timestamp`.
- Stored inline in RTDB (no Firebase Storage bucket configured — hobby-scale tradeoff, swappable later).
- Cap 10 photos per stop (client-enforced).

---

## Screen Inventory

### Android Screens (Kotlin + Jetpack Compose)

| # | Screen | Notes |
|---|--------|-------|
| 1 | Map Home | Full-screen map, bottom sheet nearby, activity halos |
| 2 | Stop Detail | Hero stop name, check-in/report chips, photo strip, reviews |
| 3 | Search | Search + filter + results |
| 4 | Rate & Review | 5-star + subcategories + comment + photo attach |
| 5 | Route Detail | Route map + stops list |
| 6 | Add Stop | Map pin + form |
| 7 | User Profile | Stats, level ring, badges, leaderboard, reviews, settings |
| 8 | Nearby List View | List alternative to map view |
| 9 | Onboarding | Welcome + permissions |
| 10 | Directions to Stop | Map + bottom sheet walk steps |

### Web Pages (React + Vite)

| # | Page | Notes |
|---|------|-------|
| 1 | Map Home | Full-viewport map + sidebar, activity halos |
| 2 | Stop Detail | Map + reviews + check-in/report + photo strip |
| 3 | Auth (Sign In / Register) | Centered card, Google SSO |
| 4 | Route Explorer | Sidebar + map |
| 5 | User Profile | Stats, level ring, badges, leaderboard, reviews |
| 6 | Search Results | Results + filter sidebar |
| 7 | Add Stop | Map pin + centered form |
| 8 | About | Hero, features, stats, GitHub CTA |

---

## Android Theme Implementation Reference

### Color.kt

```kotlin
val Background = Color(0xFFFAFAF7)
val Surface = Color(0xFFFFFFFF)
val SurfaceSunken = Color(0xFFF1F1EC)
val Ink = Color(0xFF1A1D1B)
val InkSecondary = Color(0xFF5A605C)
val InkFaint = Color(0xFF9AA19C)
val Outline = Color(0xFFE4E4DE)
val Primary = Color(0xFF00A862)
val PrimaryDark = Color(0xFF007A47)
val PrimaryTint = Color(0xFFE0F5EB)
val OnPrimary = Color(0xFFFFFFFF)
val Accent = Color(0xFFFFB000)
val ErrorColor = Color(0xFFDE3730)
val TransitBus = Color(0xFF00A862)
val TransitTrain = Color(0xFF2563EB)
val TransitSubway = Color(0xFF8B2FC9)
val TransitFerry = Color(0xFF0891B2)
val TransitTram = Color(0xFFEA7317)
```

### Shape.kt

```kotlin
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(9999.dp)
)
```

### Type.kt

```kotlin
// Single family: Plus Jakarta Sans (system sans-serif fallback)
val displayLarge = TextStyle(fontFamily = JakartaFontFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
val headlineMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold)
val titleMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold)
val bodyMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal)
val labelMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
```

---

## Web CSS Custom Properties

```css
:root {
  --color-background: #FAFAF7;
  --color-surface: #FFFFFF;
  --color-surface-sunken: #F1F1EC;

  --color-ink: #1A1D1B;
  --color-ink-secondary: #5A605C;
  --color-ink-faint: #9AA19C;
  --color-outline: #E4E4DE;

  --color-primary: #00A862;
  --color-primary-dark: #007A47;
  --color-primary-tint: #E0F5EB;
  --color-on-primary: #FFFFFF;

  --color-accent: #FFB000;
  --color-error: #DE3730;
  --color-success: #00A862;

  --color-transit-bus: #00A862;
  --color-transit-train: #2563EB;
  --color-transit-subway: #8B2FC9;
  --color-transit-ferry: #0891B2;
  --color-transit-tram: #EA7317;
  --color-rating-star: #FFB000;

  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;
  --space-2xl: 48px;

  --radius-sm: 4px;
  --radius-md: 14px;
  --radius-lg: 20px;
  --radius-full: 9999px;

  --font-display: 'Plus Jakarta Sans', system-ui, sans-serif;
  --font-body: 'Plus Jakarta Sans', system-ui, sans-serif;
}
```
