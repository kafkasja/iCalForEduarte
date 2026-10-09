# Eduarte Sync

Android app that copies my Talnet Eduarte schedule into the phone calendar, since the school has iCal export turned off.

Lessons go into a separate calendar called "Eduarte rooster", with the subject as title, the room as location, and a color per subject (all customizable per subject). Lessons of the same subject that follow each other directly are merged into one event.

## Install

1. Download the APK from the latest release on the Releases page.
2. Allow your browser to install unknown apps when Android asks.
3. Open Eduarte Sync, allow calendar and notification access, and log in with your school Microsoft account.

## Usage

- Tapping the app icon syncs right away. A short message shows the result, for example "Synced 20 lessons until 18/10: 1 moved".
- Tapping it twice quickly opens the settings.
- It also syncs in the background about every 12 hours.
- By default each sync covers today until the end of next week. Days that have passed are left alone.
- When the schedule changed, a notification lists what changed. Days that are new to the calendar don't count as changes. "Ignore" turns these notifications off; they can be turned back on from the settings.
- When a sync fails, the message says what went wrong in plain language, with the technical error underneath. A failed sync never changes the calendar.

## Settings

- **Status:** when the last sync was and what it did, plus a "Sync now" button.
- **Sync:** turn the automatic sync off (tapping the icon still syncs), and choose how far ahead to sync: today only, rest of this week, through next week, or as far as possible (8 weeks).
- **Notifications:** shows whether change notifications are on, with a shortcut to Android's settings for them.
- **Clear calendar:** removes the "Eduarte rooster" calendar and everything in it. The next sync brings it back.
- **Log out / switch account:** removes the login from the phone. There is no "change password", because the app never has your password.
- **Subjects (beta):** every subject that showed up in your schedule stays in this list until you remove it. Give each one its own name, emoji and color, e.g. "PDO-Expert" becomes "💻 PDO".

Clearing the app's storage in Android settings resets everything, including the subject list.

## What the app does, step by step

1. **Login.** The first time, the app shows the normal Eduarte login page in a built-in browser (WebView). Eduarte sends you to the Microsoft login of the school. The app never sees or stores the password; Microsoft and Eduarte handle it on their own pages.
2. **What is stored.** The cookies that the login sets, a "logged in" flag, your settings and your subject list, all in the app's private storage on the phone. Backups and device transfer of this data are disabled (`allowBackup="false"` and `data_extraction_rules.xml`).
3. **Reading the schedule.** A hidden built-in browser opens `talnet-student.educus.nl/agenda` with those cookies. To see next week it enters a date in the agenda's date field, like you would. The page HTML is parsed on the phone (`AgendaParser.kt`). The page shows no times as text, so the start and end times are calculated from the position of each lesson in the week grid.
4. **Writing the calendar.** The app creates one local calendar on the phone and only ever touches that calendar. Each event stores a key (date, start time, subject, group), so the next sync updates the event instead of adding a second one.
5. **Network.** The app only connects to the Eduarte portal and, when logging in, to the school's Microsoft login. It has no server of its own, no analytics and no ads.

### Permissions

| Permission | Why |
|---|---|
| Internet | To open the Eduarte portal |
| Calendar (read/write) | To create and update the "Eduarte rooster" calendar |
| Notifications | To show schedule changes and sync problems |

## Code overview

| File | What it does |
|---|---|
| `auth/LoginActivity.kt` | Login screen (Eduarte/Microsoft in a WebView) |
| `auth/SessionManager.kt` | Remembers whether you're logged in |
| `source/PortalBrowser.kt` | Hidden WebView that opens the agenda with the login session and switches weeks |
| `source/EduarteSource.kt` | Loads this week and next week |
| `source/AgendaParser.kt` | Reads lessons and times from the agenda HTML |
| `source/LessonMerger.kt` | Merges back-to-back lessons of the same subject |
| `calendar/CalendarWriter.kt` | Creates the calendar and writes the events |
| `calendar/SyncPlan.kt` | Decides what to add, update and remove |
| `calendar/ScheduleChanges.kt` | Describes the changes in readable sentences |
| `calendar/SubjectColors.kt` | Color per subject |
| `sync/SyncWorker.kt` | One sync, start to finish |
| `sync/SyncScheduler.kt` | Background sync every 12 hours, and sync on opening the app |
| `sync/Notifier.kt` | Notifications for changes and problems |
| `ui/MainActivity.kt` | What happens when you tap the app icon (once: sync, twice: settings) |
| `ui/SettingsActivity.kt` | Settings page |
| `ui/SubjectsActivity.kt` | Subjects page (beta) |
| `settings/AppSettings.kt` | Stored settings and the sync range options |
| `subjects/SubjectStore.kt` | The subject list with names, emoji and colors |

## Building

```
./gradlew testDebugUnitTest assembleRelease
```

Pushing a tag like `v1.0` builds a signed APK through GitHub Actions (`.github/workflows/release.yml`) and attaches it to a release. This needs the repository secrets `SIGNING_KEYSTORE_BASE64` (base64 of the PKCS12 keystore) and `SIGNING_STORE_PASSWORD`.
