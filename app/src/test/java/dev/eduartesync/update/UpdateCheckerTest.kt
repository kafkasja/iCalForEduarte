package dev.eduartesync.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `version comparison`() {
        assertTrue(UpdateChecker.isNewer("v0.6", "v0.5"))
        assertTrue(UpdateChecker.isNewer("v0.10", "v0.9"))
        assertTrue(UpdateChecker.isNewer("v1.0", "v0.9.9"))
        assertFalse(UpdateChecker.isNewer("v0.5", "v0.5"))
        assertFalse(UpdateChecker.isNewer("v0.4", "v0.5"))
        // Local builds are named like "0.1-dev".
        assertTrue(UpdateChecker.isNewer("v0.5", "0.1-dev"))
    }

    @Test
    fun `releases are parsed newest first, without drafts and pre-releases`() {
        val json = """
            [
              {"tag_name": "v0.4", "html_url": "https://github.com/x/y/releases/tag/v0.4",
               "assets": [{"name": "EduarteSync-v0.4.apk", "browser_download_url": "https://github.com/x/y/releases/download/v0.4/EduarteSync-v0.4.apk"}]},
              {"tag_name": "v0.10", "html_url": "https://github.com/x/y/releases/tag/v0.10", "assets": []},
              {"tag_name": "v0.11", "html_url": "u", "draft": true, "assets": []},
              {"tag_name": "v0.12", "html_url": "u", "prerelease": true, "assets": []}
            ]
        """.trimIndent()
        val releases = UpdateChecker.parseReleases(json)
        assertEquals(listOf("v0.10", "v0.4"), releases.map { it.version })
        assertEquals(null, releases[0].apkUrl)
        assertEquals("https://github.com/x/y/releases/download/v0.4/EduarteSync-v0.4.apk", releases[1].apkUrl)
    }
}
