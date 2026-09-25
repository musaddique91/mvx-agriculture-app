# AgriCareAi — agentic AI, per-location weather, proactive alerts

**Date:** 2026-09-16
**Status:** approved (design)

## Problem

AgriBot talks about farming in general but knows nothing about *this* farmer.
`chat/NvidiaChatClient.java` sends a system prompt and 12 turns of history to
NVIDIA NIM and nothing else. Meanwhile the app already holds the farmer's mapped
plots, crops, scouting notes, live forecast, soil moisture, mandi rates, a crop
calendar, a disease encyclopedia and scheme data — none of which the model can
reach.

Separately, weather is resolved from the registered **city** centroid
(`ui/WeatherFragment.java:82`, `ui/HomeFragment.java:163` both call
`CityRepository.coordinatesOf`). A plot 15 km outside the district town gets the
town's weather, and two plots on opposite sides of a district get identical
forecasts when only one is under rain.

## Scope

Three sub-projects, built in this order. Each gets its own implementation plan.

1. **Weather locations** — app only. Prerequisite for the other two.
2. **Agent backend + AgriBot upgrade** — new FastAPI service.
3. **Proactive per-field alerts** — backend endpoint + WorkManager + local notifications.

Ordering rationale: 2 and 3 both need "which location?" to be a first-class
concept. Building 1 first means the agent's weather tool is written against the
right contract instead of retrofitted.

## Sub-project 1 — Weather locations

`WeatherRepository.load(double latitude, double longitude, Listener)`
(`data/WeatherRepository.java:44`) is already coordinate-based and does not
change. `Field.centroid()` (`data/Field.java:32`) already returns exact per-plot
coordinates. The gap is only that nothing reads them for weather.

### Units

- **`data/WeatherLocation.java`** — immutable value: `kind`
  (`FIELD`/`CITY`/`GPS`/`CUSTOM`), stable `id`, `label`, `sublabel`, `lat`, `lon`.
  The only type passed to the weather screen, the home card and the agent tool.
  Nothing downstream cares where a location came from.
- **`data/WeatherLocations.java`** — resolver building the dropdown list:
  mapped fields (label = field name, sublabel = crop + area) from
  `FieldRepository`; the registered city from the user profile via
  `CityRepository`; *My current location* from GPS; saved custom points plus a
  *Choose on map…* entry opening the existing `LocationPickerDialog`.
- **`data/WeatherLocationStore.java`** — SharedPreferences. Remembers the
  selected location id; holds saved custom points as JSON.

### Default resolution order

Remembered choice if it still resolves → most recently mapped field →
registered city. A farmer with plots opens onto their farm's weather; one with no
plots sees exactly what they see today. No existing user regresses.

### Call sites

`WeatherFragment:82` and `HomeFragment:163` take coordinates from the selected
`WeatherLocation` instead of `CityRepository`. The home card's "Today in
Belagavi" becomes "Today at Tomato plot" so the reading is always attributed.

### UI

A Material exposed-dropdown at the top of the Weather screen. Home shows the
same selection, tappable to jump to Weather. One source of truth, one control.

### Error handling

- Remembered field since deleted resolves to null → falls back down the chain
  rather than showing an empty screen.
- GPS denied, disabled or no fix → entry renders unavailable, selection falls
  back, screen never blocks on a fix. Last-known location used immediately,
  refined when a fresh fix arrives.
- Dropdown is built from on-disk data, so it populates instantly and never waits
  on the network.

### Testing

Resolver and fallback chain are pure logic over a field list and a stored id —
JVM unit tests covering: no fields, deleted remembered field, GPS unavailable,
ordering. The instrumented suite already covers `CityRepository`.

## Sub-project 2 — Agent backend

### Architecture

Android `ChatFragment` → `chat/AgentClient.java` → `POST /agent/chat` on a
FastAPI service → LangGraph ReAct agent → OpenRouter / NVIDIA NIM.

Reached in development over `adb reverse tcp:8000 tcp:8000`, so the phone calls
`http://localhost:8000` with no IP config and no cleartext exception.

### Stack

| Piece | Choice | Why |
|---|---|---|
| Web | FastAPI + Uvicorn | async, native SSE, Pydantic validates `FarmContext` |
| Agent | LangGraph `create_react_agent` | loop/retries/state handled; same graph reused by sub-project 3 |
| Models | `langchain-openai` at OpenRouter and NVIDIA base URLs | both OpenAI-compatible, so the fallback chain is a base_url + model id swap |
| HTTP out | `httpx` | async tool calls don't block the worker |
| Config | `pydantic-settings` + gitignored `.env` | keys never touch git |
| Packaging | `requirements.txt` + `Dockerfile` | container is the deploy unit |

