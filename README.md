# Eduarte Sync

Android app for the Talnet Eduarte student portal. It puts your schedule in your phone's calendar (the school has iCal export turned off) and shows your schedule, grades, attendance and other Eduarte pages in one app.

Everything happens on your phone: the app logs in to Eduarte like a browser would, reads the pages and keeps a copy on the phone. There is no server, no analytics and no ads.

## Install

1. Download the APK from the latest release on the Releases page.
2. Allow your browser to install unknown apps when Android asks.
3. Open Eduarte Sync, allow calendar and notification access, and log in with your school Microsoft account. Tick "Remember my email and password" at the top of the login screen if you want the app to log in again by itself later (see [Login](#login)).

## The app

Opening the app syncs your schedule (not again when the last sync worked less than 2 minutes ago, and again when you come back after 15 minutes or more). The result only pops up at the bottom when the schedule changed, when something went wrong, or when you started the sync yourself.

The app has three tabs.

### Rooster

Your lessons, one day per page.

- The top bar shows the week and its lesson hours (`Week 46 · 15 h`; tap it to go back to today), arrows for the previous and next week, the sync status as an icon and the gear for the settings. The icon is a check when the schedule is up to date, a spinner while syncing, a clock when it may be out of date and red when a sync failed or you need to log in. Tap it for the details and "Sync now".
- Swipe left or right between days, or tap a day in the week strip. Saturday and Sunday only show up when they have lessons.
- Above the lessons one line sums up the day, e.g. `Tomorrow · 3 lessons · 13:15 – 16:30`.
- Each lesson shows its time, room, teacher and group. Free time between lessons and the current time are marked; cancelled lessons are struck through.
- Tap a lesson for its details, **Rename** and **Calendar** (opens it in your calendar app). Rename gives the subject your own name in the calendar; leave the name empty to go back to the automatic one.
- Pull down to sync. Days after the synced range say so; the range can be changed in the settings.

### Cijfers

Your grades from Eduarte's Resultaten page: the newest first, then per subject with the average (below 5,5 in red). Each grade shows the test name, date and weight. It reads Eduarte again when the copy is more than 30 minutes old, or when you pull down.

### Meer

Your other Eduarte pages, plus Subjects, Settings, the login log, Eduarte in your browser and the app version. Each Eduarte page shows the copy it read last time right away (also offline; when it was read is under the title), reads Eduarte again when that copy is more than an hour old, and always when you pull down.

- **Presentie:** your attendance, the same numbers as Eduarte's Presentie and Absentie tabs.
  - A ring with the percentage you were present, the hours present and absent, and chips for the period (school year, this or last month, this or last week).
  - Excused (geoorloofd) and unexcused (ongeoorloofd) absence.
  - Every subject from the lowest percentage up: green from 90%, amber from 80%, red below.
  - The lessons of the last four weeks, one by one.
  - Eduarte only gives percentages per subject one subject at a time, so the first read takes about half a minute. The totals show first, and a line counts the subjects while they are read.
- **Studie:** your programmes (level, BOL/BBL, status, dates, location), and study points once there are any.
- **Vakken:** every subject with its code.
- **Groepen:** your classes; tap one to see who is in it.
- **Dossier:** notes from talks with your mentor.
- **Documents:** letters and forms from school. Tapping one downloads it with your Eduarte login and opens it in your PDF app.
- **Subjects:** every subject that showed up in your schedule, to give it its own name, emoji and color in the calendar.

### Widget

A home screen widget shows the rest of today's lessons (or the next day with lessons), with the current lesson highlighted. Tap a lesson to open it in your calendar, or the header to sync.

## The calendar

Lessons go into a separate calendar called "Eduarte rooster" on the phone. The app only ever touches that calendar.

- The title is the subject, the location is the room, and each subject gets its own color.
- Back-to-back lessons of the same subject are merged into one event.
- **Short lesson names** (on by default): level endings and prefixes are left out, so the name stays short on lock screens and widgets that cut it off. `Testing-Advanced` becomes `Testing`, `DOCKER-INTRO` becomes `DOCKER`, `LES-Solliciteren` becomes `Solliciteren`, and long names of three or more capitalized words become initials (`Object Oriented Programming` becomes `OOP`). A name you set yourself always wins.
- Each sync covers today until the end of next week by default. Days that have passed are never changed.
- When the schedule changed, a notification lists what changed (moved, cancelled, new room, added, removed). Days that are new to the calendar and renamed lessons don't count as changes. "Ignore" turns these notifications off; the settings can turn them back on.
- When a sync fails, the message says what went wrong in plain language ("no internet", "Eduarte didn't respond in time", "log in needed", ...). The settings page also shows the technical error. A failed sync never changes the calendar.
- It also syncs in the background about every 12 hours.

## Settings

The settings page starts with a status card: whether the calendar is up to date, when it last synced, which days it covers, when the next automatic sync is, and the last sync's message, with "Sync now" (or "Log in" when that's needed).

- **Calendar**
  - **How far ahead:** today only, rest of this week, through next week, or as far as possible (8 weeks).
  - **Short lesson names:** see [The calendar](#the-calendar).
  - **Room in the event name:** puts the room before or after the lesson name, e.g. `PDO · B1.12`. Off by default.
  - **Subjects (beta):** the same list as Subjects in the Meer tab.
  - **Clear calendar:** removes the "Eduarte rooster" calendar and everything in it. The next sync brings it back.
- **Background sync & notifications:** turn the sync every 12 hours off (opening the app still syncs), and a shortcut to Android's settings for the schedule change notifications.
- **Account**
  - **Fill in my email and password:** see [Login](#login).
  - **Login log:** what happened during the last logins and session renewals. No passwords or codes are in it.
  - **Log out / switch account:** removes the login, and a saved email and password, from the phone.
- **About:** your version and, if there is one, a newer version to download. "All versions" lists every release, so you can also go back to an older one (uninstall first: Android doesn't allow installing an older version over a newer one).

Changing a setting that affects the calendar syncs right away and updates every lesson from today on. Clearing the app's storage in Android's settings resets everything.

## How it works

### Login

The first time, the app shows the normal Eduarte login page in a built-in browser (WebView), which sends you on to the school's Microsoft login. The app answers Microsoft's "Stay signed in?" with Yes.

Eduarte's own session runs out after a while, and then the school's Microsoft login asks for the password again (even with "stay signed in"). What happens next:

- **Without a saved password** you get a "Log in to Eduarte again" notification.
- **With "Remember my email and password" on**, the app fills it in itself, also during background syncs. The password is stored encrypted with a key in the Android Keystore, which never leaves the phone, and it is only ever filled in on the Microsoft login page.
- **If Microsoft then also wants the Authenticator**, a background sync stops and notifies you instead, so you never get an Authenticator request you didn't ask for. A sync you started in the app shows the number to tap in the Authenticator and finishes once you approve.
- **If Microsoft says the saved password is wrong**, the app stops filling it in until you log in once with the right one, so a changed password can't lock your account.

### Reading Eduarte

A hidden built-in browser opens the Eduarte pages with your login, like you would, and the page HTML is read on the phone.

- **Schedule:** the agenda page in its Week view (`source/AgendaParser.kt`). The page shows no times as text, so start and end times come from the position of each lesson in the week grid. Other weeks are shown by typing a date into the agenda's date field. Eduarte remembers the view you last chose, so when the agenda is in another view (Lijst, Werkweek) or "Toon lesuren" is off, the app switches it back first.
- **Grades:** the Resultaten page. The details of each grade (test name, date, weight) come from clicking it, like you would.
- **Attendance:** the Presentie and Absentie pages, choosing each subject in Eduarte's filter in turn for the per-subject numbers.
- **Other pages:** Studie, Vakken, Groepen, Dossier and Documenten.

The app checks every page it reads. When something looks different than expected, the sync stops with an error instead of saving a partial schedule, because a missing lesson would otherwise be removed from your calendar.

### Stored on the phone

The login cookies, your settings, your subject names, the last copy of every Eduarte page shown in the app, and the last opened document. All of it is in the app's private storage. Backups and device transfer of this data are disabled (`allowBackup="false"` and `data_extraction_rules.xml`).

### Network

The app only connects to the Eduarte portal and the school's Microsoft login. While the settings page is open it also reads the list of releases from GitHub (a plain request that sends nothing about you).

### Permissions

| Permission | Why |
|---|---|
| Internet | To open the Eduarte portal |
| Calendar (read/write) | To create and update the "Eduarte rooster" calendar, and show it in the app and the widget |
| Notifications | Schedule changes, sync problems, and the Authenticator number for a sync you started |

## Code overview

Kotlin, Android Views with Material 3, minimum Android 8 (API 26). The code is in `app/src/main/java/dev/eduartesync/`.

| Package / file | What it does |
|---|---|
| `auth/` | Login screen (`LoginActivity`), login state, the encrypted saved login, the Microsoft login helper and the login log |
| `source/PortalBrowser.kt` | The hidden WebView: opens portal pages with the login session, renews the session, switches agenda weeks |
| `source/EduarteSource.kt`, `AgendaParser.kt`, `LessonMerger.kt` | Reading the schedule week by week and merging back-to-back lessons |
| `portal/` | Readers for the other portal pages: grades, attendance and presence charts, study info, dossier, documents, subjects and groups |
| `calendar/` | Writing and reading the calendar, deciding what to add, update and remove, describing changes, short names, colors |
| `sync/` | One sync start to finish (`SyncWorker`), scheduling, notifications and error messages |
| `grades/`, `presence/`, `records/` | The stored copies of grades, attendance and the other pages, and the numbers shown from them |
| `subjects/` | The subject list with your own names, emoji and colors |
| `settings/` | Stored settings |
| `widget/` | The home screen widget |
| `update/` | Reading the releases from GitHub for the About section |
| `ui/` | The main screen and tabs (`rooster/`, `grades/`, `more/`), the Eduarte page screens (`portal/`), settings and subjects |

Debug builds also contain two developer screens in `app/src/debug/` that save a portal page or run one reader and log what it found, for checking the readers against the live portal with adb.

## Building

```
./gradlew testDebugUnitTest assembleRelease
```

Pushing a tag like `v1.0` builds a signed APK through GitHub Actions (`.github/workflows/release.yml`) and attaches it to a release. This needs the repository secrets `SIGNING_KEYSTORE_BASE64` (base64 of the PKCS12 keystore) and `SIGNING_STORE_PASSWORD`.
