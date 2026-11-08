package dev.eduartesync.auth

import dev.eduartesync.auth.MicrosoftLogin.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun pageErrorsAreNotLoginFailures() {
        val message = MicrosoftLogin.parseState("state: page-error Too many attempts. Try again later.")
        assertEquals(MicrosoftLogin.StateMessage(State.PAGE_ERROR, "Too many attempts. Try again later."), message)
        assertFalse(State.PAGE_ERROR.needsUser)
        assertFalse(State.ERROR.needsUser)
    }

    @Test
    fun stuckOnEmailOrPasswordMeansPassword() {
        assertTrue(State.EMAIL.asksForPassword)
        assertTrue(State.PASSWORD.asksForPassword)
        assertFalse(State.MFA_NUMBER.asksForPassword)
        assertFalse(State.MFA_APPROVE.asksForPassword)
        assertFalse(State.ERROR.asksForPassword)
    }

    @Test
    fun scriptOnlyTreatsFieldErrorsAsLoginFailure() {
        val script = MicrosoftLogin.script(pickAccount = true)
        assertTrue(script.contains("var fieldError = text('#passwordError') || text('#usernameError');"))
        assertTrue(script.contains("var pageError = text('#idTD_Error') || text('.alert-error');"))
        assertTrue(script.contains("report('page-error '"))
    }

    @Test
    fun ignoresOtherMessages() {
        assertNull(MicrosoftLogin.parseState("clicked \"stay signed in\""))
        assertNull(MicrosoftLogin.parseState("state: something-new"))
    }
}
