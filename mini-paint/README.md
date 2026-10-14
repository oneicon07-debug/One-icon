# Mini Paint (Android app)

The Mini Paint web page (`app/src/main/assets/index.html`) wrapped in a full-screen WebView.
Works offline. Built automatically by `.github/workflows/build-mini-paint.yml`.

Download (latest build):
https://github.com/oneicon07-debug/One-icon/releases/download/mini-paint/Mini-Paint.apk

Changes to the page itself:
- JSZip is loaded from `jszip.min.js` bundled in the app instead of a CDN.
- Home screen footer (copyright lines + Privacy Policy | Terms of Use pop-ups) under "Draw · Animate · Create".
