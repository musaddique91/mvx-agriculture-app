# Running AgriCareAi locally (Android emulator)

## TL;DR

```bash
cd "Mobile App/AgriCareA"
./run.sh
```

That builds the debug APK, boots an emulator if none is running, installs the app
and launches it. Everything below is the detail behind that one command.

## Prerequisites

| Requirement | Why | Check |
|---|---|---|
| **JDK 17–21** | AGP 8.13 / Gradle 8.13 reject newer JDKs | `java -version` |
| **Android SDK** | build tools + platform-tools | `ls $ANDROID_HOME` |
| **SDK Platform 36** | `compileSdk = 36` | Android Studio → SDK Manager |
| **An AVD, API 34+** | to run on | `emulator -list-avds` |

The easiest way to get all of it is Android Studio — it ships a JDK 21 (JBR) at
`/Applications/Android Studio.app/Contents/jbr/Contents/Home`, which `run.sh`
picks up automatically when your own `JAVA_HOME` is too new.

Toolchain in use: AGP 8.13.2, Gradle 8.13, `compileSdk`/`targetSdk` 36, Java 17
language level, `minSdk` 24.

Put the SDK tools on your PATH (add to `~/.zshrc` if you want it permanent):

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

## API keys

Both live in `local.properties`, which is gitignored, and reach the code as
`BuildConfig` fields.

| Key | Powers | Without it |
|---|---|---|
| `NVIDIA_API_KEY` | AgriBot, AI photo check, field crop advice | Those screens say no key is configured; everything else works |
| `DATA_GOV_API_KEY` | Mandi prices (free from data.gov.in) | The Mandi screen explains how to add one |

Everything else — weather, satellite imagery, NDVI, parcel detection — needs no key
at all.

## API key (required for the chatbot)

AgriBot talks to NVIDIA NIM. The key lives in `local.properties`, which is
gitignored, and reaches the code as a `BuildConfig` field — it is never committed:

```properties
NVIDIA_API_KEY=nvapi-...
```

Without it the app still runs; the chat screen just says no key is configured.
Model and endpoint are `buildConfigField`s in `app/build.gradle.kts`
(`meta/llama-3.2-11b-vision-instruct` on `integrate.api.nvidia.com`), so swapping
models is a one-line change.

> The key ends up inside the APK, where anyone with the file can extract it. That
> is unavoidable for a direct device-to-provider call. For anything public, put a
> small backend between the app and NVIDIA and keep the key there.

## First-time setup

1. **`local.properties`** — points Gradle at the SDK. It is gitignored, so it does
   not exist on a fresh clone. `run.sh` creates it; manually it is:

   ```bash
   echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
   ```

2. **Create an AVD** if `emulator -list-avds` is empty — Android Studio →
   Device Manager → Create Device → Pixel 6 → API 34 (Google APIs). Or from the CLI:

   ```bash
   sdkmanager "system-images;android-34;google_apis;arm64-v8a"   # x86_64 on Intel
   avdmanager create avd -n pixel_emulator -k "system-images;android-34;google_apis;arm64-v8a" -d pixel_6
   ```

## Manual steps (what `run.sh` does)

```bash
# 1. use a JDK the build accepts
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# 2. boot the emulator (backgrounded) and wait for it
emulator -avd pixel_emulator &
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'

# 3. build
./gradlew assembleDebug

# 4. install + launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.mvx.agriculture/.HomeActivity
```

## run.sh options

```
./run.sh                 build, boot emulator, install, launch
./run.sh --device        use an already-connected device/emulator
./run.sh --serial <id>   target a specific device (see: adb devices)
./run.sh --phone         target the first non-emulator device
./run.sh --avd <name>    boot a specific AVD
./run.sh --release       build the release variant
./run.sh --clean         gradle clean first
```

## Day-to-day commands

```bash
adb logcat --pid=$(adb shell pidof -s com.mvx.agriculture)   # app logs only
adb logcat -d -b crash                                          # last crash
adb exec-out screencap -p > screen.png                          # screenshot
adb uninstall com.mvx.agriculture                             # remove app
./gradlew test                                                  # unit tests
./gradlew connectedAndroidTest                                  # instrumented tests
```

## Region and city selection

Sign-up asks for a **Region** and then a **City**; picking a region refills the
city list. The data lives in `app/src/main/assets/cities.csv` (`Region,City`,
one city per line) and is read by `CityRepository`. Three regions ship today —
Tunisia (24 governorates), Karnataka (33 cities) and Maharashtra (36 cities).

To add a region, append its rows to that CSV — no code change needed; the
spinner picks up any new region in file order.

The choice is stored per user in the `users` table (`city` + `region`) and shown
on the profile as "City, Region". The `region` column arrived in database
version 2 and is added by an in-place `ALTER TABLE`, so existing accounts survive
the upgrade with an empty region.

