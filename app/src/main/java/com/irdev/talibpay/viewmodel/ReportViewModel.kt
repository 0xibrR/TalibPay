package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.MonthlyReport
import com.irdev.talibpay.domain.YearMonth
import com.irdev.talibpay.domain.todaySimpleDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** التقرير الشهري للشهر المختار (الحالي افتراضيًا)؛ null أثناء التحميل. */
class ReportViewModel(private val savedState: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    private val currentMonth: YearMonth get() = todaySimpleDate().yearMonth

    // الشهر يبقى بعد تدوير الشاشة أو إغلاق التطبيق في الخلفية
    private val month = savedState.getStateFlow(KEY_MONTH, currentMonth.key)

    val report: StateFlow<MonthlyReport?> = combine(
        repository.observeStudents().catch {
            handleError(it)
            emit(emptyList())
        },
        month
    ) { students, key ->
        MonthlyReport.build(
            students.map { it.student to it.ledger },
            YearMonth.parse(key) ?: currentMonth
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

    private companion object {
        const val KEY_MONTH = "month"
    }
}
