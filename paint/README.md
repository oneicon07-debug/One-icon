# Mini Paint 3D (Android)

A thin native Android shell around the single-file `Mini Paint` web app
(`android/app/src/main/assets/www/index.html`). The app runs inside a
WebView served from a virtual `https://appassets.androidplatform.net`
origin, so canvas, IndexedDB, layers/frames autosave and MediaRecorder
video export all behave the same as in a desktop browser. CDN scripts the
page loads itself (fonts, jszip, jsQR, the AI inpainting model) load over
the phone's real internet connection, so the app needs network access the
first time those features are used.

Exported PNG/JPEG images and recorded MP4/WebM animations are saved to
**Pictures/Mini Paint** or **Movies/Mini Paint** in the phone's gallery.

## Building

GitHub Actions builds this automatically on every push that touches
`paint/**` (see `.github/workflows/build-paint-apk.yml`) and publishes a
direct-download APK as a GitHub Release:

https://github.com/oneicon07-debug/One-icon/releases/latest/download/Mini-Paint-3D.apk

To build locally:

```
cd paint/android
./gradlew assembleDebug
```

The APK is signed with a fixed debug key (`app/debug.keystore`) so newer
builds install over older ones without losing saved drawings.
