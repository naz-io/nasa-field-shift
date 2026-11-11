# Field Shift

**Compare crop rotations that fit your field's changing climate — using NASA Earth data.**

Field Shift is an Android app that helps farmers explore crop-rotation options for a specific field. It combines NASA climate observations with soil information, crop characteristics and the farmer's own priorities, and explains every suggestion in plain language.

> A personal project inspired by the NASA Space Apps Challenge 2026 challenge
> [Field Shift: Adapting Farms with NASA Data](https://www.spaceappschallenge.org/2026/challenges/adapting-farms-with-nasa-data/?tab=details).
> It is not an official NASA product.

---

## The problem

Farmers are dealing with shifting temperatures, less predictable rainfall, water shortages, extreme weather and declining soil health. Choosing what to add to a rotation is hard: a crop that builds soil in one place may use too much water, bring in disease, have no local market, or not fit the growing season somewhere else.

NASA has decades of global Earth observations that describe local growing conditions. That data is rarely in a form a farmer can act on. Field Shift turns it into a short, explained comparison of rotation options.

## Who it's for

- **Farmers** of field crops, including those with little technical or mapping experience.
- **Advisors** who work with them and want a starting point for a conversation.

Field Shift is **global by design**: climate data is available for any point on Earth. Crop knowledge is regional, so it ships in [region packs](#region-packs). **v1 covers Ontario, Canada.**

## How it works

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

*Data source: NASA Langley Research Center (LaRC) POWER Project, funded through the NASA Earth Science/Applied Science Program.*

## Roadmap

Items marked *(new)* were added after reading the challenge brief.

- [x] **M1** Climate summary for a fixed location: precipitation and frost dates from NASA POWER
- [ ] **M2** Hilt dependency injection and a Room offline cache
- [ ] **M3** Rule-based rotation scorer, the region-pack structure and the Ontario pack, with unit tests. *(new)* Soil input, priority sub-factors, evidence and gaps text, and hemisphere-aware frost logic.
- [ ] **M4** Priority sliders, constraints *(new)* and the 3 five-section comparison cards
- [ ] **M5** Map field picker and location permission
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
| Planned | Hilt, Room, DataStore, Google Maps Compose, WorkManager, Vico |

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

Current packages, under `app/src/main/kotlin/com/naz/fieldshift/`:
- `data/remote/PowerApi.kt`: the API client and JSON models
- `data/ClimateRepository.kt`: the repository, which maps the API response to domain models
- `domain/ClimateCalculator.kt`: precipitation and frost-date statistics
- `ui/climate/`: the climate summary screen and its ViewModel

## Build and run

Requirements:
- Android Studio (recent stable)
- JDK 17. Android Studio's bundled JDK works.
- An emulator or device on Android 8.0 (API 26) or newer

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

No API key is needed, because NASA POWER is open.

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
