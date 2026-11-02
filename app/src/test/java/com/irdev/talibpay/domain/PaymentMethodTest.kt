package com.irdev.talibpay.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMethodTest {

    @Test
    fun `reads stored codes`() {
        PaymentMethod.entries.forEach {
            assertEquals(it, PaymentMethod.fromStored(it.code))
        }
    }

    @Test
    fun `reads legacy localized labels from older app versions`() {
        assertEquals(PaymentMethod.CASH, PaymentMethod.fromStored("نقدي"))
        assertEquals(PaymentMethod.CASH, PaymentMethod.fromStored("Cash"))
        assertEquals(PaymentMethod.BANK, PaymentMethod.fromStored(" بنكي "))
        assertEquals(PaymentMethod.MIXED, PaymentMethod.fromStored("جزء نقدي وجزء بنكي"))
        assertEquals(PaymentMethod.MIXED, PaymentMethod.fromStored("Part cash and part bank"))
        assertEquals(PaymentMethod.UNPAID, PaymentMethod.fromStored("Not Paid"))
    }

    @Test
    fun `unknown or empty values return null`() {
        assertNull(PaymentMethod.fromStored(""))
        assertNull(PaymentMethod.fromStored(null))
        assertNull(PaymentMethod.fromStored("Crypto"))
    }

    @Test
    fun `spinner positions round-trip, position zero is the placeholder`() {
        assertNull(PaymentMethod.fromSpinnerPosition(0))
        PaymentMethod.entries.forEach {
            assertEquals(it, PaymentMethod.fromSpinnerPosition(PaymentMethod.spinnerPositionOf(it)))
        }
        assertEquals(0, PaymentMethod.spinnerPositionOf(null))
    }
}
