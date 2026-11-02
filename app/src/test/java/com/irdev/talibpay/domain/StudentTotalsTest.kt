package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentTotalsTest {

    @Test
    fun `no payments gives zero totals`() {
        assertEquals(StudentTotals(0.0, 0.0, 0.0, 0.0, 0.0), StudentTotals.from(emptyList()))
    }

    @Test
    fun `totals are summed from all payments`() {
        val totals = StudentTotals.from(
            listOf(
                Payment(cash = 100.0, bank = 0.0, total = 500.0),
                Payment(cash = 50.0, bank = 150.0, total = 500.0)
            )
        )
        assertEquals(150.0, totals.paidCash, 0.0)
        assertEquals(150.0, totals.paidBank, 0.0)
        assertEquals(300.0, totals.paid, 0.0)
        assertEquals(500.0, totals.total, 0.0)
        assertEquals(200.0, totals.remaining, 0.0)
    }

    @Test
    fun `total fee is the highest fee recorded in any payment`() {
        val totals = StudentTotals.from(
            listOf(Payment(cash = 100.0, total = 400.0), Payment(cash = 100.0, total = 600.0))
        )
        assertEquals(600.0, totals.total, 0.0)
        assertEquals(400.0, totals.remaining, 0.0)
    }

    @Test
    fun `remaining never goes negative`() {
        val totals = StudentTotals.from(
            listOf(Payment(cash = 300.0, total = 200.0), Payment(bank = 100.0, total = 200.0))
        )
        assertEquals(0.0, totals.remaining, 0.0)
    }

    @Test
    fun `validatePayment accepts valid input`() {
        val result = validatePayment("100", "50", "200")
        assertEquals(PaymentValidation.Valid(100.0, 50.0, 200.0), result)
    }

    @Test
    fun `validatePayment rejects paid above total`() {
        assertEquals(PaymentValidation.PaidExceedsTotal, validatePayment("150", "100", "200"))
    }

    @Test
    fun `validatePayment rejects invalid amounts`() {
        assertTrue(validatePayment("abc", "0", "100") is PaymentValidation.InvalidAmount)
        assertTrue(validatePayment("10", "-1", "100") is PaymentValidation.InvalidAmount)
        assertTrue(validatePayment("10", "0", "") is PaymentValidation.InvalidAmount)
    }
}
