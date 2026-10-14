# Mini Paint (Android app)

Mini Paint's web app (`app/src/main/assets/index.html`) packaged as a real Android app.
It runs fully offline inside a full-screen WebView; no third-party libraries.

## Download

After the build finishes (Actions tab, about 5 minutes), the APK is here:

`https://github.com/oneicon07-debug/One-icon/releases/download/minipaint-latest/MiniPaint.apk`

## What the Android wrapper adds

- Saves/exports go to **Downloads/MiniPaint** (the browser's `blob:` downloads do not work in a plain WebView).
- Import buttons open the normal Android file picker (images, video, audio, `.brush`).
- Links to Pinterest / Google etc. open in your browser.
- The Back button first closes the pop-up / panel / editor; press it twice on the home screen to exit.
- JSZip is bundled (downloaded during the build) so zip features work without internet.

## Update the app

Replace `app/src/main/assets/index.html` with the new version and push. The workflow builds a new APK
automatically. Keep `versionCode` in `app/build.gradle` going up (1, 2, 3 ...) for each release you hand out.

## Signing key (important before Google Play)

Every build is signed with `app/minipaint.keystore`, so a new APK installs over the old one and keeps
the user's drawings. This key is committed to a **public** repo, so it is fine for personal sideloading
only. Before publishing on Google Play, create your own private key, store it as GitHub Actions secrets,
and stop using this one.
