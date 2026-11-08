# Eduarte Sync

Android app that copies my Talnet Eduarte schedule into the phone calendar, since the school has iCal export turned off.

Lessons go into a separate calendar called "Eduarte rooster", with the subject as title, the room as location, and a color per subject (all customizable per subject). Lessons of the same subject that follow each other directly are merged into one event.

## Install

1. Download the APK from the latest release on the Releases page.
2. Allow your browser to install unknown apps when Android asks.
3. Open Eduarte Sync, allow calendar and notification access, and log in with your school Microsoft account.

## Usage

- Tapping the app icon opens the app and syncs right away (not again when the last sync worked less than 2 minutes ago). Coming back to the app after 15 minutes or more syncs too. The result shows at the bottom of the screen, for example "Synced 20 lessons until 18/10: 1 moved".
- The app has three tabs:
  - **Rooster:** your lessons, one day per page. Swipe between days, tap a day in the week strip, or use the arrows and "Today" to jump. Saturday and Sunday only show up when they have lessons. Each lesson shows its time, room and teacher; free time between lessons and the current time are marked, cancelled lessons are struck through. Tap a lesson for the details and "Open in calendar". The line at the top says whether the rooster is up to date (tap it for details); pull down to sync. Days after the synced range say so, the range can be changed in the settings.
  - **Cijfers:** your grades, the newest first and per subject with the average. Not available yet; this tab is ready for when the app can read grades from Eduarte.
  - **Meer:** your Eduarte pages in the app (below), Subjects, Settings, the login log, Eduarte in your browser, and the app version.
- From the Meer tab you can open these Eduarte pages in the app. Each shows what it read last time right away (also offline), reads Eduarte again when that is more than an hour old, and pull down always reads again:
  - **Presentie:** your attendance. A ring with the percentage you were present, the hours present and absent, and chips to choose the period (school year, this or last month, this or last week). Below that the excused (geoorloofd) and unexcused (ongeoorloofd) absence, every subject from the lowest percentage up (green from 90%, amber from 80%, red below), and the lessons of the last four weeks one by one. Reading per subject takes about a second per subject, so the totals show first and a line counts the subjects while they are read. The Meer tab shows the school year's percentage under "Presentie".
  - **Studie:** your programmes (level, BOL/BBL, status, dates, location) and study points.
  - **Vakken**, **Groepen** (tap a group to see who is in it), **Dossier** and **Documents**. Tapping a document downloads it with your Eduarte login and opens it in your PDF app.
- The gear at the top opens the settings.
- Syncs started from the widget's sync button or the Subjects page show the result as a short message, since there is no status line there.
- It also syncs in the background about every 12 hours.
- By default each sync covers today until the end of next week. Days that have passed are left alone.
- When the schedule changed, a notification lists what changed. Days that are new to the calendar don't count as changes. "Ignore" turns these notifications off; they can be turned back on from the settings.
- When a sync fails, the message says what went wrong in plain language, with the technical error underneath. A failed sync never changes the calendar.

## Settings

The page starts with a status card that tells you at a glance whether your calendar is up to date: a colored dot (green: up to date, yellow: syncing or not synced for a while, red: failed or logged out), when it last synced, which days the calendar covers, when the next automatic sync is, and the message of the last sync. Its button is "Sync now", or "Log in" when the login has expired.

The settings below it are grouped:

