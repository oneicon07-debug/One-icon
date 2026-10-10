# ARISE SYSTEM (Android app)

Offline leveling app: Awakening screen, player profile, daily quests, XP / levels / ranks,
ability points, streaks and a focus-timer "Dungeon Gate". No internet, no permissions, no ads,
no paid services. Progress is saved privately on the phone.

## Your original artwork (used unmodified)

| File | Used for |
|------|----------|
| `app/src/main/res/drawable-nodpi/arise_logo.jpg` | App icon + Awakening screen logo |
| `app/src/main/res/drawable-nodpi/arise_profile.jpg` | Player / profile icon on the STATUS tab |

They are byte-for-byte copies of the files you supplied. To swap which image is which, just
exchange those two files (keep the names). The app only rounds the on-screen corners; the files are never edited.

## Build the APK

**Option A - Android Studio:** File > Open this folder, let it sync, then Build > Build APK(s).
Or on the command line (JDK 17 + Android SDK platform 34): `./gradlew assembleDebug`
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Option B - free cloud build with GitHub Actions (no installs):**
the workflow is already included at `.github/workflows/build-apk.yml`.
1. Create a free GitHub repository (Public).
2. Upload this project's files (keep the `.github` folder), OR upload just the ZIP file
   and create `.github/workflows/build-apk.yml` by pasting the workflow text in on github.com.
3. Open the Actions tab and wait for the green check (about 5 minutes).
4. Download: `https://github.com/YOUR-USERNAME/YOUR-REPO/releases/latest/download/ARISE-SYSTEM.apk`

Install on the phone: open the APK, allow installs from your browser/Files app, tap Install
(Play Protect may say "unknown developer": tap Install anyway).

## Code map

- `Player.java`, `Quest.java`, `QuestCodec.java` - all game rules (pure Java, unit-tested)
- `Storage.java` - saves/loads progress on the device
- `MainActivity.java` - every screen
