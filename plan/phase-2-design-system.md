# Phase 2: Design System (Google Stitch MCP)

> **Goal:** Use Google Stitch MCP to create a complete, beautiful design system and every screen for CrowdTransit — both Android and web. Export designs and establish DESIGN.md as the single source of truth for all visual decisions.

---

## Overview

Google Stitch is an AI-native design tool accessible via MCP that generates high-fidelity UI designs from text descriptions. It produces exportable frontend code and design tokens. This phase happens **before** any UI code is written in Android or web, so all implementation phases have pixel-perfect references.

**What we'll create:**
1. Design system (colors, typography, spacing, components)
2. All Android screens (10 screens)
3. All web pages (8 pages)
4. DESIGN.md — machine-readable design spec for all AI agents
5. Exported assets (icons, illustrations)

---

## Design Philosophy

**Aesthetic:** Modern transit-forward. Think: clean interfaces inspired by real transit apps (Citymapper, Google Maps transit), but warmer and more community-feel. Dark mode first, light mode second. Vibrant accent colors that feel energetic but professional.

**Brand Identity:**
- **Name:** CrowdTransit
- **Tagline:** "Find your ride. Share the knowledge."
- **Icon:** A stylized crowd of people around a transit pin/location marker
- **Personality:** Helpful, community-driven, globally inclusive

**Core Principles:**
- Map is always center stage — never hidden
- Minimal chrome — let the data speak
- Anonymous-friendly — no forced login screens blocking content
- Accessibility first — high contrast, large touch targets (48dp minimum)

---

## Step 1: Initialize Stitch Project

Use the `mcp__stitch__create_project` tool:

```
Project name: CrowdTransit
Description: Community-powered crowdsourced public transit locator for Android and Web
```

---

## Step 2: Create the Design System

Use `mcp__stitch__create_design_system` with this specification:

### Color Palette

```
Primary: #1565C0 (Deep Blue — trust, transit, reliability)
Primary Light: #1E88E5
Primary Dark: #0D47A1

Secondary: #FF6F00 (Amber/Orange — energy, community, action)
Secondary Light: #FFA000
Secondary Dark: #E65100

Surface Dark: #0F1724 (Near-black with blue tint)
Surface: #1A2332
Surface Elevated: #243044
Surface Card: #2A3650

On Primary: #FFFFFF
On Secondary: #000000
On Surface: #E8EAED
On Surface Secondary: #9AA0A6

Success: #00C853 (On-time green)
Warning: #FFB300 (Delay amber)
Error: #F44336 (Cancelled red)

Rating Gold: #FFC107
Transit Bus: #4CAF50
Transit Train: #2196F3
Transit Ferry: #00BCD4
Transit Subway: #9C27B0

Gradient Primary: linear 135deg #1565C0 → #0D47A1
Gradient Map Overlay: linear 180deg rgba(15,23,36,0) → rgba(15,23,36,0.95)
```

### Typography

```
Font Family: Google Sans (display), Roboto (body), Roboto Mono (codes/numbers)

Display Large: Google Sans 57sp / 64sp line height / -0.25 tracking
Display Medium: Google Sans 45sp / 52sp line height / 0 tracking
Headline Large: Google Sans 32sp / 40sp line height / 0 tracking
Headline Medium: Google Sans 28sp / 36sp line height / 0 tracking
Title Large: Google Sans Medium 22sp / 28sp line height / 0 tracking
Title Medium: Roboto Medium 16sp / 24sp line height / 0.15 tracking
Body Large: Roboto 16sp / 24sp line height / 0.5 tracking
Body Medium: Roboto 14sp / 20sp line height / 0.25 tracking
Label Large: Roboto Medium 14sp / 20sp line height / 0.1 tracking
Label Small: Roboto Medium 11sp / 16sp line height / 0.5 tracking
```

### Spacing System

```
4dp base unit
xs: 4dp
sm: 8dp
md: 16dp
lg: 24dp
xl: 32dp
2xl: 48dp
3xl: 64dp
```

### Border Radius

```
xs: 4dp (small chips)
sm: 8dp (buttons, input fields)
md: 12dp (cards)
lg: 16dp (bottom sheets)
xl: 24dp (modal dialogs)
full: 999dp (pills, avatars)
```

### Elevation / Shadows

```
Level 0: No shadow (flat surfaces)
Level 1: 0 1px 3px rgba(0,0,0,0.4) (cards at rest)
Level 2: 0 4px 8px rgba(0,0,0,0.5) (elevated cards)
Level 3: 0 8px 16px rgba(0,0,0,0.6) (bottom sheets)
Level 4: 0 16px 32px rgba(0,0,0,0.7) (modals)
```

