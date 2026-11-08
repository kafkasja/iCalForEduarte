package dev.eduartesync.sync

import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.NoConnectionException
import dev.eduartesync.source.PortalTimeoutException
import dev.eduartesync.source.SignInPageException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncProblemTest {
    private fun message(e: Throwable) = SyncProblem.from(e).message

    @Test
    fun `network errors ask to check the connection`() {
        listOf(
            UnknownHostException("talnet-student.educus.nl"),
            ConnectException("refused"),
            SocketTimeoutException("read timed out"),
            NoConnectionException("net::ERR_INTERNET_DISCONNECTED"),
            IOException("wrapped", UnknownHostException("x")),
        ).forEach { assertTrue("$it", "internet connection" in message(it)) }
    }

    @Test
    fun `the app's own time limits say Eduarte was slow, not the internet`() {
        listOf(
            PortalTimeoutException("Eduarte stopped responding"),
            PortalTimeoutException("Eduarte did not switch to the week of 2026-11-09"),
            IOException("Eduarte page script did not finish in time"),
        ).forEach {
            assertTrue("$it", "didn't respond in time" in message(it))
            assertFalse("$it", "internet" in message(it))
        }
    }

    @Test
    fun `sign-in page errors are temporary`() {
        assertTrue("try again later" in message(SignInPageException("Too many attempts")))
        assertTrue(SyncProblem.isNetworkError(NoConnectionException("x")))
        assertFalse(SyncProblem.isNetworkError(SignInPageException("x")))
    }

    @Test
    fun `other failures`() {
        assertTrue("looked different" in message(AgendaFormatException("no days found")))
        assertTrue("Something went wrong" in message(IllegalStateException("boom")))
    }

    @Test
    fun `calendar write failures`() {
        assertTrue("calendar access" in SyncProblem.fromCalendar(SecurityException("no permission")).message)
        assertTrue("Couldn't write to your calendar" in SyncProblem.fromCalendar(IllegalStateException("db")).message)
    }

    @Test
    fun `details keep the technical error`() {
        val details = SyncProblem.from(PortalTimeoutException("Eduarte stopped responding")).details
        assertTrue(details, details.startsWith("PortalTimeoutException: Eduarte stopped responding"))
    }
}
