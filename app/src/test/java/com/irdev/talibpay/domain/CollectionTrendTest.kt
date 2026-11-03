package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionTrendTest {

    private val nov = YearMonth(2026, 11)

    @Test
    fun `window is the last six months ending now, oldest first, across a year boundary`() {
        val window = CollectionTrend.window(selected = nov, current = YearMonth(2027, 2))
        assertEquals(
            listOf(YearMonth(2026, 9), YearMonth(2026, 10), nov, YearMonth(2026, 12), YearMonth(2027, 1), YearMonth(2027, 2)),
            window
        )
    }

    @Test
    fun `selecting a month inside the window keeps it in place`() {
        assertEquals(CollectionTrend.window(nov, nov), CollectionTrend.window(YearMonth(2026, 7), nov))
    }

    @Test
    fun `selecting an older month moves the window to end at it`() {
        val window = CollectionTrend.window(YearMonth(2026, 3), nov)
        assertEquals(YearMonth(2025, 10), window.first())
        assertEquals(YearMonth(2026, 3), window.last())
    }

    @Test
    fun `each point has the month's collected money and billed dues`() {
        val student = Student(id = "s1", name = "Lana")
        val ledger = StudentLedger.build(
            listOf(
                Charge(id = "oct", amount = 100.0, period = "2026-10", createdAt = 1),
                Charge(id = "nov", amount = 100.0, period = "2026-11", createdAt = 2)
            ),
            listOf(
                Payment(id = "p1", chargeId = "oct", cash = 60.0, paymentDate = "05/10/2026"),
                // دفعة متأخرة عن أكتوبر تُحسب في نوفمبر لأنها استُلمت فيه
                Payment(id = "p2", chargeId = "oct", bank = 40.0, paymentDate = "02/11/2026"),
                Payment(id = "p3", chargeId = "nov", cash = 30.0, paymentDate = "03/11/2026")
            )
        )
        val points = CollectionTrend.build(listOf(student to ledger), listOf(YearMonth(2026, 9), YearMonth(2026, 10), nov))
        assertEquals(
            listOf(
                TrendPoint(YearMonth(2026, 9), 0.0, 0.0),
                TrendPoint(YearMonth(2026, 10), 60.0, 100.0),
                TrendPoint(nov, 70.0, 100.0)
            ),
            points
        )
    }
}
