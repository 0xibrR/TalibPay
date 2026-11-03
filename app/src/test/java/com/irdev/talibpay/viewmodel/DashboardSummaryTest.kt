package com.irdev.talibpay.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSummaryTest {

    @Test
    fun `collected is what was paid minus wallet refunds`() {
        val summary = DashboardSummary(cash = 1315.0, bank = 435.0, paid = 1750.0, refunded = 50.0)
        assertEquals(1700.0, summary.collected, 0.001)
    }

    @Test
    fun `without refunds collected equals paid`() {
        assertEquals(1750.0, DashboardSummary(paid = 1750.0).collected, 0.001)
    }
}
