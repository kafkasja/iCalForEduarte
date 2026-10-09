package dev.eduartesync.subjects

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SubjectStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = SubjectStore(File(folder.root, "subjects.json"))

    @Test
    fun `json round trip`() {
        val styles = listOf(
            SubjectStyle("PDO-Expert", displayName = "PDO", emoji = "💻", color = 0xFF43A047.toInt()),
            SubjectStyle("NOSQL"),
        )
        assertEquals(styles, SubjectStore.decode(SubjectStore.encode(styles)))
    }

    @Test
    fun `seen subjects are added without touching existing styles`() {
        val store = store()
        store.save(SubjectStyle("PDO-Expert", emoji = "💻"))
        store.addSeen(listOf("PDO-Expert", "NOSQL", "NOSQL", ""))
        assertEquals(
            listOf(SubjectStyle("NOSQL"), SubjectStyle("PDO-Expert", emoji = "💻")),
            store.all(),
        )
    }

    @Test
    fun `subjects stay until removed`() {
        val store = store()
        store.addSeen(listOf("Mentoruur", "Python"))
        store.addSeen(listOf("Python"))
        assertEquals(listOf("Mentoruur", "Python"), store.all().map { it.name })
        store.remove("Mentoruur")
        assertEquals(listOf("Python"), store.all().map { it.name })
    }

    @Test
    fun `missing or broken file gives an empty list`() {
        assertEquals(emptyList<SubjectStyle>(), store().all())
        File(folder.root, "subjects.json").writeText("{ not json")
        assertEquals(emptyList<SubjectStyle>(), store().all())
    }
}
