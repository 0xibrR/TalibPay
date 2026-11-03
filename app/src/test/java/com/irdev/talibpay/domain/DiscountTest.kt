package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountTest {

    @Test
    fun `percent discount takes a share of the fee`() {
        val sibling = StudentDiscount(DiscountType.PERCENT, 20.0, "أخ")
        assertEquals(20.0, sibling.amountOff(100.0), 0.001)
        assertEquals(80.0, sibling.netFee(100.0), 0.001)
        // تقريب لأقرب قرش
        assertEquals(11.11, StudentDiscount(DiscountType.PERCENT, 33.33).amountOff(33.33), 0.001)
    }

    @Test
    fun `full scholarship makes the fee zero`() {
        assertEquals(0.0, StudentDiscount(DiscountType.PERCENT, 100.0).netFee(150.0), 0.001)
    }

    @Test
    fun `fixed discount never goes below zero`() {
        assertEquals(70.0, StudentDiscount(DiscountType.AMOUNT, 30.0).netFee(100.0), 0.001)
        // لو قلّت الرسوم لاحقًا عن مبلغ الخصم: الرسوم صفر لا سالبة
        assertEquals(0.0, StudentDiscount(DiscountType.AMOUNT, 30.0).netFee(20.0), 0.001)
    }

    @Test
    fun `no discount leaves the fee as is`() {
        assertFalse(StudentDiscount.NONE.isActive)
        assertEquals(100.0, StudentDiscount.NONE.netFee(100.0), 0.001)
        assertFalse(StudentDiscount(DiscountType.PERCENT, 0.0).isActive)
    }

    @Test
    fun `discount is read back from the saved student fields`() {
        val student = Student(discountType = "PERCENT", discountValue = 25.0, discountReason = "منحة")
        assertEquals(StudentDiscount(DiscountType.PERCENT, 25.0, "منحة"), student.discount)
        assertFalse(Student(discountType = "OLD").discount.isActive)
    }

    @Test
    fun `validation`() {
        assertEquals(DiscountValidation.Valid(StudentDiscount.NONE), validateDiscount(null, "50", "", 100.0))
        assertEquals(DiscountValidation.InvalidValue, validateDiscount(DiscountType.PERCENT, "", "", 100.0))
        assertEquals(DiscountValidation.InvalidValue, validateDiscount(DiscountType.PERCENT, "0", "", 100.0))
        assertEquals(DiscountValidation.PercentOver100, validateDiscount(DiscountType.PERCENT, "120", "", 100.0))
        assertEquals(DiscountValidation.ExceedsFee, validateDiscount(DiscountType.AMOUNT, "150", "", 100.0))
        val valid = validateDiscount(DiscountType.AMOUNT, "30", "  أخ  ", 100.0)
        assertEquals(DiscountValidation.Valid(StudentDiscount(DiscountType.AMOUNT, 30.0, "أخ")), valid)
    }

    @Test
    fun `report adds up the discounts given in the month`() {
        val ledger = StudentLedger.build(
            listOf(
                Charge(id = "auto-2026-11", amount = 80.0, discount = 20.0, period = "2026-11", createdAt = 1),
                Charge(id = "auto-2026-10", amount = 80.0, discount = 20.0, period = "2026-10", createdAt = 0)
            ),
            emptyList()
        )
        val report = MonthlyReport.build(listOf(Student(id = "s") to ledger), YearMonth(2026, 11))
        assertEquals(80.0, report.billed, 0.001)
        assertEquals(20.0, report.discounts, 0.001)
        assertTrue(report.unpaid.single().remaining == 80.0)
    }
}
