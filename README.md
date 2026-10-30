# Cross with Friends - Android Application

A high-performance, native Android wrapper for [Cross with Friends](https://www.crosswithfriends.com/) (collaborative real-time multiplayer crossword puzzles).

---

## 🌟 Features Implemented

* **⚡ Real-Time Multiplayer Support**: Full WebSocket (`Socket.IO`), DOM Storage, and IndexedDB support for synchronized puzzle solving with friends.
* **📱 Immersive Mobile UI**: Edge-to-edge layout with adaptive soft keyboard handling (`adjustResize`) so crossword clues and input cells remain visible while typing.
* **🔗 Deep Linking**: Tapping any puzzle room link (`https://www.crosswithfriends.com/...` or `https://downforacross.com/...`) automatically opens directly in the app.
* **🔄 Pull-to-Refresh (`SwipeRefreshLayout`)**: Easily reload when disconnected, intelligently disabled while scrolling puzzle grids to prevent accidental reloads.
* **📤 Share Sheet Integration**: Dedicated "Share Room" button in the top action bar to instantly invite friends via WhatsApp, Discord, Messages, etc.
* **🔐 Google OAuth & External Link Compatibility**: Custom-engineered User-Agent and Chrome Custom Tabs integration to bypass Google's `disallowed_useragent` restriction for WebViews.
* **📂 Puzzle File Chooser**: Supports uploading `.puz` (NYT format), `.cpf`, and text puzzle files natively.
* **📶 Offline State Handler**: Graceful offline fallback screen with an instant "Retry" button.
* **🔙 Smart Back Button**: Intercepts Android back gestures to navigate crossword history without accidentally quitting the app.

---

## 🚀 How to Build the APK

### Method 1: Automated Cloud Build via GitHub Actions (No local SDK required)

If you don't have Android Studio installed, you can build the APK in 2 minutes for free using GitHub Actions:

1. Initialize a Git repo and push to GitHub:
   ```bash
   cd /Users/ark/.gemini/antigravity/scratch/crosswithfriends-android
   git init
   git add .
   git commit -m "Initial commit for Cross with Friends Android app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/crosswithfriends-android.git
   git push -u origin main
   ```
2. Open your repository on GitHub and click on the **Actions** tab.
3. The **Build Android APK** workflow will automatically run.
4. Once completed (approx. 1-2 minutes), download the ready-to-install **`CrossWithFriends-Debug-APK`** or **`CrossWithFriends-Release-APK`** from the **Artifacts** section!

---

### Method 2: Build with Android Studio (Recommended for local dev)

1. Open **Android Studio**.
2. Select **File > Open...** and select `/Users/ark/.gemini/antigravity/scratch/crosswithfriends-android`.
3. Wait for Gradle to sync dependencies.
4. In the top menu, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will generate the `.apk` file at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

### Method 3: Command Line Build (Requires JDK 17+ and Android SDK)

```bash
cd /Users/ark/.gemini/antigravity/scratch/crosswithfriends-android
./gradlew assembleDebug
```
The output APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📲 How to Install the APK on Your Android Device

### Option A: Transfer via Google Drive / WhatsApp / Email
1. Send the `app-debug.apk` to your phone via Google Drive, WhatsApp, or Telegram.
2. Tap the file on your phone.
3. If prompted, toggle **Allow from this source** in Android Settings.
4. Tap **Install** and enjoy!

### Option B: Install via ADB (USB Debugging)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚡ Instant No-Install Alternative: Chrome WebAPK (PWA)

If you need immediate access on your phone without building an APK right this second:
1. Open Chrome on your Android device and visit `https://www.crosswithfriends.com/`.
2. Tap the **Three Dots Menu (⋮)** in the top right.
3. Tap **Install app** or **Add to Home screen**.
4. Android creates a standalone WebAPK with its own icon on your home screen and launcher.
