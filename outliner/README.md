# 3D Outliner (Android app)

Original `3d-outliner.html` ko native Android WebView app me wrap kiya gaya hai.
Ye ARISE SYSTEM app se bilkul alag hai (alag folder, alag workflow, alag release).

## Install

Phone ke browser me ye link kholo, `3D-Outliner.apk` download karo, phir kholkar Install karo:

https://github.com/oneicon07-debug/One-icon/releases/download/outliner-latest/3D-Outliner.apk

(Pehli baar "Install unknown apps" allow karna padega.)

Build tab chalta hai jab `outliner/**` me kuch change ho, ya Actions tab me **Build 3D Outliner APK → Run workflow**.
Fail hone par Releases me `outliner-build-failed-N` naam ka pre-release aata hai jisme error log hota hai.

## Kya hai

- `android/app/src/main/assets/www/index.html` – original app (sirf three.js ab local file se load hota hai).
- `three.js` build ke time `scripts/copy-three.js` se app ke andar daala jaata hai → **offline chalta hai**.
- `native-shim.js` – Screenshot / Auto Angles ki PNG seedha Gallery me (`Pictures/3D Outliner`) save hoti hai,
  aur "Landscape" button landscape + fullscreen toggle karta hai.
- Import (+ GLB/GLTF/OBJ) Android ke file picker se chalta hai; imported models app me save rehte hain.
- Debug key repo me fixed hai, isliye naya build purane ke upar install ho jaata hai (uninstall nahi karna padta).
