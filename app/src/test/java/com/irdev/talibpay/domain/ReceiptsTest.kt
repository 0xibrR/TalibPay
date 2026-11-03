package com.irdev.talibpay.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReceiptsTest {

    @Test
    fun `receipt number is short, readable and stable for the same payment`() {
        // مفاتيح Firebase تحتوي - و _ ؛ الرقم يأخذ آخر 6 حروف/أرقام بأحرف كبيرة
        assertEquals("R-OZK9Q2", Receipts.number("-OZx3_aOZK9Q2"))
        assertEquals("R-DEF123", Receipts.number("-Nabcdef123"))
        assertEquals(Receipts.number("-NabcDEF123"), Receipts.number("-NabcDEF123"))
    }

    @Test
    fun `different payments get different numbers`() {
        assertNotEquals(Receipts.number("-Nabc111111"), Receipts.number("-Nabc222222"))
    }

    @Test
    fun `short ids are padded to a fixed length`() {
        assertEquals("R-0000P1", Receipts.number("p1"))
    }
}
