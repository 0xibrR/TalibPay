package com.irdev.talibpay.viewmodel

import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** الطلاب المؤرشفون، الأحدث أرشفة أولًا. null أثناء التحميل. */
class ArchiveViewModel : BaseViewModel() {

    private val repository = StudentRepository()

    val students: StateFlow<List<StudentWithPayments>?> = repository.observeStudents()
        .map { archivedStudents(it) }
        .catch {
            handleError(it)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Unarchive بالسحب (swipe): الـ monthly fee تُستأنف من اليوم. */
    fun restore(item: StudentWithPayments) = runAction {
        repository.unarchiveStudent(item.student.id)
        send(UiEvent.StudentRestored(item))
    }

    fun undoRestore(item: StudentWithPayments) = runAction {
        repository.archiveStudent(item.student.id)
    }
}

fun archivedStudents(list: List<StudentWithPayments>): List<StudentWithPayments> =
    list.filter { it.student.archived }.sortedByDescending { it.student.archivedAt }
