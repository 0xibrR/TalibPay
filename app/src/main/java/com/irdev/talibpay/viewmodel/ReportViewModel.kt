package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.CollectionTrend
import com.irdev.talibpay.domain.MonthlyReport
import com.irdev.talibpay.domain.TrendPoint
import com.irdev.talibpay.domain.YearMonth
import com.irdev.talibpay.domain.todaySimpleDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** تقرير الشهر المختار مع مخطط التحصيل للأشهر الأخيرة. */
data class ReportState(val report: MonthlyReport, val trend: List<TrendPoint>)

/** التقرير الشهري للشهر المختار (الحالي افتراضيًا)؛ null أثناء التحميل. */
class ReportViewModel(private val savedState: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    private val currentMonth: YearMonth get() = todaySimpleDate().yearMonth

    // الشهر يبقى بعد تدوير الشاشة أو إغلاق التطبيق في الخلفية
    private val month = savedState.getStateFlow(KEY_MONTH, currentMonth.key)

    val state: StateFlow<ReportState?> = combine(
        repository.observeStudents().catch {
            handleError(it)
            emit(emptyList())
        },
        month
    ) { students, key ->
        val selected = YearMonth.parse(key) ?: currentMonth
        val data = students.map { it.student to it.ledger }
        ReportState(
            MonthlyReport.build(data, selected),
            CollectionTrend.build(data, CollectionTrend.window(selected, currentMonth))
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** لا تقارير لأشهر لم تأتِ بعد. */
    fun canGoNext(month: YearMonth): Boolean = month < currentMonth

    fun previousMonth() {
        val current = YearMonth.parse(month.value) ?: currentMonth
        savedState[KEY_MONTH] = current.previous().key
    }

    fun nextMonth() {
        val current = YearMonth.parse(month.value) ?: currentMonth
        if (canGoNext(current)) savedState[KEY_MONTH] = current.next().key
    }

    /** الضغط على شهر في المخطط. */
    fun selectMonth(value: YearMonth) {
        if (value <= currentMonth) savedState[KEY_MONTH] = value.key
    }

    private companion object {
        const val KEY_MONTH = "month"
    }
}
