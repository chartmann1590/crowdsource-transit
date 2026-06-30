# CrowdTransit Design System

> Single source of truth for all visual decisions. Generated from Google Stitch MCP, Phase 2.
> Stitch Project ID: `2382527016111832252`
> Design System ID: `assets/5797279417132822971`

---

## Brand

- **App Name:** CrowdTransit
- **Tagline:** Find your ride. Share the knowledge.
- **Personality:** Helpful, community-driven, globally inclusive
- **Aesthetic:** Technical Elegance — dark mode transit-forward, clean interfaces inspired by Citymapper/Google Maps transit but warmer and more community-feel

### Core Principles
- Map is always center stage
- Minimal chrome — let the data speak
- Anonymous-friendly — no forced login blocking content
- Accessibility first — high contrast, 48dp minimum touch targets

---

## Color Tokens

### Dark Theme (Primary)

| Token | Hex | Usage |
|-------|-----|-------|
| `surface` | `#111319` | Base background (Level 0) |
| `surface-dim` | `#111319` | Dimmed surface |
| `surface-bright` | `#37393f` | Bright surface |
| `surface-container-lowest` | `#0c0e14` | Lowest container |
| `surface-container-low` | `#191c21` | Nav bars, fixed headers (Level 2) |
| `surface-container` | `#1d2025` | Cards, interactive surfaces (Level 1) |
| `surface-container-high` | `#272a30` | Modals, drawers (Level 3) |
| `surface-container-highest` | `#32353b` | Transit line tags |
| `on-surface` | `#e1e2ea` | Primary text on surface |
| `on-surface-variant` | `#c2c6d4` | Secondary text |
| `outline` | `#8c919d` | Borders, dividers |
| `outline-variant` | `#424752` | Subtle borders |
| `primary` | `#a9c7ff` | Primary interactive / brand |
| `primary-container` | `#1565bf` | Primary container / distance badges |
| `on-primary` | `#003063` | Text on primary |
| `on-primary-container` | `#dae5ff` | Text on primary container |
| `secondary` | `#b2c7f0` | Secondary interactive |
| `secondary-container` | `#344a6c` | Secondary container |
| `tertiary` | `#ffb68c` | Ratings, warm accents (star color) |
| `tertiary-container` | `#a64c00` | Tertiary container |
| `error` | `#ffb4ab` | Error states |
| `error-container` | `#93000a` | Error container |
| `background` | `#111319` | Page background |
| `on-background` | `#e1e2ea` | Text on background |

### Semantic Transit Colors

| Token | Hex | Usage |
|-------|-----|-------|
| `transit-bus` | `#4CAF50` | Bus route / badge |
| `transit-train` | `#3277d2` | Train / commuter rail |
| `transit-subway` | `#9C27B0` | Subway / metro |
| `transit-ferry` | `#00BCD4` | Ferry |
| `rating-star` | `#ffb68c` | Star rating fill |

### Override Colors (Stitch theme keys)

| Key | Hex |
|-----|-----|
| `overridePrimaryColor` | `#3277d2` |
| `overrideSecondaryColor` | `#63789d` |
| `overrideTertiaryColor` | `#a64c00` |
| `overrideNeutralColor` | `#75777e` |

---

## Typography

Three-font strategy:

| Role | Font | Usage |
|------|------|-------|
| **Headline** | Manrope | Page titles, station names, hero text |
| **Body** | Inter | Content, descriptions, UI text |
| **Label** | Public Sans | Metadata, buttons, transit codes, badges |

### Type Scale

| Token | Font | Size | Weight | Line Height | Letter Spacing |
|-------|------|------|--------|-------------|----------------|
| `headline-lg` | Manrope | 32px | 700 | 40px | — |
| `headline-md` | Manrope | 24px | 600 | 32px | — |
| `body-lg` | Inter | 16px | 400 | 24px | — |
| `body-md` | Inter | 14px | 400 | 20px | — |
| `label-md` | Public Sans | 12px | 500 | 16px | 0.5px |
| `label-sm-bold` | Public Sans | 12px | 700 | 16px | — |

---

## Spacing Scale

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | 4px | Icon-label gap, tight grouping |
| `sm` | 8px | Within-component spacing |
| `md` | 16px | Card internal padding, component breathing room |
| `lg` | 24px | Section separation |
| `xl` | 32px | Major section separation |
| `safe-margin` | 16px | Page edge margin |
| `gutter` | 16px | Column gutter |

---

## Shapes / Corners