---

## Step 3: Define Component Library

Use `mcp__stitch__create_design_system` to establish these components:

### Transit Badges
- **Bus badge:** Green pill `#4CAF50` with bus icon + route number
- **Train badge:** Blue pill `#2196F3` with train icon + line name  
- **Subway badge:** Purple pill `#9C27B0` with subway icon + line number
- **Ferry badge:** Cyan pill `#00BCD4` with ferry icon + route name
- **Tram badge:** Orange pill `#FF9800` with tram icon + route number

### Stop Card (Primary UI element)
```
Background: Surface Card #2A3650
Corner radius: 12dp
Padding: 16dp
Shadow: Level 1

Contents:
- Stop name (Title Medium, On Surface)
- Agency name (Body Medium, On Surface Secondary)
- Distance indicator (Label Small, Primary)
- Transit type badges (row, scrollable)
- Star rating (5 stars, Rating Gold, small)
- "X reviews" count (Label Small, On Surface Secondary)
- Accessibility icon (if ADA compliant)
- Next departure time pill (if real-time data available)
```

### Rating Component
```
5 stars, tap to rate
Empty star: outline, On Surface Secondary
Filled star: Rating Gold #FFC107
Half star: supported
Size variants: sm (16dp), md (24dp), lg (32dp)
```

### Map Pin Variants
```
Default stop: Blue circle #1565C0, white transit icon, drop shadow
User-added stop: Orange circle #FF6F00, white plus icon
Selected stop: Large version with info bubble
Current location: Blue pulsing dot
Agency cluster: Blue hexagon with count badge
```

### Bottom Sheet (Android)
```
Drag handle: centered, 4dp × 24dp pill, On Surface Secondary
Background: Surface Elevated #243044
Corner radius top: 24dp
Two states: collapsed (peek 120dp), expanded (full)
```

### Search Bar
```
Background: Surface Elevated
Border: 1dp Surface Elevated
Corner radius: 28dp (pill)
Icon left: search icon, On Surface Secondary
Placeholder: "Search stops, routes, cities…"
Clear button: right side, appears when text entered
Height: 56dp
Shadow: Level 2
```

### Rating Review Card
```
Background: Surface Card
Corner radius: 12dp
Padding: 16dp
Avatar: 40dp circle (initials if no photo)
Username: Title Medium (or "Anonymous Rider" in italic)
Date: Label Small, On Surface Secondary
Star rating: sm size
Comment text: Body Medium
Transit type tag: small badge
Helpful upvote button: "👍 Helpful (X)"
```

### Primary Button
```
Background: Primary #1565C0
Text: On Primary #FFFFFF, Label Large
Corner radius: 8dp
Height: 48dp
Min width: 120dp
Horizontal padding: 24dp
Pressed state: darken 10%
Disabled: 38% opacity
```

### FAB (Floating Action Button)
```
Background: Secondary #FF6F00
Icon: white, 24dp
Size: 56dp circle
Shadow: Level 3
Extended FAB: pill shape with icon + label
```

---

## Step 4: Generate Android Screens

Use `mcp__stitch__generate_screen_from_text` for each screen:

### Screen 1: Map Home (Main Screen)
```
Prompt: "Dark-themed crowdsourced transit map app. Full-screen map (dark OSM style, night mode) takes 80% of the screen. Status bar transparent. Bottom of screen shows a draggable bottom sheet in dark navy (#243044) with rounded top corners showing nearby stops as horizontally-scrollable cards. Each card has a stop name, colored transit type badges (bus=green, train=blue), distance, and 5-star rating. A search bar floats at the top with a slight shadow. FAB button (orange #FF6F00) in bottom right with a plus icon for adding stops. Small location crosshair button near FAB. Top right has user avatar/login button. Current location shown as blue pulsing dot. Transit stops shown as colored map pins. Clean, professional, like Citymapper but darker and more vibrant."
```

### Screen 2: Stop Detail
```
Prompt: "Transit stop detail screen. Dark theme (#0F1724 background). Top section: full-width map showing the stop location with a bold colored map pin. Below the map: stop name in large white text, agency name in gray, ADA accessibility badge if applicable. Row of colored transit type badges (bus=green pill, train=blue pill). Large star rating display (4.2 stars, gold, 247 reviews). Row of action buttons: 'Get Directions' (primary blue button), 'Add Review' (secondary), 'Share' (icon). Scrollable review cards below. Each review has avatar circle, username or 'Anonymous Rider', date, 5-star rating, and comment text. Sticky bottom bar with 'Write a Review' button. Community-submitted indicator if stop was user-added (orange crown badge). Next departures section showing real-time schedule if available."
```