## What the app does

| Screen | What it gives a farmer |
|---|---|
| **Home** | Greeting, live weather for their district, the day's most useful advisory, a daily tip, and every tool as a tile |
| **Scan** | Live TFLite detection (offline, **tomato only** — see below) **plus** AI photo check in four modes — disease, pest, weed, nutrient deficiency — which works on any crop |
| **Mandi** | Daily Agmarknet rates, filtered to the farmer's own district first, then widened to the state |
| **Weather** | 7-day forecast with soil temperature and moisture, plus spray-window, irrigation and heat-stress advisories |
| **AgriBot** | Conversation with NVIDIA NIM, answering in the app's language |
| **My fields** | Saved plots with area, crop, AI crop recommendations and scouting notes |
| **Field mapping** | Satellite map, tap-to-detect boundary from OpenStreetMap, manual drawing, area in acres/hectares/guntha |
| **Encyclopedia** | Offline disease reference with photos and a detail sheet |
| **Crop calendar** | Sowing and harvest windows for 16 Indian crops, in-season first |
| **Calculators** | Urea/DAP/MOP, seed rate and spray mix for a given plot and crop |
| **Govt schemes** | PM-KISAN, PMFBY, KCC, Soil Health Card and more, with official links |

## Appearance

Two themes, switchable from the toolbar overflow or the drawer and remembered
across launches:

- **Green** — Material 3, the default.
- **Soft** — neumorphic. One flat ground with every surface moulded out of it:
  a white highlight up-left, a soft shadow down-right, and inputs carved *into*
  the surface rather than sitting on it.

Android elevation casts a single shadow, so the look cannot be expressed with
`elevation` or offset rectangles. `theme/NeumorphDrawable` draws both shadows as
blurred layers, in raised and pressed variants. Because Material bakes card,
button and text-field backgrounds into their own drawables where a theme cannot
reach, `theme/SoftTheme` walks each view tree once after inflation and swaps just
those backgrounds — colours, spacing and behaviour stay with the theme. Fragment
views are caught through a `FragmentLifecycleCallbacks` hook on the shell.

Blurred shadow layers are skipped by the hardware renderer, so each moulded view
opts itself into software rendering.

## Languages

English, हिन्दी, ಕನ್ನಡ, मराठी and اردو, switchable from the toolbar or the drawer
and remembered across launches.

Urdu is right-to-left and set in **Noto Nastaliq Urdu** (bundled at
`res/font/`, SIL Open Font License in `assets/licenses/`). The font is applied as
a **runtime theme overlay**, not a `values-ur` theme: a language-qualified theme
outranks `values-night`, which would have stranded Urdu users in light colours.
`LocaleManager.applyFont()` merges it into whatever theme is already in force. AgriBot and the AI photo check answer in the selected
language too. Disease names in the encyclopedia stay English — they come from
`assets/diseases.json`, which is data rather than UI strings.

## Tip of the day

Rebuilt each day from what is actually true, not a fixed list on rotation. It
reads the forecast, the season, the farmer's own fields and their scouting notes,
then rotates subjects — spraying, soil, crop, disease watch, market, scheme — so
consecutive days do not repeat a theme. Two conditions jump the queue because
they are time-critical: rain due within hours (do not spray) and warm, humid air
(fungal disease weather). Tapping the tip opens the screen that acts on it.

It works with no network: without a forecast it still speaks from the season and
the farmer's crops, then upgrades itself once the weather loads.

## What the offline scanner can and cannot do

The bundled TFLite model has exactly **eight classes, all tomato**: bacterial
spot, early blight, healthy, late blight, leaf mould, septoria, spider mites and
yellow leaf curl. Point it at maize rust or wheat blight and it can only answer
with one of those eight, which will be wrong — it has no way to say "not in my
training set".

Two changes make that honest rather than misleading:

- The confidence threshold went from **0.3 to 0.55**. At 0.3 it produced
  confident boxes on anything, including a keyboard and a browser window.
- Anything under **70% confidence** now says "Not sure" instead of naming a
  disease, and the detection chip shows the percentage so a weak guess reads as a
  guess.

The scan screen carries a tappable note stating the tomato-only scope and a
button that switches straight to AI photo check, which handles any crop, pest,
weed or deficiency through the vision model.

## Profile

Split by what actually differs. The **username is the account identity and is
fixed**; region, district and date of birth are editable in place; the password
is changed in its own section because it requires the current one to authorise.
This replaced a dialog that asked you to pick "username or password" from a
toggle and then type the new value twice, which made changing your district
impossible.

Both signup and the profile screen can **set location from a satellite map**,
starting at your GPS fix. The picked point is snapped to the nearest district the
app holds coordinates for, so weather and mandi lookups keep working.

