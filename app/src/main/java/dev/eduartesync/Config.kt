package dev.eduartesync

import java.time.ZoneId

object Config {
    const val PORTAL_URL = "https://talnet-student.educus.nl/"
    const val PORTAL_HOST = "talnet-student.educus.nl"

    /** Where new versions are published. */
    const val GITHUB_REPO = "kafkasja/iCalForEduarte"

    const val ACCOUNT_NAME = "Eduarte Sync"
    const val CALENDAR_NAME = "Eduarte rooster"
    const val CALENDAR_COLOR = 0xFF1E88E5.toInt()

    val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

    const val PERIODIC_SYNC_HOURS = 12L
}
