# Field Shift

[![CI](https://github.com/naz-io/nasa-field-shift/actions/workflows/ci.yml/badge.svg)](https://github.com/naz-io/nasa-field-shift/actions/workflows/ci.yml)

**Compare crop rotations that fit your field's changing climate — using NASA Earth data.**

Field Shift is an Android app that helps farmers explore crop-rotation options for a specific field. It combines NASA climate observations with soil information, crop characteristics and the farmer's own priorities, and explains every suggestion in plain language.

> A personal project inspired by the NASA Space Apps Challenge 2026 challenge
> [Field Shift: Adapting Farms with NASA Data](https://www.spaceappschallenge.org/2026/challenges/adapting-farms-with-nasa-data/?tab=details).
> It is not an official NASA product.

### Available today

Field Shift is an **early prototype**. What works now:
- **Pick your field** on a Google Map, by searching or by using your location. The app remembers it.
- **See its historical climate** from NASA POWER, over the last 10 complete years:
  - average yearly and growing-season precipitation
  - average last spring frost and first fall frost
  - the frost-free season, in both hemispheres

Every number says how much data it's based on. When data is too thin, the app says "not enough data" instead of guessing.

**Not built yet:** soil and priority inputs, the crop-rotation comparison, the offline cache, charts and alerts. See the [roadmap](#roadmap). The app shows historical *averages*; it doesn't yet show how the climate is *changing*.

## The NASA Space Apps challenge

[![Watch the challenge video: Field Shift: Adapting Farms with NASA Data](https://img.youtube.com/vi/cF0U2WXsM7o/maxresdefault.jpg)](https://www.youtube.com/watch?v=cF0U2WXsM7o)

▶ *Click the image to watch NASA's challenge video on YouTube.*

- **Official challenge description:** [Field Shift: Adapting Farms with NASA Data](https://www.spaceappschallenge.org/2026/challenges/adapting-farms-with-nasa-data/?tab=details) (NASA Space Apps Challenge 2026)
- **Challenge resources:** [Resources tab](https://www.spaceappschallenge.org/2026/challenges/adapting-farms-with-nasa-data/?tab=resources), opening on 13 November 2026

**In short** (summarised in our own words, not NASA's): the challenge asks for a decision-support tool that combines NASA Earth observations with local soil information, crop traits and the farmer's own priorities. The tool should help farmers explore crop rotations that improve soil health and adapt to changing weather and water conditions. It should compare at least three rotations, including the current one, and explain the evidence, benefits, gaps and risks of each. It should support farmers' judgment, not replace it.

**How Field Shift is designed to answer it:** an Android app that works for any field on Earth, a transparent rule-based comparison of 3 rotations, and region packs for local crop knowledge (Ontario first). Every result says how much data it rests on. The field picker and climate summary exist today; the rotation comparison is being built. See [How it will work](#how-it-will-work) and the [roadmap](#roadmap).

---

## The problem

Farmers are dealing with shifting temperatures, less predictable rainfall, water shortages, extreme weather and declining soil health. Choosing what to add to a rotation is hard: a crop that builds soil in one place may use too much water, bring in disease, have no local market, or not fit the growing season somewhere else.

NASA has decades of global Earth observations that describe local growing conditions. That data is rarely in a form a farmer can act on. Field Shift turns it into a short, explained comparison of rotation options.

## Who it's for

- **Farmers** of field crops, including those with little technical or mapping experience.
- **Advisors** who work with them and want a starting point for a conversation.

Field Shift is **global by design**: climate data is available for any point on Earth. Crop knowledge is regional, so it ships in [region packs](#region-packs). **v1 covers Ontario, Canada.**

## How it will work

This is the target design. Steps 1 and 4 work today, in part.

1. **Pick your field** on a map, or use your current location.
2. **Describe it**:
   - soil texture and drainage, chosen from a short list
   - your current rotation
   - any constraints, for example *no irrigation* or *no forage equipment*
3. **Set your priorities** with three sliders:

   | Priority | What it's made of |
   |---|---|
   | Save water | crop water use, fit with rainfall and dry spells |
   | Build soil | nitrogen fixing, erosion reduction, organic matter, rooting depth |
   | Extra income | market suitability, forage value |

4. **Field Shift analyses multi-year NASA climate data** for that spot: precipitation, frost dates, the frost-free season, and later heat units and dry spells.
5. **It compares 3 rotations, always including your current one.** Each rotation card has five sections:
   - **Why suggested**: which of your priorities it serves
   - **Evidence**: the NASA numbers behind it
   - **Benefits**: soil, water, pest-cycle and income effects
   - **Gaps in what we know**: where the data is thin or uncertain
   - **Check locally**: what a local agronomist or experienced neighbour should confirm
6. **A season timeline** for each rotation shows bare-field months and frost or dry-spell risk windows.
7. **Plain-language explainers** cover terms such as *frost risk*, *evapotranspiration* and *NDVI*.

## Principles

- **Supports, never replaces, farmer judgment** and local expertise.
- **Explainable**: every score shows the evidence and rules behind it. v1 uses a transparent rule-based scorer rather than a black-box model.
- **Honest about uncertainty**: every recommendation states its gaps.
- **Plain language** first, with technical detail one tap away.

## Region packs

A region pack contains:
- the region's crops and their traits: water use, nitrogen fixing, rooting depth, forage value, frost tolerance
- rotation and pest-cycle rules
- the local advice sources to point farmers to

Adding a region means adding a pack, not changing the app.

| Pack | Status | Crops | Local sources |
|---|---|---|---|
| Ontario, Canada | v1 (in progress) | corn, soybean, winter wheat, red clover, cereal rye, alfalfa | OMAFRA (Ontario Ministry of Agriculture, Food and Agribusiness) |

## What it is not

- **Not agronomic or financial advice.**
- **Not a yield or price forecast.**
- **Not field-precise.** NASA POWER data describes a grid cell roughly 50 km wide. Your field can be frostier, wetter or drier than the cell average. Satellite and model data are not the same as a weather station on your farm.

## NASA data

| Dataset | Used for | Status |
|---|---|---|
| [NASA POWER](https://power.larc.nasa.gov/) (daily `T2M_MAX`, `T2M_MIN`, `PRECTOTCORR`, AG community) | precipitation, frost dates, frost-free season | **Used** |
| MODIS vegetation indices (NDVI) | crop vigour, bare-field periods | Planned |
| SRTM elevation | slope, erosion risk | Planned |
| SMAP soil moisture, ECOSTRESS, GPM, Landsat, GRACE / GRACE-FO | soil moisture, heat stress, rainfall extremes, long-term water trends | Future (needs a backend for Earthdata access and processing) |

**How the climate numbers are calculated:**
- **Period:** the last 10 complete years, rolling. A year counts as complete 3 months after it ends, because NASA's data arrives with a lag. In November 2026 that's 2016–2025.
- **Enough data:** a year counts for a measurement only if at least 95% of its days have a valid value for that measurement. Rain and temperature are checked separately. Small gaps are filled with that year's daily mean.
- **At least 3 usable years** are needed; otherwise the app shows "not enough data", never 0 mm or "no frost".
- **Frost:** a frost day is a daily minimum at or below 0 °C at 2 m. The frost-free season is averaged from each season's own length. In the southern hemisphere, seasons run July to June.
- **Growing season:** the hemisphere's typical months, May–Sep in the north and Nov–Mar in the south. These are defaults, not a crop's actual season.

**Known limit:** POWER's daily minimum temperature is an average over a grid cell about 50 km wide, so it can miss frosts in cold hollows and valleys. Near Clare, South Australia, for example, it shows only one day at or below 0 °C in five years.

*Data source: NASA Langley Research Center (LaRC) POWER Project, funded through the NASA Earth Science/Applied Science Program.*

## Roadmap

Items marked *(new)* were added after reading the challenge brief.

- [x] **M1** Climate summary for a fixed location: precipitation and frost dates from NASA POWER
- [x] **M5** Google Maps field picker, location permission and the remembered last field. Built before M2–M4 so the app works for any farm. Also covers the hemisphere-aware frost seasons from M3.
- [ ] **M2** Hilt dependency injection and a Room offline cache
- [ ] **M3** Rule-based rotation scorer, the region-pack structure and the Ontario pack, with unit tests. *(new)* Soil input, priority sub-factors, and the evidence and gaps text.
- [ ] **M4** Priority sliders, constraints *(new)* and the 3 five-section comparison cards
- [ ] **M6** Charts (Vico) and the season timeline *(new)*, WorkManager frost and dry-spell alerts, MODIS NDVI
- [ ] **M7** *(new)* Plain-language explainers, SRTM slope and erosion risk
- **Future:** automatic soil lookup (ISRIC SoilGrids); SMAP, ECOSTRESS and GRACE through a backend; more region packs; a model-based scorer experiment

## Design

Screens are designed in Google Stitch and coloured with Material Theme Builder. See the [design brief](docs/design-brief.md). Screenshots will be added here once the designs exist.

## Tech stack

| Area | Tools |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, `StateFlow`, Navigation 3 |
| Networking | Retrofit, kotlinx.serialization, coroutines |
| Testing | JUnit, kotlinx-coroutines-test |
| Maps and location | Google Maps Compose, Fused Location Provider, Android Geocoder |
| Storage | DataStore Preferences |
| Planned | Hilt, Room, WorkManager, Vico |

### Architecture

```
ui/      Compose screens + ViewModels (state as StateFlow)
  │
  ▼
domain/  Pure Kotlin: models and calculations (no Android imports, fast unit tests)
  ▲
  │
data/    Repositories, NASA POWER API client (Retrofit), later Room cache
```

The UI depends on the repository *interface*, not on Retrofit. This keeps the ViewModel testable with fakes and lets a cache be added behind it without UI changes.

Current packages, under `app/src/main/kotlin/io/naz/fieldshift/`:
- `AppContainer.kt`: manual dependency injection, until Hilt in M2
- `data/remote/PowerApi.kt`: the NASA POWER client and JSON models
- `data/ClimateRepository.kt`: maps the API response to domain models
- `data/FieldRepository.kt`: remembers the last field with DataStore
- `data/location/`: place search (Geocoder) and device location (Fused Location)
- `domain/ClimateCalculator.kt`: precipitation, frost dates and the growing season, per hemisphere, with data-coverage rules
- `domain/ClimateRange.kt`: which 10 years to analyse
- `ui/picker/`: the Google Maps field picker
- `ui/climate/`: the climate summary screen

## Build and run

Requirements:
- Android Studio (recent stable)
- A JDK to start Gradle: 17 or newer. Android Studio's bundled JDK works.
  - Gradle downloads its own daemon JDK (JetBrains 25), as set in `gradle/gradle-daemon-jvm.properties`.
  - Kotlin still compiles the app to Java 17 bytecode (`jvmToolchain(17)`).
  - So three JDK versions are involved, and you only install one of them.
- An emulator or device on Android 8.0 (API 26) or newer

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lintDebug
```

[CI](.github/workflows/ci.yml) runs all three on every push and pull request.

NASA POWER needs no API key. The map needs a **Google Maps API key**:

1. In the [Google Cloud Console](https://console.cloud.google.com/), enable **Maps SDK for Android** and create an API key.
2. Restrict the key: under **Android apps**, add package `io.naz.fieldshift` with your debug SHA-1. Get the SHA-1 with `./gradlew signingReport`. Under **API restrictions**, allow **Maps SDK for Android** only.
3. Create `secrets.properties` in the project root, containing:
   ```properties
   MAPS_API_KEY=your-key-here
   ```
   This file is git-ignored, so never commit it. Without it the app still builds, but the map stays blank.

## Learning goals

This is also a project for practising modern Android development. Each milestone exercises:

| Milestone | Skills |
|---|---|
| M1 | Compose, MVVM + StateFlow, Retrofit + kotlinx.serialization, coroutines, unit tests |
| M2 | Hilt, Room, offline caching |
| M3 | Pure-Kotlin domain logic, test-driven rules |
| M4 | Stateful UI, sliders and forms, DataStore for preferences |
| M5 | Google Maps Compose, runtime permissions |
| M6 | Charts with Vico, WorkManager background work, notifications |
| M7 | Content design, bottom sheets, more NASA APIs |
