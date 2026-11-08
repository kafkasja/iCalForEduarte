package dev.eduartesync.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortNamesTest {
    @Test
    fun dropsLevelSuffixes() {
        assertEquals("Testing", ShortNames.of("Testing-Advanced"))
        assertEquals("DOCKER", ShortNames.of("DOCKER-INTRO"))
        assertEquals("PDO", ShortNames.of("PDO-Expert"))
        assertEquals("Digitale vaardigheden", ShortNames.of("Digitale vaardigheden gevorderd"))
    }

    @Test
    fun dropsLessonPrefix() {
        assertEquals("Solliciteren", ShortNames.of("LES-Solliciteren"))
    }

    @Test
    fun longCapitalizedNamesBecomeInitials() {
        assertEquals("OOP", ShortNames.of("Object Oriented Programming"))
    }

    @Test
    fun leavesOtherNamesAlone() {
        assertEquals("Engels", ShortNames.of("Engels"))
        assertEquals("Duurzaamheid in het beroep D", ShortNames.of("Duurzaamheid in het beroep D"))
        assertEquals("B1KT1 Gebruikersondersteuning", ShortNames.of("B1KT1 Gebruikersondersteuning"))
        assertEquals("Advanced", ShortNames.of("Advanced"))
    }
}
