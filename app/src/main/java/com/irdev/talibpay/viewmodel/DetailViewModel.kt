package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.util.Extras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class DetailViewModel(savedStateHandle: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    val studentId: String = savedStateHandle[Extras.STUDENT_ID] ?: ""

    /** بيانات الطالب الحية؛ null قبل التحميل. */
    val student: StateFlow<StudentWithPayments?> = repository.observeStudent(studentId)
        .onEach { if (it == null) send(UiEvent.Finished) } // حُذف من هذا الجهاز أو غيره
        .catch { handleError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deletePayment(paymentId: String) = runAction {
        repository.deletePayment(studentId, paymentId)
    }

    fun deleteStudent() = runAction {
        repository.deleteStudent(studentId)
    }
}
