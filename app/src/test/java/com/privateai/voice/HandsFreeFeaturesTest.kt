package com.privateai.voice

import com.privateai.control.PhoneAction
import com.privateai.monitor.ThreatScanner
import com.privateai.multimodal.WakeWordHelper
import com.privateai.persona.RelationshipStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeFeaturesTest {

    @Test
    fun wakePhraseOnlyReturnsEmptyCommand() {
        assertEquals("", WakeWordHelper.commandAfterWakePhrase("Hey Jarvis"))
        assertEquals("", WakeWordHelper.commandAfterWakePhrase("jarvis"))
    }

    @Test
    fun wakePhraseWithCommandReturnsCommand() {
        assertEquals("open youtube", WakeWordHelper.commandAfterWakePhrase("Hey Jarvis, open youtube"))
    }

    @Test
    fun speechWithoutWakePhraseIsIgnored() {
        assertNull(WakeWordHelper.commandAfterWakePhrase("what a lovely day"))
        assertNull(WakeWordHelper.commandAfterWakePhrase("the jarvises are here"))
    }

    @Test
    fun parsesPhoneActions() {
        assertEquals(PhoneAction.OpenApp("youtube"), PhoneAction.parse("Open the YouTube app."))
        assertEquals(PhoneAction.GoBack, PhoneAction.parse("go back"))
        assertEquals(PhoneAction.GoHome, PhoneAction.parse("Home screen"))
        assertNull(PhoneAction.parse("explain quicksort"))
        assertEquals(
            PhoneAction.PlayMedia("radhima", com.privateai.control.MediaApp.SPOTIFY),
            PhoneAction.parse("play Radhima")
        )
        assertEquals(
            PhoneAction.PlayMedia("recent raj shamani podcast with sahar yousef", com.privateai.control.MediaApp.YOUTUBE),
            PhoneAction.parse("play recent Raj Shamani podcast with Sahar Yousef in youtube")
        )
        val battery = com.privateai.monitor.BatteryAlerts
        assertNull(battery.check(-1, 30, false))
        assertNull(battery.check(31, 31, false))
        assertEquals(true, battery.check(31, 30, false)?.contains("30 percent"))
        assertEquals(true, battery.check(79, 80, true)?.contains("80 percent"))
        assertNull(battery.check(79, 80, false))
        val audit = com.privateai.monitor.SecurityAudit
        assertNull(audit.spokenSummary(audit.findings(com.privateai.monitor.SecurityAudit.Snapshot(true, false, 10, emptyList(), emptyList()))))
        assertEquals(
            2,
            audit.findings(com.privateai.monitor.SecurityAudit.Snapshot(false, true, 10, emptyList(), emptyList())).size
        )
        assertEquals(PhoneAction.Flashlight(true), PhoneAction.parse("turn on flashlight"))
        assertEquals(PhoneAction.Flashlight(false), PhoneAction.parse("turn off the flashlight"))
        assertEquals(PhoneAction.AutoRotate(false), PhoneAction.parse("turn off autorotate"))
        assertEquals(PhoneAction.Volume(1), PhoneAction.parse("increase the volume"))
        assertEquals(PhoneAction.SetAlarm(5, 0), PhoneAction.parse("set up an alarm for 5 AM tomorrow morning"))
        assertEquals(PhoneAction.SetAlarm(17, 30), PhoneAction.parse("set an alarm for 5:30 pm"))
        assertEquals(PhoneAction.OpenApp("whatsapp"), PhoneAction.parse("open WhatsApp"))
        assertEquals(
            PhoneAction.AddEvent("Urgent", 11, 2, 14, 30, 16, 30),
            PhoneAction.parse("open the calendar and put an event on November 2nd, 2:30 to 4:30, name it urgent")
        )
        assertEquals(PhoneAction.OpenSettings(com.privateai.control.SettingsTarget.BLUETOOTH), PhoneAction.parse("turn on bluetooth"))
    }

    @Test
    fun flagsRiskyContentOnly() {
        assertTrue(ThreatScanner.scan("Please verify your account now").isNotEmpty())
        assertTrue(ThreatScanner.scan("visit http://192.168.4.4/login").isNotEmpty())
        assertTrue(ThreatScanner.scan("http://free-prize.tk/claim").isNotEmpty())
        assertTrue(ThreatScanner.scan("Read https://developer.android.com/guide today").isEmpty())
    }

    @Test
    fun quietHoursAndNudgeFallback() {
        assertTrue(ProactiveNudges.isQuietHour(23))
        assertTrue(ProactiveNudges.isQuietHour(3))
        assertFalse(ProactiveNudges.isQuietHour(12))
        assertTrue(ProactiveNudges.compose(emptyList()).isNotBlank())
    }

    @Test
    fun parentStyleAsksForMistakesAndGoalDrift() {
        val directive = RelationshipStyle.PARENT.styleDirective
        assertTrue(directive.startsWith("[STYLE: PARENT]"))
        assertTrue(directive.contains("mistakes"))
        assertTrue(directive.contains("goals"))
    }
}
