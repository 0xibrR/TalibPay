package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.util.formatAmount
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.RefundValidation
import com.irdev.talibpay.domain.validateRefund
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.R
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.ToastKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class DetailViewModel(savedStateHandle: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    val studentId: String = savedStateHandle[Extras.STUDENT_ID] ?: ""

    /** بيانات الطالب live من Firebase؛ null قبل التحميل. */
    val student: StateFlow<StudentWithPayments?> = repository.observeStudent(studentId)
        .onEach { if (it == null) send(UiEvent.Finished) } // حُذف من هذا الجهاز أو من جهاز آخر
        .catch { handleError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deletePayment(payment: Payment) {
        // دفعة أضافت credit للـ wallet لا تُحذف حتى لا يختل رصيد الـ wallet
        if (payment.credited > 0) {
            send(UiEvent.Message(R.string.payment_locked_by_wallet))
            return
        }
        runAction {
            repository.deletePayment(studentId, payment.id)
            send(UiEvent.Message(R.string.payment_deleted, kind = ToastKind.SUCCESS))
        }
    }

    /** Refund من الـ wallet للطالب (يُسجَّل كحركة جديدة، ولا يُعدَّل أي سجل سابق). */
    fun refund(amountText: String, note: String) {
        val balance = student.value?.ledger?.walletBalance ?: 0.0
        val amount = when (val result = validateRefund(amountText, balance)) {
            is RefundValidation.Valid -> result.amount
            RefundValidation.InvalidAmount -> return send(UiEvent.Message(R.string.invalid_amount))
            is RefundValidation.ExceedsBalance -> return send(
                UiEvent.Message(R.string.refund_exceeds_balance, formatAmount(result.balance))
            )
        }
        runAction {
            repository.refundFromWallet(studentId, amount, note)
            send(UiEvent.Message(R.string.refund_done, kind = ToastKind.SUCCESS))
        }
    }

    /** لا يُحذف charge له دفعات أو خصم من الـ wallet، حتى لا تضيع حركات مالية. */
    fun deleteDue(due: DueSummary) {
        if (due.hasActivity) {
            send(UiEvent.Message(R.string.due_has_payments))
            return
        }
        runAction {
            repository.deleteCharge(studentId, due.charge.id)
            send(UiEvent.Message(R.string.due_deleted, kind = ToastKind.SUCCESS))
        }
    }

    fun archive() = runAction {
        repository.archiveStudent(studentId)
        send(UiEvent.Message(R.string.student_archived, kind = ToastKind.SUCCESS))
    }

    fun unarchive() = runAction {
        repository.unarchiveStudent(studentId)
        send(UiEvent.Message(R.string.student_unarchived, kind = ToastKind.SUCCESS))
    }

    fun deleteStudent() = runAction {
        repository.deleteStudent(studentId)
    }
}