### Layout — deployable independently

```
AgriCareAi/
├── Mobile App/AgriCareA/     ← Android
└── backend/                  ← standalone, no shared code or build
    ├── app/  main.py, agent/, tools/, models.py, settings.py
    ├── scripts/probe_models.py
    ├── Dockerfile
    ├── .env.example          ← committed; .env is not
    └── README.md
```

Three properties keep it separable: **stateless** (no DB, no disk, no session —
scales to N instances, no migrations, no backups); **one HTTP contract** (the app
only knows `POST /agent/chat`); **base URL is a `buildConfigField`**
(`AGENT_BASE_URL`) — debug points at localhost, release at a hosted HTTPS URL,
one line to switch. HTTPS hosting also avoids `usesCleartextTraffic`.

### Keys

The `OPENROUTER_API_KEY` lives **only** in the backend `.env` and never enters
the APK.

`NVIDIA_API_KEY` cannot leave the APK in this sub-project:
`chat/VisionClient.java` reads `BuildConfig.NVIDIA_API_KEY` directly and keeps
its direct path so Scan does not regress when the backend is down. So the key
stays in gitignored `local.properties` *and* in the backend `.env` for the
duration of sub-project 2.

Getting it out of the APK requires routing Scan's photo check through the
backend too. That is a deliberate follow-up, not part of this scope, and is the
point at which `NVIDIA_API_KEY` can be deleted from `local.properties` and the
`buildConfigField` removed. Until then the existing extractability noted in
RUN.md still applies.

### Privacy

The backend stores nothing. Fields, notes and profile travel as a per-request
`FarmContext` snapshot; field tools read that snapshot rather than a database.
On-device SQLite remains the only home for user data.

### Tools

Farmer-data tools read `FarmContext` (no network). Live tools call out via
`httpx`. All return compact JSON, never prose.

| Tool | Source | Returns |
|---|---|---|
| `get_weather(location)` | live | current + 7-day forecast, soil temp/moisture |
| `get_spray_window(location)` | live | safe/unsafe, reason, next safe window |
| `list_fields()` | context | name, crop, area, sown date, days since sowing |
| `get_field(name)` | context | plot detail + NDVI + that plot's scouting notes |
| `get_mandi_prices(commodity)` | live | district rates then state, with trend |
| `get_crop_calendar(crop)` | bundled | sowing/harvest windows |
| `lookup_disease(query)` | bundled | entry, symptoms, treatment |
| `find_schemes(topic)` | bundled | scheme, eligibility, official link |

`get_weather` and `get_spray_window` take a **location name** resolved against
the sub-project 1 `WeatherLocation` list. This is what lets the agent answer
"can I spray my tomato plot tomorrow?" differently from "…my north plot?".

### Contract

```
POST /agent/chat
{ message, history[], language, context: { profile, locations[], fields[], notes[] } }
→ SSE: tool_start · tool_end · token · done
```

Streaming so the farmer sees "checking your forecast…" rather than a 20-second
spinner. `AgentClient` mirrors `NvidiaChatClient`'s listener shape, so
`ChatFragment` changes little — it gains a tool-chip row above the reply.

### Prompt serialisation — TOON

Everything entering the model context is serialised as **TOON** (Token-Oriented
Object Notation) where it is a uniform array of flat objects, and as minified
JSON otherwise.

| Path | Format | Why |
|---|---|---|
| `FarmContext` block in the system prompt | TOON | fields, notes, locations are uniform flat arrays |
| Tool return values | TOON | forecast rows, mandi rows, field lists; re-sent every loop iteration |
| HTTP wire, app to backend | JSON | not in a prompt, saves no tokens; keeps Pydantic validation |
| Tool-call arguments emitted by the model | JSON | the function-calling API mandates JSON Schema |
| Nested or non-uniform data | minified JSON | TOON can be larger than JSON here |

The saving compounds per loop rather than per request: a ReAct agent resends the
full message history each iteration, so a 7-day forecast, a mandi table and a
field list are re-tokenised three or four times within one question. That is
where a free-tier rate limit bites.