| Token | Value | Usage |
|-------|-------|-------|
| `sm` | 0.25rem (4px) | — |
| `DEFAULT` | 0.5rem (8px) | Buttons, inputs, segmented controls |
| `md` | 0.75rem (12px) | — |
| `lg` | 1rem (16px) | — |
| `xl` | 1.5rem (24px) | Cards (standard) |
| `full` | 9999px | FABs, badges, line numbers, pills |

Stitch roundness setting: `ROUND_EIGHT`

---

## Elevation Model

| Level | Surface Token | Usage |
|-------|--------------|-------|
| 0 — Base | `surface` `#111319` | Page background |
| 1 — Cards | `surface-container` `#1d2025` | Stop cards, interactive surfaces |
| 2 — Nav | `surface-container-low` `#191c21` | Fixed headers, bottom nav |
| 3 — Overlays | `surface-container-high` `#272a30` | Modals, drawers, filter menus |

Interaction: Active cards scale-down `scale-98` to simulate physical press.

---

## Components

### Transit Badge
Pill-shaped, full-rounded. Background: transit color at 20% opacity, text: transit color. Used for Bus/Train/Subway/Ferry labels inline.

### Stop Card
- Left border accent: 6px, transit type color
- Background: `surface-container`
- Corner radius: `xl` (24px)
- Padding: `md` (16px)
- Shows: stop name (`headline-md`), transit badges, star rating, distance badge

### Star Rating
Stars filled with `tertiary` (`#ffb68c`). Interactive (Android) and display (web) variants.

### FAB
- 56×56px circle, `full` radius
- Background: `primary-container`
- Hides on scroll-down, reappears on scroll-up

### Segmented Control
Track: `surface-container-low`, 4px padding. Active pill: `primary-container` with subtle shadow.

### Bottom Navigation (Android)
Fixed container, `rounded-t-xl` (24px) top corners. Active state: `primary-container` pill around icon+label.

### Map Tiles
Style: OpenFreeMap Liberty — `https://tiles.openfreemap.org/styles/liberty`
Dark overlay gradient: `rgba(15,23,36,0) → rgba(15,23,36,0.95)`

---

## Motion

- **Card press:** `scale(0.98)` on active, `scale(1)` on release — 150ms ease-out
- **FAB:** slide-up on scroll-up, slide-down on scroll-down — 200ms ease-in-out
- **Modal/Drawer:** slide-up from bottom — 300ms ease-out, backdrop fade 200ms
- **Page transition:** shared element if stop card → stop detail

---

## Screen Inventory

### Android Screens (MOBILE, Kotlin + Jetpack Compose)

| # | Screen | Stitch ID | Notes |
|---|--------|-----------|-------|
| 1 | Map Home | `4e03e3b002ea4f888d399af696ce02a8` | Full-screen map, bottom sheet nearby |
| 2 | Stop Detail | `02832926ec5e42df80e8f000a6de79cc` | Hero stop name, reviews, actions |
| 3 | Search | `a1152b8bf5f84545badce5f426d80f6e` | Search + filter + results |
| 4 | Rate & Review | `9e59c11705a548dd854156e0b8d2c14f` | 5-star + subcategories + comment |
| 5 | Route Detail | `a6ec6a00550647e2a0772e59c510853f` | Route map + stops list |
| 6 | Add Stop | `f44a18ee5dfa4434aefb52c203f2acb1` | Map pin + form |
| 7 | User Profile | `8344c47f0971442e9d5850c23f33e093` | Stats, reviews, settings |
| 8 | Nearby List View | `00c4949bf1a7403d92b74dbf0e2d4b1f` | List alternative to map view |
| 9 | Onboarding | `1bbefdea1e9144ea911a3398dec2c7ce` | Welcome + permissions |
| 10 | Directions to Stop | — | Timed out; regenerate if needed |

### Web Pages (DESKTOP, React + Vite)

| # | Page | Stitch ID | Notes |
|---|------|-----------|-------|
| 1 | Map Home | `fd5e789112f64bcb8028d84d0e9ab925` | Full-viewport map + 320px sidebar |
| 2 | Stop Detail | `1dba43615ea043c6a4cf6c09ecc731e9` | 50/50 map + reviews split |
| 3 | Auth (Sign In / Register) | `52ea7343c2d441aaa6e9a630e493dfe9` | Centered card, Google SSO |
| 4 | Route Explorer | `2099dd30cc96453898e64945174b5e18` | 380px sidebar + map |
| 5 | User Profile | `883ff703078845a1b4f66f70a848dac0` | Centered 720px, stats + reviews |
| 6 | Search Results | `7463df12f84f4c7ebb3368f672d03b49` | Two-col: results + filter sidebar |
| 7 | Add Stop | `2cdba7666b094bd095626964971b87dc` | Map pin + centered form |
| 8 | About | — | Not generated; simple marketing page |

