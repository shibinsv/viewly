# Viewly

Viewly is a modern, offline-first Android application built with Jetpack Compose for seamless local viewing and management of documents (`.pdf`, `.docx`, `.xlsx`, and `.pptx`). It operates 100% locally on-device with zero network access required.

<img width="500" height="500" alt="viewly" src="https://github.com/user-attachments/assets/c43c225a-8d9a-4050-9448-b49e6e19f15b" />

---

## 📱 App Info & Features

- **Supported Formats**:
  - **PDF**: Fast, smooth rendering via Jetpack Compose PDF reader.
  - **Microsoft Office (`.docx`, `.xlsx`, `.pptx`)**: High-fidelity offline rendering powered by a custom WebView and WASM/JS Office Open XML viewing engine (`viewer` module).
- **Offline & Secure**: No `INTERNET` permission required. Documents are read and rendered entirely on-device.
- **Device Document Discovery**: Automatically scans local external storage and MediaStore for supported files, organizing them by type, folder, and modification date.
- **Modern UI / UX**: Built with Jetpack Compose and Material 3, supporting Edge-to-Edge display, zoom, page navigation, sharing, and search.

---

## 📸 App Screenshots
<img width="350" height="800" alt="image" src="https://github.com/user-attachments/assets/7b59feeb-49eb-4ad4-90f5-e920ebc99882" />
<img width="350" height="800" alt="image" src="https://github.com/user-attachments/assets/ed7a02e1-1339-4f20-9e98-ce24176aa542" />


---

## 🏗️ Architecture & Modules

Viewly is split into two modules:
1. **`:app`**: Main application module containing UI screens ([HomeScreen.kt](file:///Users/shibinreshma/AndroidStudioProjects/Viewly/app/src/main/java/shibin/kmp/viewly/presentation/HomeScreen.kt), [DocumentViewerScreen.kt](file:///Users/shibinreshma/AndroidStudioProjects/Viewly/app/src/main/java/shibin/kmp/viewly/presentation/DocumentViewerScreen.kt)), file scanners, and theme definitions.
2. **`:viewer`**: A self-contained Android library module packaging the Office Open XML webjar, asset extractor (`packageOoxmlZip`), and `OfficeDocumentViewer` composable.

---

## 🛠️ Tech Stack

- **UI Framework**: Jetpack Compose & Material 3 (Edge-to-Edge)
- **Language**: Kotlin 2.2+
- **Build System**: Gradle with Version Catalogs (`libs.versions.toml`) & Android Gradle Plugin (AGP) 9.x
- **SDK**: Compile SDK 37, Min SDK 24, Target SDK 37
- **Key Libraries**:
  - `androidx.compose` (Foundation, Material 3, UI)
  - `androidx.webkit`
  - `compose-pdf-reader`

---

## 🚀 Setup & Getting Started

### Prerequisites
- **Android Studio**: Ladybug / Meerkat or newer.
- **JDK**: Java 11 or higher.
- **Android SDK**: API 37 installed.

### Quick Start
1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/viewly.git
   cd Viewly
   ```
2. **Open in Android Studio**:
   - Open Android Studio, select **Open**, and choose the `Viewly` root directory.
3. **Sync Gradle**:
   - Allow Android Studio to sync the project with Gradle (or click **Sync Project with Gradle Files**).
4. **Build and Run**:
   - Connect an Android device or emulator (API 24+).
   - Run the app via Android Studio or build via terminal:
     ```bash
     ./gradlew assembleDebug
     ```
