# CrowdTransit Visual Redesign — "Sunny Transit"

**Date:** 2026-07-02
**Scope:** Full visual reskin of the web app and Android app. No feature changes, no layout/navigation restructuring, no data-layer changes. One shared design system applied to both platforms in one pass.

## Why

The current design is a Stitch-generated dark "Technical Elegance" theme (muddy gray-blue surfaces, washed-out `#a9c7ff` primary). It clashes with the light map basemap (OpenFreeMap Liberty) both apps already use, and the user dislikes it. Direction chosen: **bright, friendly, modern** — light-first, bold color, big type.

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

## Error handling / risk

- Web token names are kept backward-compatible so any missed CSS module still renders sanely (worst case: right palette, slightly off styling) rather than broken.
- Android: screens using `MaterialTheme.colorScheme` inherit automatically; the audit greps for `Color(0x` to catch stragglers.
- Map basemap stays OpenFreeMap Liberty (already light, already matches).

## Verification

- Web: `npm run build` passes; visual pass over Home, Search, Route, Stop detail, Profile, About, Add-stop, login modal in the browser (light backgrounds, no leftover dark surfaces, legible text everywhere).
- Android: `gradlew assembleDebug` passes; install on device/emulator and visually check each screen; grep confirms no orphaned dark palette constants.

## Out of scope

- Dark mode (can be added later on top of the token system)
- Layout/navigation/feature changes
- Map basemap changes
- New illustrations/branding assets beyond replacing the dark hero image
