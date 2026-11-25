# Local Calls Only

A tiny, privacy-first Android app (Kotlin + Jetpack Compose, minSdk 31, targets Android 15) that rejects incoming calls from outside your country. You toggle it from a Quick Settings tile.

## How it decides

When the blocker is **on**, an incoming call is allowed if any of these is true:

- the caller is in your Contacts (looked up only on the device)
- the number starts with your country code (default `+370`, `00` is treated as `+`)
- the number is in national format starting with a trunk prefix (default `0`, `8`)
- it's a short service number (up to 6 digits, no `+`)
- it's an emergency number, or has no number at all (hidden/private)

Every other call is rejected silently and still appears in your call history. The app also keeps its own list of the last 10 blocked numbers.

## Privacy

- No `INTERNET` permission. The manifest explicitly strips it, so no library can add it back.
- The only runtime permission is `READ_CONTACTS`, which is used on the device to let saved foreign contacts ring.
- `allowBackup="false"`, so the blocked-number list never leaves the phone.

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

The release build is signed with your local debug key, which is fine for personal sideloading.

## Caveats

- The blocker doesn't run until the first unlock after a reboot, so calls ring normally until then.
- If this isn't your default phone app, Android may still log blocked calls in call history.
