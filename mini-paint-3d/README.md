# Mini Paint 3D - Fixed_31 (Android app)

The Mini Paint page `Mini_Paint_3D_Fixed_31.html` (`app/src/main/assets/index.html`) wrapped in a
full-screen WebView. The HTML file is used exactly as supplied (byte-for-byte; the build checks this).

Built automatically by `.github/workflows/build-mini-paint-3d.yml` on the branch
`mini-paint-3d-fixed-31`. This branch is independent: it is not merged into `main` and does not
change the other Mini Paint branches.

Download (latest build):
https://github.com/oneicon07-debug/One-icon/releases/download/mini-paint-3d-fixed-31/Mini-Paint-3D-Fixed-31.apk

What the app adds around the page (the page itself is not edited):
- Camera permission for the QR scanner (asked when the scanner is first opened).
- "Save" goes to Pictures / Movies / Music / Downloads in a "Mini Paint" folder.
- JSZip and jsQR (the two libraries the page loads from cdnjs) are bundled at build time, so
  zip export/import and the QR scanner work without internet.
- App icon taken from the icon inside the HTML.

Same app id (`com.minipaint.app`) and same fixed debug signing key as the earlier Mini Paint
builds, so it installs over them as an update and keeps your saved drawings. Version code 3.
