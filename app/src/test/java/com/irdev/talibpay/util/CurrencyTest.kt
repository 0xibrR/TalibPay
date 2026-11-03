package com.irdev.talibpay.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyTest {

    @Test
    fun `saved currency is restored by its code`() {
        assertEquals(Currency.USD, Currency.fromCode("USD"))
        assertEquals(Currency.JOD, Currency.fromCode("JOD"))
    }

    @Test
    fun `unknown or missing currency falls back to shekel`() {
        assertEquals(Currency.ILS, Currency.fromCode(null))
        assertEquals(Currency.ILS, Currency.fromCode("XYZ"))
        assertEquals(Currency.ILS, Currency.DEFAULT)
    }

    @Test
    fun `every currency has a distinct symbol`() {
        val symbols = Currency.entries.map { it.symbol }
        assertEquals(symbols.size, symbols.toSet().size)
        assertTrue(symbols.none { it.isBlank() })
    }

    @Test
    fun `amount is followed by the currency symbol without a line break`() {
        // بدون تحميل الإعدادات تُستخدم العملة الافتراضية
        assertTrue(formatAmount(150.0).endsWith(" ₪"))
    }
}