## Field mapping and satellite data

- **Basemap**: Esri World Imagery — satellite, keyless. OpenStreetMap's own tile
  servers return **403 Access blocked** to apps using a default or `com.example`
  user agent, which is why the street layer is a toggle rather than the default.
- **Place search**: Nominatim, keyless, and it knows Indian villages rather than
  only cities. Typing is debounced 450 ms because Nominatim asks callers not to
  fire on every keystroke.
- **Boundary detection**: one tap queries the Overpass API for an OSM `landuse`
  parcel under the map crosshair and snaps to it. Three public Overpass mirrors
  are tried in turn — the main one returns 504 under load often enough that a
  single endpoint makes the feature look broken when it is only busy. This is the keyless stand-in for
  the neural field delineation commercial apps run over Sentinel-2; where nobody
  has mapped the parcel, the farmer draws the corners instead.
- **Crop health**: NASA GIBS VIIRS vegetation index, free and keyless, **375 m per
  pixel on an 8-day composite**. That reads regional greening, not zones inside one
  small plot. Field-level 10 m NDVI needs Sentinel-2 through a keyed provider such
  as Sentinel Hub; NASA GIBS carries no Sentinel-2 optical layer.
- **Marking a field**: the crosshair is the contract — "Add corner here" drops a
  point exactly where it sits. It previously used the GPS fix instead, so lining
  up a corner and tapping the obvious button put the point at your own feet. A
  banner states the next step and the corner count, Done stays disabled until
  three corners exist, and every control is labelled in words rather than a bare
  icon.
- **Opening position**: the map resumes where it was last left, falling back to
  the most recently mapped field, then the registered town. It used to reset to
  the town every visit, which made mapping a second plot in the same village mean
  panning back each time. Plots already saved are drawn as pale outlines so a new
  boundary is not placed on top of one.
- **Area**: shoelace formula on an equirectangular projection about the plot's own
  latitude — under a percent of error at field scale, without a geodesy library.

## Accounts and sessions

- Passwords are stored as salted PBKDF2-HMAC-SHA1 (20 000 iterations), never in
  plain text. Accounts created before this change still log in once with their old
  password and are re-hashed on the way through.
- The signed-in user is held as an **HS256 JWT** in `EncryptedSharedPreferences`,
  signed with a per-install secret, valid for 7 days. Every screen reads the
  session rather than an intent extra, so it survives rotation and Back.
- A missing, expired, edited or foreign-signed token logs the user out.
- There is no server, so the JWT buys a real expiry and a tamper-evident payload,
  not server-side trust. That arrives with a backend.

## Tests

```bash
./gradlew test                  # JVM unit tests
./gradlew connectedAndroidTest  # 11 auth/session/city tests, needs a device
```

The instrumented suite covers JWT signing, expiry, forged signatures and tampered
payloads; password hashing, salting and the legacy-plaintext path; session
round-trip; and the region/city data.

## Feature notes

- **Disease detection** runs a bundled YOLOv8 TFLite model (`app/src/main/assets/model.tflite`)
  over CameraX frames. The emulator's virtual camera shows a synthetic scene — for real
  detection use a physical device, or set the AVD camera to "webcam0" in Device Manager.
- **AgriBot** (`chat/NvidiaChatClient.java`) is a full conversation, not a single
  question: it keeps the last 12 turns and builds the request with `JSONObject`,
  so quotes and newlines in a question can no longer break the JSON.
- **Weather** (`meteo.java`) is a WebView over a public Power BI report — needs network.
- **Design**: Material 3 throughout, one green palette in `values/colors.xml`,
  light and dark. Every screen insets itself for the system bars and the keyboard
  via `InsetsSupport`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Unsupported class file major version` / toolchain error | JDK too new — `export JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home` |
| `SDK location not found` | create `local.properties` (see above) |
| `Failed to find target with hash string 'android-36'` | install SDK Platform 36 in the SDK Manager |
| `adb: more than one device` | both a phone and an emulator are attached — use `--phone` or `--serial <id>` |
| "This app isn't 16 KB compatible / ELF alignment check failed" | a native library is not 16 KB page-aligned — TFLite 2.17+ and CameraX 1.4.2+ are, older ones are not |
| Chat says no API key configured | add `NVIDIA_API_KEY` to `local.properties` and rebuild |
| Chat says the model is unavailable | that model is not enabled for your NVIDIA account; change `NVIDIA_MODEL` in `app/build.gradle.kts` |
| `adb: no devices/emulators found` | emulator still booting — rerun, or `adb devices` to confirm |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.mvx.agriculture` then reinstall |
| Emulator won't start | check `/tmp/agricare-emulator.log` |
