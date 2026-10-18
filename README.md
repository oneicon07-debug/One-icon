# AuraLab – Android APK

Is repo me AuraLab (v16) ka web app hai. GitHub Actions isse khud Android APK me convert karta hai.

## APK kaise milega

1. GitHub par naya repository banao (Public ya Private, dono chalega).
2. Is ZIP ke andar ki saari files repo me upload karo. `.github` folder bhi jana chahiye (hidden folder hai, isliye dhyan rakhna).
3. Repo me **Actions** tab kholo. Build apne aap shuru ho jata hai. Na ho to **Build APK** → **Run workflow** dabao.
4. 4–8 minute baad green tick aayega. APK yahan milegi:
   - **Releases** (repo ke right side me): `AuraLab.apk` pe tap karke seedha download.
   - Ya Actions → latest run → **Artifacts** → `AuraLab-apk` (ZIP me hota hai).
5. Phone me APK kholo. "Unknown source se install" allow karna padega.

## Notes

- Ye debug-signed APK hai. Personal use ke liye install ho jati hai. Play Store ke liye release keystore chahiye.
- App me Export dabane par file share sheet me khulti hai. Wahan se "Save to device" ya Gallery/Files chuno.
- Photo, video, PNG character import gallery se chalta hai.
- `index.html` hi poori app hai. Isme change karke push karoge to nayi APK khud ban jayegi.
