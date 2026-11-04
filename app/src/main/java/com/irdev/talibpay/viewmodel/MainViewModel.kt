package com.irdev.talibpay.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.SimpleDate
import com.irdev.talibpay.domain.WalletEntryType
import com.irdev.talibpay.domain.entryType
import com.irdev.talibpay.domain.overdueDues
import com.irdev.talibpay.domain.overdueTotal
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.util.ToastKind
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
    val total: Double = 0.0,
    /** مجموع الـ refunds للطلاب من الـ wallets: مال خرج، فيُخصم من المحصّل. */
    val refunded: Double = 0.0
) {
    /** المحصّل فعلًا: نقدي + بنكي ناقص الـ refunds (مثل التقرير الشهري). */
    val collected: Double get() = Money.subtract(paid, refunded)
}

data class MainUiState(
    val loading: Boolean = true,
    val students: List<StudentWithPayments> = emptyList(),
    val studentCount: Int = 0,
    val summary: DashboardSummary = DashboardSummary(),
    /** كل الطلاب المتأخرين (بغض النظر عن البحث)، الأعلى مبلغًا أولًا. */
    val overdue: List<OverdueStudent> = emptyList(),
    val filter: StudentFilter = StudentFilter.ALL,
    val sort: StudentSort = StudentSort.DEFAULT,
    /** عدد الطلاب في كل filter (ضمن نتيجة البحث)، للعرض على الـ chips. */
    val filterCounts: Map<StudentFilter, Int> = emptyMap(),
    val archivedCount: Int = 0,
    /** كل الطلاب ومنهم المؤرشفون (للـ Excel export). */
    val everyone: List<StudentWithPayments> = emptyList()
) {
    val hasAnyStudent: Boolean get() = studentCount > 0
}

class MainViewModel : BaseViewModel() {

    private val repository = StudentRepository()
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StudentFilter.ALL)
    private val sort = MutableStateFlow(StudentSort.DEFAULT)

    init {
        // عند فتح التطبيق: إنشاء الـ monthly charges التي حان موعدها (ومنها أشهر فاتت بدون فتح التطبيق).
        // فشلها لا يمنع عرض البيانات، وتُعاد المحاولة في الـ daily worker وعند الفتح التالي.
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

    // الـ Firebase listener يعمل فقط أثناء ظهور الشاشة (+5 ثوانٍ تكفي للـ rotation)
    val uiState: StateFlow<MainUiState> = combine(allStudents, query, filter, sort) { all, q, f, s ->
        // المؤرشفون في شاشة الأرشيف فقط: لا يظهرون في القائمة ولا الـ summary ولا التذكير
        val list = all.filterNot { it.student.archived }
        val today = todaySimpleDate()
        val searched = search(list, q)
        MainUiState(
            loading = false,
            students = s.apply(searched.filter { f.matches(it, today) }, today, Locale.getDefault()),
            studentCount = list.size,
            archivedCount = all.size - list.size,
            everyone = all,
            filter = f,
            sort = s,
            filterCounts = StudentFilter.entries.associateWith { entry -> searched.count { entry.matches(it, today) } },
            summary = DashboardSummary(
                cash = Money.sum(list.map { it.student.paidCash }),
                bank = Money.sum(list.map { it.student.paidBank }),
                paid = Money.sum(list.map { it.student.paid }),
                remaining = Money.sum(list.map { it.student.remaining }),
                total = Money.sum(list.map { it.student.total }),
                refunded = Money.sum(
                    list.flatMap { it.walletEntries }.filter { it.entryType == WalletEntryType.REFUND }.map { it.amount }
                )
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

    /** Archive بالسحب: فوري لأنه قابل للـ undo (الحذف من ملف الطالب فقط). */
    fun archive(item: StudentWithPayments) = runAction {
        repository.archiveStudent(item.student.id)
        send(UiEvent.StudentArchived(item))
    }

    fun undoArchive(item: StudentWithPayments) = runAction {
        repository.undoArchive(item.student.id)
    }

    fun deleteAll() = runAction {
        repository.deleteAllStudents()
        send(UiEvent.Message(R.string.all_students_deleted, kind = ToastKind.SUCCESS))
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

/** طالب متأخر، مع الـ charges المتأخرة ومجموعها (لقائمة التذكير). */
data class OverdueStudent(
    val data: StudentWithPayments,
    val dues: List<DueSummary>,
    val total: Double
)

/** الطلاب المتأخرون، الأعلى مبلغًا متأخرًا أولًا. */
fun overdueStudents(list: List<StudentWithPayments>, today: SimpleDate): List<OverdueStudent> =
    list.mapNotNull { item ->
        val dues = item.ledger.overdueDues(today)
        if (dues.isEmpty()) null else OverdueStudent(item, dues, dues.overdueTotal())
    }.sortedByDescending { it.total }
