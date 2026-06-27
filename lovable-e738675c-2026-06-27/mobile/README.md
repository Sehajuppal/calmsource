# CalmSource — Web + Android + TV

Three apps share this repo:

- **Web app** — TanStack Start, lives in `/src`, runs in the Lovable preview and is what gets published with the Publish button.
- **Android phone/tablet app** — Expo / React Native, lives in this `/mobile` folder, ships as an APK / AAB.
- **Android TV / Fire TV app** — same `/mobile` codebase. The manifest declares a `LEANBACK_LAUNCHER` intent so the same APK appears in the TV launcher, and the UI auto-switches to a 10-foot layout (larger tiles, top tab bar, D-pad focus ring) when running on a TV-class device.

The mobile/TV app uses native components and ExoPlayer for HLS.

---

## Android — quick path (cloud build, no Android Studio)

Recommended. Builds run on Expo's servers; you get an installable `.apk`.

```bash
cd mobile
npm install
npm install -g eas-cli
eas login                       # free Expo account
eas build:configure             # one-time, links the project
eas build -p android --profile preview
```

When the build finishes, EAS prints a URL — open it on your Android phone and install the APK (allow "Install unknown apps" for your browser the first time).

Profiles defined in `eas.json`:
- `preview` → APK for sideloading / internal testing (works on phones, tablets, Android TV, Fire TV)
- `tv`      → APK tuned for TV sideloading (same binary, explicit TV profile for clarity)
- `production` → AAB for Google Play
- `development` → APK with the Expo dev client

### Installing on Android TV / Fire TV

1. Build the APK: `eas build -p android --profile tv` (or use the `preview` APK — same result).
2. Sideload it:
   - **Fire TV**: enable *Settings → My Fire TV → Developer options → Apps from Unknown Sources*, then push with `adb install lumen.apk` or use the **Downloader** app.
   - **Android TV / Google TV**: enable *Settings → Apps → Security → Unknown sources*, then `adb install lumen.apk` over the LAN (`adb connect <tv-ip>:5555`).
3. Launch from the TV home screen — D-pad focus, larger tiles, and a top nav rail activate automatically.

## Android — local path (Android Studio required)

```bash
cd mobile
npm install
npm run android                 # builds + installs on a connected device/emulator
```

Release APK:

```bash
cd android
./gradlew assembleRelease
# APK: android/app/build/outputs/apk/release/
```

Prereqs: Node 18+, Android Studio with the SDK, JDK 17 (`JAVA_HOME`), `ANDROID_HOME` set, USB debugging on or an emulator running.

---

## Web app

Nothing to do here — edit files under `/src` and use the Lovable preview and Publish button as usual. The `/mobile` folder is ignored by the web build.

## Screens (Android)

- **Profile Gate** — picker, manage/rename, add up to 5 profiles, persisted via `AsyncStorage`.
- **Home** — cinematic hero, "Up Next" with progress bars, content rows.
- **Live TV** — `expo-video` HLS player, search, grouped channels, demo channels preloaded.
- **Settings** — load M3U from URL or `.m3u` file, reset to demo, switch profile.

Notes: HLS plays natively via ExoPlayer (no `hls.js`). All data is client-side in `AsyncStorage`.
