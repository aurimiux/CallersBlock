# Local Calls Only

A tiny, privacy-first Android app (Kotlin + Jetpack Compose, minSdk 31, targets Android 15) that rejects incoming calls from outside your country. You toggle it from a Quick Settings tile.

## How it decides

When the blocker is **on**, an incoming call is allowed if any of these is true:

- the caller is in your Contacts (looked up only on the device)
- the number starts with one of your **allowed prefixes** (default `+370`; add or remove e.g. `+371`, `+44` in the app — `00` is treated as `+`)
- the number is in national format starting with a trunk prefix (default `0`, `8`)
- it's a short service number (up to 6 digits, no `+`)
- it's an emergency number, or has no number at all (hidden/private)

Every other call is rejected silently and still appears in your call history. The app also keeps its own list of the last 10 blocked numbers.

## Privacy

- No `INTERNET` permission. The manifest explicitly strips it, so no library can add it back.
- The only runtime permission is `READ_CONTACTS`, which is used on the device to let saved foreign contacts ring.
- `allowBackup="false"`, so the blocked-number list never leaves the phone.

## Download

Every push to `main` builds a signed APK and publishes it to the rolling
[**latest**](https://github.com/aurimiux/CallersBlock/releases/tag/latest) release, so the APK is
always one tap away on the phone — no GitHub login, no artifact zip:

```
https://github.com/aurimiux/CallersBlock/releases/download/latest/CallersBlock-main.apk
```

Pushing a version tag cuts a permanent release instead, with the APK and its SHA-256 attached:

```sh
git tag v1.0 && git push origin v1.0
```

The same APK is also kept as an Actions artifact (`CallersBlock-apk`), which needs a GitHub login.

## Build & install

Open the project in Android Studio and press Run, or:

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # rule tests
```

Then, on the phone:

1. Open the app and tap **Set as call screening app**.
2. Tap **Allow contacts access**.
3. Edit Quick Settings and add the **Local Calls Only** tile.

Release builds are signed with a release key that is never committed. Locally the key comes from a gitignored `keystore.properties`. In CI it comes from the repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. If those are missing, CI falls back to a throwaway debug key.

## Security notes

- The screening and tile services can only be bound by the system (`BIND_SCREENING_SERVICE` / `BIND_QUICK_SETTINGS_TILE`).
- Toggling the tile from the lock screen asks you to unlock first.
- If anything goes wrong during screening, the call is allowed rather than blocked.
- CI actions are pinned to commit SHAs. The Gradle wrapper jar is validated, and the distribution is checked against its SHA-256. The build fails if `INTERNET` ever appears in the merged manifest.

## Caveats

- The blocker doesn't run until the first unlock after a reboot, so calls ring normally until then.
- If this isn't your default phone app, Android may still log blocked calls in call history.