### Screen 3: Route Detail
```
Prompt: "Transit route detail screen. Dark theme. Top hero: route number/name in huge text with the route's transit color as accent. Route badge (e.g. 'Bus 38' in green pill). Route description and agency. Map showing the full route polyline in the route's color. List of all stops along the route as a vertical timeline — each stop is a dot on the line with the stop name, distance from user, and time. Overall rating for the route (stars + review count). Action bar: 'View on Map', 'Share Route'. Upcoming trips section showing next departures."
```

### Screen 4: Search Screen
```
Prompt: "Transit search screen. Dark theme. Large search bar at top, active/focused state with keyboard visible. Recent searches section showing 3-4 recent stops/routes with clock icon. 'Nearby Stops' section as a list with distance and transit type badges. Search results (when typing) show stops first, then routes, then cities/areas. Each result has a transit icon, name, distance, and small color badge. Clean list format, no map visible. Filter chips below search bar: 'Stops', 'Routes', 'Agencies'."
```

### Screen 5: Rate & Review Screen
```
Prompt: "Write a review screen for a transit stop. Dark theme. Stop name and location at top. Big 5-star rating widget (tappable, stars light up gold as you tap). Category ratings: Cleanliness, Safety, Accessibility, Reliability — each with its own 5-star row. Text input area for comment (multiline, 'Share your experience...' placeholder). Photo attachment button (camera icon, up to 3 photos shown as thumbnails if selected). Transit type selector: 'What did you ride?' with bus/train/subway/ferry chips. Post as: 'Your username' or toggle to 'Anonymous'. Submit button at bottom (primary blue, full width). Cancel X in top bar."
```

### Screen 6: Add Stop Screen (Crowdsourcing)
```
Prompt: "Add a new transit stop screen. Dark theme. Title: 'Add a Stop'. Mini-map at top showing the user's current location with a draggable pin to position the stop. Form below: Stop Name (text field), Stop Code/Number (optional, text field), Transit Type chips (Bus/Train/Subway/Ferry/Tram — select one or more), Agency Name (text field with autocomplete dropdown), Notes (multiline). Accessibility checkboxes: Wheelchair accessible, Elevator, Bench, Shelter, Lighting. Submit button. Note: 'Your addition will be reviewed by the community.' Orange accent colors for this screen to indicate user contribution."
```

### Screen 7: User Profile
```
Prompt: "User profile screen for transit app. Dark theme. Top section: large avatar circle (80dp) with user's initials or photo, username, 'Joined [date]', contribution badge count. Stats row: Reviews Written, Stops Added, Helpful Votes. 'Your Contributions' section with tabs: Reviews, Stops Added, Reports. Reviews tab shows list of their past reviews with mini star rating and truncated comment. Settings section: Account settings, Notification preferences, Dark/Light mode toggle, About, Sign Out. If anonymous user, shows 'Create Account' banner with benefits listed."
```

### Screen 8: Nearby List View (Alternative to map)
```
Prompt: "List view of nearby transit stops. Dark theme. Toggleable with map view (toggle at top right). Sorted by distance. Each item: transit color bar on left (bus=green, train=blue), stop name (bold), agency + route numbers, distance pill (e.g. '0.3 mi'), star rating (small, gold), accessibility icon if applicable. Swipe right to favorite a stop. Pull-to-refresh. Filter FAB with options: transit type, max distance, min rating."
```

### Screen 9: Onboarding / Welcome
```
Prompt: "App onboarding screen 1 of 3. Dark navy gradient background. Large transit map illustration with glowing pins. Big headline: 'Find Your Ride'. Subtext: 'Real transit stops, real reviews, powered by people like you.' Page indicators at bottom (3 dots). 'Continue' primary blue button. 'Skip' text link. Top: CrowdTransit logo (stylized crowd + location pin icon)."

Second prompt: "Onboarding screen 2: 'Rate & Review' theme. Illustration of people leaving star reviews on a transit stop. Headline: 'Share Your Experience'. Subtext: 'Rate stops, flag delays, add missing stops. Your contributions help everyone.'"

Third prompt: "Onboarding screen 3: 'Join or Stay Anonymous'. Illustration of person with choice arrows. Headline: 'You Choose'. Subtext: 'Create an account to track your contributions, or explore anonymously. No pressure.' Buttons: 'Create Account' (primary), 'Continue as Guest' (text link)."
```

