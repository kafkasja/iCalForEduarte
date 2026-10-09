# Eduarte Sync

Android app that copies my Talnet Eduarte schedule into the phone calendar, since the school has iCal export turned off.

Lessons go into a separate calendar called "Eduarte rooster", with the subject as title, the room as location, and a color per subject. Lessons of the same subject that follow each other directly are merged into one event.

## Install

1. Download the APK from the latest release on the Releases page.
2. Allow your browser to install unknown apps when Android asks.
3. Open Eduarte Sync, allow calendar access and log in with your school Microsoft account.

## Usage

- Opening the app syncs right away, then it closes again.
- It also syncs in the background about every 12 hours.
- Each sync covers today until the end of next week. Days that have passed are left alone.
- To log out or switch accounts, clear the app's storage in Android settings.

## Privacy

The login happens on the normal Eduarte/Microsoft pages inside the app, so the app never sees the password. Only the session cookies are kept, in the app's private storage, and backups of it are disabled. The app only talks to Eduarte.

## Building

```
./gradlew testDebugUnitTest assembleRelease
```

Pushing a tag like `v1.0` builds a signed APK through GitHub Actions and attaches it to a release. This needs the repository secrets `SIGNING_KEYSTORE_BASE64` (base64 of the PKCS12 keystore) and `SIGNING_STORE_PASSWORD`.
