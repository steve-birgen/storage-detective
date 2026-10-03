# StorageDetective 🔍📱

**StorageDetective** is a modern, high-performance Android storage analyzer and cleanup utility built natively with **Jetpack Compose**, **Kotlin Coroutines**, **StateFlow**, and **Room Database**. 

It provides deep insights into device storage distribution, visualizes disk usage with interactive **Treemaps**, identifies **Duplicate Files** using SHA-256 hashing, offers a safe **Recycle Bin** quarantine, cleans disposable **Junk & Cache**, tracks **Storage History Trends**, and generates **AI-Powered Smart Cleanup Suggestions**.

---

## 🌟 Key Features

### 📊 Storage Summary & Certainty Classification
- Analyzes total, used, and free storage using Android `StatFs`.
- Classifies storage into three certainty tiers:
  - **Identified**: Media files (Photos, Videos, Audio), Documents, and Downloads.
  - **Attributed**: Installed applications and app data.
  - **Unclassified**: System storage and inaccessible app internals.

### 🗺️ Interactive Storage Treemap
- **Proportional Layout Algorithm**: Uses a squarified recursive slice-and-dice layout algorithm to render space-filling rectangles proportional to actual storage consumption.
- **Interactive Details**: Tap any block or legend item to view exact sizes and usage percentages.
- **Dual View Modes**:
  - **Category Mode**: Groups storage into primary categories (Photos, Videos, Apps, Documents, etc.).
  - **Detailed Mode**: Breaks down top individual applications alongside storage categories.

### 🧹 Junk & Cache Cleaner
- Scans internal and external app caches, temporary files (`.tmp`), log files (`.log`), and backup caches (`.bak`, `.chk`).
- Displays a cleanable junk summary grouped by category.
- Reclaims space with **1-tap cleanup**.

### 👯 Fast Duplicate File Detector
- **Two-Phase Detection**:
  1. **Size Filtering**: Groups files by exact byte size to eliminate unique files rapidly.
  2. **SHA-256 Content Hashing**: Hashes candidate files with matching sizes to guarantee true content duplicates without unnecessary I/O.
- Displays duplicate sets with wasted space metrics and original vs. duplicate tags.

### 🗑️ Quarantine Recycle Bin
- Safely moves deleted duplicate or large files to an isolated in-app quarantine directory (`recycle_bin`).
- Tracks file metadata (`originalPath`, `fileName`, `sizeBytes`, `recycledTimestamp`) using a **Room Database**.
- Features 1-tap **Restore** (↺) to move files back to their original location, or **Delete Permanently** (🗑) / **Empty Bin**.

### 📱 App Manager Hub
- Detailed manager for installed applications showing total size, data size, and cache size.
- **Segmented Sorting**: Sort apps **By Total Size**, **By Cache Size**, or **By Data Size**.
- **Direct System Settings Action**: One-tap jump to Android System App Settings (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) to clear app cache or uninstall heavy apps.

### 💡 Smart Recommendations
- AI/Rule-based engine analyzing current storage state to generate prioritized recommendations (`HIGH`, `MEDIUM`, `LOW` priority):
  - *Clean Temporary Junk*
  - *Remove Duplicate Files*
  - *Review Heavy Large Files (>100MB)*
  - *Manage Heavy Applications*
- Direct 1-tap resolution buttons on each suggestion card.

### 📈 Storage History Trend Chart
- Custom **Jetpack Compose Canvas** trend chart visualizing historical storage snapshots over time.
- Renders gradient-filled area curves, data points, and usage percentages recorded in the Room database.

### 🔔 Alerts & System Notifications
- Creates a dedicated **Storage Alerts** notification channel (`storage_alerts_channel`).
- Automatically triggers system alerts and in-app warning banners when storage usage reaches critical levels (**>= 85%**) or cleanable junk exceeds **200 MB**.

### 📉 Delta Growth Tracking
- Calculates day-to-day growth or reduction per storage category compared to previous scans.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin `2.0.20`
- **UI Framework**: Jetpack Compose with Material 3 Design
- **Architecture Pattern**: MVVM (Model-View-ViewModel) + `StateFlow` & Coroutines
- **Database**: Room `2.6.1` with **KSP** (Kotlin Symbol Processing)
- **Async & Concurrency**: Kotlin Coroutines (`Dispatchers.IO`) & `StateFlow`
- **Build System**: Android Gradle Plugin (AGP `8.5.2`) & Gradle 8.7 / 9.x compatible
- **Target SDK**: Android 14 (API 34) | **Min SDK**: Android 7.0 (API 24)

---

## 📁 Directory Structure

```
StorageApp/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/yourapp/storageapp/
│           ├── MainActivity.kt
│           ├── storage/
│           │   ├── StorageDetectiveActivity.kt
│           │   ├── StorageDetectiveScreen.kt   # Jetpack Compose UI Components
│           │   ├── StorageViewModel.kt         # UI State & Storage Logic
│           │   ├── StorageScanner.kt           # File Scanner & SHA-256 Hasher
│           │   ├── StorageNotificationHelper.kt# System Notification Channel & Alerts
│           │   ├── DeltaCalculator.kt          # Historical Growth Calculator
│           │   ├── data/
│           │   │   ├── StorageDao.kt           # Room DAO Interface
│           │   │   └── StorageDatabase.kt      # Room Database Definition
│           │   └── models/
│           │       └── StorageModels.kt        # Domain & UI Models
│           └── ui/theme/
│               └── Theme.kt                    # Material 3 Color Schemes
├── build.gradle
└── README.md
```

---

## 🚀 Getting Started & Building

### Prerequisites
- **Android Studio** (Jellyfish / Ladybug or newer recommended)
- **JDK 17**
- Android device or emulator running **Android 7.0 (API 24)** or higher

### Building the Project
Open a terminal in the project root directory and run:

```bash
# Assemble Debug APK
./gradlew :app:assembleDebug

# Run Unit Tests
./gradlew :app:testDebugUnitTest
```

---

## 🔐 Required Permissions
StorageDetective requests standard Android permissions depending on OS version:
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 12 & below)
- `PACKAGE_USAGE_STATS` (Usage Access in Settings for App Storage sizes)
- `POST_NOTIFICATIONS` (Android 13+ for storage alerts)

---

