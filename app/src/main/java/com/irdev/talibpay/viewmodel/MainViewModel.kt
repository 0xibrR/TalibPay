package com.irdev.talibpay.viewmodel

import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    val summary: DashboardSummary = DashboardSummary()
) {
    val hasAnyStudent: Boolean get() = studentCount > 0
}

class MainViewModel : BaseViewModel() {

    private val repository = StudentRepository()
    private val query = MutableStateFlow("")

    private val allStudents = repository.observeStudents()
        .catch {
            handleError(it)
            emit(emptyList())
        }

    // المستمع على Firebase يعمل فقط أثناء ظهور الشاشة (+5 ثوانٍ لتغيير الاتجاه)
    val uiState: StateFlow<MainUiState> = combine(allStudents, query) { list, q ->
        MainUiState(
            loading = false,
            students = filter(list, q),
            studentCount = list.size,
            summary = DashboardSummary(
                cash = Money.sum(list.map { it.student.paidCash }),
                bank = Money.sum(list.map { it.student.paidBank }),
                paid = Money.sum(list.map { it.student.paid }),
                remaining = Money.sum(list.map { it.student.remaining }),
                total = Money.sum(list.map { it.student.total })
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

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

    private fun filter(list: List<StudentWithPayments>, query: String): List<StudentWithPayments> {
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