### Screen 10: Directions to Stop
```
Prompt: "Walking directions to transit stop screen. Dark theme. Map filling top 60% showing walking route from user (blue dot) to transit stop (colored pin) with a blue dotted walking path. Bottom sheet with: destination stop name, walking duration ('8 min walk', large), distance ('0.4 mi'). Step-by-step walking directions list. 'Open in Maps' button to hand off to OS maps app. Transit details at destination: next bus in 4 minutes, bus number 38 to Downtown."
```

---

## Step 5: Generate Web Pages

Use `mcp__stitch__generate_screen_from_text` for web:

### Web Page 1: Landing/Map Home
```
Prompt: "Web app for crowdsourced transit. Full-viewport dark map (85% of screen). Minimal top navbar: CrowdTransit logo left, search bar center, Login/Sign Up buttons right (or user avatar if logged in). Left sidebar panel (collapsible, 320px): 'Nearby Stops' list with cards showing stop name, transit badges, rating stars, distance. Each card clickable to show details. Right side info panel slides in when stop selected. FAB bottom-right: '+ Add Stop' (orange). Map pins for all transit stops colored by type. Legend at bottom. Responsive, looks great on desktop and tablet."
```

### Web Page 2: Stop Detail Page
```
Prompt: "Transit stop detail page, web. Dark theme. Split layout: left half shows map with stop highlighted, right half shows stop details panel. Stop name as hero heading. Transit badges. Star rating with count. Action buttons row. Reviews section in a scrollable list. 'Write Review' button sticky at top of right panel. Responsive — stacks vertically on mobile."
```

### Web Page 3: Login/Auth Page
```
Prompt: "Authentication page for transit app. Dark theme with subtle map pattern in background. Centered card (400px wide). CrowdTransit logo at top. 'Sign In' / 'Create Account' tab toggle. Email + Password fields. 'Continue with Google' button (white, Google colors). Divider OR. 'Continue as Guest' text link at bottom. Clean, minimal. OAuth badges shown."
```

### Web Page 4: User Profile Page
```
Prompt: "User profile page, web, dark theme. Profile header with avatar, name, join date, contribution stats (reviews, stops added, helpful votes). Tabs: Reviews, Stops Added, Reports. Review feed below tabs. Sidebar: account settings, notification preferences. Admin section if user is a moderator."
```

### Web Page 5: Route Explorer
```
Prompt: "Route explorer page, web. Dark theme. Left panel: searchable list of transit routes with transit type filter (bus/train/subway). Each route shows line color, route number, short description, city/region. Right: full-screen map showing selected route's stops and path. Clicking a stop in the route list or on the map shows a mini-popup with stop name and rating."
```

### Web Page 6: About / How It Works
```
Prompt: "About page for CrowdTransit community transit app. Dark theme. Hero section: headline 'Powered by your community.' Brief description. 3 feature columns with icons: 'Find Transit', 'Rate & Review', 'Add Missing Stops'. How It Works section with numbered steps. Data sources section: 'We use GTFS open data from transit agencies worldwide plus your contributions.' Community stats: X stops, X reviews, X contributors."
```

### Web Page 7: Add Stop Page
```
Prompt: "Add a transit stop form page, web. Dark theme. Split: left side form, right side mini-map with draggable pin. Form fields: Stop Name, Stop Code (optional), Transit Types (checkbox group), Agency Name, Notes, Accessibility features (checkboxes). Submit button. Community guidelines note. 'Preview on map' updates the right-side map as user fills in the location."
```

### Web Page 8: Search Results
```
Prompt: "Transit search results page, web. Dark theme. Search bar at top (wide). Filter row: All / Stops / Routes / Agencies, plus distance filter, rating filter. Results in two columns on desktop: map left (with pins for all results), list right. List items show transit type icon, name, location, distance, rating. Pagination or infinite scroll."
```

---

## Step 6: Export and Generate DESIGN.md

After all screens are created in Stitch, use `mcp__stitch__get_screen` to retrieve each screen and compile into a DESIGN.md file.

**Create `DESIGN.md` in the project root with this structure:**

