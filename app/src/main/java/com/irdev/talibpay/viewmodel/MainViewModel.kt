package com.irdev.talibpay.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.SimpleDate
import com.irdev.talibpay.domain.overdueDues
import com.irdev.talibpay.domain.overdueTotal
import com.irdev.talibpay.domain.todaySimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

data class DashboardSummary(
    val cash: Double = 0.0,
    val bank: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    val total: Double = 0.0
)

data class MainUiState(
    val loading: Boolean = true,
    val students: List<StudentWithPayments> = emptyList(),
    val studentCount: Int = 0,
    val summary: DashboardSummary = DashboardSummary(),
    /** كل الطلاب الذين عليهم مستحقات متأخرة (بغض النظر عن البحث)، الأعلى مبلغًا أولًا. */
    val overdue: List<OverdueStudent> = emptyList(),
    val filter: StudentFilter = StudentFilter.ALL,
    val sort: StudentSort = StudentSort.DEFAULT,
    /** عدد الطلاب في كل فلتر (ضمن نتيجة البحث)، للعرض على أزرار التصفية. */
    val filterCounts: Map<StudentFilter, Int> = emptyMap()
) {
    val hasAnyStudent: Boolean get() = studentCount > 0
}

class MainViewModel : BaseViewModel() {

    private val repository = StudentRepository()
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StudentFilter.ALL)
    private val sort = MutableStateFlow(StudentSort.DEFAULT)

    init {
        // عند فتح التطبيق: إنشاء الرسوم الشهرية التي حان موعدها (ومنها أشهر فاتت دون فتح التطبيق).
        // فشلها لا يمنع عرض البيانات، وستُعاد المحاولة في المهمة اليومية وعند الفتح التالي.
        viewModelScope.launch {
            try {
                repository.generateMonthlyDues()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MainViewModel", "Monthly dues generation failed", e)
            }
        }
    }

    private val allStudents = repository.observeStudents()
        .catch {
            handleError(it)
            emit(emptyList())
        }

    // المستمع على Firebase يعمل فقط أثناء ظهور الشاشة (+5 ثوانٍ لتغيير الاتجاه)
    val uiState: StateFlow<MainUiState> = combine(allStudents, query, filter, sort) { list, q, f, s ->
        val today = todaySimpleDate()
        val searched = search(list, q)
        MainUiState(
            loading = false,
            students = s.apply(searched.filter { f.matches(it, today) }, today, Locale.getDefault()),
            studentCount = list.size,
            filter = f,
            sort = s,
            filterCounts = StudentFilter.entries.associateWith { entry -> searched.count { entry.matches(it, today) } },
            summary = DashboardSummary(
                cash = Money.sum(list.map { it.student.paidCash }),
                bank = Money.sum(list.map { it.student.paidBank }),
                paid = Money.sum(list.map { it.student.paid }),
                remaining = Money.sum(list.map { it.student.remaining }),
                total = Money.sum(list.map { it.student.total })
            ),
            overdue = overdueStudents(list, today)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setFilter(value: StudentFilter) {
        filter.value = value
    }

    fun setSort(value: StudentSort) {
        sort.value = value
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun deleteStudent(item: StudentWithPayments) = runAction {
        repository.deleteStudent(item.student.id)
        send(UiEvent.StudentDeleted(item))
    }

    fun undoDelete(item: StudentWithPayments) = runAction {
        repository.restoreStudent(item)
        send(UiEvent.Message(R.string.restored_successfully_with_name, item.student.name))
    }

    fun deleteAll() = runAction {
        repository.deleteAllStudents()
        send(UiEvent.Message(R.string.delete_all_note))
    }

    private fun search(list: List<StudentWithPayments>, query: String): List<StudentWithPayments> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { (student) ->
            student.name.contains(q, ignoreCase = true) ||
                    student.phoneNumber.contains(q) ||
                    Money.format(student.remaining).contains(q) ||
                    student.remaining.toString().contains(q)
        }
    }
}

/** طالب عليه مستحقات متأخرة، مع هذه المستحقات ومجموعها (لقائمة التذكير). */
data class OverdueStudent(
    val data: StudentWithPayments,
    val dues: List<DueSummary>,
    val total: Double
)

/** الطلاب المتأخرون مرتبين من الأعلى مبلغًا متأخرًا. */
fun overdueStudents(list: List<StudentWithPayments>, today: SimpleDate): List<OverdueStudent> =
    list.mapNotNull { item ->
        val dues = item.ledger.overdueDues(today)
        if (dues.isEmpty()) null else OverdueStudent(item, dues, dues.overdueTotal())
    }.sortedByDescending { it.total }
