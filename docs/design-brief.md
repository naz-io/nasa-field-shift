# Field Shift — Design brief

This brief is for designing Field Shift's screens in **Google Stitch** and its colours in **Material Theme Builder**. The designs are a visual reference; they are rebuilt in Jetpack Compose with Material 3 components, so stick to standard Material 3 patterns.

See the [README](../README.md) for the product definition.

## Who we're designing for

- Field-crop farmers and their advisors, often with limited technical or mapping experience.
- Used **outdoors**: bright sunlight, sometimes with gloves, sometimes with a weak signal.
- They want a clear answer and the reasons behind it, not a dashboard.

## Design principles

1. **Readable outdoors**: high contrast, text no smaller than 14 sp, no light-grey-on-white.
2. **Big touch targets**: at least 48 dp, with generous spacing. Sliders and chips must work with a thumb in a glove.
3. **Plain language first.** Technical terms get a "What does this mean?" link to an explainer.
4. **Few steps.** Field → details → priorities → results. Every screen has one main action.
5. **Honest.** Every recommendation shows its evidence and its gaps, and says what to check locally. Never use "guaranteed" or "best" wording.
6. **Works offline** once data is loaded. Show when data is cached and how old it is.
7. **Light and dark mode**, both checked for contrast.
8. **Material 3 only**: top app bars, cards, sliders, filter chips, segmented buttons, bottom sheets, navigation bar. No custom widgets that don't map onto Compose.

## Screens

| # | Screen | Purpose | Main content | Main action |
|---|---|---|---|---|
| 1 | Field picker | Choose the field | Full-screen map, search bar, "Use my location" button, pin | Confirm location |
| 2 | Field setup | Describe the field | Soil texture and drainage chips, current-rotation crop picker, constraint toggles | Continue |
| 3 | Priorities | Say what matters | 3 sliders (Save water, Build soil, Extra income), each with a one-line explanation | See rotations |
| 4 | Your field's climate | Show the NASA data | Precipitation card, frost card (last spring frost, first fall frost, frost-free days), data-source note | View rotations |
| 5 | Rotation comparison | Compare 3 options | 3 cards, with the current rotation marked; each has a crop sequence, 3 priority score bars and a one-line summary | Open details |
| 6 | Rotation detail | Explain one option | Season timeline and 5 sections: Why suggested, Evidence, Benefits, Gaps in what we know, Check locally | Back / compare |
| 7 | Explainer | Teach a term | Bottom sheet: term, plain-language explanation, a simple illustration, "how NASA measures this" | Close |
| 8 | Alerts settings | Opt into warnings | Frost alert and dry-spell alert switches, with a threshold for each | Save |

## Stitch prompts

Open Stitch (stitch.withgoogle.com), choose a **mobile** design, and start with the style prompt. Then add each screen prompt in the same project, so the screens share a look.

### Overall style (use first)

```
A Material 3 Android app called "Field Shift" that helps farmers compare crop
rotations using NASA climate data. Calm, trustworthy, earthy style: deep field
green as the primary colour, warm soil brown and wheat gold as accents, off-white
backgrounds. Large readable type, high contrast for outdoor use, 48dp+ touch
targets, rounded Material 3 cards, plain-language labels. No stock photos;
simple line icons (map pin, water drop, sprout, coin, snowflake, sun).
```

### 1. Field picker

```
Field picker screen. Full-screen map with a search bar at the top ("Search a
place or road") and a floating "Use my location" button. A green pin marks the
selected field. Bottom card shows the coordinates and nearest town, with a large
primary button "Use this field".
```

### 2. Field setup

```
Field setup screen titled "About your field". Section "Soil texture" with
single-select chips: Sandy, Loam, Clay loam, Clay. Section "Drainage" with a
segmented button: Poor, Moderate, Good. Section "Current rotation" showing crop
chips in order (Corn → Soybean → Winter wheat) with an "Edit" button. Section
"Limits" with switches: "I can irrigate", "I can harvest forage/hay". Large
"Continue" button at the bottom.
```

### 3. Priorities

```
Priorities screen titled "What matters most?". Three large sliders, each with an
icon, a name and a one-line explanation: "Save water" (water drop) "Prefer crops
that need less water", "Build soil" (sprout) "Add nitrogen, organic matter and
cover", "Extra income" (coin) "Add a crop with a local market or forage value".
Primary button "Compare rotations".
```

### 4. Your field's climate

```
Climate summary screen titled "Your field's climate", subtitle "Toronto, Ontario
· 2020–2024 averages". Card "Rain and snow": 600 mm per year, 262 mm in the
growing season (May–Sep). Card "Frost": last spring frost Apr 9, first fall
frost Nov 20, frost-free season 224 days. Each card has a small "What does this
mean?" text link. Footer note in small text: "From NASA POWER satellite and model
data for an area about 50 km wide — your field may differ." Primary button
"See rotation options".
```

### 5. Rotation comparison

```
Rotation comparison screen titled "3 rotations for your field". Three stacked
cards. First card labelled "Your current rotation": Corn → Soybean → Winter
wheat. Second: Corn → Soybean → Winter wheat + Red clover cover. Third: Corn →
Soybean → Winter wheat → Alfalfa (2 yrs). Each card shows three small horizontal
score bars (Save water, Build soil, Extra income) and one plain-language summary
line. Cards are tappable with a "Details" chevron.
```

### 6. Rotation detail

```
Rotation detail screen for "Corn → Soybean → Winter wheat + Red clover". At the
top a 12-month season timeline: coloured bars for each crop, hatched grey for
bare soil, and small snowflake and sun markers for frost and dry-spell risk
months. Below, five expandable sections with icons: "Why suggested", "Evidence"
(showing NASA numbers like "frost-free season 224 days"), "Benefits", "Gaps in
what we know", and "Check locally" (with a note to ask an agronomist or OMAFRA).
```

### 7. Explainer

```
Bottom sheet explaining "Frost-free season". Short plain-language explanation
(2–3 sentences), a simple illustration of a calendar year with spring and fall
frost dates marked, and a small section "How NASA measures this" mentioning
daily minimum temperature at 2 metres. Close button.
```

### 8. Alerts settings

```
Alerts settings screen titled "Field alerts". Two cards with switches: "Frost
warning" with a threshold field "Alert when below 0 °C", and "Dry spell
warning" with "Alert after 14 days without rain". Explanatory text that alerts
use NASA POWER data and may lag by a few days. Save button.
```

**Tips:**
- Edit by chatting with Stitch, for example *"make the score bars thicker"* or *"increase contrast"*, instead of starting over.
- Export to Figma if you want to fine-tune by hand.
- Save PNG screenshots of the chosen screens to `docs/design/` so they can be linked from the README.

## Colours with Material Theme Builder

1. Open [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/).
2. Set the **source / primary colour** to an earthy field green, for example around `#3B6939`. Optionally set a soil-brown secondary and a wheat-gold tertiary.
3. Check light and dark previews, including the medium- and high-contrast schemes, for outdoor readability.
4. **Export → Jetpack Compose (Theme.kt)**. This produces the same `Color.kt` / `Theme.kt` format already in `app/src/main/kotlin/com/naz/fieldshift/ui/theme/`.
5. Share the exported files. Bringing them into the app is a separate, planned step.

## Who does what

| You | Claude |
|---|---|
| Generate and refine screens in Stitch | Turn the chosen screens into Compose (M4–M6) |
| Choose the palette in Theme Builder and export it | Plan and apply the theme change |
| Save screenshots to `docs/design/` | Link them from the README |
