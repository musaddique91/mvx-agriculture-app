# AgriCareAI - Intelligent Agricultural Mobile Assistant

**AgriCareAI** is an advanced native Android application built in **Java** designed to empower farmers with real-time weather advisories, AI-powered disease & pest detection, smart chatbot assistance, market prices (Mandi rates), satellite-assisted field mapping, offline agricultural tools, and multi-language support.

---

## 🌾 Features & Architecture Overview

| Feature / Module | Description & Capabilities |
|---|---|
| **Home Dashboard** | Personalized greeting, local district weather, time-critical daily advisories (spray window, irrigation, heat stress), smart daily tip generator, and feature shortcuts |
| **Scan & AI Diagnosis** | **Offline TFLite Scanner** (real-time camera detection for tomato leaf diseases) + **AI Photo Check** (multimodal vision AI for any crop: diseases, pests, weeds, nutrient deficiencies) |
| **AgriBot AI Assistant** | Multi-turn conversational AI powered by NVIDIA NIM (`meta/llama-3.2-11b-vision-instruct`), providing context-aware answers in the user's preferred language |
| **Weather & Forecast** | 7-day forecast with soil temperature/moisture, spray windows, irrigation recommendations, and embedded interactive Power BI weather dashboard |
| **Mandi Market Prices** | Daily Agmarknet commodity prices auto-filtered to the farmer's district first, with option to expand statewide |
| **Field Mapping & GIS** | Esri World Imagery basemap, Nominatim village search, tap-to-detect parcel boundaries via Overpass API (OSM), area calculation (acres/ha/guntha), and NASA GIBS VIIRS vegetation index |
| **My Fields & Scouting** | Saved plot boundaries, crop details, scouting notes, and AI crop recommendations |
| **Offline Encyclopedia** | Offline reference guide for crop diseases with symptoms, causes, preventive measures, and solution detail sheets |
| **Crop Calendar** | Sowing and harvest schedules for 16 major crops, prioritized by current season |
| **Calculators** | Fertilizer dose optimizer (Urea/DAP/MOP), seed rate calculator, and spray mix estimator |
| **Government Schemes** | Information and official links for PM-KISAN, PMFBY, KCC, Soil Health Card, and state agricultural initiatives |

---

## 🎨 Visual Themes

AgriCareAI features two user-switchable visual themes that persist across sessions:
- **Green (Default)**: Material 3 design system with light and dark mode support, using system bar insets.
- **Soft (Neumorphic)**: 3D embossed and inset UI surfaces rendered using `theme/NeumorphDrawable` with dual blurred shadow layers and tree-walking background replacement via `theme/SoftTheme`.

---

## 🌐 Multi-Language Support

Supports 5 languages switchable at runtime from the toolbar or navigation drawer:
- **English**
- **Hindi (हिन्दी)**
- **Kannada (<ctrl42><ctrl42>कन्नड / ಕನ್ನಡ)**
- **Marathi (मराठी)**
- **Urdu (اردو)** — Integrated with **Noto Nastaliq Urdu** font via runtime theme overlays (`LocaleManager.applyFont()`), ensuring proper RTL rendering while preserving theme colors.

AgriBot and AI Photo Diagnosis respond directly in the user's active language.

---

## 🔊 Voice Help (for farmers who prefer listening to reading)

Everything works by voice in the app's current language (hi-IN, kn-IN, mr-IN, ur-IN, en-IN):

- **Listen to any screen**: a speaker button in the toolbar reads the screen's title and content aloud (weather, mandi prices, schemes, crop journey, fields…). Tap it again to stop. It works from the live view tree, so new screens need no extra code.
- **Speak instead of typing**: every text box gets a mic icon, including the add-money, save-field, scouting-note and start-season dialogs, plus login and signup. For number boxes (amount, quantity, rate, days) the number is pulled out of what was said, and Devanagari, Kannada and Urdu digits are handled.
- **AgriBot and scan results aloud**: AgriBot answers and AI photo diagnoses are read out automatically (you can turn this off), and every answer bubble has its own listen button. The disease encyclopedia sheet has one too.
- **Voice help settings** (toolbar ⋮ or drawer): speaking speed (slow / normal / fast), auto-read on/off, a test sentence, and a shortcut to download the phone's voice for your language. If the phone has no voice for the language, the app offers to download one; once downloaded it works offline.
- **Voice engine fallback**: if the phone's default engine (e.g. Samsung's) lacks the language, every other installed engine is tried, Google's first, preferring downloaded voices over network ones. For Urdu with no Urdu voice anywhere, the text is rewritten in Devanagari (`UrduScript`) and read by a Hindi voice, with a one-time hint on how to get a real Urdu voice.

