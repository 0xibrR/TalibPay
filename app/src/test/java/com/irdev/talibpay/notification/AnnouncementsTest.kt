package com.irdev.talibpay.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementsTest {

    @Test
    fun `arabic app language uses the arabic topic, anything else the english one`() {
        assertEquals("announcements_ar", Announcements.topicFor("ar"))
        assertEquals("announcements_en", Announcements.topicFor("en"))
        assertEquals("announcements_en", Announcements.topicFor("fr"))
        assertEquals("announcements_en", Announcements.topicFor(null))
    }

    @Test
    fun `only http and https links are opened from a notification`() {
        assertEquals("https://play.google.com/store/apps/details?id=com.irdev.talibpay",
            Announcements.safeUrl(" https://play.google.com/store/apps/details?id=com.irdev.talibpay "))
        assertEquals("http://example.com", Announcements.safeUrl("http://example.com"))
        assertNull(Announcements.safeUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(Announcements.safeUrl("file:///sdcard/x.apk"))
        assertNull(Announcements.safeUrl("javascript:alert(1)"))
        assertNull(Announcements.safeUrl(""))
        assertNull(Announcements.safeUrl(null))
    }
}
