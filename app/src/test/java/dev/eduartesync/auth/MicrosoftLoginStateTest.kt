package dev.eduartesync.auth

import dev.eduartesync.auth.MicrosoftLogin.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MicrosoftLoginStateTest {
    @Test
    fun readsStepAndDetail() {
        assertEquals(MicrosoftLogin.StateMessage(State.MFA_NUMBER, "42"), MicrosoftLogin.parseState("state: mfa-number 42"))
        assertEquals(MicrosoftLogin.StateMessage(State.PASSWORD, ""), MicrosoftLogin.parseState("state: password"))
        assertEquals(
            MicrosoftLogin.StateMessage(State.ERROR, "Your password is incorrect."),
            MicrosoftLogin.parseState("state: error Your password is incorrect."),
        )
    }

    @Test
    fun ignoresOtherMessages() {
        assertNull(MicrosoftLogin.parseState("clicked \"stay signed in\""))
        assertNull(MicrosoftLogin.parseState("state: something-new"))
    }
}
