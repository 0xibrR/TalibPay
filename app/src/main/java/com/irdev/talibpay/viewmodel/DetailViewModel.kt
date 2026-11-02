package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.RefundValidation
import com.irdev.talibpay.domain.validateRefund
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.R
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

    fun deletePayment(payment: Payment) {
        // دفعة أضافت رصيدًا للمحفظة لا تُحذف حتى لا يختل رصيد المحفظة
        if (payment.credited > 0) {
            send(UiEvent.Message(R.string.payment_locked_by_wallet))
            return
        }
        runAction {
            repository.deletePayment(studentId, payment.id)
            send(UiEvent.Message(R.string.payment_deleted))
        }
    }

    /** استرداد مبلغ من المحفظة للطالب (يُسجَّل كحركة، لا يُعدَّل أي سجل سابق). */
    fun refund(amountText: String, note: String) {
        val balance = student.value?.ledger?.walletBalance ?: 0.0
        val amount = when (val result = validateRefund(amountText, balance)) {
            is RefundValidation.Valid -> result.amount
            RefundValidation.InvalidAmount -> return send(UiEvent.Message(R.string.invalid_amount))
            is RefundValidation.ExceedsBalance -> return send(
                UiEvent.Message(R.string.refund_exceeds_balance, Money.format(result.balance))
            )
        }
        runAction {
            repository.refundFromWallet(studentId, amount, note)
            send(UiEvent.Message(R.string.refund_done))
        }
    }

    /** لا يُحذف مستحق له دفعات أو خصم من المحفظة، حتى لا تضيع حركات مالية. */
    fun deleteDue(due: DueSummary) {
        if (due.hasActivity) {
            send(UiEvent.Message(R.string.due_has_payments))
            return
        }
        runAction {
            repository.deleteCharge(studentId, due.charge.id)
            send(UiEvent.Message(R.string.due_deleted))
        }
    }

    fun deleteStudent() = runAction {
        repository.deleteStudent(studentId)
    }
}
