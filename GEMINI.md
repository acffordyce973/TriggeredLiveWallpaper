# Gemini Agent Guide - Triggered Live Wallpaper

## 📌 Project Overview
**Triggered Live Wallpaper** is a battery-efficient, offline-first Android Live Wallpaper application built using **Kotlin**, **Jetpack Compose**, and Android platform background services. It dynamically changes wallpapers based on real-time device conditions (screen orientation, fold state, charging, time windows, day of week, seasonal months, and GPS geofences) and system triggers (intervals, screen on/unlock, double-tap, orientation changes, fold transitions, and quick settings tiles).

- **Package Name / Namespace:** `com.antigravity.triggeredwallpaper`
- **Minimum SDK:** `24` (Android 7.0)
- **Target / Compile SDK:** `36` (Android 16)
- **Primary Architecture:** MVVM + Jetpack Compose Material 3 UI + Android WallpaperService engine + WorkManager & AlarmManager schedulers.

---

## 🏗️ Directory Structure
- `app/src/main/java/com/antigravity/triggeredwallpaper/`
	- `engine/`: Wallpaper rendering logic (`WallpaperEngine.kt`), condition evaluation (`ConditionEvaluator.kt`), image caching & rotation (`ImageManager.kt`), and hardware state collection (`DeviceStateCollector.kt`).
	- `service/`: `TriggeredWallpaperService.kt` (Android Live Wallpaper engine service), `TriggeredWallpaperTileService.kt` (Quick Settings toggle for Wallpaper Override), and `TriggeredWallpaperNextTileService.kt` (Quick Settings tile to advance wallpaper).
	- `scheduler/`: AlarmManager wakeful alarm scheduling (`AlarmScheduler.kt`) and WorkManager tasks for interval rotation.
	- `receiver/`: `WallpaperAlarmReceiver.kt` (handles boot, power connect/disconnect, and exact alarm broadcasts) and `WallpaperActionReceiver.kt` (handles notification action clicks).
	- `model/`: Data structures (`WallpaperModels.kt`) including `FolderSet`, `DeviceState`, `AppSettings`, `GeofenceArea`, `WallpaperTarget`, and conditions.
	- `data/`: Local persistent store (`ConfigStore.kt` for JSON sets/geofences, `PreferencesStore.kt` for app settings).
	- `ui/`: Compose navigation, components, and screens:
		- `StatusScreen.kt`: Real-time sensor diagnostics and condition match indicators.
		- `SetsScreen.kt` & `EditSetScreen.kt`: Creation, editing, and rule management for Wallpaper Sets.
		- `SettingsScreen.kt`: Global app preferences, overlay configuration, and backup import/export.
		- `GeofencePickerScreen.kt`: OpenStreetMap interactive map picker with geocoding and radius preview.
		- `OpenImageActivity.kt`: Lightweight trampoline activity to view the current wallpaper in external gallery apps.
- `.github/workflows/`:
	- `github-actions.yml`: CI/CD workflow mirroring PSO2 Alert to build signed release APKs and publish GitHub releases upon tag push.
- Scripts & Tooling:
	- `BuildDebug.bat`: One-click command to compile the debug APK (`./gradlew assembleDebug`).
	- Releases: Automated via GitHub Actions on tag push (local `BuildRelease` scripts are no longer needed or maintained).

---

## 📋 MANDATORY DOCUMENTATION RULES FOR AI AGENTS

Whenever you make changes to this codebase, you **MUST** uphold the following documentation requirements:

1. **Permission Changes (`AndroidManifest.xml`):**
	- If ANY permission is added, modified, or removed, you **MUST immediately update the Permissions table in [`README.md`](file:///D:/Personal/Projects/TriggeredWallpaper/README.md)**.
	- Clearly state the permission name and its exact, user-facing technical justification.

2. **Library / Dependency Changes (`gradle/libs.versions.toml` or `build.gradle.kts`):**
	- If ANY new library, SDK, or third-party dependency is introduced, you **MUST credit it in [`README.md`](file:///D:/Personal/Projects/TriggeredWallpaper/README.md)** under the **Credits & Acknowledgments** section.

3. **Feature / Trigger / Condition Changes:**
	- Any newly added features, conditions, triggers, or UI capabilities **MUST be documented in [`README.md`](file:///D:/Personal/Projects/TriggeredWallpaper/README.md)** under Features, Supported Conditions, or Supported Triggers.
	- Any modified or removed features must be kept synchronized in [`README.md`](file:///D:/Personal/Projects/TriggeredWallpaper/README.md).

---

## 📐 Coding Conventions

### 1. Function Naming
- Format: `<purpose><Name>` (camelCase starting with a purpose verb followed by PascalCase name):
	- Purpose verbs: `get`, `set`, `has`, `is`, `build`, `update`, `clean`, `load`, `save`, `format`, `evaluate`, etc.
	- Examples: `getWallpaperTarget`, `isWithinTimeWindow`, `evaluateSetConditions`, `formatBackupFilename`.

### 2. Variable Naming
- Format: `<typePrefix><Name>`:
	- `bool` for booleans (e.g. `boolEnabled`, `boolCharging`)
	- `string` for strings (e.g. `stringName`, `stringFolderUri`)
	- `int` for integers (e.g. `intIntervalMinutes`, `intStartHour`)
	- `float` / `double` for floats/doubles (e.g. `floatRadiusMeters`, `doubleLatitude`)
	- `list` for lists (e.g. `listFolderUris`, `listDaysOfWeek`)
	- `file` / `directory` for files and directories (e.g. `fileSourceApk`, `directoryProject`)
	- `obj` for generic object instances (e.g. `objDeviceState`)

### 3. Indentation
- Use **tabs** for indentation across all files (Kotlin, Gradle, Batch, Markdown where applicable). Do not use spaces.

### 4. Versioning
- Semantic versioning format: `<major>.<minor>.<revision>`.
- Tracked in `app/build.gradle.kts` via `versionName` and `versionCode`.
- Increment rules:
	- Breaking change / overhaul: Bump **major** (`1.1.1` → `2.0.0`).
	- New features / triggers / conditions: Bump **minor** (`1.1.1` → `1.2.0`).
	- Bug fixes, optimizations, UI polish: Bump **revision** (`1.1.1` → `1.1.2`).
- Always increment `versionCode` alongside `versionName`.
- **Push & Release Tagging:** When the version is incremented and changes are committed, the AI assistant **must push commits to GitHub** (`git push origin <branch>`) and **push a git tag for that version** (`git tag <version>` and `git push origin <version>`) so that GitHub Actions triggers and builds the new release.

---

## 🔐 Keystore and Secrets Safety
- **NEVER** commit `keystore.properties`, `*.keystore`, `*.jks`, or `local.properties` into git or GitHub.
- For CI/CD, signing keys and properties are decoded from GitHub Secrets:
	- `KEYSTORE_KEY_BASE64`
	- `KEYSTORE_KEY_PATH`
	- `KEYSTORE_PROPERTIES_BASE64`
	- `KEYSTORE_PROPERTIES_PATH`
	- `REPO_TOKEN`
- Local debug builds sign using release config if `keystore.properties` is present, or fall back to default debug keystore if absent.
