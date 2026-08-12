<div align="center">

<img src="https://raw.githubusercontent.com/Mahan07dev/GalacticHub/main/icon.png" width="130" alt="Logo">
<br>

# 🚀 Galactic Hub

An ultra-sleek, lightweight, and customizable offline media hub for Android built using **Kotlin**, **Android WebView**, and an embedded **JavaScript Bridge**. **Galactic Hub** allows users to seamlessly catalog, search, and play video archives stored in compressed `.obb` expansion files or custom `.zip` packages without cluttering device media storage.

<p>
<a href="https://github.com/Mahan07dev/GalacticHub/releases"><img src="https://img.shields.io/badge/🚀%20Install%20right%20now!-7c3aed?style=for-the-badge" /></a>
<br><br>
<a href="https://github.com/Mahan07dev/GalacticHub/releases">
  <img src="https://img.shields.io/github/v/release/Mahan07dev/GalacticHub?style=for-the-badge" alt="Latest Version">
</a>
<a href="https://github.com/Mahan07dev/GalacticHub/releases">
  <img src="https://img.shields.io/github/release-date/Mahan07dev/GalacticHub?style=for-the-badge" alt="Latest Release Date">
</a>
<a href="https://github.com/Mahan07dev/GalacticHub"><img src="https://img.shields.io/github/stars/Mahan07dev/GalacticHub?style=for-the-badge" /></a>
<a href="https://github.com/Mahan07dev/GalacticHub/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Mahan07dev/GalacticHub?style=for-the-badge" /></a>
</p>
</div>

---

## 🌟 Key Features

- 🌌 **Futuristic Neon Interface:** Smooth, responsive RTL UI loaded inside a high-performance WebView wrapper.
- ⚡ **Economy / Performance Mode:** Toggleable dark/economy theme modes to eliminate animations, blur, and unnecessary graphics processing for low-end hardware.
- 🎬 **Dual Video Player Support:** Choose between an integrated native fullscreen Kotlin video player (`VideoPlayerActivity`) or send media to external system players (e.g., VLC, MX Player).
- 🏷️ **Dynamic Metadata Management:** In-app metadata editor allowing title, category, and description updates on-the-fly.
- 📦 **Flexible Media Sourcing:** Supports default OBB structures, custom `.obb` picker, and external `.zip` archives.
- 🖼️ **On-the-Fly Thumbnail Generation:** Automated background thumbnail extraction using Android `MediaMetadataRetriever` with multi-strategy frame fallback.
- 🔐 **Passkey Access Control:** Passkey security protecting app access (default passkey: `THE BOYS`).
- 📁 **JSON Import/Export:** Backup and sync video metadata across multiple installations.

---

## 💡 Crucial Notice: Bring Your Own OBB / ZIP File!

> **You do NOT actually need our pre-bundled `.obb` file!**  
> Galactic Hub operates as an open media engine. You can easily feed your **own video collection** (packed into a `.zip` or `.obb` file) directly into the app using any of the supported source methods below.

---

## 📂 How to Add Your Own Video Archive (`.obb` or `.zip`)

Galactic Hub scans `.obb` and `.zip` archives for video formats including `.mp4`, `.mkv`, `.webm`, `.avi`, and `.mov`. You can organize your video archives using any of the following **3 Methods**:

---

### 🔹 Method 1: Using the Built-in In-App File Picker (Recommended & Standard)

No root or file manager required!

1. Open **Galactic Hub**.
2. On the main home screen, tap **Settings (تنظیمات)**.
3. Locate the **OBB Configuration / Storage** section.
4. Tap **Pick OBB/ZIP File (انتخاب فایل OBB)**.
5. Browse your local device storage, SD Card, or Download folder and select any valid `.obb` or `.zip` file.
6. The app will immediately copy and bind your archive. Tap **Save**, and the hub will automatically extract thumbnails and index all video entries!
7. *To revert back to system default paths, simply click **Reset OBB (بازنشانی OBB)**.*

---

### 🔹 Method 2: Standard Android OBB Directory (Manual Placement)

If you prefer placing files via a USB cable or Android File Manager:

1. Package your video files into an uncompressed or standard `.zip` archive.
2. Rename the extension from `.zip` to `.obb` (e.g., `main.1.com.mahanverse.galactichub.obb`).
3. Place the file inside your device's standard OBB folder:

   `/storage/emulated/0/Android/obb/com.mahanverse.galactichub/`

4. Relaunch **Galactic Hub** — the application automatically scans and auto-detects `.obb` files placed in its registered package directory.

---

### 🔹 Method 3: Internal Application Directory

For developers or custom system builds:

1. Store or push your OBB file to the internal app directory:

   `/data/data/com.mahanverse.galactichub/files/`

2. You can also import custom `metadata.json` alongside it to auto-populate titles, categories, and descriptions!

---

## 📝 Customizing Metadata (`metadata.json`)

To provide rich titles, categories, and descriptions for your custom video files:

1. Create a `metadata.json` file formatted as follow:

```json
{
  "videos/my_epic_video.mp4": {
    "title": "My Epic Video Title",
    "category": "Tutorials",
    "description": "An in-depth guide to Galactic Hub."
  },
  "videos/nature.mp4": {
    "title": "Nature Clips",
    "category": "Documentary",
    "description": "Scenery recorded in 4K."
  }
}
```

2. Navigate to **Settings → Import Metadata** to load your configuration, or edit entries directly within the UI by clicking the edit icon (✎) on any video card!

---

## 🔐 Default Authentication

- **Default Passkey:** `THE BOYS`
- You can update or change this password anytime in **Settings → Passkey Settings**.

---

## 🛠️ Build & Architecture Overview

- **Language:** Kotlin & JavaScript (ES6+)
- **UI Framework:** HTML5/CSS3 (RTL Persian Layout with Custom CSS Variables & Glassmorphism)
- **Minimum SDK:** Android 5.0 (API Level 21)
- **Core Components:**
  - `MainActivity.kt`: Manages OBB file searching, Zip parsing, background coroutines, and WebApp JavaScript interface bindings (`AndroidInterface`, `VideoPlayerInterface`).
  - `VideoPlayerActivity.kt`: Native video container featuring standard Android `MediaController` and multi-orientation support.
  - `VideoProvider.kt`: ContentProvider streaming backend for pipe-based data streaming directly out of archive files.

---

## 📄 License & Credits

Developed by **Mahan07dev (Galactic Hub Team)**  
© 2026 **THE BOYS** — All Rights Reserved.