- **Calendar**
  - **How far ahead:** today only, rest of this week, through next week, or as far as possible (8 weeks). Changing it syncs right away.
  - **Room in the event name:** puts the room before or after the lesson name, e.g. "PDO · B1.12". Lock screens and widgets (like Samsung's) often show only the name and time of an event, so this is the way to see the room there. Off by default; changing it syncs right away.
  - **Subjects (beta):** every subject that showed up in your schedule stays in this list until you remove it. Give each one its own name, emoji and color, e.g. "PDO-Expert" becomes "💻 PDO".
  - **Clear calendar:** removes the "Eduarte rooster" calendar and everything in it. The next sync brings it back.
- **Background sync & notifications**
  - **Sync automatically:** turn the sync every 12 hours off (opening the app still syncs).
  - **Schedule change notifications:** shows whether they are on, and opens Android's settings for them.
- **Account**
  - **Fill in my email and password:** off by default. When on, the email and password you type at the next login are saved, and every login after that fills them in, so only the Authenticator is left. Turning it off deletes them.
  - **Login log:** what happened during the last logins, to find out why the login expires. No passwords or codes are in it.
  - **Log out / switch account:** removes the login, and a saved email and password, from the phone.
- **About:** your version and, if there is one, a newer version to download. "All versions" lists every release, so you can also go back to an older one (uninstall first, Android doesn't allow installing an older version over a newer one). There are no update notifications.

Clearing the app's storage in Android settings resets everything, including the subject list.

## What the app does, step by step

1. **Login.** The first time, the app shows the normal Eduarte login page in a built-in browser (WebView). Eduarte sends you to the Microsoft login of the school. By default the app never stores the password; Microsoft and Eduarte handle it on their own pages. With "Fill in my email and password" on, the app reads what you type on the Microsoft login page and stores it encrypted with a key in the Android Keystore, which never leaves the phone. It only fills it in on the Microsoft login page, and tries a saved password once per login screen, so a changed password doesn't get tried over and over. The app answers Microsoft's "Stay signed in?" with Yes, so the login lasts instead of expiring after a short while.
2. **What is stored.** The cookies that the login sets, a "logged in" flag, your settings and your subject list, and the last read of the Eduarte pages shown in the app (grades, attendance, dossier, documents list, subjects, groups, programmes), all in the app's private storage on the phone. An opened document is kept in the app's cache until the next one is opened. Backups and device transfer of this data are disabled (`allowBackup="false"` and `data_extraction_rules.xml`).
3. **Reading the schedule.** A hidden built-in browser opens `talnet-student.educus.nl/agenda` with those cookies. To see next week it enters a date in the agenda's date field, like you would. The page HTML is parsed on the phone (`AgendaParser.kt`). The page shows no times as text, so the start and end times are calculated from the position of each lesson in the week grid.
4. **Writing the calendar.** The app creates one local calendar on the phone and only ever touches that calendar. Each event stores a key (date, start time, subject, group), so the next sync updates the event instead of adding a second one.
5. **Network.** The app connects to the Eduarte portal and, when logging in, to the school's Microsoft login. While the settings page is open it also reads the list of releases from GitHub (a plain request that sends nothing about you). It has no server of its own, no analytics and no ads.

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
| `auth/SavedLogin.kt` | Encrypted email and password, when you turned that on |
| `auth/MicrosoftLogin.kt` | Clicks through "Stay signed in?" and fills in a saved login |
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
| `ui/MainActivity.kt` | Main screen with the tabs; asks for permissions, sends you to the login, syncs on opening |
| `ui/rooster/` | Rooster tab: day pages, week strip, lesson details, sync status line |
| `ui/grades/` | Cijfers tab |
| `ui/more/MoreFragment.kt` | Meer tab |
| `ui/SettingsActivity.kt` | Settings page |
| `ui/SubjectsActivity.kt` | Subjects page (beta) |
| `ui/SyncStatus.kt` | The plain-language sync status on the settings page and the Rooster tab |
| `calendar/CalendarReader.kt` | Reads the lessons back from the calendar for the app and the widget |
| `grades/` | Grades: the data, the stored copy, averages, and the (not yet built) fetcher |
| `portal/` | Readers for the other portal pages: grades, attendance, presence charts (`PresenceSource`, also per subject), study, dossier, documents, subjects and groups |
| `presence/` | Attendance: percentages per period and subject, colors, lesson statuses, and the stored copy |
| `records/` | Stored copy of the dossier, documents, subjects, groups and programmes; document download |
| `ui/portal/` | The Presentie, Studie, Vakken, Groepen, Dossier and Documents screens |
| `settings/AppSettings.kt` | Stored settings and the sync range options |
| `subjects/SubjectStore.kt` | The subject list with names, emoji and colors |
| `update/UpdateChecker.kt` | Reads the releases from GitHub for the version section |

## Building

```
./gradlew testDebugUnitTest assembleRelease
```

Pushing a tag like `v1.0` builds a signed APK through GitHub Actions (`.github/workflows/release.yml`) and attaches it to a release. This needs the repository secrets `SIGNING_KEYSTORE_BASE64` (base64 of the PKCS12 keystore) and `SIGNING_STORE_PASSWORD`.