Code lives in `app/src/main/java/com/mvx/agriculture/voice/`: `Speaker` (one shared text-to-speech engine), `ScreenReader`, `VoiceInput` and `SpeechText` (text shaping, unit-tested in `SpeechTextTest`).

---

## 🔐 Security & Authentication

- **Password Hashing**: Passwords stored using salted **PBKDF2-HMAC-SHA1** (20,000 iterations). Legacy accounts automatically migrate upon login.
- **Session Tokens**: Authenticated users hold an **HS256 JWT** stored in `EncryptedSharedPreferences` with 7-day validity.
- **Local Persistence**: User profile, district preferences, mapped fields, and offline notes are securely managed via a local SQLite database.

---

## 📍 Region & Location Setup

Region and city selections are dynamically populated from `assets/cities.csv`:
- **Tunisia** (24 Governorates)
- **Karnataka, India** (33 Districts/Cities)
- **Maharashtra, India** (36 Districts/Cities)

Additional regions and cities can be configured directly in `cities.csv` without modifying application source code.

---

## 🔑 API Key Configuration

Keys are stored in `local.properties` (gitignored) and supplied to the build as `BuildConfig` parameters:

```properties
NVIDIA_API_KEY=nvapi-...      # Powers AgriBot chatbot & AI Photo Check
DATA_GOV_API_KEY=your_key     # Powers live Mandi commodity rates
```
*Note: Without API keys, the app functions smoothly; AI and Mandi screens will display configuration guidance while all offline tools and weather features remain fully active.*

---

## 🚀 Quick Start Guide

### Prerequisites
- **JDK 17–21** (Required by AGP 8.13 / Gradle 8.13)
- **Android SDK Platform 36** (`compileSdk` / `targetSdk` = 36)
- **Android Emulator / Device** (API 34+)

### Automated Setup & Run
Execute the automated run script inside `Mobile App/AgriCareA`:

```bash
cd "Mobile App/AgriCareA"
./run.sh
```
What `./run.sh` does automatically:
1. Detects Android Studio JDK 17-21 / system `JAVA_HOME`.
2. Generates `local.properties` pointing to your local Android SDK.
3. Boots an emulator if no active device is connected.
4. Compiles the app (`./gradlew assembleDebug`).
5. Installs and launches `com.mvx.agriculture`.

### Command Line Options
```bash
./run.sh --device        # Run on an attached physical device or emulator
./run.sh --serial <id>   # Target a specific device serial ID
./run.sh --avd <name>    # Boot a specific Android Virtual Device
./run.sh --clean         # Perform a clean build prior to running
```

---

## 🧪 Running Tests

```bash
cd "Mobile App/AgriCareA"
./gradlew test                  # JVM Unit Tests (models, filters, session JWTs)
./gradlew connectedAndroidTest  # Instrumented UI & session tests (requires connected device/emulator)
```

---

## 🛠️ Technical Stack

- **Framework**: Native Android (Java 17, AGP 8.13.2, Gradle 8.13, Android SDK 36, Min SDK 24)
- **ML / AI**: TensorFlow Lite Interpreter, CameraX, NVIDIA NIM (`meta/llama-3.2-11b-vision-instruct`)
- **GIS & Mapping**: Esri World Imagery, Nominatim, Overpass API, NASA GIBS VIIRS
- **Security**: AndroidX Security Crypto (`EncryptedSharedPreferences`), PBKDF2, JWT (HS256)
- **UI Architecture**: Material Design 3, AndroidX Navigation, Custom Neumorphic Drawables