**Implementation.** Written in-house as `app/serde/toon.py`, roughly 50 lines for
the flat-uniform case, deterministic and unit-tested. The two TOON packages on
PyPI (`toon-format` 0.1.0, `python-toon` 0.1.3) are both 0.1.x, too early to sit
on the path every prompt takes; the unrelated `toon` package is neuroscience
tooling. Swappable behind one function if either matures.

**Costs, accepted knowingly.** A short primer on reading TOON is needed in the
system prompt (a fixed cost, roughly 80 tokens, amortised across the
conversation). Models are less familiar with TOON than JSON, so a weaker free
model may parse it less reliably.

**Therefore it is measured, not assumed.** The plan includes a token-count
comparison of TOON against JSON on real payloads, and a correctness check that
the chosen model reads TOON tool results as accurately as JSON ones. Keep TOON
where it wins on both; fall back to JSON where it does not.

### Model selection — the main risk

Free-tier models are inconsistent about function-calling; some `:free` models
silently ignore tools and answer in prose, which looks identical to "the agent is
broken". Therefore:

- **`scripts/probe_models.py`** fires one tool-call request at each candidate
  across both providers and prints which actually returned a `tool_calls` block.
  The fallback chain is ordered from measured results, not assumption.
- **Fallback chain.** A 429, a 5xx, *or a reply that ignores the tools* demotes
  to the next model.

### Failure handling

- Tool failure is not turn failure: a dead upstream returns a structured error
  *to the model*, which then reports what it could not check.
- Backend unreachable → `AgentClient` falls back to `NvidiaChatClient`. AgriBot
  loses its tools, never its pulse.
- Budgets: 10s per tool, 60s per turn.

### Testing

pytest over tools with mocked `httpx`; one live smoke test, marked skippable;
device check at the end.

## Sub-project 3 — Proactive per-field alerts

A `/advisories` endpoint runs the same graph per field on a schedule. The app
fetches via WorkManager and raises a **local** notification. No Firebase, no push
infrastructure, no accounts. Per-field rather than per-district, which is the
difference between a useful alert and a noisy one.

## Deployment

Container runs anywhere that accepts a Dockerfile; ~256–512 MB, no GPU (all
inference is remote). The one thing to verify on any free tier is **cold starts**
— a service that sleeps after idle makes the first question of a demo hang. Add a
`/health` keep-alive if needed. Host choice is deferred until the agent is
working locally, to be made with current information rather than assumption.

## Security note

The NVIDIA and OpenRouter keys used here were shared in a chat transcript and
should be treated as exposed. They go into gitignored config only, and should be
rotated once the work is done.

## Sub-project 4 — Better offline disease detection

The bundled TFLite model has eight classes, all tomato. Pointed at maize rust it
can only answer with one of those eight. RUN.md already records it producing
confident boxes on a keyboard and a browser window — the classic signature of a
model trained on lab imagery.

**Roboflow Universe gives free datasets, not free weights.** Roboflow's docs:
"Manual weights download is only available for paid users on Core plans and
certain Enterprise customers." So a Universe model cannot be pulled down as
`.tflite` and bundled for free. The datasets are free, which is what matters,
because `training-YoloV8.ipynb` already exists in this repo and Roboflow lists
TFLite (int8) export as supported for YOLOv8.

**Approach: train our own on mixed field and lab data.** Free Universe datasets
plus field imagery, trained with the existing notebook on a free Colab GPU,
exported to TFLite, replacing `app/src/main/assets/model.tflite`.

The dataset choice matters more than the architecture:

| | PlantVillage | PlantDoc |
|---|---|---|
| Images | 54,306 | 2,598 |
| Coverage | 38 classes, 14 crops, 26 diseases | 13 crops, 17 diseases |
| Conditions | Lab, uniform backgrounds | Real fields |
| Weakness | ~99% in test, disappoints in field; tomato is 43.4% of images | Some mislabels; train/test inconsistency |

Training on a larger *lab* dataset would raise the class count from 8 to 38 while
leaving the real-field failure intact. Field imagery is what fixes it.

**Per dataset, before shipping:** check the licence (Universe datasets vary —
CC BY 4.0, MIT, some non-commercial, which matters for a distributed app) and
whether the images are field or lab.

**Scope boundary.** A larger offline model still cannot say "not in my training
set". The vision LLM path can. So the model is fast offline triage and the LLM
remains the fallback for anything outside its classes — which is already the
shape of the Scan screen.