---

## Android Theme Implementation

### Color.kt
```kotlin
val PrimaryBlue = Color(0xFFA9C7FF)
val PrimaryContainer = Color(0xFF1565BF)
val OnPrimary = Color(0xFF003063)
val OnPrimaryContainer = Color(0xFFDAE5FF)
val Secondary = Color(0xFFB2C7F0)
val SecondaryContainer = Color(0xFF344A6C)
val Tertiary = Color(0xFFFFB68C)  // star ratings
val TertiaryContainer = Color(0xFFA64C00)
val Surface = Color(0xFF111319)
val SurfaceContainerLow = Color(0xFF191C21)
val SurfaceContainer = Color(0xFF1D2025)
val SurfaceContainerHigh = Color(0xFF272A30)
val SurfaceContainerHighest = Color(0xFF32353B)
val OnSurface = Color(0xFFE1E2EA)
val OnSurfaceVariant = Color(0xFFC2C6D4)
val Outline = Color(0xFF8C919D)
val OutlineVariant = Color(0xFF424752)
val Error = Color(0xFFFFB4AB)
val ErrorContainer = Color(0xFF93000A)
val TransitBus = Color(0xFF4CAF50)
val TransitTrain = Color(0xFF3277D2)
val TransitSubway = Color(0xFF9C27B0)
val TransitFerry = Color(0xFF00BCD4)
```

### Shape.kt
```kotlin
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
```

### Type.kt
```kotlin
// Headlines: Manrope
// Body: Inter
// Labels: Public Sans (use default Roboto as fallback if custom fonts not bundled)
val headlineLarge = TextStyle(fontFamily = ManropeFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
val headlineMedium = TextStyle(fontFamily = ManropeFontFamily, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
val bodyLarge = TextStyle(fontFamily = InterFontFamily, fontSize = 16.sp, lineHeight = 24.sp)
val bodyMedium = TextStyle(fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
val labelMedium = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp)
```

---

## Web CSS Custom Properties

```css
:root {
  /* Surfaces */
  --color-background: #111319;
  --color-surface: #111319;
  --color-surface-container-lowest: #0c0e14;
  --color-surface-container-low: #191c21;
  --color-surface-container: #1d2025;
  --color-surface-container-high: #272a30;
  --color-surface-container-highest: #32353b;

  /* Text */
  --color-on-surface: #e1e2ea;
  --color-on-surface-variant: #c2c6d4;
  --color-outline: #8c919d;
  --color-outline-variant: #424752;

  /* Primary */
  --color-primary: #a9c7ff;
  --color-primary-container: #1565bf;
  --color-on-primary: #003063;
  --color-on-primary-container: #dae5ff;

  /* Secondary */
  --color-secondary: #b2c7f0;
  --color-secondary-container: #344a6c;

  /* Tertiary / Rating */
  --color-tertiary: #ffb68c;
  --color-rating-star: #ffb68c;

  /* Error */
  --color-error: #ffb4ab;
  --color-error-container: #93000a;

  /* Transit semantic */
  --color-transit-bus: #4caf50;
  --color-transit-train: #3277d2;
  --color-transit-subway: #9c27b0;
  --color-transit-ferry: #00bcd4;

  /* Spacing */
  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;

  /* Radius */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 16px;
  --radius-xl: 24px;
  --radius-full: 9999px;

  /* Typography */
  --font-headline: 'Manrope', sans-serif;
  --font-body: 'Inter', sans-serif;
  --font-label: 'Public Sans', sans-serif;

  /* Map */
  --map-tile-url: 'https://tiles.openfreemap.org/styles/liberty';
}
```

---

## Map Style

- **Tile source:** OpenFreeMap Liberty — `https://tiles.openfreemap.org/styles/liberty`
- **Library (Android):** MapLibre Native Android v11.5.2
- **Library (Web):** MapLibre GL JS
- **Stop pin colors:** Match transit type semantic colors above
- **Selected stop:** Larger pin with glow ring in primary color
- **Route polyline:** Transit type color, 4px width, 80% opacity

---

## Asset Sizes

### Android Icons
- App icon: 108×108dp vector (`drawable/ic_launcher.xml`) — no mipmap dirs
- Tab bar icons: 24dp, filled style
- FAB icon: 24dp

### Web
- Favicon: 32×32 SVG
- OG image: 1200×630

---

*Generated 2026-06-30 using Google Stitch MCP.*
*Do not edit manually — update via Stitch and re-export.*