```markdown
# CrowdTransit Design System

> This file is the single source of truth for all design decisions.
> All AI agents implementing UI must reference this document.

## Brand

- App Name: CrowdTransit
- Tagline: "Find your ride. Share the knowledge."
- Primary market: Global, mobile-first

## Color Tokens

| Token | Value | Usage |
|-------|-------|-------|
| color-primary | #1565C0 | Buttons, links, selected states |
| color-primary-light | #1E88E5 | Hover states |
| color-primary-dark | #0D47A1 | Pressed states |
| color-secondary | #FF6F00 | FABs, CTAs, user-added content |
| color-surface-dark | #0F1724 | App background |
| color-surface | #1A2332 | Primary surface |
| color-surface-elevated | #243044 | Cards, bottom sheets |
| color-surface-card | #2A3650 | Card backgrounds |
| color-on-surface | #E8EAED | Primary text |
| color-on-surface-secondary | #9AA0A6 | Secondary text |
| color-success | #00C853 | On-time, available |
| color-warning | #FFB300 | Delays, caution |
| color-error | #F44336 | Cancelled, errors |
| color-rating | #FFC107 | Star ratings |
| color-bus | #4CAF50 | Bus transit type |
| color-train | #2196F3 | Train/rail transit type |
| color-subway | #9C27B0 | Subway/metro transit type |
| color-ferry | #00BCD4 | Ferry transit type |
| color-tram | #FF9800 | Tram/streetcar transit type |

## Typography Scale

| Token | Font | Size | Weight | Line Height |
|-------|------|------|--------|-------------|
| text-display-lg | Google Sans | 57sp | 400 | 64sp |
| text-headline-lg | Google Sans | 32sp | 400 | 40sp |
| text-title-lg | Google Sans | 22sp | 500 | 28sp |
| text-title-md | Roboto | 16sp | 500 | 24sp |
| text-body-lg | Roboto | 16sp | 400 | 24sp |
| text-body-md | Roboto | 14sp | 400 | 20sp |
| text-label-lg | Roboto | 14sp | 500 | 20sp |
| text-label-sm | Roboto | 11sp | 500 | 16sp |

## Spacing

Base unit: 4dp
xs=4, sm=8, md=16, lg=24, xl=32, 2xl=48, 3xl=64

## Components

See Stitch project for interactive component specs.

Key components:
- StopCard: Shows a transit stop with name, badges, rating, distance
- TransitBadge: Colored pill for bus/train/subway/ferry/tram
- StarRating: 1-5 stars, interactive or display-only
- ReviewCard: User review with avatar, rating, comment
- MapPin: Location markers by transit type
- SearchBar: Full-width pill search input
- BottomSheet: Draggable panel for mobile

## Screen Inventory

### Android
1. MapHome — main map + nearby stops bottom sheet
2. StopDetail — stop info, departures, reviews
3. RouteDetail — route info, stops list, reviews
4. Search — search + filters + results
5. RateReview — write a review form
6. AddStop — crowdsource a new stop
7. UserProfile — user stats and contributions
8. NearbyList — list view alternative to map
9. Onboarding — 3-screen welcome flow
10. Directions — walking directions to a stop

### Web
1. MapHome — full-viewport map + sidebar
2. StopDetail — split map + detail panel
3. Auth — sign in / create account
4. UserProfile — profile + contribution history
5. RouteExplorer — browse routes
6. About — marketing page
7. AddStop — web form for crowdsourcing
8. SearchResults — search with map + list

## Map Style

OSM-based dark style (compatible with MapLibre GL JS and MapLibre Native Android).
Recommended tile source: OpenFreeMap (Liberty or Dark style)
Style URL (web): https://tiles.openfreemap.org/styles/liberty
For Android: Use MapLibre with same style JSON URL or bundle locally.

Transit stop pins:
- Bus: #4CAF50 circle
- Train: #2196F3 circle
- Subway: #9C27B0 circle  
- Ferry: #00BCD4 circle
- Tram: #FF9800 circle
- User-added: #FF6F00 circle with + icon
- Selected: 1.5× scale with bounce animation

## Motion

Page transitions: slide from right (push), slide to right (pop)
Bottom sheet: spring physics (damping 0.8, stiffness 300)
Map pins: drop in with bounce on first load
Rating stars: fill left to right with scale pulse
FAB: scale from 0 to 1 on screen enter
Loading: skeleton screens (no spinners for map data)
```

---

## Step 7: Apply Design System to Stitch Project

```
Use mcp__stitch__apply_design_system to apply the design tokens to all created screens.
Use mcp__stitch__generate_variants to create light mode variants of key screens.
```

---

## Deliverables

- [ ] Stitch project created and saved
- [ ] Design system with all color tokens, typography, spacing
- [ ] All 10 Android screens generated in Stitch
- [ ] All 8 web pages generated in Stitch
- [ ] `DESIGN.md` written and committed to repo root
- [ ] Design exported to `android/app/src/main/res/` (icons, colors.xml reference)
- [ ] Design exported to `web/src/styles/design-tokens.css`
- [ ] All team members / AI agents can reproduce the design from DESIGN.md alone
