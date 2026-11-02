package com.irdev.talibpay.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {

    @Test
    fun `sum avoids floating point drift`() {
        assertEquals(0.3, Money.sum(listOf(0.1, 0.2)), 0.0)
        assertEquals(100.03, Money.sum(listOf(33.01, 33.01, 34.01)), 0.0)
    }

    @Test
    fun `subtract rounds to two decimals`() {
        assertEquals(0.1, Money.subtract(0.3, 0.2), 0.0)
    }

    @Test
    fun `parse accepts latin and arabic digits and separators`() {
        assertEquals(150.5, Money.parse("150.5")!!, 0.0)
        assertEquals(150.5, Money.parse(" ١٥٠٫٥ ")!!, 0.0)
        assertEquals(150.5, Money.parse("150,5")!!, 0.0)
        assertEquals(12.0, Money.parse("۱۲")!!, 0.0)
    }

    @Test
    fun `parse rejects empty, invalid and negative values`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse(null))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
    }

    @Test
    fun `format drops trailing zeros`() {
        assertEquals("1,250", Money.format(1250.0, Locale.US))
        assertEquals("12.5", Money.format(12.5, Locale.US))
    }
}
